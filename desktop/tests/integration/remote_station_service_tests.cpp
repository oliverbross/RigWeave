// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/desktop/RemoteStationService.hpp"
#include "rigweave/desktop/RemoteStationClient.hpp"

#include <QHash>
#include <QFile>
#include <QCryptographicHash>
#include <QDateTime>
#include <QJsonDocument>
#include <QTcpServer>
#include <QUuid>
#include <QtTest>

using namespace rigweave::desktop;

class MemoryVault final : public DesktopCredentialVault {
public:
  using DesktopCredentialVault::DesktopCredentialVault;
  bool write(const QString &alias, const QString &, const QString &secret, QString *) override {
    values[alias] = secret; return true;
  }
  std::optional<QString> read(const QString &alias, QString *) const override {
    const auto found = values.constFind(alias);
    return found == values.cend() ? std::nullopt : std::optional<QString>(*found);
  }
  bool remove(const QString &alias, QString *) override { return values.remove(alias) > 0; }
  QHash<QString, QString> values;
};

class RemoteStationServiceTests final : public QObject {
  Q_OBJECT
private slots:
  void webObserverGoldenFixturesDeclareSupportedAndRefusedAgentVersions() {
    const auto load = [](const QString &name) {
      QFile file(QStringLiteral(RIGWEAVE_REMOTE_FIXTURES_DIR) + "/" + name);
      if (!file.open(QIODevice::ReadOnly)) return QJsonObject{};
      return QJsonDocument::fromJson(file.readAll()).object();
    };
    const QJsonObject supported = load("observer-hello-supported.json");
    const QJsonObject unsupported = load("observer-hello-unsupported-agent.json");
    QCOMPARE(supported.value("kind").toString(), QString("observer.hello"));
    QCOMPARE(supported.value("agentProtocol").toObject().value("major").toInt(), 1);
    QCOMPARE(supported.value("mediaProtocol").toObject().value("major").toInt(), 1);
    QCOMPARE(unsupported.value("agentProtocol").toObject().value("major").toInt(), 2);
  }

  void defaultsAreStoppedAndDisarmed() {
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    QCOMPARE(service.running(), false);
    QCOMPARE(service.sessionCount(), 0);
    QCOMPARE(service.configuration().value("remoteTxPolicy").toBool(), false);
    QCOMPARE(service.configuration().value("rotatorPolicy").toBool(), false);
    QVERIFY(!service.armThirdPartyWriter());
  }

  void secureLoopbackLifecycleCreatesPrivateIdentityAndBoundedOffer() {
    QTcpServer probe;
    QVERIFY(probe.listen(QHostAddress::LocalHost, 0));
    const quint16 port = probe.serverPort(); probe.close();
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    QVERIFY(service.applyLocalSettings({{"enabled", true}, {"stationName", "Fixture Station"},
        {"listenAddress", "127.0.0.1"}, {"port", port}, {"lanEnabled", false}}));
    QString error;
    QVERIFY2(service.start(&error), qPrintable(error));
    QVERIFY(service.running());
    const QVariantMap health = service.health();
    QCOMPARE(health.value("protocolVersion").toInt(), 1);
    QCOMPARE(health.value("certificateSha256").toString().size(), 64);
    const QVariantMap offer = service.createPairingOffer("OPERATOR");
    QCOMPARE(offer.value("version").toInt(), 1);
    QCOMPARE(offer.value("defaultRole").toString(), QString("OPERATOR"));
    QVERIFY(offer.value("nonce").toString().size() >= 32);
    QVERIFY(vault.values.size() == 2);
    const QString serialized = QString::fromUtf8(QJsonDocument::fromVariant(service.configuration()).toJson());
    QVERIFY(!serialized.contains("PRIVATE KEY"));
    service.stop();
    QVERIFY(!service.running());
  }

  void malformedStoredDeviceFailsClosed() {
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    QString error;
    const QVariantMap badDevice{{"role", "ADMIN"}, {"publicKeyPem", ""}, {"revoked", false}};
    QVERIFY(!service.restoreConfiguration({{"schema", 1}, {"stationName", "Station"},
        {"port", 7443}, {"pairedDevices", QVariantMap{{"device-12345678", badDevice}}}}, &error));
    QVERIFY(!error.isEmpty());
  }

  void localHubObserverIdentityStaysInVaultAndSignsBoundedChallenges() {
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    QString error;
    const QVariantMap identity = service.hubObserverIdentity(&error);
    QVERIFY2(!identity.isEmpty(), qPrintable(error));
    QVERIFY(identity.value("deviceId").toString().startsWith("hub-"));
    QVERIFY(identity.value("publicKeyPem").toString().contains("PUBLIC KEY"));
    const QString stationId = service.configuration().value("stationId").toString();
    const QString signature = service.signHubObserverChallenge(
        (stationId + "|pairing-fixture|" + identity.value("deviceId").toString()).toUtf8(), &error);
    QVERIFY2(!signature.isEmpty(), qPrintable(error));
    QVERIFY(vault.values.contains("rigweave.remote.hub-observer.signing-key"));
    const QString serialized = QString::fromUtf8(QJsonDocument::fromVariant(service.configuration()).toJson());
    QVERIFY(!serialized.contains("PRIVATE KEY"));
    QVERIFY(service.signHubObserverChallenge("wrong-station|challenge", &error).isEmpty());
    QVERIFY(!service.observerJournal().isEmpty());
  }

  void opaqueDomainJournalIsBoundedHashedAndIdempotentlyAcknowledged() {
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    const QByteArray opaqueCiphertext("authenticated-encrypted-fixture");
    const QString hash = QString::fromLatin1(QCryptographicHash::hash(opaqueCiphertext, QCryptographicHash::Sha256).toHex());
    const QDateTime created = QDateTime::currentDateTimeUtc();
    QVariantMap envelope{{"eventId", QUuid::createUuid().toString(QUuid::WithoutBraces)},
        {"stationId", "station-fixture"}, {"applicationId", "application-service-fixture"},
        {"origin", "APPLICATION_SERVICE_OUTAGE"}, {"createdUtc", created.toString(Qt::ISODateWithMs)},
        {"expiresUtc", created.addDays(2).toString(Qt::ISODateWithMs)}, {"payloadSchema", "rigweave.qso-event"},
        {"payloadVersion", 1}, {"protection", "APPLICATION_SERVICE_AEAD_V1"},
        {"ciphertextBase64", QString::fromLatin1(opaqueCiphertext.toBase64())}, {"hashSha256", hash}};
    QString error;
    QVERIFY2(service.appendDomainJournalEnvelope(envelope, &error), qPrintable(error));
    QVERIFY(service.appendDomainJournalEnvelope(envelope, &error));
    QCOMPARE(service.domainJournal().size(), 1);
    QCOMPARE(service.domainJournal().front().toMap().value("acknowledgmentState").toString(), QString("PENDING"));
    QVERIFY(!QString::fromUtf8(QJsonDocument::fromVariant(service.configuration()).toJson()).contains("callsign"));
    QVERIFY(!service.acknowledgeDomainJournalEvent(envelope.value("eventId").toString(), QString(64, '0'), &error));
    QVERIFY(service.acknowledgeDomainJournalEvent(envelope.value("eventId").toString(), hash, &error));
    QVERIFY(service.acknowledgeDomainJournalEvent(envelope.value("eventId").toString(), hash, &error));
    QCOMPARE(service.domainJournal().front().toMap().value("acknowledgmentState").toString(), QString("ACKNOWLEDGED"));
    QCOMPARE(service.health().value("pendingDomainJournalEntries").toInt(), 0);

    QVariantMap invalid = envelope;
    invalid["eventId"] = QUuid::createUuid().toString(QUuid::WithoutBraces);
    invalid["ciphertextBase64"] = QString::fromLatin1(QByteArray(16 * 1024 + 1, 'x').toBase64());
    QVERIFY(!service.appendDomainJournalEnvelope(invalid, &error));
  }

  void debugNoRadioForcesLoopbackAndDisablesSideChannels() {
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    QVERIFY(service.restoreConfiguration({{"schema", 1}, {"stationName", "Station"},
        {"port", 7443}, {"listenAddress", "0.0.0.0"}, {"lanEnabled", true},
        {"rigctldEnabled", true}, {"tciEnabled", true}}, nullptr));
    service.setDebugNoRadio(true);
    const QVariantMap config = service.configuration();
    QCOMPARE(config.value("listenAddress").toString(), QString("127.0.0.1"));
    QCOMPARE(config.value("lanEnabled").toBool(), false);
    QCOMPARE(config.value("rigctldEnabled").toBool(), false);
    QCOMPARE(config.value("tciEnabled").toBool(), false);
  }

  void m6WorkflowAuthorityIsGenerationBoundedAndPhysicallyInert() {
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    service.setDebugNoRadio(true);
    const QVariantMap state = service.workflowState();
    QCOMPARE(state.value("protocol").toMap().value("minor").toInt(), 2);
    QCOMPARE(state.value("source").toString(), QString("DEMO · NO RADIO"));
    const QVariantMap context = state.value("context").toMap();
    auto command = [&](QString id, QString domain, QString action, QVariantMap arguments = {}) {
      return QVariantMap{{"kind", "command"}, {"_trustedOperator", true},
          {"_operatorSessionId", "stationd-test-operator"}, {"command", QVariantMap{
          {"protocol", QVariantMap{{"major", 1}, {"minor", 2}}},
          {"requestId", "request-" + id}, {"idempotencyKey", "idempotency-" + id},
          {"domain", domain}, {"action", action},
          {"contextGeneration", context.value("contextGeneration")},
          {"agentGeneration", context.value("agentGeneration")},
          {"expiresMs", QString::number(QDateTime::currentMSecsSinceEpoch() + 10'000)},
          {"reason", "deterministic Qt integration test"}, {"arguments", arguments}}}};
    };
    QCOMPARE(service.workflowAdmin(command("tx-arm-01", "digi", "arm.tx")).value("code").toString(),
             QString("DEMO_TX_ACCEPTANCE_ARMED"));
    const QVariantMap tx = service.workflowAdmin(command("tx-send-1", "digi", "digi.tx"));
    QCOMPARE(tx.value("accepted").toBool(), true);
    QCOMPARE(tx.value("readback").toMap().value("rf").toString(), QString("false"));
    QCOMPARE(service.workflowAdmin(command("rot-arm1", "rotator", "arm.rotator")).value("accepted").toBool(), true);
    const QVariantMap movement = service.workflowAdmin(command("rot-move", "rotator", "rotator.move",
        {{"azimuth", "90"}, {"elevation", "10"}}));
    QCOMPARE(movement.value("readback").toMap().value("physicalMovement").toString(), QString("false"));
    service.globalStop();
    QVERIFY(service.workflowState().value("authority").toMap().value("txArmId").toString().isEmpty());

    RemoteStationService physical(&vault, nullptr, nullptr, nullptr);
    const QVariantMap physicalContext = physical.workflowState().value("context").toMap();
    QVariantMap physicalCommand = command("physical", "digi", "arm.tx");
    QVariantMap envelope = physicalCommand.value("command").toMap();
    envelope["contextGeneration"] = physicalContext.value("contextGeneration");
    envelope["agentGeneration"] = physicalContext.value("agentGeneration");
    physicalCommand["command"] = envelope;
    QCOMPARE(physical.workflowAdmin(physicalCommand).value("code").toString(),
             QString("TX_UNAVAILABLE_PHYSICAL_ACCEPTANCE_REQUIRED"));
  }

  void rawIqRequiresExplicitHostEnableAndDoesNotAutoRestore() {
    MemoryVault vault;
    RemoteStationService service(&vault, nullptr, nullptr, nullptr);
    QVERIFY(service.applyLocalSettings({{"rawIqEnabled", true}, {"audioChannels", 2}}));
    QCOMPARE(service.configuration().value("rawIqEnabled").toBool(), true);
    QCOMPARE(service.configuration().value("audioChannels").toInt(), 2);

    RemoteStationService restored(&vault, nullptr, nullptr, nullptr);
    QString error;
    QVERIFY2(restored.restoreConfiguration(service.configuration(), &error), qPrintable(error));
    QCOMPARE(restored.configuration().value("rawIqEnabled").toBool(), false);
    QCOMPARE(restored.configuration().value("audioChannels").toInt(), 2);
  }

  void remoteClientProfilesValidateAndRestoreDisconnected() {
    MemoryVault vault;
    RemoteStationClient client(&vault, nullptr);
    QString error;
    const QVariantMap profile{{"stationId", "station-12345678"},
        {"name", "Remote fixture"}, {"host", "station.example"},
        {"port", 7443}, {"certificateSha256", QString(64, 'a')},
        {"deviceId", "device-12345678"}, {"role", "OPERATOR"}};
    QVERIFY2(client.restoreConfiguration({{"profiles", QVariantList{profile}},
        {"selectedStationId", "station-12345678"}}, &error), qPrintable(error));
    QCOMPARE(client.state(), QString("Disconnected"));
    QCOMPARE(client.connected(), false);
    QCOMPARE(client.certificatePinned(), false);
    QCOMPARE(client.writerLease(), false);
    QCOMPARE(client.profiles().size(), 1);
    QVERIFY(client.health().value("tx").toString().contains("physical acceptance"));
  }

  void malformedRemoteClientProfileFailsClosed() {
    MemoryVault vault;
    RemoteStationClient client(&vault, nullptr);
    QString error;
    QVERIFY(!client.restoreConfiguration({{"profiles", QVariantList{
        QVariantMap{{"stationId", "bad"}, {"host", ""},
                    {"certificateSha256", "not-a-pin"}}}}}, &error));
    QVERIFY(!error.isEmpty());
    QCOMPARE(client.connected(), false);
  }
};

QTEST_MAIN(RemoteStationServiceTests)
#include "remote_station_service_tests.moc"

// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/desktop/RemoteStationService.hpp"
#include "rigweave/desktop/RemoteStationClient.hpp"

#include <QHash>
#include <QFile>
#include <QJsonDocument>
#include <QTcpServer>
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

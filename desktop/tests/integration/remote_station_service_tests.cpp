// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/desktop/RemoteStationService.hpp"

#include <QHash>
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
};

QTEST_MAIN(RemoteStationServiceTests)
#include "remote_station_service_tests.moc"

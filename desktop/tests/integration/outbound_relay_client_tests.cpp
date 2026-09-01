// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/desktop/OutboundRelayClient.hpp"

#include <QSignalSpy>
#include <QtTest>
#include <openssl/evp.h>
#include <openssl/pem.h>
#include <memory>

using namespace rigweave::desktop;
namespace {
QString privateKeyPem() {
  std::unique_ptr<EVP_PKEY_CTX, decltype(&EVP_PKEY_CTX_free)> context(EVP_PKEY_CTX_new_id(EVP_PKEY_EC, nullptr), EVP_PKEY_CTX_free);
  EVP_PKEY *raw{};
  if (!context || EVP_PKEY_keygen_init(context.get()) != 1 ||
      EVP_PKEY_CTX_set_ec_paramgen_curve_nid(context.get(), NID_X9_62_prime256v1) != 1 ||
      EVP_PKEY_keygen(context.get(), &raw) != 1) return {};
  std::unique_ptr<EVP_PKEY, decltype(&EVP_PKEY_free)> key(raw, EVP_PKEY_free);
  std::unique_ptr<BIO, decltype(&BIO_free)> bio(BIO_new(BIO_s_mem()), BIO_free);
  if (!bio || PEM_write_bio_PrivateKey(bio.get(), key.get(), nullptr, nullptr, 0, nullptr, nullptr) != 1) return {};
  char *data{}; const long size = BIO_get_mem_data(bio.get(), &data);
  return QString::fromUtf8(data, static_cast<qsizetype>(size));
}
}

class OutboundRelayClientTests final : public QObject {
  Q_OBJECT
private slots:
  void failsClosedWithoutWssOrVaultKey() {
    FakeCredentialVault vault; OutboundRelayClient client(&vault);
    client.configure({QUrl("ws://localhost:9999"), "station", "registration", "key", "alias", "sha"},
                     [](const QString &, const QVariantMap &) { return QVariantMap{}; });
    QString error; QVERIFY(!client.start(&error)); QCOMPARE(error, QString("Outbound relay requires a complete wss:// configuration"));
  }
  void challengeUsesVaultAndRpcIsAllowlisted() {
    FakeCredentialVault vault; QString error;
    QVERIFY(vault.write("relay-key", "test only", privateKeyPem(), &error));
    OutboundRelayClient client(&vault);
    client.configure({QUrl("wss://relay.invalid/agent"), "station", "registration", "key", "relay-key", "sha"},
                     [](const QString &method, const QVariantMap &) { return QVariantMap{{"handled", method}}; });
    const QString challenge = QString::fromLatin1(QByteArray(32, 'x').toBase64(QByteArray::Base64UrlEncoding | QByteArray::OmitTrailingEquals));
    const QJsonObject proof = client.processControlFrame({{"kind", "relay.challenge"}, {"challengeId", "challenge-1"}, {"challenge", challenge}});
    QCOMPARE(proof.value("kind").toString(), QString("relay.authenticate"));
    QVERIFY(!proof.value("signature").toString().isEmpty());
    client.processControlFrame({{"kind", "relay.accepted"}});
    const QJsonObject accepted = client.processControlFrame({{"kind", "rpc.request"}, {"requestId", "1"}, {"method", "system.health"}, {"payload", QJsonObject{}}});
    QVERIFY(accepted.value("ok").toBool());
    const QJsonObject iq = client.processControlFrame({{"kind", "rpc.request"}, {"requestId", "2"}, {"method", "spectrum.rawIq"}, {"payload", QJsonObject{}}});
    QVERIFY(!iq.value("ok").toBool()); QCOMPARE(iq.value("code").toString(), QString("RAW_IQ_DISABLED"));
    const QJsonObject unknown = client.processControlFrame({{"kind", "rpc.request"}, {"requestId", "3"}, {"method", "proxy.forward"}, {"payload", QJsonObject{}}});
    QVERIFY(!unknown.value("ok").toBool()); QCOMPARE(unknown.value("code").toString(), QString("METHOD_NOT_ALLOWED"));
    QCOMPARE(client.health().value("offlineQueue").toBool(), false);
  }
};

QTEST_MAIN(OutboundRelayClientTests)
#include "outbound_relay_client_tests.moc"

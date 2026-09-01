// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/desktop/OutboundRelayClient.hpp"

#include <QJsonDocument>
#include <QNetworkRequest>
#include <QSslConfiguration>
#include <QUuid>
#include <QWebSocketHandshakeOptions>
#include <openssl/evp.h>
#include <openssl/pem.h>
#include <memory>

namespace rigweave::desktop {
namespace {
const QSet<QString> RpcAllowList{
    "system.health", "system.compatibility", "station.snapshot", "station.presence",
    "home.read", "radio.read", "band-maps.read", "dx.read", "panadapter.read",
    "presets.read", "scanner.read", "recordings.read", "measurements.read",
    "spectrum-intelligence.read", "logbook.list", "logbook.get", "logbook.audit",
    "logbook.export", "sync.status", "digi.read", "keyer.read", "contest.read",
    "portable.read", "operations.read", "satellites.read", "rotator.read", "groups.read",
    "alerts.read", "settings.read", "hamclock.read", "hamclock.layouts.read",
    "hamclock.history.read", "neural-dx.read", "intelligence.read", "needs.read",
    "awards.read", "goals.read", "maps.read", "rf-globe.read", "logbook.create",
    "logbook.update", "logbook.delete", "logbook.restore", "logbook.import",
    "sync.provider-action", "presets.update", "scanner.update", "hamclock.layout-update",
    "watchlist.update", "goals.update", "groups.send", "agent.safe-receive",
    "agent.writer-lease", "agent.workflow", "agent.tx-lease", "agent.rotator-lease",
    "agent.rotator-stop", "agent.global-stop"};
QString toBase64Url(const QByteArray &value) {
  return QString::fromLatin1(value.toBase64(QByteArray::Base64UrlEncoding | QByteArray::OmitTrailingEquals));
}
}

OutboundRelayClient::OutboundRelayClient(DesktopCredentialVault *vault, QObject *parent)
    : QObject(parent), m_vault(vault) {
  connect(&m_socket, &QWebSocket::connected, this, [this] {
    m_authenticated = false;
    setState("AUTHENTICATING", "TLS connected; proving station identity");
    send({{"kind", "relay.hello"}, {"protocol", QJsonObject{{"major", 1}, {"minor", 0}}},
          {"stationId", m_configuration.stationId}, {"registrationId", m_configuration.registrationId},
          {"publicKeyId", m_configuration.publicKeyId}, {"agentVersion", m_configuration.buildSha},
          {"compatibility", QJsonObject{{"relay", "1.0"}, {"rpc", "1.0"}, {"rawIq", "DISABLED"}}},
          {"nonce", QUuid::createUuid().toString(QUuid::WithoutBraces)},
          {"generation", static_cast<qint64>(m_generation)}});
  });
  connect(&m_socket, &QWebSocket::textMessageReceived, this, [this](const QString &text) {
    if (text.toUtf8().size() > MaxControlBytes) {
      ++m_rejected; m_socket.close(QWebSocketProtocol::CloseCodeTooMuchData, "Control frame too large"); return;
    }
    QJsonParseError error;
    const QJsonDocument document = QJsonDocument::fromJson(text.toUtf8(), &error);
    if (error.error != QJsonParseError::NoError || !document.isObject()) {
      ++m_rejected; m_socket.close(QWebSocketProtocol::CloseCodeProtocolError, "Malformed control frame"); return;
    }
    const QJsonObject reply = processControlFrame(document.object());
    if (!reply.isEmpty()) send(reply);
  });
  connect(&m_socket, &QWebSocket::binaryMessageReceived, this, [this](const QByteArray &) {
    ++m_rejected;
    m_socket.close(QWebSocketProtocol::CloseCodeUnsupportedData, "Inbound binary and raw IQ are disabled");
  });
  connect(&m_socket, &QWebSocket::disconnected, this, [this] {
    m_authenticated = false; m_heartbeat.stop(); ++m_generation;
    setState("DISCONNECTED", "No offline command queue; reconnect requires a new challenge");
  });
  connect(&m_socket, &QWebSocket::errorOccurred, this, [this](QAbstractSocket::SocketError) {
    setState("ERROR", m_socket.errorString().left(240));
  });
  m_heartbeat.setInterval(15'000);
  connect(&m_heartbeat, &QTimer::timeout, this, [this] {
    if (m_authenticated) send({{"kind", "relay.heartbeat"}, {"stationId", m_configuration.stationId},
                               {"generation", static_cast<qint64>(m_generation)}});
  });
}

void OutboundRelayClient::configure(Configuration configuration, RpcExecutor executor) {
  m_configuration = std::move(configuration); m_executor = std::move(executor);
}

bool OutboundRelayClient::start(QString *error) {
  if (!m_vault || !m_executor || m_configuration.stationId.isEmpty() ||
      m_configuration.registrationId.isEmpty() || m_configuration.publicKeyId.isEmpty() ||
      m_configuration.vaultAlias.isEmpty() || !m_configuration.relayUrl.isValid() ||
      m_configuration.relayUrl.scheme() != "wss") {
    if (error) *error = "Outbound relay requires a complete wss:// configuration";
    return false;
  }
  QString vaultError;
  if (!m_vault->read(m_configuration.vaultAlias, &vaultError).has_value()) {
    if (error) *error = vaultError.isEmpty() ? "Relay signing key alias is not configured" : vaultError;
    return false;
  }
  QSslConfiguration tls = QSslConfiguration::defaultConfiguration();
  tls.setProtocol(QSsl::TlsV1_3OrLater);
  m_socket.setSslConfiguration(tls);
  setState("CONNECTING", "Outbound WSS only");
  QNetworkRequest request(m_configuration.relayUrl);
  QUrl origin = m_configuration.relayUrl; origin.setScheme("https"); origin.setPath(QString()); origin.setQuery(QString()); origin.setFragment(QString());
  request.setRawHeader("Origin", origin.toString(QUrl::RemovePath | QUrl::RemoveQuery | QUrl::RemoveFragment).toUtf8());
  QWebSocketHandshakeOptions options;
  options.setSubprotocols({QStringLiteral("rigweave.relay.v1")});
  m_socket.open(request, options);
  return true;
}

void OutboundRelayClient::stop() {
  m_heartbeat.stop(); m_authenticated = false;
  m_socket.close(QWebSocketProtocol::CloseCodeGoingAway, "Agent stopping");
  setState("STOPPED", "Remote leases invalidated; TX remains disarmed");
}

QVariantMap OutboundRelayClient::health() const {
  return {{"state", m_state}, {"detail", m_detail}, {"authenticated", m_authenticated},
          {"stationId", m_configuration.stationId}, {"generation", QVariant::fromValue(m_generation)},
          {"rejectedFrames", QVariant::fromValue(m_rejected)}, {"protocol", "rigweave.relay.v1"},
          {"transport", "OUTBOUND_WSS"}, {"rawIq", "DISABLED"}, {"offlineQueue", false}};
}

bool OutboundRelayClient::allowedMethod(const QString &method) {
  return RpcAllowList.contains(method) && !method.contains("iq", Qt::CaseInsensitive) &&
         !method.contains("tune", Qt::CaseInsensitive) && !method.contains("ptt", Qt::CaseInsensitive);
}

QJsonObject OutboundRelayClient::processControlFrame(const QJsonObject &frame) {
  const QString kind = frame.value("kind").toString();
  if (kind == "relay.challenge") {
    const QByteArray challenge = frame.value("challenge").toString().toUtf8();
    QString error;
    const QByteArray signature = challenge.size() >= 32 && challenge.size() <= 1024
        ? signChallenge(challenge, &error) : QByteArray{};
    if (signature.isEmpty()) {
      ++m_rejected;
      return {{"kind", "relay.authenticate.reject"}, {"code", error.isEmpty() ? "INVALID_CHALLENGE" : "SIGNING_FAILED"}};
    }
    return {{"kind", "relay.authenticate"}, {"challengeId", frame.value("challengeId")},
            {"stationId", m_configuration.stationId}, {"publicKeyId", m_configuration.publicKeyId},
            {"signature", toBase64Url(signature)}};
  }
  if (kind == "relay.accepted") {
    m_authenticated = true; m_heartbeat.start(); setState("LIVE", "Authenticated typed relay; no generic proxy");
    return {};
  }
  if (kind == "relay.heartbeat.ack") return {};
  if (kind != "rpc.request" || !m_authenticated) {
    ++m_rejected;
    return {{"kind", "rpc.response"}, {"requestId", frame.value("requestId")}, {"ok", false},
            {"code", m_authenticated ? "UNKNOWN_FRAME" : "NOT_AUTHENTICATED"},
            {"auditId", QUuid::createUuid().toString(QUuid::WithoutBraces)}};
  }
  const QString method = frame.value("method").toString();
  if (!allowedMethod(method)) {
    ++m_rejected;
    return {{"kind", "rpc.response"}, {"requestId", frame.value("requestId")}, {"ok", false},
            {"code", method.contains("iq", Qt::CaseInsensitive) ? "RAW_IQ_DISABLED" : "METHOD_NOT_ALLOWED"},
            {"auditId", QUuid::createUuid().toString(QUuid::WithoutBraces)}};
  }
  const QVariantMap result = m_executor(method, frame.value("payload").toObject().toVariantMap());
  const bool ok = result.value("ok", true).toBool();
  return {{"kind", "rpc.response"}, {"requestId", frame.value("requestId")}, {"ok", ok},
          {"code", ok ? "OK" : result.value("code", "AGENT_METHOD_UNAVAILABLE").toString()},
          {"payload", QJsonObject::fromVariantMap(result)},
          {"auditId", QUuid::createUuid().toString(QUuid::WithoutBraces)}};
}

QByteArray OutboundRelayClient::signChallenge(const QByteArray &challenge, QString *error) const {
  const auto secret = m_vault->read(m_configuration.vaultAlias, error);
  if (!secret) return {};
  const QByteArray pem = secret->toUtf8();
  std::unique_ptr<BIO, decltype(&BIO_free)> bio(BIO_new_mem_buf(pem.constData(), pem.size()), BIO_free);
  std::unique_ptr<EVP_PKEY, decltype(&EVP_PKEY_free)> key(
      bio ? PEM_read_bio_PrivateKey(bio.get(), nullptr, nullptr, nullptr) : nullptr, EVP_PKEY_free);
  if (!key) { if (error) *error = "Relay signing key is not a valid private key"; return {}; }
  std::unique_ptr<EVP_MD_CTX, decltype(&EVP_MD_CTX_free)> context(EVP_MD_CTX_new(), EVP_MD_CTX_free);
  if (!context || EVP_DigestSignInit(context.get(), nullptr, EVP_sha256(), nullptr, key.get()) != 1 ||
      EVP_DigestSignUpdate(context.get(), challenge.constData(), static_cast<size_t>(challenge.size())) != 1) {
    if (error) *error = "Could not initialize relay challenge signature"; return {};
  }
  size_t size{};
  if (EVP_DigestSignFinal(context.get(), nullptr, &size) != 1 || size == 0 || size > 512) {
    if (error) *error = "Could not size relay challenge signature"; return {};
  }
  QByteArray signature(static_cast<qsizetype>(size), Qt::Uninitialized);
  if (EVP_DigestSignFinal(context.get(), reinterpret_cast<unsigned char *>(signature.data()), &size) != 1) {
    if (error) *error = "Could not sign relay challenge"; return {};
  }
  signature.resize(static_cast<qsizetype>(size));
  return signature;
}

void OutboundRelayClient::setState(QString state, QString detail) {
  m_state = std::move(state); m_detail = std::move(detail); emit stateChanged();
}
void OutboundRelayClient::send(const QJsonObject &message) {
  emit controlReplyReady(message);
  if (m_socket.state() == QAbstractSocket::ConnectedState)
    m_socket.sendTextMessage(QString::fromUtf8(QJsonDocument(message).toJson(QJsonDocument::Compact)));
}

} // namespace rigweave::desktop

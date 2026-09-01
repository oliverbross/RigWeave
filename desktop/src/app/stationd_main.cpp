// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/desktop/DesktopPanadapter.hpp"
#include "rigweave/desktop/DesktopPlatform.hpp"
#include "rigweave/desktop/DesktopRadioController.hpp"
#include "rigweave/desktop/DesktopRotatorController.hpp"
#include "rigweave/desktop/RemoteStationService.hpp"

#include <QCommandLineParser>
#include <QCoreApplication>
#include <QDir>
#include <QJsonDocument>
#include <QJsonObject>
#include <QLocalServer>
#include <QLocalSocket>
#include <QSslSocket>
#include <QTextStream>
#include <QTemporaryDir>
#include <memory>

using namespace rigweave::desktop;
namespace {
const QString AdminSocket = QStringLiteral("rigweave-stationd-v1");

QJsonObject adminRequest(const QCommandLineParser &parser) {
  if (parser.isSet("status")) return {{"action", "status"}};
  if (parser.isSet("list-clients")) return {{"action", "list-clients"}};
  if (parser.isSet("pairing-offer")) return {{"action", "pairing-offer"}};
  if (parser.isSet("operator-pairing-offer")) return {{"action", "operator-pairing-offer"}};
  if (parser.isSet("hub-identity")) return {{"action", "hub-identity"}};
  if (parser.isSet("hub-sign")) return {{"action", "hub-sign"}, {"challengeBase64", parser.value("hub-sign")}};
  if (parser.isSet("approve-observer")) return {{"action", "approve-observer"}, {"deviceId", parser.value("approve-observer")}};
  if (parser.isSet("approve-operator")) return {{"action", "approve-operator"}, {"deviceId", parser.value("approve-operator")}};
  if (parser.isSet("safe-control-state")) return {{"action", "safe-control-state"}};
  if (parser.isSet("workflow-state")) return {{"action", "workflow-state"}};
  if (parser.isSet("workflow-request")) {
    const QByteArray encoded = parser.value("workflow-request").toLatin1();
    if (encoded.size() > 48 * 1024) return {{"action", "invalid-workflow-request"}};
    QJsonParseError error;
    const QJsonDocument document = QJsonDocument::fromJson(
        QByteArray::fromBase64(encoded, QByteArray::Base64UrlEncoding), &error);
    if (error.error != QJsonParseError::NoError || !document.isObject())
      return {{"action", "invalid-workflow-request"}};
    return {{"action", "workflow-request"}, {"request", document.object()}};
  }
  if (parser.isSet("safe-control-request")) {
    const QByteArray encoded = parser.value("safe-control-request").toLatin1();
    if (encoded.size() > 48 * 1024) return {{"action", "invalid-safe-control-request"}};
    QJsonParseError error;
    const QJsonDocument document = QJsonDocument::fromJson(
        QByteArray::fromBase64(encoded, QByteArray::Base64UrlEncoding), &error);
    if (error.error != QJsonParseError::NoError || !document.isObject())
      return {{"action", "invalid-safe-control-request"}};
    return {{"action", "safe-control-request"}, {"request", document.object()}};
  }
  if (parser.isSet("journal")) return {{"action", "journal"}};
  if (parser.isSet("domain-journal")) return {{"action", "domain-journal"}};
  if (parser.isSet("revoke")) return {{"action", "revoke"}, {"deviceId", parser.value("revoke")}};
  if (parser.isSet("stop")) return {{"action", "stop"}};
  return {};
}

int sendAdminRequest(const QJsonObject &request, const QString &adminSocket) {
  QLocalSocket socket;
  socket.connectToServer(adminSocket, QIODevice::ReadWrite);
  if (!socket.waitForConnected(2'000)) return 5;
  socket.write(QJsonDocument(request).toJson(QJsonDocument::Compact) + '\n');
  if (!socket.waitForBytesWritten(2'000) || !socket.waitForReadyRead(3'000)) return 6;
  QTextStream(stdout) << socket.readAll();
  return 0;
}
}

int main(int argc, char **argv) {
  QCoreApplication application(argc, argv);
  QCoreApplication::setApplicationName("rigweave-stationd");
  QCoreApplication::setApplicationVersion("1.0");
  QCommandLineParser parser;
  parser.setApplicationDescription("RigWeave Remote Station Service v1");
  parser.addHelpOption(); parser.addVersionOption();
  parser.addOption(QCommandLineOption({"f", "foreground"}, "Run the explicitly enabled service in the foreground"));
  parser.addOption(QCommandLineOption({"s", "status"}, "Print bounded service status"));
  parser.addOption(QCommandLineOption({"p", "pairing-offer"}, "Create a short-lived pairing offer"));
  parser.addOption(QCommandLineOption(QStringLiteral("operator-pairing-offer"), "Create a short-lived OPERATOR pairing offer"));
  parser.addOption(QCommandLineOption(QStringLiteral("debug-no-radio"), "Run a loopback-only deterministic no-radio Agent source"));
  parser.addOption(QCommandLineOption(QStringLiteral("listen-port"), "Override the loopback listener port in debug-no-radio mode", "port"));
  parser.addOption(QCommandLineOption(QStringLiteral("hub-identity"), "Return the Local Hub public observer identity"));
  parser.addOption(QCommandLineOption(QStringLiteral("hub-sign"), "Sign one bounded base64-encoded station challenge in the configured credential vault", "challenge-base64"));
  parser.addOption(QCommandLineOption(QStringLiteral("approve-observer"), "Approve one pending Local Hub device as OBSERVER", "device-id"));
  parser.addOption(QCommandLineOption(QStringLiteral("approve-operator"), "Approve one pending Local Hub device as OPERATOR", "device-id"));
  parser.addOption(QCommandLineOption(QStringLiteral("safe-control-state"), "Print bounded M5 safe-control state"));
  parser.addOption(QCommandLineOption(QStringLiteral("safe-control-request"), "Submit one bounded M5 request through the private Agent admin socket", "base64url-json"));
  parser.addOption(QCommandLineOption(QStringLiteral("workflow-state"), "Print bounded M6 workflow authority state"));
  parser.addOption(QCommandLineOption(QStringLiteral("workflow-request"), "Submit one bounded M6 request through the private Agent admin socket", "base64url-json"));
  parser.addOption(QCommandLineOption(QStringLiteral("journal"), "Print the bounded observer Agent journal"));
  parser.addOption(QCommandLineOption(QStringLiteral("domain-journal"), "Print bounded opaque pending/acknowledged Application Service envelopes"));
  parser.addOption(QCommandLineOption(QStringLiteral("list-clients"), "List paired public device metadata"));
  parser.addOption(QCommandLineOption(QStringLiteral("revoke"), "Revoke a paired device", "device-id"));
  parser.addOption(QCommandLineOption(QStringLiteral("stop"), "Request station Global Stop and shut down"));
  parser.process(application);

  bool listenPortOk{};
  const int requestedListenPort = parser.isSet("listen-port")
      ? parser.value("listen-port").toInt(&listenPortOk)
      : 0;
  if (parser.isSet("listen-port") && (!listenPortOk || requestedListenPort < 1 || requestedListenPort > 65535)) {
    QTextStream(stderr) << "--listen-port requires a port from 1 to 65535\n";
    return 2;
  }
  const QString adminSocket = parser.isSet("listen-port")
      ? AdminSocket + QStringLiteral("-") + QString::number(requestedListenPort)
      : AdminSocket;
  const QJsonObject requestedAdminAction = adminRequest(parser);
  if (!parser.isSet("foreground") && !requestedAdminAction.isEmpty()) return sendAdminRequest(requestedAdminAction, adminSocket);
  if (!parser.isSet("foreground")) parser.showHelp(1);

  // The cert-only backend can parse identities but cannot terminate TLS. Prefer
  // OpenSSL when packaged, then require a backend that implements TLS 1.3.
  if (QSslSocket::availableBackends().contains(QStringLiteral("openssl")) &&
      QSslSocket::isProtocolSupported(QSsl::TlsV1_3, QStringLiteral("openssl"))) {
    QSslSocket::setActiveBackend(QStringLiteral("openssl"));
  }
  if (!QSslSocket::supportsSsl() || !QSslSocket::isProtocolSupported(QSsl::TlsV1_3)) {
    QTextStream(stderr) << "A TLS 1.3-capable Qt network backend is required\n";
    return 2;
  }

  const bool debugNoRadio = parser.isSet("debug-no-radio");
  DesktopPaths paths;
  std::unique_ptr<QTemporaryDir> debugRoot;
  if (debugNoRadio) {
    debugRoot = std::make_unique<QTemporaryDir>(QDir::tempPath() + QStringLiteral("/rigweave-stationd-debug-XXXXXX"));
    if (!debugRoot->isValid()) { QTextStream(stderr) << "Could not create isolated debug state\n"; return 2; }
    paths.setEphemeralRoot(debugRoot->path());
  }
  QString error;
  if (!paths.create(&error)) { QTextStream(stderr) << error << '\n'; return 2; }
  DesktopConfigurationManager configuration(paths.configuration() + "/desktop-config.json");
  if (!configuration.load(&error)) { QTextStream(stderr) << error << '\n'; return 2; }
  SystemCredentialVault systemVault;
  FakeCredentialVault debugVault;
  DesktopCredentialVault *vault = debugNoRadio
      ? static_cast<DesktopCredentialVault *>(&debugVault)
      : static_cast<DesktopCredentialVault *>(&systemVault);
  DesktopRadioController radio;
  DesktopRotatorController rotator;
  DesktopPanadapter panadapter;
  if (!radio.restoreConfiguration(configuration.section("radioProfiles"), &error) ||
      !rotator.restoreConfiguration(configuration.section("rotatorProfiles"), &error) ||
      !panadapter.restoreConfiguration(configuration.section("panadapter"), &error)) {
    QTextStream(stderr) << error << '\n'; return 2;
  }
  QVariantMap remoteStationConfiguration = configuration.section("remoteStation");
  if (parser.isSet("listen-port")) {
    if (!debugNoRadio) {
      QTextStream(stderr) << "--listen-port requires debug-no-radio for foreground service startup\n";
      return 2;
    }
    remoteStationConfiguration["port"] = requestedListenPort;
  }
  RemoteStationService service(vault, &radio, &rotator, &panadapter);
  if (!service.restoreConfiguration(remoteStationConfiguration, &error)) {
    QTextStream(stderr) << error << '\n'; return 2;
  }
  if (debugNoRadio) service.setDebugNoRadio(true);
  QObject::connect(&service, &RemoteStationService::pairingChanged, &application, [&] {
    configuration.setSection("remoteStation", service.configuration()); configuration.save();
  });
  QObject::connect(&service, &RemoteStationService::domainJournalChanged, &application, [&] {
    configuration.setSection("remoteStation", service.configuration()); configuration.save();
  });
  QObject::connect(&application, &QCoreApplication::aboutToQuit, &application, [&] {
    service.globalStop(); service.stop();
    configuration.setSection("remoteStation", service.configuration()); configuration.save();
  });

  QLocalServer admin;
  admin.setSocketOptions(QLocalServer::UserAccessOption);
  QLocalSocket existing;
  existing.connectToServer(adminSocket);
  if (existing.waitForConnected(250)) {
    QTextStream(stderr) << "Another rigweave-stationd instance already owns local administration\n";
    return 4;
  }
  QLocalServer::removeServer(adminSocket);
  if (!admin.listen(adminSocket)) { QTextStream(stderr) << admin.errorString() << '\n'; return 4; }
  if (!service.start(&error)) { QTextStream(stderr) << error << '\n'; return 3; }
  QObject::connect(&admin, &QLocalServer::newConnection, &application, [&] {
    while (QLocalSocket *socket = admin.nextPendingConnection()) {
      QObject::connect(socket, &QLocalSocket::readyRead, socket, [&, socket] {
        const QJsonDocument document = QJsonDocument::fromJson(socket->readLine(16 * 1024));
        const QJsonObject request = document.object();
        const QString action = request.value("action").toString();
        QVariant response;
        bool ok = true;
        if (action == "status") response = service.health();
        else if (action == "list-clients") response = service.pairedDevices();
        else if (action == "pairing-offer") response = service.createPairingOffer();
        else if (action == "operator-pairing-offer") response = service.createPairingOffer("OPERATOR");
        else if (action == "hub-identity") response = service.hubObserverIdentity(&error);
        else if (action == "hub-sign") {
          const QByteArray challenge = QByteArray::fromBase64(request.value("challengeBase64").toString().toLatin1());
          const QString signature = service.signHubObserverChallenge(challenge, &error);
          ok = !signature.isEmpty(); response = ok ? QVariant(signature) : QVariantMap{{"error", error.left(240)}};
        }
        else if (action == "approve-observer") {
          ok = service.approvePendingDevice(request.value("deviceId").toString(), "OBSERVER");
          response = QVariantMap{{"approved", ok}, {"role", "OBSERVER"}};
        }
        else if (action == "approve-operator") {
          ok = debugNoRadio && service.approvePendingDevice(request.value("deviceId").toString(), "OPERATOR");
          response = QVariantMap{{"approved", ok}, {"role", "OPERATOR"}, {"debugNoRadio", debugNoRadio}};
        }
        else if (action == "safe-control-state") response = service.safeControlState();
        else if (action == "safe-control-request") response = service.safeControlAdmin(request.value("request").toObject().toVariantMap());
        else if (action == "workflow-state") response = service.workflowState();
        else if (action == "workflow-request") {
          QVariantMap workflowRequest = request.value("request").toObject().toVariantMap();
          workflowRequest["_trustedOperator"] = debugNoRadio;
          workflowRequest["_operatorSessionId"] = "stationd-debug-operator";
          response = service.workflowAdmin(workflowRequest);
        }
        else if (action == "journal") response = service.observerJournal();
        else if (action == "domain-journal") response = service.domainJournal();
        else if (action == "domain-journal-append") {
          ok = service.appendDomainJournalEnvelope(request.value("envelope").toObject().toVariantMap(), &error);
          response = ok ? QVariantMap{{"accepted", true}} : QVariantMap{{"error", error.left(240)}};
        }
        else if (action == "domain-journal-ack") {
          ok = service.acknowledgeDomainJournalEvent(request.value("eventId").toString(),
              request.value("hashSha256").toString(), &error);
          response = ok ? QVariantMap{{"acknowledged", true}} : QVariantMap{{"error", error.left(240)}};
        }
        else if (action == "revoke") { service.revokeDevice(request.value("deviceId").toString()); response = QVariantMap{{"revoked", true}}; }
        else if (action == "stop") { service.globalStop(); response = QVariantMap{{"stopped", true}}; }
        else { ok = false; response = QVariantMap{{"error", "unknown admin action"}}; }
        socket->write(QJsonDocument(QJsonObject{{"ok", ok}, {"result", QJsonValue::fromVariant(response)}}).toJson(QJsonDocument::Indented));
        socket->flush(); socket->disconnectFromServer();
        if (action == "stop") QMetaObject::invokeMethod(&application, "quit", Qt::QueuedConnection);
      });
      QObject::connect(socket, &QLocalSocket::disconnected, socket, &QObject::deleteLater);
    }
  });
  if (parser.isSet("pairing-offer"))
    QTextStream(stdout) << QJsonDocument::fromVariant(service.createPairingOffer()).toJson(QJsonDocument::Indented);
  return application.exec();
}

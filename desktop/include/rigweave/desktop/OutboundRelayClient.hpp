// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "rigweave/desktop/DesktopPlatform.hpp"

#include <QJsonObject>
#include <QObject>
#include <QTimer>
#include <QUrl>
#include <QVariantMap>
#include <QWebSocket>
#include <functional>

namespace rigweave::desktop {

class OutboundRelayClient final : public QObject {
  Q_OBJECT
public:
  using RpcExecutor = std::function<QVariantMap(const QString &, const QVariantMap &)>;
  struct Configuration final {
    QUrl relayUrl;
    QString stationId;
    QString registrationId;
    QString publicKeyId;
    QString vaultAlias;
    QString buildSha;
  };

  explicit OutboundRelayClient(DesktopCredentialVault *vault, QObject *parent = nullptr);
  void configure(Configuration configuration, RpcExecutor executor);
  bool start(QString *error = nullptr);
  void stop();
  QVariantMap health() const;

  // Exposed for deterministic protocol tests; live traffic follows the same path.
  QJsonObject processControlFrame(const QJsonObject &frame);
  static bool allowedMethod(const QString &method);

signals:
  void stateChanged();
  void controlReplyReady(QJsonObject reply);

private:
  QByteArray signChallenge(const QByteArray &challenge, QString *error) const;
  void setState(QString state, QString detail = {});
  void send(const QJsonObject &message);

  DesktopCredentialVault *m_vault{};
  QWebSocket m_socket;
  QTimer m_heartbeat;
  Configuration m_configuration;
  RpcExecutor m_executor;
  QString m_state{"DISABLED"};
  QString m_detail{"Outbound relay not configured"};
  quint64 m_generation{1};
  quint64 m_rejected{};
  bool m_authenticated{};
  static constexpr qsizetype MaxControlBytes = 64 * 1024;
};

} // namespace rigweave::desktop

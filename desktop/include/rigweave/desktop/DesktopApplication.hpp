#pragma once

#include "rigweave/desktop/AdifService.hpp"
#include "rigweave/desktop/ClusterController.hpp"
#include "rigweave/desktop/DesktopPanadapter.hpp"
#include "rigweave/desktop/DesktopParityPlatform.hpp"
#include "rigweave/desktop/DesktopPlatform.hpp"
#include "rigweave/desktop/DesktopRadioController.hpp"
#include "rigweave/desktop/DesktopRotatorController.hpp"
#include "rigweave/desktop/WavelogSync.hpp"
#include "rigweave/desktop/RfObservationModel.hpp"

#include <QQmlApplicationEngine>
#include <QTemporaryDir>

namespace rigweave::desktop {

class DesktopApplication final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString currentDestination READ currentDestination WRITE setCurrentDestination NOTIFY currentDestinationChanged)
    Q_PROPERTY(bool shuttingDown READ shuttingDown NOTIFY shuttingDownChanged)
    Q_PROPERTY(bool demoMode READ demoMode CONSTANT)
public:
    explicit DesktopApplication(QObject *parent = nullptr);
    ~DesktopApplication() override;
    bool initialize(QString *error = nullptr);
    void expose(QQmlApplicationEngine &engine);
    QString currentDestination() const { return m_currentDestination; }
    void setCurrentDestination(const QString &destination);
    void setGalleryVariant(const QString &workspace, int variant);
    bool shuttingDown() const { return m_shuttingDown; }
    bool demoMode() const { return m_demoMode; }
    Q_INVOKABLE QVariantMap health() const;
    Q_INVOKABLE QVariantMap intelligence() const;
    Q_INVOKABLE QVariantMap buildInformation() const;
    Q_INVOKABLE bool saveFastEntry(const QVariantMap &values);
    Q_INVOKABLE void globalStop();
    Q_INVOKABLE void shutdown();

signals:
    void currentDestinationChanged();
    void shuttingDownChanged();
    void error(QString message);

private:
    DesktopPaths m_paths;
    SystemCredentialVault m_credentials;
    std::unique_ptr<DesktopConfigurationManager> m_configuration;
    std::unique_ptr<QsoDatabase> m_database;
    std::unique_ptr<QsoTableModel> m_logbook;
    std::unique_ptr<AdifService> m_adif;
    SpotRepository m_spots;
    RfObservationModel m_rfObservations;
    ClusterController m_cluster;
    WavelogSyncEngine *m_wavelog{};
    HamlibModelRegistry m_radioModels;
    DesktopRadioController m_radio;
    DesktopRotatorController m_rotator;
    DesktopPanadapter m_panadapter;
    DesktopParityPlatform m_parity;
    SupportBundle m_supportBundle;
    std::unique_ptr<QTemporaryDir> m_demoDirectory;
    QString m_currentDestination{"Home"};
    bool m_shuttingDown{};
    bool m_demoMode{};
};

} // namespace rigweave::desktop

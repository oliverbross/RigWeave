#include "rigweave/desktop/DesktopApplication.hpp"

#include "rigweave/core.h"

#include <QDateTime>
#include <QDir>
#include <QQmlContext>
#include <QUuid>
#include <QSet>
#include <QTimer>

#ifndef RIGWEAVE_BUILD_SHA
#define RIGWEAVE_BUILD_SHA "local-uncommitted-build"
#endif

namespace rigweave::desktop {

DesktopApplication::DesktopApplication(QObject*parent):QObject(parent),m_rfObservations(this),m_cluster(&m_spots,this),m_parity(this),m_supportBundle(&m_paths,this){connect(&m_radio,&DesktopRadioController::iqFrame,&m_panadapter,[this](const QString&id,quint32 rate,const QVector<float>&values){const quint64 centre=m_radio.receivers()->receiver(id).value("centreFrequencyHz").toULongLong();m_panadapter.pushFloatIq(id,rate,values,centre,false);});}
DesktopApplication::~DesktopApplication(){shutdown();}

bool DesktopApplication::initialize(QString *error) {
    m_demoMode = qEnvironmentVariableIntValue("RIGWEAVE_DESKTOP_DEMO") == 1;
    if (m_demoMode) {
        const QString explicitRoot = qEnvironmentVariable("RIGWEAVE_DEMO_ROOT");
        if (!explicitRoot.isEmpty()) {
            m_paths.setEphemeralRoot(explicitRoot);
        } else {
        m_demoDirectory = std::make_unique<QTemporaryDir>(
            QDir::tempPath() + "/rigweave-desktop-demo-XXXXXX");
        if (!m_demoDirectory->isValid()) {
            if (error) *error = "Cannot create isolated demo directory";
            return false;
        }
        if (qEnvironmentVariableIntValue("RIGWEAVE_DEMO_PRESERVE") == 1)
            m_demoDirectory->setAutoRemove(false);
        m_paths.setEphemeralRoot(m_demoDirectory->path());
        }
    }
    if (!m_paths.create(error) || !BoundedLogger::install(m_paths.logs(), error)) return false;
    m_configuration = std::make_unique<DesktopConfigurationManager>(
        m_paths.configuration() + "/desktop-config.json", this);
    if (!m_configuration->load(error)) return false;
    if (!m_radio.restoreConfiguration(m_configuration->section("radioProfiles"), error)) return false;
    m_configuration->setSection("radioProfiles", m_radio.configuration());
    if (!m_panadapter.restoreConfiguration(m_configuration->section("panadapter"), error)) return false;
    m_configuration->setSection("panadapter", m_panadapter.configuration());
    if (!m_rfObservations.restoreConfiguration(m_configuration->section("display").value("rfObservations").toMap(), error)) return false;
    connect(&m_radio, &DesktopRadioController::preferencesChanged, this, [this] {
        if (m_configuration) m_configuration->setSection("radioProfiles", m_radio.configuration());
    });
    connect(&m_panadapter, &DesktopPanadapter::settingsChanged, this, [this] {
        if (m_configuration) m_configuration->setSection("panadapter", m_panadapter.configuration());
    });
    connect(&m_rfObservations, &RfObservationModel::filtersChanged, this, [this] {
        if (!m_configuration) return; auto display=m_configuration->section("display");display["rfObservations"]=m_rfObservations.configuration();m_configuration->setSection("display",display);
    });
    m_currentDestination = m_configuration->lastDestination();
    m_database = std::make_unique<QsoDatabase>(
        m_paths.databases() + "/rigweave-desktop.sqlite", this);
    if (!m_database->open(error)) return false;
    m_logbook = std::make_unique<QsoTableModel>(m_database.get(), this);
    m_adif = std::make_unique<AdifService>(m_database.get(), this);
    m_wavelog = new WavelogSyncEngine(m_database.get(), this);
    auto *endpoint = new QtWavelogEndpoint(m_wavelog);
    m_wavelog->setEndpoint(endpoint);
    m_wavelog->setCredentialResolver(
        [this](const QString &alias) { return m_credentials.read(alias).value_or(QString{}); });
    if (!m_parity.open(m_paths.databases(), m_paths.cache(), m_demoMode, error)) return false;
    QTimer::singleShot(0, &m_radio, &DesktopRadioController::startConfiguredAutoConnect);
    if (m_demoMode) {
        m_rfObservations.loadDeterministicDemo();
        const qint64 now = QDateTime::currentSecsSinceEpoch();
        m_spots.ingest({14074000, "K1ABC", "N0TEST", "CQ FT8", "20m", "FT8",
                        "DEMO", now - 42, true, false, false});
        m_spots.ingest({14062000, "DL1AAA", "G0TEST", "CQ CW", "20m", "CW",
                        "DEMO", now - 91, false, true, false});
        m_spots.ingest({14244000, "JA1XYZ", "VK0TEST", "CQ DX", "20m", "USB",
                        "DEMO", now - 155, false, false, true});
    }
    return true;
}

void DesktopApplication::expose(QQmlApplicationEngine&engine){auto*context=engine.rootContext();context->setContextProperty("Desktop",this);context->setContextProperty("DesktopPaths",&m_paths);context->setContextProperty("DesktopConfig",m_configuration.get());context->setContextProperty("LogbookModel",m_logbook.get());context->setContextProperty("Adif",m_adif.get());context->setContextProperty("Spots",&m_spots);context->setContextProperty("RfObservations",&m_rfObservations);context->setContextProperty("Cluster",&m_cluster);context->setContextProperty("Wavelog",m_wavelog);context->setContextProperty("RadioModels",&m_radioModels);context->setContextProperty("Radio",&m_radio);context->setContextProperty("Rotator",&m_rotator);context->setContextProperty("Panadapter",&m_panadapter);context->setContextProperty("Parity",&m_parity);context->setContextProperty("CredentialVault",&m_credentials);context->setContextProperty("SupportBundle",&m_supportBundle);}

void DesktopApplication::setCurrentDestination(const QString&destination){static const QSet<QString>allowed{"Home","Radio","Digi","Panadapter","EQ","Logbook","Intelligence","Sync","Contest","Band Maps","Presets","DX","Portable","Operations","Groups.io","Rotator","Settings","Health","About"};if(!allowed.contains(destination)||m_currentDestination==destination)return;m_currentDestination=destination;if(m_configuration)m_configuration->setLastDestination(destination);emit currentDestinationChanged();}
void DesktopApplication::setGalleryVariant(const QString&workspace,int variant){if(m_demoMode&&workspace=="Band Maps")m_parity.setGalleryBandMapLayout(variant);}

QVariantMap DesktopApplication::health()const{QString projectionError;const bool projection=m_database&&m_database->verifyProjection(&projectionError);return{{"database",m_database?"Open / schema 16":"Unavailable"},{"databaseRevision",m_database?QVariant::fromValue<qulonglong>(m_database->revision()):0},{"projection",projection?"Verified":projectionError},{"domainStores",m_parity.databaseHealth()},{"wavelog",m_wavelog?m_wavelog->state():"Unavailable"},{"cluster",m_cluster.state()},{"radio",m_radio.health()},{"rotator",m_rotator.state()},{"panadapter",m_panadapter.health()},{"configuration",m_configuration?"Loaded":"Unavailable"},{"providers",QStringLiteral("%1 registered; disabled by default").arg(m_parity.providers()->rowCount())}};}
QVariantMap DesktopApplication::buildInformation()const{return{{"buildSha",QString::fromLatin1(RIGWEAVE_BUILD_SHA).isEmpty()?"local-uncommitted-build":QString::fromLatin1(RIGWEAVE_BUILD_SHA)},{"qtVersion",QString::fromLatin1(qVersion())},{"coreVersion",QString::fromLatin1(rw_core_version())},{"databaseSchema",QsoDatabase::SchemaVersion},{"licence","GPL-3.0-only"},{"hamlib","4.7.2 pinned source; operational only when linked in this build"}};}
QVariantMap DesktopApplication::intelligence()const{return m_database?m_database->intelligenceSummary():QVariantMap{};}

bool DesktopApplication::saveFastEntry(const QVariantMap&values){if(!m_database)return false;QsoRecord q;q.id=QUuid::createUuid().toString(QUuid::WithoutBraces);q.callsign=values.value("callsign").toString();q.frequencyHz=values.value("frequencyHz").toLongLong();q.frequencyRxHz=values.value("frequencyRxHz").toLongLong();q.band=values.value("band").toString();q.mode=values.value("mode").toString();q.submode=values.value("submode").toString();q.rstSent=values.value("rstSent","59").toString();q.rstReceived=values.value("rstReceived","59").toString();q.grid=values.value("grid").toString();q.comment=values.value("comment").toString();q.stationProfileId=values.value("stationProfileId").toString();q.stationCallsign=values.value("stationCallsign").toString();q.operatorCallsign=values.value("operatorCallsign").toString();q.contestId=values.value("contestId").toString();q.satelliteName=values.value("satelliteName").toString();q.satelliteMode=values.value("satelliteMode").toString();q.potaRef=values.value("potaRef").toString();q.sotaRef=values.value("sotaRef").toString();q.iota=values.value("iota").toString();q.wwffRef=values.value("wwffRef").toString();q.extraAdif=QJsonObject::fromVariantMap(values.value("extraAdif").toMap());q.createdAt=QDateTime::currentSecsSinceEpoch();QString error;if(!m_database->save(q,&error)){emit this->error(error);return false;}m_logbook->firstPage();if(m_wavelog&&m_wavelog->binding())m_wavelog->enqueue(q.id,"CREATE");return true;}

void DesktopApplication::globalStop(){m_parity.globalStop();m_radio.globalStop();m_rotator.stop();m_panadapter.stop();qInfo("Global STOP requested: pending radio mutations and receive streams stopped; rotator stop sent when connected; PTT/TUNE are unavailable");}
void DesktopApplication::shutdown(){if(m_shuttingDown)return;m_shuttingDown=true;emit shuttingDownChanged();if(m_adif)m_adif->cancel();if(m_wavelog)m_wavelog->close();m_cluster.disconnectProfile();m_parity.close();m_panadapter.stop();m_rotator.stop();m_rotator.disconnectRotator();m_radio.disconnectRadio();m_supportBundle.close();if(m_configuration)m_configuration->save();BoundedLogger::shutdown();}

} // namespace rigweave::desktop

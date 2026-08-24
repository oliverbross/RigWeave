#include "rigweave/desktop/DesktopApplication.hpp"

#include "rigweave/core.h"

#include <QDateTime>
#include <QQmlContext>
#include <QUuid>
#include <QSet>

#ifndef RIGWEAVE_BUILD_SHA
#define RIGWEAVE_BUILD_SHA "local-uncommitted-build"
#endif

namespace rigweave::desktop {

DesktopApplication::DesktopApplication(QObject*parent):QObject(parent),m_cluster(&m_spots,this),m_supportBundle(&m_paths,this){}
DesktopApplication::~DesktopApplication(){shutdown();}

bool DesktopApplication::initialize(QString*error){if(!m_paths.create(error))return false;if(!BoundedLogger::install(m_paths.logs(),error))return false;m_configuration=std::make_unique<DesktopConfigurationManager>(m_paths.configuration()+"/desktop-config.json",this);if(!m_configuration->load(error))return false;m_currentDestination=m_configuration->lastDestination();m_database=std::make_unique<QsoDatabase>(m_paths.databases()+"/rigweave-desktop.sqlite",this);if(!m_database->open(error))return false;m_logbook=std::make_unique<QsoTableModel>(m_database.get(),this);m_adif=std::make_unique<AdifService>(m_database.get(),this);m_wavelog=new WavelogSyncEngine(m_database.get(),this);auto*endpoint=new QtWavelogEndpoint(m_wavelog);m_wavelog->setEndpoint(endpoint);m_wavelog->setCredentialResolver([this](const QString&alias){return m_credentials.read(alias).value_or(QString{});});return true;}

void DesktopApplication::expose(QQmlApplicationEngine&engine){auto*context=engine.rootContext();context->setContextProperty("Desktop",this);context->setContextProperty("DesktopPaths",&m_paths);context->setContextProperty("DesktopConfig",m_configuration.get());context->setContextProperty("LogbookModel",m_logbook.get());context->setContextProperty("Adif",m_adif.get());context->setContextProperty("Spots",&m_spots);context->setContextProperty("Cluster",&m_cluster);context->setContextProperty("Wavelog",m_wavelog);context->setContextProperty("RadioModels",&m_radioModels);context->setContextProperty("Radio",&m_radio);context->setContextProperty("Rotator",&m_rotator);context->setContextProperty("Panadapter",&m_panadapter);context->setContextProperty("CredentialVault",&m_credentials);context->setContextProperty("SupportBundle",&m_supportBundle);}

void DesktopApplication::setCurrentDestination(const QString&destination){static const QSet<QString>allowed{"Home","Radio","Panadapter","Logbook","Intelligence","Sync","DX","Band Maps","Rotator","Settings","Health","Digi","Contest","Portable","Operations","Groups.io","Satellite/QO-100","About"};if(!allowed.contains(destination)||m_currentDestination==destination)return;m_currentDestination=destination;if(m_configuration)m_configuration->setLastDestination(destination);emit currentDestinationChanged();}

QVariantMap DesktopApplication::health()const{QString projectionError;const bool projection=m_database&&m_database->verifyProjection(&projectionError);return{{"database",m_database?"Open / schema 16":"Unavailable"},{"databaseRevision",m_database?QVariant::fromValue<qulonglong>(m_database->revision()):0},{"projection",projection?"Verified":projectionError},{"wavelog",m_wavelog?m_wavelog->state():"Unavailable"},{"cluster",m_cluster.state()},{"radio",m_radio.state()},{"rotator",m_rotator.state()},{"panadapter",m_panadapter.state()},{"configuration",m_configuration?"Loaded":"Unavailable"},{"providers","No duplicated background provider work; platform foundations are inert."}};}
QVariantMap DesktopApplication::buildInformation()const{return{{"buildSha",QString::fromLatin1(RIGWEAVE_BUILD_SHA).isEmpty()?"local-uncommitted-build":QString::fromLatin1(RIGWEAVE_BUILD_SHA)},{"qtVersion",QString::fromLatin1(qVersion())},{"coreVersion",QString::fromLatin1(rw_core_version())},{"databaseSchema",QsoDatabase::SchemaVersion},{"licence","GPL-3.0-only"},{"hamlib","4.7.2 pinned source; operational only when linked in this build"}};}
QVariantMap DesktopApplication::intelligence()const{return m_database?m_database->intelligenceSummary():QVariantMap{};}

bool DesktopApplication::saveFastEntry(const QVariantMap&values){if(!m_database)return false;QsoRecord q;q.id=QUuid::createUuid().toString(QUuid::WithoutBraces);q.callsign=values.value("callsign").toString();q.frequencyHz=values.value("frequencyHz").toLongLong();q.frequencyRxHz=values.value("frequencyRxHz").toLongLong();q.band=values.value("band").toString();q.mode=values.value("mode").toString();q.submode=values.value("submode").toString();q.rstSent=values.value("rstSent","59").toString();q.rstReceived=values.value("rstReceived","59").toString();q.grid=values.value("grid").toString();q.comment=values.value("comment").toString();q.stationProfileId=values.value("stationProfileId").toString();q.stationCallsign=values.value("stationCallsign").toString();q.operatorCallsign=values.value("operatorCallsign").toString();q.contestId=values.value("contestId").toString();q.satelliteName=values.value("satelliteName").toString();q.satelliteMode=values.value("satelliteMode").toString();q.potaRef=values.value("potaRef").toString();q.sotaRef=values.value("sotaRef").toString();q.iota=values.value("iota").toString();q.wwffRef=values.value("wwffRef").toString();q.extraAdif=QJsonObject::fromVariantMap(values.value("extraAdif").toMap());q.createdAt=QDateTime::currentSecsSinceEpoch();QString error;if(!m_database->save(q,&error)){emit this->error(error);return false;}m_logbook->firstPage();if(m_wavelog&&m_wavelog->binding())m_wavelog->enqueue(q.id,"CREATE");return true;}

void DesktopApplication::globalStop(){m_rotator.stop();m_panadapter.stop();qInfo("Global STOP requested: rotator stop sent when connected; receive audio stopped; PTT/TUNE are unavailable");}
void DesktopApplication::shutdown(){if(m_shuttingDown)return;m_shuttingDown=true;emit shuttingDownChanged();if(m_adif)m_adif->cancel();m_panadapter.stop();m_rotator.stop();m_rotator.disconnectRotator();m_radio.disconnectRadio();m_cluster.disconnectProfile();if(m_configuration)m_configuration->save();BoundedLogger::shutdown();}

} // namespace rigweave::desktop

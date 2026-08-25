#include "rigweave/desktop/DesktopPlatform.hpp"

#include <QCoreApplication>
#include <QCryptographicHash>
#include <QDateTime>
#include <QDesktopServices>
#include <QDir>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonObject>
#include <QLocalSocket>
#include <QMutex>
#include <QSaveFile>
#include <QStandardPaths>
#include <QRegularExpression>

#ifdef Q_OS_WIN
#include <windows.h>
#include <wincred.h>
#endif

namespace rigweave::desktop {
namespace {
QMutex logMutex;
std::unique_ptr<QFile> logFile;
QString logDirectory;

QString cleanMessage(QString value) {
    value.replace(QRegularExpression(QStringLiteral("(?i)(bearer|token|api[_ -]?key|password|secret)\\s*[:= ]\\s*[^\\s,;]+")),QStringLiteral("\\1=[REDACTED]"));
    const QString home=QDir::homePath();if(!home.isEmpty())value.replace(home,QStringLiteral("[HOME]"));
    return value.left(4096);
}

QVariant sanitizeValue(const QVariant &value) {
    if (value.metaType().id() == QMetaType::QVariantMap) {
        QVariantMap output;
        const auto input = value.toMap();
        for (auto it = input.cbegin(); it != input.cend(); ++it) {
            const QString key = it.key().toLower();
            const bool unsafe = key == "connected" || key == "ptt" || key == "tune" ||
                                key.contains("armed") || key == "moving" || key.contains("pendingcommand") ||
                                key == "livespots" || key == "providerbodies" || key == "qsodata" ||
                                key.contains("credential") || key.contains("token") || key.contains("password") ||
                                key.contains("secret") || key.contains("apikey");
            if (!unsafe) output.insert(it.key(), sanitizeValue(it.value()));
        }
        return output;
    }
    if (value.metaType().id() == QMetaType::QVariantList) {
        QVariantList output;
        for (const auto &entry : value.toList()) output << sanitizeValue(entry);
        return output;
    }
    return value;
}

quint32 crc32(const QByteArray &bytes) {
    quint32 crc=0xffffffffU;for(unsigned char c:bytes){crc^=c;for(int i=0;i<8;i++)crc=(crc>>1)^((crc&1U)?0xedb88320U:0U);}return ~crc;
}
void append16(QByteArray &out,quint16 v){out.append(char(v&0xff));out.append(char((v>>8)&0xff));}
void append32(QByteArray &out,quint32 v){append16(out,quint16(v&0xffff));append16(out,quint16(v>>16));}

bool writeZip(const QString &path,const QMap<QString,QByteArray>&entries,QString*error){QSaveFile file(path);if(!file.open(QIODevice::WriteOnly)){if(error)*error=file.errorString();return false;}struct Central{QByteArray name;quint32 crc;quint32 size;quint32 offset;};QVector<Central> central;
    for(auto it=entries.cbegin();it!=entries.cend();++it){const QByteArray name=it.key().toUtf8(),data=it.value();if(name.size()>255||data.size()>1048576){if(error)*error="Support bundle entry exceeds bound";file.cancelWriting();return false;}Central c{name,crc32(data),quint32(data.size()),quint32(file.pos())};QByteArray h;append32(h,0x04034b50);append16(h,20);append16(h,0);append16(h,0);append16(h,0);append16(h,0);append32(h,c.crc);append32(h,c.size);append32(h,c.size);append16(h,quint16(name.size()));append16(h,0);file.write(h);file.write(name);file.write(data);central<<c;}
    const quint32 centralOffset=quint32(file.pos());for(const auto&c:central){QByteArray h;append32(h,0x02014b50);append16(h,20);append16(h,20);append16(h,0);append16(h,0);append16(h,0);append16(h,0);append32(h,c.crc);append32(h,c.size);append32(h,c.size);append16(h,quint16(c.name.size()));append16(h,0);append16(h,0);append16(h,0);append16(h,0);append32(h,0);append32(h,c.offset);file.write(h);file.write(c.name);}const quint32 centralSize=quint32(file.pos())-centralOffset;QByteArray end;append32(end,0x06054b50);append16(end,0);append16(end,0);append16(end,quint16(central.size()));append16(end,quint16(central.size()));append32(end,centralSize);append32(end,centralOffset);append16(end,0);file.write(end);if(!file.commit()){if(error)*error=file.errorString();return false;}return true;}
}

DesktopPaths::DesktopPaths(QObject *parent):QObject(parent){const QString config=QStandardPaths::writableLocation(QStandardPaths::AppConfigLocation);const QString data=QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);const QString cacheRoot=QStandardPaths::writableLocation(QStandardPaths::CacheLocation);m_configuration=config;m_databases=data+"/databases";m_cache=cacheRoot;m_logs=data+"/logs";m_exports=QStandardPaths::writableLocation(QStandardPaths::DocumentsLocation)+"/RigWeave Exports";m_supportBundles=data+"/support-bundles";}
bool DesktopPaths::create(QString*error)const{for(const auto&path:{m_configuration,m_databases,m_cache,m_logs,m_exports,m_supportBundles})if(!QDir().mkpath(path)){if(error)*error=QStringLiteral("Cannot create %1").arg(path);return false;}return true;}

bool FakeCredentialVault::write(const QString&alias,const QString&label,const QString&secret,QString*error){if(alias.size()<1||alias.size()>128||label.size()>128||secret.size()>16384){if(error)*error="Credential label or value exceeds bound";return false;}m_values.insert(alias,secret);return true;}
std::optional<QString> FakeCredentialVault::read(const QString&alias,QString*)const{const auto it=m_values.constFind(alias);return it==m_values.cend()?std::nullopt:std::optional<QString>(*it);}
bool FakeCredentialVault::remove(const QString&alias,QString*){m_values.remove(alias);return true;}

bool SystemCredentialVault::write(const QString&alias,const QString&label,const QString&secret,QString*error){if(alias.size()<1||alias.size()>128||label.size()>128||secret.size()>16384){if(error)*error="Credential label or value exceeds bound";return false;}
#ifdef Q_OS_WIN
    const std::wstring target=(QStringLiteral("RigWeave/")+alias).toStdWString(),comment=label.toStdWString();const QByteArray bytes=secret.toUtf8();CREDENTIALW credential{};credential.Type=CRED_TYPE_GENERIC;credential.TargetName=const_cast<wchar_t*>(target.c_str());credential.Comment=const_cast<wchar_t*>(comment.c_str());credential.CredentialBlobSize=DWORD(bytes.size());credential.CredentialBlob=reinterpret_cast<LPBYTE>(const_cast<char*>(bytes.constData()));credential.Persist=CRED_PERSIST_LOCAL_MACHINE;credential.UserName=const_cast<wchar_t*>(L"RigWeave");if(!CredWriteW(&credential,0)){if(error)*error=QStringLiteral("Windows Credential Manager rejected the credential (%1)").arg(GetLastError());return false;}return true;
#else
    Q_UNUSED(alias);Q_UNUSED(label);Q_UNUSED(secret);if(error)*error="macOS Keychain adapter is a compiled platform stub in Windows Alpha";return false;
#endif
}
std::optional<QString> SystemCredentialVault::read(const QString&alias,QString*error)const{
#ifdef Q_OS_WIN
    const std::wstring target=(QStringLiteral("RigWeave/")+alias).toStdWString();PCREDENTIALW credential=nullptr;if(!CredReadW(target.c_str(),CRED_TYPE_GENERIC,0,&credential)){if(GetLastError()!=ERROR_NOT_FOUND&&error)*error=QStringLiteral("Windows Credential Manager read failed (%1)").arg(GetLastError());return std::nullopt;}const QByteArray bytes(reinterpret_cast<const char*>(credential->CredentialBlob),int(credential->CredentialBlobSize));const QString value=QString::fromUtf8(bytes);CredFree(credential);return value;
#else
    Q_UNUSED(alias);Q_UNUSED(error);return std::nullopt;
#endif
}
bool SystemCredentialVault::remove(const QString&alias,QString*error){
#ifdef Q_OS_WIN
    const std::wstring target=(QStringLiteral("RigWeave/")+alias).toStdWString();if(!CredDeleteW(target.c_str(),CRED_TYPE_GENERIC,0)&&GetLastError()!=ERROR_NOT_FOUND){if(error)*error=QStringLiteral("Windows Credential Manager delete failed (%1)").arg(GetLastError());return false;}return true;
#else
    Q_UNUSED(alias);if(error)*error="macOS Keychain adapter is not wired in Windows Alpha";return false;
#endif
}

DesktopConfigurationManager::DesktopConfigurationManager(QString path,QObject*parent):QObject(parent),m_path(std::move(path)){}
bool DesktopConfigurationManager::safeSection(const QString&name){return QStringList{"window","navigation","stations","radioProfiles","rotatorProfiles","bandMaps","clusterProfiles","wavelogBinding","panadapter","alerts","display"}.contains(name);}
bool DesktopConfigurationManager::load(QString*error){QFile file(m_path);if(!file.exists()){m_root={{"version",1},{"navigation",QVariantMap{{"lastDestination","Home"}}}};return save(error);}if(!file.open(QIODevice::ReadOnly)){if(error)*error=file.errorString();return false;}if(file.size()>1048576){if(error)*error="Configuration exceeds 1 MiB bound";return false;}QJsonParseError parse;const auto doc=QJsonDocument::fromJson(file.readAll(),&parse);if(parse.error!=QJsonParseError::NoError||!doc.isObject()){if(error)*error="Invalid configuration JSON";return false;}m_root=doc.object().toVariantMap();return true;}
bool DesktopConfigurationManager::save(QString*error)const{QSaveFile file(m_path);if(!file.open(QIODevice::WriteOnly)){if(error)*error=file.errorString();return false;}file.write(QJsonDocument::fromVariant(m_root).toJson(QJsonDocument::Indented));if(!file.commit()){if(error)*error=file.errorString();return false;}return true;}
QString DesktopConfigurationManager::lastDestination()const{return m_root.value("navigation").toMap().value("lastDestination","Home").toString();}
void DesktopConfigurationManager::setLastDestination(const QString&value){auto navigation=m_root.value("navigation").toMap();navigation["lastDestination"]=value;m_root["navigation"]=navigation;save();emit changed();}
QVariantMap DesktopConfigurationManager::previewImport(const QString&path)const{QFile file(path);if(!file.open(QIODevice::ReadOnly)||file.size()>1048576)return{{"valid",false},{"error","Cannot read bounded configuration bundle"}};QJsonParseError parse;const auto doc=QJsonDocument::fromJson(file.readAll(),&parse);if(parse.error!=QJsonParseError::NoError||!doc.isObject())return{{"valid",false},{"error","Invalid JSON"}};QVariantList sections;QStringList unknownSections;for(auto it=doc.object().begin();it!=doc.object().end();++it){if(it.key()=="version")continue;if(safeSection(it.key()))sections<<QVariantMap{{"name",it.key()},{"changed",m_root.value(it.key())!=it.value().toVariant()}};else unknownSections<<it.key();}return{{"valid",true},{"sections",sections},{"unknownSections",unknownSections},{"requiresReview",!unknownSections.isEmpty()},{"restoreSafety","Radio disconnected; PTT/TUNE unavailable; rotator disarmed; pending commands excluded."}};}
bool DesktopConfigurationManager::applyImport(const QString&path,const QStringList&sections,QString*error){const auto preview=previewImport(path);if(!preview.value("valid").toBool()){if(error)*error=preview.value("error").toString();return false;}for(const auto&section:sections)if(!safeSection(section)){if(error)*error=QStringLiteral("Unknown or unsafe configuration section: %1").arg(section);return false;}QFile file(path);if(!file.open(QIODevice::ReadOnly)){if(error)*error=file.errorString();return false;}const auto incoming=QJsonDocument::fromJson(file.readAll()).object().toVariantMap();const auto before=m_root;for(const auto&section:sections)if(incoming.contains(section))m_root[section]=incoming.value(section);if(!save(error)){m_root=before;QString rollbackError;if(!save(&rollbackError)&&error)*error+=QStringLiteral("; rollback failed: %1").arg(rollbackError);return false;}emit changed();return true;}
bool DesktopConfigurationManager::exportBundle(const QString&path,QString*error)const{QVariantMap safe{{"version",1}};for(auto it=m_root.cbegin();it!=m_root.cend();++it)if(safeSection(it.key()))safe.insert(it.key(),sanitizeValue(it.value()));QSaveFile file(path);if(!file.open(QIODevice::WriteOnly)){if(error)*error=file.errorString();return false;}file.write(QJsonDocument::fromVariant(safe).toJson(QJsonDocument::Indented));return file.commit();}
QVariantMap DesktopConfigurationManager::section(const QString&name)const{return safeSection(name)?m_root.value(name).toMap():QVariantMap{};}
void DesktopConfigurationManager::setSection(const QString&name,const QVariantMap&value){if(!safeSection(name))return;m_root[name]=value;save();emit changed();}

SingleInstance::SingleInstance(QString name,QObject*parent):QObject(parent),m_name(std::move(name)){connect(&m_server,&QLocalServer::newConnection,this,[this]{while(auto*socket=m_server.nextPendingConnection()){socket->disconnectFromServer();socket->deleteLater();}emit activationRequested();});}
bool SingleInstance::acquire(){QLocalSocket socket;socket.connectToServer(m_name);if(socket.waitForConnected(300)){socket.write("activate");socket.waitForBytesWritten(300);return false;}QLocalServer::removeServer(m_name);return m_server.listen(m_name);}

bool BoundedLogger::install(const QString&directory,QString*error){QMutexLocker lock(&logMutex);logDirectory=directory;if(!QDir().mkpath(directory)){if(error)*error="Cannot create log directory";return false;}for(int i=4;i>=1;--i){const QString from=directory+QStringLiteral("/rigweave.%1.log").arg(i),to=directory+QStringLiteral("/rigweave.%1.log").arg(i+1);if(QFile::exists(from)){QFile::remove(to);QFile::rename(from,to);}}const QString active=directory+"/rigweave.log";if(QFileInfo(active).size()>1048576)QFile::rename(active,directory+"/rigweave.1.log");logFile=std::make_unique<QFile>(active);if(!logFile->open(QIODevice::WriteOnly|QIODevice::Append|QIODevice::Text)){if(error)*error=logFile->errorString();logFile.reset();return false;}qInstallMessageHandler(handler);return true;}
void BoundedLogger::handler(QtMsgType type,const QMessageLogContext&,const QString&message){QMutexLocker lock(&logMutex);if(!logFile)return;const char*level=type==QtDebugMsg?"DEBUG":type==QtInfoMsg?"INFO":type==QtWarningMsg?"WARN":type==QtCriticalMsg?"ERROR":"FATAL";logFile->write(QStringLiteral("%1 %2 %3\n").arg(QDateTime::currentDateTimeUtc().toString(Qt::ISODateWithMs),QString::fromLatin1(level),cleanMessage(message)).toUtf8());logFile->flush();}
void BoundedLogger::shutdown(){qInstallMessageHandler(nullptr);QMutexLocker lock(&logMutex);if(logFile){logFile->flush();logFile->close();logFile.reset();}}

SupportBundle::SupportBundle(DesktopPaths*paths,QObject*parent):QObject(parent),m_paths(paths){}
QString SupportBundle::create(const QVariantMap&health,QString*error)const{if(m_closed){if(error)*error="Support bundle service is closed";return{};}QVariantMap sanitized;for(auto it=health.cbegin();it!=health.cend();++it){const QString key=it.key().toLower();if(!key.contains("credential")&&!key.contains("token")&&!key.contains("comment")&&!key.contains("path"))sanitized.insert(it.key(),it.value());}const QString output=m_paths->supportBundles()+QStringLiteral("/RigWeave-Support-%1.zip").arg(QDateTime::currentDateTimeUtc().toString("yyyyMMdd-HHmmss"));QMap<QString,QByteArray> entries{{"health.json",QJsonDocument::fromVariant(sanitized).toJson(QJsonDocument::Indented)},{"privacy.txt","Credentials, QSO payloads/comments, raw cluster/CAT/serial traffic, and private paths are excluded.\n"},{"build.txt",QStringLiteral("RigWeave Windows Desktop Alpha\nQt %1\nSchema 16\n").arg(qVersion()).toUtf8()}};return writeZip(output,entries,error)?output:QString{};}

bool openAllowlistedExternalUrl(const QUrl&url){return url.scheme()==QStringLiteral("https")&&QStringList{"github.com","www.qt.io","doc.qt.io","hamlib.github.io","www.wavelog.org"}.contains(url.host().toLower())&&QDesktopServices::openUrl(url);}

} // namespace rigweave::desktop

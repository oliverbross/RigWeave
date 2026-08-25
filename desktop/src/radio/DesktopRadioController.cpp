#include "rigweave/desktop/DesktopRadioController.hpp"

#include <algorithm>
#include <iterator>
#include <tuple>

#ifdef RIGWEAVE_HAVE_HAMLIB
#include <hamlib/rig.h>
#endif

namespace rigweave::desktop {

#ifdef RIGWEAVE_HAVE_HAMLIB
int collectModel(const struct rig_caps *caps, void *data) {
    if (!caps || !data || caps->rig_model == RIG_MODEL_NONE) return 1;
    auto *models = static_cast<QVector<RadioModel> *>(data);
    models->push_back({static_cast<int>(caps->rig_model), QString::fromUtf8(caps->mfg_name),
                       QString::fromUtf8(caps->model_name),
                       QStringLiteral("backend-%1").arg(RIG_BACKEND_NUM(caps->rig_model)),
                       QString::fromLatin1(rig_strstatus(caps->status)),
                       QStringLiteral("port-type-%1").arg(static_cast<int>(caps->port_type))});
    return 1;
}
#endif

HamlibModelRegistry::HamlibModelRegistry(QObject *parent):QAbstractListModel(parent){load();}
void HamlibModelRegistry::load(){
#ifdef RIGWEAVE_HAVE_HAMLIB
    rig_set_debug(RIG_DEBUG_NONE);rig_load_all_backends();rig_list_foreach(collectModel,&m_all);
#else
    m_all.push_back({1,"Hamlib","Unavailable in this build","not-linked","platform gap","none"});
#endif
    std::sort(m_all.begin(),m_all.end(),[](const RadioModel&a,const RadioModel&b){return std::tie(a.manufacturer,a.model,a.id)<std::tie(b.manufacturer,b.model,b.id);});m_visible=m_all;
}
int HamlibModelRegistry::rowCount(const QModelIndex&p)const{return p.isValid()?0:m_visible.size();}
QVariant HamlibModelRegistry::data(const QModelIndex&i,int role)const{if(!i.isValid()||i.row()<0||i.row()>=m_visible.size())return{};const auto&m=m_visible.at(i.row());switch(role){case IdRole:return m.id;case ManufacturerRole:return m.manufacturer;case ModelRole:return m.model;case BackendRole:return m.backend;case StatusRole:return m.status;case TransportRole:return m.transport;default:return{};}}
QHash<int,QByteArray> HamlibModelRegistry::roleNames()const{return{{IdRole,"modelId"},{ManufacturerRole,"manufacturer"},{ModelRole,"model"},{BackendRole,"backend"},{StatusRole,"status"},{TransportRole,"transport"}};}
void HamlibModelRegistry::setSearch(const QString&search){m_search=search.trimmed();beginResetModel();if(m_search.isEmpty())m_visible=m_all;else{m_visible.clear();std::copy_if(m_all.cbegin(),m_all.cend(),std::back_inserter(m_visible),[this](const RadioModel&m){return(m.manufacturer+' '+m.model+' '+m.backend).contains(m_search,Qt::CaseInsensitive);});}endResetModel();emit countChanged();}

DesktopRadioController::DesktopRadioController(QObject *parent):QObject(parent){m_poll.setInterval(250);connect(&m_poll,&QTimer::timeout,this,&DesktopRadioController::poll);}
DesktopRadioController::~DesktopRadioController(){disconnectRadio();}
bool DesktopRadioController::connectRadio(int modelId,const QString&port,int baudRate){disconnectRadio();
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(port.trimmed().isEmpty()){emit error("An explicit serial or network route is required");return false;}RIG *rig=rig_init(modelId);if(!rig){emit error("Hamlib rejected the selected model");return false;}auto set=[rig](const char*name,const QString&value){const token_t token=rig_token_lookup(rig,name);return token!=RIG_CONF_END&&rig_set_conf(rig,token,value.toUtf8().constData())==RIG_OK;};if(!set("rig_pathname",port)){rig_cleanup(rig);emit error("Hamlib rejected the route");return false;}if(baudRate>0)set("serial_speed",QString::number(baudRate));const int code=rig_open(rig);if(code!=RIG_OK){const QString message=QStringLiteral("Hamlib connect failed: %1").arg(QString::fromLatin1(rigerror(code)));rig_cleanup(rig);emit error(message);return false;}m_rig=rig;m_generation++;m_state="Connected — receive controls only; PTT/TUNE disabled";m_model=QString::number(modelId);m_poll.start();poll();emit snapshotChanged();return true;
#else
    Q_UNUSED(modelId);Q_UNUSED(port);Q_UNUSED(baudRate);emit error("This build was compiled without pinned Hamlib 4.7.2");return false;
#endif
}
void DesktopRadioController::disconnectRadio(){m_poll.stop();
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(m_rig){auto*rig=static_cast<RIG*>(m_rig);rig_close(rig);rig_cleanup(rig);m_rig=nullptr;}
#endif
    m_generation++;m_state="Disconnected";m_frequencyHz=0;m_mode.clear();emit snapshotChanged();}
bool DesktopRadioController::requestFrequency(qulonglong hz){
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(!m_rig||hz<100000||hz>10500000000ULL){return false;}const int code=rig_set_freq(static_cast<RIG*>(m_rig),RIG_VFO_CURR,static_cast<freq_t>(hz));if(code!=RIG_OK){emit error(QString::fromLatin1(rigerror(code)));return false;}poll();return true;
#else
    Q_UNUSED(hz);return false;
#endif
}
bool DesktopRadioController::requestMode(const QString&value){
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(!m_rig){return false;}const rmode_t mode=rig_parse_mode(value.toUtf8().constData());if(mode==RIG_MODE_NONE){return false;}const int code=rig_set_mode(static_cast<RIG*>(m_rig),RIG_VFO_CURR,mode,RIG_PASSBAND_NORMAL);if(code!=RIG_OK){emit error(QString::fromLatin1(rigerror(code)));return false;}poll();return true;
#else
    Q_UNUSED(value);return false;
#endif
}
void DesktopRadioController::poll(){
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(!m_rig){return;}freq_t frequency=0;rmode_t mode=RIG_MODE_NONE;pbwidth_t width=0;auto*rig=static_cast<RIG*>(m_rig);if(rig_get_freq(rig,RIG_VFO_CURR,&frequency)==RIG_OK)m_frequencyHz=static_cast<quint64>(frequency);if(rig_get_mode(rig,RIG_VFO_CURR,&mode,&width)==RIG_OK)m_mode=QString::fromLatin1(rig_strrmode(mode));emit snapshotChanged();
#endif
}

} // namespace rigweave::desktop

#include "rigweave/desktop/DesktopRotatorController.hpp"

#ifdef RIGWEAVE_HAVE_HAMLIB
#include <hamlib/rotator.h>
#endif

namespace rigweave::desktop {
DesktopRotatorController::DesktopRotatorController(QObject*parent):QObject(parent){m_poll.setInterval(500);connect(&m_poll,&QTimer::timeout,this,&DesktopRotatorController::poll);}
DesktopRotatorController::~DesktopRotatorController(){disconnectRotator();}
bool DesktopRotatorController::connectRotator(int modelId,const QString&port,int baudRate){disconnectRotator();
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(port.trimmed().isEmpty()){emit error("An explicit rotator route is required");return false;}ROT*rot=rot_init(modelId);if(!rot){emit error("Hamlib rejected the rotator model");return false;}auto set=[rot](const char*name,const QString&value){const token_t token=rot_token_lookup(rot,name);return token!=RIG_CONF_END&&rot_set_conf(rot,token,value.toUtf8().constData())==RIG_OK;};if(!set("rot_pathname",port)){rot_cleanup(rot);emit error("Hamlib rejected the rotator route");return false;}if(baudRate>0)set("serial_speed",QString::number(baudRate));const int code=rot_open(rot);if(code!=RIG_OK){const QString message=QStringLiteral("Hamlib rotator connect failed: %1").arg(QString::fromLatin1(rigerror(code)));rot_cleanup(rot);emit error(message);return false;}m_rotator=rot;m_state="Connected / automation disarmed / PROMPT movement";m_poll.start();poll();emit snapshotChanged();return true;
#else
    Q_UNUSED(modelId);Q_UNUSED(port);Q_UNUSED(baudRate);emit error("This build was compiled without pinned Hamlib 4.7.2");return false;
#endif
}
void DesktopRotatorController::disconnectRotator(){m_poll.stop();
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(m_rotator){auto*rot=static_cast<ROT*>(m_rotator);rot_close(rot);rot_cleanup(rot);m_rotator=nullptr;}
#endif
    m_state="Disconnected / automation disarmed";m_targetPrepared=false;emit snapshotChanged();emit preparedChanged();}
bool DesktopRotatorController::prepareTarget(double azimuth,double elevation){if(azimuth<0||azimuth>450||elevation<-10||elevation>180){return false;}m_preparedAzimuth=azimuth;m_preparedElevation=elevation;m_targetPrepared=true;emit preparedChanged();emit confirmationRequired(azimuth,elevation);return true;}
bool DesktopRotatorController::confirmMove(){
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(!m_rotator||!m_targetPrepared){return false;}m_targetPrepared=false;emit preparedChanged();const int code=rot_set_position(static_cast<ROT*>(m_rotator),azimuth_t(m_preparedAzimuth),elevation_t(m_preparedElevation));if(code!=RIG_OK){emit error(QString::fromLatin1(rigerror(code)));return false;}return true;
#else
    return false;
#endif
}
void DesktopRotatorController::stop(){
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(m_rotator){const int code=rot_stop(static_cast<ROT*>(m_rotator));if(code!=RIG_OK)emit error(QString::fromLatin1(rigerror(code)));}
#endif
}
bool DesktopRotatorController::park(){
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(!m_rotator){return false;}const int code=rot_park(static_cast<ROT*>(m_rotator));if(code!=RIG_OK){emit error(QString::fromLatin1(rigerror(code)));return false;}return true;
#else
    return false;
#endif
}
void DesktopRotatorController::poll(){
#ifdef RIGWEAVE_HAVE_HAMLIB
    if(!m_rotator){return;}azimuth_t az=0;elevation_t el=0;if(rot_get_position(static_cast<ROT*>(m_rotator),&az,&el)==RIG_OK){m_azimuth=az;m_elevation=el;emit snapshotChanged();}
#endif
}
} // namespace rigweave::desktop

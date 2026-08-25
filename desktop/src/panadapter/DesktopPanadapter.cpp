#include "rigweave/desktop/DesktopPanadapter.hpp"

#include "rigweave/core.h"

#include <QAudioFormat>
#include <vector>

namespace rigweave::desktop {

class DesktopPanadapter::Sink final : public QIODevice {
public:
    explicit Sink(DesktopPanadapter *owner):QIODevice(owner),m_owner(owner){open(QIODevice::WriteOnly);}
    qint64 readData(char*,qint64)override{return 0;}
    qint64 writeData(const char*data,qint64 length)override{m_owner->consume(data,length);return length;}
private:DesktopPanadapter*m_owner;
};

DesktopPanadapter::DesktopPanadapter(QObject*parent):QObject(parent),m_dsp(rw_panadapter_context_create()){}
DesktopPanadapter::~DesktopPanadapter(){stop();rw_panadapter_context_destroy(m_dsp);}
void DesktopPanadapter::setSelectedDeviceId(const QString&id){if(id==m_selectedDeviceId){return;}stop();m_selectedDeviceId=id;emit selectedDeviceChanged();}
QVariantList DesktopPanadapter::devices()const{QVariantList list;for(const auto&device:QMediaDevices::audioInputs())list<<QVariantMap{{"id",QString::fromLatin1(device.id().toBase64())},{"description",device.description()},{"default",device.isDefault()},{"minimumChannels",device.minimumChannelCount()},{"maximumChannels",device.maximumChannelCount()}};return list;}
bool DesktopPanadapter::configureDsp(int sampleRate,bool swapIq){if(!m_dsp||sampleRate<48000||sampleRate>192000){return false;}rw_panadapter_config config{};config.sample_rate=static_cast<uint32_t>(sampleRate);config.fft_size=4096;config.overlap_percent=50;config.window=0;config.display_floor_db=-120.0F;config.display_top_db=0.0F;config.attack=.78F;config.release=.16F;config.average_frames=2;config.peak_hold=1;config.swap_iq=swapIq;config.i_trim=1.0F;config.q_trim=1.0F;config.zoom_decimation=1;return rw_panadapter_configure(m_dsp,&config)==1;}
bool DesktopPanadapter::start(int sampleRate,bool swapIq){stop();if(m_selectedDeviceId.isEmpty()){emit error("Select an exact stereo audio input; microphone fallback is disabled");return false;}QAudioDevice selected;for(const auto&device:QMediaDevices::audioInputs())if(QString::fromLatin1(device.id().toBase64())==m_selectedDeviceId){selected=device;break;}if(selected.isNull()){m_state="Offline — configured audio route is absent";emit stateChanged();emit error(m_state);return false;}if(selected.maximumChannelCount()<2){emit error("The selected input does not expose real stereo I/Q");return false;}QAudioFormat format;format.setSampleRate(sampleRate);format.setChannelCount(2);format.setSampleFormat(QAudioFormat::Int16);if(!selected.isFormatSupported(format)){emit error("The exact audio route does not support requested stereo Int16 format");return false;}if(!configureDsp(sampleRate,swapIq)){emit error("Panadapter DSP rejected configuration");return false;}m_sink=std::make_unique<Sink>(this);m_source=std::make_unique<QAudioSource>(selected,format,this);m_source->setBufferSize(sampleRate*4/10);m_source->start(m_sink.get());if(m_source->error()!=QAudio::NoError){emit error("Qt Multimedia could not start the exact audio route");stop();return false;}m_state=QStringLiteral("Receiving %1 Hz stereo I/Q — receive only").arg(sampleRate);emit stateChanged();return true;}
void DesktopPanadapter::stop(){if(m_source){m_source->stop();m_source.reset();}m_sink.reset();m_trace.clear();m_waterfall.clear();m_validStereo=false;m_peakDb=-140.0;m_state="Offline — select an exact stereo audio route";emit stateChanged();emit frameReady();}
void DesktopPanadapter::consume(const char*data,qint64 length){if(!m_dsp||length<=0){return;}if(rw_panadapter_push(m_dsp,reinterpret_cast<const uint8_t*>(data),static_cast<size_t>(length),2,2,16,0)==1)updateFrame();}
void DesktopPanadapter::updateFrame(){rw_panadapter_snapshot snapshot{};std::vector<float>trace(4096),waterfall(4096),peak(4096);const size_t count=rw_panadapter_copy_frame(m_dsp,&snapshot,trace.data(),waterfall.data(),peak.data(),trace.size());if(count==0){return;}m_trace.clear();m_waterfall.clear();m_trace.reserve(qsizetype(count));m_waterfall.reserve(qsizetype(count));for(size_t i=0;i<count;i++){m_trace<<trace[i];m_waterfall<<waterfall[i];}m_peakDb=snapshot.peak_db;m_validStereo=snapshot.valid_stereo!=0;emit frameReady();}
bool DesktopPanadapter::processPcmForTest(const QByteArray&pcm,int sampleRate){if(!configureDsp(sampleRate,false)){return false;}const bool ok=rw_panadapter_push(m_dsp,reinterpret_cast<const uint8_t*>(pcm.constData()),static_cast<size_t>(pcm.size()),2,2,16,0)==1;updateFrame();return ok;}

} // namespace rigweave::desktop

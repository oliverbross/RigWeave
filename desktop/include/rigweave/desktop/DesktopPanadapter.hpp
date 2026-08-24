#pragma once

#include <QAudioDevice>
#include <QAudioSource>
#include <QIODevice>
#include <QMediaDevices>
#include <QObject>
#include <QTimer>
#include <QVariantList>
#include <memory>

struct rw_panadapter_context;

namespace rigweave::desktop {

class DesktopPanadapter final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString state READ state NOTIFY stateChanged)
    Q_PROPERTY(QString selectedDeviceId READ selectedDeviceId WRITE setSelectedDeviceId NOTIFY selectedDeviceChanged)
    Q_PROPERTY(QVariantList trace READ trace NOTIFY frameReady)
    Q_PROPERTY(QVariantList waterfall READ waterfall NOTIFY frameReady)
    Q_PROPERTY(double peakDb READ peakDb NOTIFY frameReady)
    Q_PROPERTY(bool validStereo READ validStereo NOTIFY frameReady)
public:
    explicit DesktopPanadapter(QObject *parent = nullptr);
    ~DesktopPanadapter() override;
    QString state() const { return m_state; }
    QString selectedDeviceId() const { return m_selectedDeviceId; }
    void setSelectedDeviceId(const QString &id);
    QVariantList trace() const { return m_trace; }
    QVariantList waterfall() const { return m_waterfall; }
    double peakDb() const { return m_peakDb; }
    bool validStereo() const { return m_validStereo; }
    Q_INVOKABLE QVariantList devices() const;
    Q_INVOKABLE bool start(int sampleRate = 96000, bool swapIq = false);
    Q_INVOKABLE void stop();
    bool processPcmForTest(const QByteArray &pcm, int sampleRate = 96000);
signals:
    void stateChanged();
    void selectedDeviceChanged();
    void frameReady();
    void error(QString message);
private:
    class Sink;
    bool configureDsp(int sampleRate, bool swapIq);
    void consume(const char *data, qint64 length);
    void updateFrame();
    rw_panadapter_context *m_dsp{};
    std::unique_ptr<QAudioSource> m_source;
    std::unique_ptr<Sink> m_sink;
    QString m_state{"Offline — select an exact stereo audio route"};
    QString m_selectedDeviceId;
    QVariantList m_trace;
    QVariantList m_waterfall;
    double m_peakDb{-140.0};
    bool m_validStereo{};
};

} // namespace rigweave::desktop

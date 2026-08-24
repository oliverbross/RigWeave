#pragma once

#include <QObject>
#include <QTimer>

namespace rigweave::desktop {

class DesktopRotatorController final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString state READ state NOTIFY snapshotChanged)
    Q_PROPERTY(double azimuth READ azimuth NOTIFY snapshotChanged)
    Q_PROPERTY(double elevation READ elevation NOTIFY snapshotChanged)
    Q_PROPERTY(double preparedAzimuth READ preparedAzimuth NOTIFY preparedChanged)
    Q_PROPERTY(double preparedElevation READ preparedElevation NOTIFY preparedChanged)
    Q_PROPERTY(bool automationArmed READ automationArmed CONSTANT)
public:
    explicit DesktopRotatorController(QObject *parent = nullptr);
    ~DesktopRotatorController() override;
    QString state() const { return m_state; }
    double azimuth() const { return m_azimuth; }
    double elevation() const { return m_elevation; }
    double preparedAzimuth() const { return m_preparedAzimuth; }
    double preparedElevation() const { return m_preparedElevation; }
    bool automationArmed() const { return false; }
    Q_INVOKABLE bool connectRotator(int modelId, const QString &port, int baudRate);
    Q_INVOKABLE void disconnectRotator();
    Q_INVOKABLE bool prepareTarget(double azimuth, double elevation);
    Q_INVOKABLE bool confirmMove();
    Q_INVOKABLE void stop();
    Q_INVOKABLE bool park();
signals:
    void snapshotChanged();
    void preparedChanged();
    void confirmationRequired(double azimuth, double elevation);
    void error(QString message);
private:
    void poll();
    void *m_rotator{};
    QTimer m_poll;
    QString m_state{"Disconnected / automation disarmed"};
    double m_azimuth{};
    double m_elevation{};
    double m_preparedAzimuth{};
    double m_preparedElevation{};
    bool m_targetPrepared{};
};

} // namespace rigweave::desktop

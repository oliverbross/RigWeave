#pragma once

#include <QAbstractListModel>
#include <QObject>
#include <QQueue>
#include <QTimer>

namespace rigweave::desktop {

struct RadioModel {
    int id{};
    QString manufacturer;
    QString model;
    QString backend;
    QString status;
    QString transport;
};

class HamlibModelRegistry final : public QAbstractListModel {
    Q_OBJECT
    Q_PROPERTY(int count READ rowCount NOTIFY countChanged)
public:
    enum Role { IdRole = Qt::UserRole + 1, ManufacturerRole, ModelRole, BackendRole,
                StatusRole, TransportRole };
    explicit HamlibModelRegistry(QObject *parent = nullptr);
    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;
    Q_INVOKABLE void setSearch(const QString &search);
    const QVector<RadioModel> &allModels() const { return m_all; }
signals:
    void countChanged();
private:
    void load();
    QString m_search;
    QVector<RadioModel> m_all;
    QVector<RadioModel> m_visible;
};

class DesktopRadioController final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString state READ state NOTIFY snapshotChanged)
    Q_PROPERTY(QString model READ model NOTIFY snapshotChanged)
    Q_PROPERTY(qulonglong frequencyHz READ frequencyHz NOTIFY snapshotChanged)
    Q_PROPERTY(QString mode READ mode NOTIFY snapshotChanged)
    Q_PROPERTY(bool readOnly READ readOnly NOTIFY snapshotChanged)
    Q_PROPERTY(bool pttAvailable READ pttAvailable CONSTANT)
    Q_PROPERTY(bool tuneAvailable READ tuneAvailable CONSTANT)
public:
    explicit DesktopRadioController(QObject *parent = nullptr);
    ~DesktopRadioController() override;
    QString state() const { return m_state; }
    QString model() const { return m_model; }
    quint64 frequencyHz() const { return m_frequencyHz; }
    QString mode() const { return m_mode; }
    bool readOnly() const { return true; }
    bool pttAvailable() const { return false; }
    bool tuneAvailable() const { return false; }
    Q_INVOKABLE bool connectRadio(int modelId, const QString &port, int baudRate);
    Q_INVOKABLE void disconnectRadio();
    Q_INVOKABLE bool requestFrequency(qulonglong frequencyHz);
    Q_INVOKABLE bool requestMode(const QString &mode);
signals:
    void snapshotChanged();
    void error(QString message);
private:
    void poll();
    void *m_rig{};
    QTimer m_poll;
    QString m_state{"Disconnected"};
    QString m_model;
    quint64 m_frequencyHz{};
    QString m_mode;
    quint64 m_generation{};
};

} // namespace rigweave::desktop

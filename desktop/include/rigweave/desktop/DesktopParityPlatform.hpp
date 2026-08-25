#pragma once

#include <QAbstractListModel>
#include <QHash>
#include <QNetworkAccessManager>
#include <QObject>
#include <QPointer>
#include <QSqlDatabase>
#include <QVariantMap>
#include <utility>

class QNetworkReply;

namespace rigweave::desktop {

class ProviderResponsePolicy final {
public:
    static QVariantMap evaluate(int status, const QString &contentType, const QByteArray &body,
                                const QStringList &acceptedContentTypes, int maximumBytes,
                                bool hasCache, const QString &networkError = {},
                                const QByteArray &retryAfter = {});
};

class MapListModel final : public QAbstractListModel {
    Q_OBJECT
    Q_PROPERTY(int count READ rowCount NOTIFY countChanged)
public:
    enum Role {
        ItemRole = Qt::UserRole + 1,
        KeyRole,
        TitleRole,
        SubtitleRole,
        StateRole,
        DetailRole,
        CategoryRole,
        ValueRole,
        TimestampRole,
        EnabledRole
    };

    explicit MapListModel(QObject *parent = nullptr);
    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;
    Q_INVOKABLE QVariantMap item(int row) const;
    void replace(QVariantList rows);
    void update(const QString &key, const QVariantMap &values);

signals:
    void countChanged();

private:
    QVariantList m_rows;
};

class DesktopParityPlatform final : public QObject {
    Q_OBJECT
    Q_PROPERTY(MapListModel *homeModules READ homeModules CONSTANT)
    Q_PROPERTY(MapListModel *providers READ providers CONSTANT)
    Q_PROPERTY(MapListModel *digiModes READ digiModes CONSTANT)
    Q_PROPERTY(MapListModel *neuralOpportunities READ neuralOpportunities CONSTANT)
    Q_PROPERTY(MapListModel *contestDefinitions READ contestDefinitions CONSTANT)
    Q_PROPERTY(MapListModel *contestLog READ contestLog CONSTANT)
    Q_PROPERTY(MapListModel *groupsMessages READ groupsMessages CONSTANT)
    Q_PROPERTY(MapListModel *portableActivity READ portableActivity CONSTANT)
    Q_PROPERTY(MapListModel *satellitePasses READ satellitePasses CONSTANT)
    Q_PROPERTY(MapListModel *keyerMacros READ keyerMacros CONSTANT)
    Q_PROPERTY(QString activeReview READ activeReview NOTIFY activeReviewChanged)
    Q_PROPERTY(QString safetyState READ safetyState NOTIFY safetyStateChanged)
    Q_PROPERTY(bool demoMode READ demoMode CONSTANT)
    Q_PROPERTY(int galleryBandMapLayout READ galleryBandMapLayout WRITE setGalleryBandMapLayout NOTIFY galleryBandMapLayoutChanged)
public:
    explicit DesktopParityPlatform(QObject *parent = nullptr);
    ~DesktopParityPlatform() override;

    bool open(const QString &databaseDirectory, const QString &cacheDirectory,
              bool demoMode, QString *error = nullptr);
    void close();

    MapListModel *homeModules() { return &m_homeModules; }
    MapListModel *providers() { return &m_providers; }
    const MapListModel *providers() const { return &m_providers; }
    MapListModel *digiModes() { return &m_digiModes; }
    MapListModel *neuralOpportunities() { return &m_neuralOpportunities; }
    MapListModel *contestDefinitions() { return &m_contestDefinitions; }
    MapListModel *contestLog() { return &m_contestLog; }
    MapListModel *groupsMessages() { return &m_groupsMessages; }
    MapListModel *portableActivity() { return &m_portableActivity; }
    MapListModel *satellitePasses() { return &m_satellitePasses; }
    MapListModel *keyerMacros() { return &m_keyerMacros; }
    QString activeReview() const { return m_activeReview; }
    QString safetyState() const { return m_safetyState; }
    bool demoMode() const { return m_demoMode; }
    int galleryBandMapLayout() const { return m_galleryBandMapLayout; }
    void setGalleryBandMapLayout(int value);

    Q_INVOKABLE QVariantMap workspaceSummary(const QString &workspace) const;
    Q_INVOKABLE QVariantMap databaseHealth() const;
    Q_INVOKABLE bool setHomeModuleVisible(const QString &key, bool visible);
    Q_INVOKABLE bool setProviderEnabled(const QString &key, bool enabled);
    Q_INVOKABLE bool refreshProvider(const QString &key);
    Q_INVOKABLE bool prepareReceiveReview(const QString &source, const QVariantMap &target);
    Q_INVOKABLE bool prepareContestMerge(const QVariantMap &session);
    Q_INVOKABLE bool prepareGroupsDraft(const QVariantMap &draft);
    Q_INVOKABLE bool selectSatellitePass(const QVariantMap &pass);
    Q_INVOKABLE void clearReview();
    Q_INVOKABLE void globalStop();
    QVariantMap runDeterministicScaleProbe(QString *error = nullptr);

signals:
    void activeReviewChanged();
    void safetyStateChanged();
    void providerError(QString provider, QString message);
    void galleryBandMapLayoutChanged();

private:
    struct ProviderSpec {
        ProviderSpec() = default;
        ProviderSpec(QString providerKey, QString providerTitle, QUrl providerUrl,
                     QStringList acceptedContentTypes, int byteLimit, int cooldown)
            : key(std::move(providerKey)), title(std::move(providerTitle)),
              url(std::move(providerUrl)), contentTypes(std::move(acceptedContentTypes)),
              maximumBytes(byteLimit), cooldownSeconds(cooldown) {}
        QString key;
        QString title;
        QUrl url;
        QStringList contentTypes;
        int maximumBytes{};
        int cooldownSeconds{};
        bool enabled{};
        qint64 lastAttempt{};
        QPointer<QNetworkReply> reply;
        QByteArray body;
    };
    struct StoreSpec {
        QString key;
        QString path;
        int schema{};
        QString connection;
        QSqlDatabase database;
    };

    bool openStore(StoreSpec &store, QString *error);
    bool migrateStore(StoreSpec &store, QString *error);
    void loadRegistries();
    void seedDemo();
    void finishProvider(const QString &key);
    void setReview(const QString &text);
    static QString sanitizedNetworkError(const QString &message);

    MapListModel m_homeModules;
    MapListModel m_providers;
    MapListModel m_digiModes;
    MapListModel m_neuralOpportunities;
    MapListModel m_contestDefinitions;
    MapListModel m_contestLog;
    MapListModel m_groupsMessages;
    MapListModel m_portableActivity;
    MapListModel m_satellitePasses;
    MapListModel m_keyerMacros;
    QNetworkAccessManager m_network;
    QHash<QString, ProviderSpec> m_providerSpecs;
    QVector<StoreSpec> m_stores;
    QString m_cacheDirectory;
    QString m_activeReview;
    QString m_safetyState{"Disconnected / RX only / automation disarmed"};
    bool m_demoMode{};
    bool m_closed{true};
    int m_galleryBandMapLayout{};
};

} // namespace rigweave::desktop

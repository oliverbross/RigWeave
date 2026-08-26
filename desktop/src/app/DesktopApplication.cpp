#include "rigweave/desktop/DesktopApplication.hpp"

#include "rigweave/core.h"

#include <QDateTime>
#include <QDir>
#include <QGuiApplication>
#include <QKeyEvent>
#include <QQmlContext>
#include <QSet>
#include <QTimer>
#include <QUuid>

#include <algorithm>
#include <cmath>

#ifndef RIGWEAVE_BUILD_SHA
#define RIGWEAVE_BUILD_SHA "local-uncommitted-build"
#endif

namespace rigweave::desktop {

DesktopApplication::DesktopApplication(QObject *parent)
    : QObject(parent), m_rfObservations(this), m_cluster(&m_spots, this),
      m_parity(this), m_supportBundle(&m_paths, this) {
  connect(
      &m_radio, &DesktopRadioController::iqFrame, &m_panadapter,
      [this](const QString &id, quint32 rate, const QVector<float> &values) {
        const QVariantMap receiver = m_radio.receivers()->receiver(id);
        quint64 centre = receiver.value("centreFrequencyHz").toULongLong();
        if (centre == 0)
          centre = receiver.value("effectiveReceiveHz").toULongLong();
        m_panadapter.pushFloatIq(id, rate, values, centre, false);
        if (!m_panadapter.receiverIds().contains(
                m_panadapter.currentReceiverId()))
          m_panadapter.setCurrentReceiverId(id);
      });
}
DesktopApplication::~DesktopApplication() { shutdown(); }

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
        if (error)
          *error = "Cannot create isolated demo directory";
        return false;
      }
      if (qEnvironmentVariableIntValue("RIGWEAVE_DEMO_PRESERVE") == 1)
        m_demoDirectory->setAutoRemove(false);
      m_paths.setEphemeralRoot(m_demoDirectory->path());
    }
  }
  if (!m_paths.create(error) || !BoundedLogger::install(m_paths.logs(), error))
    return false;
  m_configuration = std::make_unique<DesktopConfigurationManager>(
      m_paths.configuration() + "/desktop-config.json", this);
  if (!m_configuration->load(error))
    return false;
  if (!m_radio.restoreConfiguration(m_configuration->section("radioProfiles"),
                                    error))
    return false;
  m_configuration->setSection("radioProfiles", m_radio.configuration());
  if (!m_panadapter.restoreConfiguration(m_configuration->section("panadapter"),
                                         error))
    return false;
  m_configuration->setSection("panadapter", m_panadapter.configuration());
  if (!m_rfObservations.restoreConfiguration(
          m_configuration->section("display").value("rfObservations").toMap(),
          error))
    return false;
  connect(&m_radio, &DesktopRadioController::preferencesChanged, this, [this] {
    if (m_configuration)
      m_configuration->setSection("radioProfiles", m_radio.configuration());
  });
  connect(&m_panadapter, &DesktopPanadapter::settingsChanged, this, [this] {
    if (m_configuration)
      m_configuration->setSection("panadapter", m_panadapter.configuration());
  });
  connect(&m_rfObservations, &RfObservationModel::filtersChanged, this, [this] {
    if (!m_configuration)
      return;
    auto display = m_configuration->section("display");
    display["rfObservations"] = m_rfObservations.configuration();
    m_configuration->setSection("display", display);
  });
  m_currentDestination = m_configuration->lastDestination();
  m_database = std::make_unique<QsoDatabase>(
      m_paths.databases() + "/rigweave-desktop.sqlite", this);
  if (!m_database->open(error))
    return false;
  m_logbook = std::make_unique<QsoTableModel>(m_database.get(), this);
  m_adif = std::make_unique<AdifService>(m_database.get(), this);
  m_wavelog = new WavelogSyncEngine(m_database.get(), this);
  auto *endpoint = new QtWavelogEndpoint(m_wavelog);
  m_wavelog->setEndpoint(endpoint);
  m_wavelog->setCredentialResolver([this](const QString &alias) {
    return m_credentials.read(alias).value_or(QString{});
  });
  if (!m_parity.open(m_paths.databases(), m_paths.cache(), m_demoMode, error))
    return false;
  if (m_demoMode) {
    m_rfObservations.loadDeterministicDemo();
    const qint64 now = QDateTime::currentSecsSinceEpoch();
    m_spots.ingest({14074000, "K1ABC", "N0TEST", "CQ FT8", "20m", "FT8", "DEMO",
                    now - 42, true, false, false});
    m_spots.ingest({14062000, "DL1AAA", "G0TEST", "CQ CW", "20m", "CW", "DEMO",
                    now - 91, false, true, false});
    m_spots.ingest({14244000, "JA1XYZ", "VK0TEST", "CQ DX", "20m", "USB",
                    "DEMO", now - 155, false, false, true});
  }
  return true;
}

void DesktopApplication::expose(QQmlApplicationEngine &engine) {
  auto *context = engine.rootContext();
  context->setContextProperty("Desktop", this);
  context->setContextProperty("DesktopPaths", &m_paths);
  context->setContextProperty("DesktopConfig", m_configuration.get());
  context->setContextProperty("LogbookModel", m_logbook.get());
  context->setContextProperty("Adif", m_adif.get());
  context->setContextProperty("Spots", &m_spots);
  context->setContextProperty("RfObservations", &m_rfObservations);
  context->setContextProperty("Cluster", &m_cluster);
  context->setContextProperty("Wavelog", m_wavelog);
  context->setContextProperty("RadioModels", &m_radioModels);
  context->setContextProperty("Radio", &m_radio);
  context->setContextProperty("Rotator", &m_rotator);
  context->setContextProperty("Panadapter", &m_panadapter);
  context->setContextProperty("Parity", &m_parity);
  context->setContextProperty("CredentialVault", &m_credentials);
  context->setContextProperty("SupportBundle", &m_supportBundle);
}

void DesktopApplication::setCurrentDestination(const QString &destination) {
  static const QSet<QString> allowed{
      "Home",    "Radio",        "Digi",     "Panadapter", "EQ",
      "Logbook", "Intelligence", "Sync",     "Contest",    "Band Maps",
      "Presets", "DX",           "Portable", "Operations", "Groups.io",
      "Rotator", "Settings",     "Health",   "About"};
  if (!allowed.contains(destination) || m_currentDestination == destination)
    return;
  m_currentDestination = destination;
  if (m_configuration)
    m_configuration->setLastDestination(destination);
  emit currentDestinationChanged();
}

QVariantList DesktopApplication::commands() const {
#ifdef Q_OS_MACOS
  const auto shortcut = [](const char *mac, const char *) { return QString::fromUtf8(mac); };
#else
  const auto shortcut = [](const char *, const char *desktop) { return QString::fromUtf8(desktop); };
#endif
  QVariantList result;
  const auto add = [&result](const char *id, const char *label,
                             const char *category, const char *icon,
                             const QString &key = {}, const char *destination = "",
                             bool workspace = false, bool enabled = true) {
    result.push_back(QVariantMap{{"id", id}, {"label", label},
                                 {"category", category}, {"icon", icon},
                                 {"shortcut", key}, {"destination", destination},
                                 {"workspace", workspace}, {"enabled", enabled}});
  };
  add("nav.home", "Home", "OPERATE", "home", shortcut("Meta+1", "Ctrl+1"), "Home", true);
  add("nav.radio", "Radio", "OPERATE", "radio", shortcut("Meta+2", "Ctrl+2"), "Radio", true);
  add("nav.digi", "Digi", "OPERATE", "digi", shortcut("Meta+3", "Ctrl+3"), "Digi", true);
  add("nav.panadapter", "Panadapter", "OPERATE", "panadapter", {}, "Panadapter", true);
  add("nav.eq", "EQ", "OPERATE", "eq", {}, "EQ", true);
  add("nav.logbook", "Logbook", "LOG & INTELLIGENCE", "logbook", shortcut("Meta+L", "Ctrl+L"), "Logbook", true);
  add("nav.intelligence", "Intelligence", "LOG & INTELLIGENCE", "intelligence", {}, "Intelligence", true);
  add("nav.sync", "Sync", "LOG & INTELLIGENCE", "sync", {}, "Sync", true);
  add("nav.contest", "Contest", "LOG & INTELLIGENCE", "contest", {}, "Contest", true);
  add("nav.bandmaps", "Band Maps", "LOG & INTELLIGENCE", "bandmaps", {}, "Band Maps", true);
  add("nav.presets", "Presets", "LOG & INTELLIGENCE", "presets", {}, "Presets", true);
  add("nav.dx", "DX", "LOG & INTELLIGENCE", "dx", {}, "DX", true);
  add("nav.portable", "Portable", "FIELD & CONNECTED", "portable", {}, "Portable", true);
  add("nav.operations", "Operations", "FIELD & CONNECTED", "operations", {}, "Operations", true);
  add("nav.groups", "Groups.io", "FIELD & CONNECTED", "groups", {}, "Groups.io", true);
  add("nav.rotator", "Rotator", "FIELD & CONNECTED", "rotator", {}, "Rotator", true);
  add("nav.settings", "Settings", "SYSTEM", "settings", shortcut("Meta+,", "Ctrl+,"), "Settings", true);
  add("nav.health", "Health", "SYSTEM", "health", {}, "Health", true);
  add("nav.about", "About", "SYSTEM", "about", {}, "About", true);
  add("file.fastEntry", "Fast Entry", "FILE", "logbook", shortcut("Meta+N", "Ctrl+N"));
  add("file.importAdif", "Import ADIF…", "FILE", "import");
  add("file.exportAdif", "Export ADIF…", "FILE", "export");
  add("file.importConfig", "Import Configuration…", "FILE", "import", {}, "", false, false);
  add("file.exportConfig", "Export Configuration…", "FILE", "export");
  add("file.close", "Close Window", "FILE", "close", shortcut("Meta+W", "Ctrl+W"));
  add("app.quit", "Quit RigWeave", "FILE", "close", shortcut("Meta+Q", "Ctrl+Q"));
  add("edit.undo", "Undo", "EDIT", "undo", shortcut("Meta+Z", "Ctrl+Z"));
  add("edit.redo", "Redo", "EDIT", "redo", shortcut("Meta+Shift+Z", "Ctrl+Y"));
  add("edit.cut", "Cut", "EDIT", "cut", shortcut("Meta+X", "Ctrl+X"));
  add("edit.copy", "Copy", "EDIT", "copy", shortcut("Meta+C", "Ctrl+C"));
  add("edit.paste", "Paste", "EDIT", "paste", shortcut("Meta+V", "Ctrl+V"));
  add("edit.delete", "Delete", "EDIT", "delete");
  add("edit.selectAll", "Select All", "EDIT", "select", shortcut("Meta+A", "Ctrl+A"));
  add("edit.find", "Find", "EDIT", "search", shortcut("Meta+F", "Ctrl+F"), "", false, false);
  add("view.fullScreen", "Full Screen", "VIEW", "fullscreen", shortcut("Meta+Ctrl+F", "F11"));
  add("view.shack", "Shack Display", "VIEW", "shack", shortcut("Meta+Shift+S", "Ctrl+Shift+D"));
  add("view.resetLayout", "Reset Workspace Layout", "VIEW", "reset");
  add("radio.connect", "Connect…", "RADIO", "connect", {}, "", false, false);
  add("radio.disconnect", "Disconnect", "RADIO", "disconnect");
  add("radio.review", "Receive Review", "RADIO", "radio");
  add("radio.stop", "Emergency RX / Global Stop", "RADIO", "stop", "Escape");
  add("tools.palette", "Command Palette", "TOOLS", "search", shortcut("Meta+K", "Ctrl+K"));
  add("tools.support", "Create Support Bundle…", "TOOLS", "support", {}, "", false, false);
  add("help.guide", "Operator Guide", "HELP", "help", {}, "", false, false);
  add("help.shortcuts", "Keyboard Shortcuts", "HELP", "keyboard");
  add("help.licences", "Licences and Acknowledgements", "HELP", "about");
  return result;
}

QVariantMap DesktopApplication::panelGeometry(
    const QString &workspace, const QString &panel,
    const QVariantMap &fallback) const {
  QVariantMap result = fallback;
  result[QStringLiteral("stored")] = false;
  if (!m_configuration || workspace.isEmpty() || panel.isEmpty())
    return result;
  const QVariantMap layouts = m_configuration->section("desktopLayouts");
  const QVariantMap workspaceLayout = layouts.value(workspace).toMap();
  QVariantMap saved = workspaceLayout.value(panel).toMap();
  if (saved.isEmpty())
    return result;
  saved[QStringLiteral("stored")] = true;
  return saved;
}

void DesktopApplication::savePanelGeometry(const QString &workspace,
                                           const QString &panel,
                                           const QVariantMap &geometry) {
  if (!m_configuration || workspace.isEmpty() || panel.isEmpty())
    return;
  QVariantMap bounded;
  for (const QString &key : {QStringLiteral("x"), QStringLiteral("y"),
                             QStringLiteral("width"), QStringLiteral("height")}) {
    bool ok = false;
    const double value = geometry.value(key).toDouble(&ok);
    if (!ok || !std::isfinite(value))
      return;
    bounded.insert(key, std::clamp(value, 0.0, 8192.0));
  }
  auto layouts = m_configuration->section("desktopLayouts");
  auto workspaceLayout = layouts.value(workspace).toMap();
  workspaceLayout[panel] = bounded;
  layouts[workspace] = workspaceLayout;
  m_configuration->setSection("desktopLayouts", layouts);
}

void DesktopApplication::resetWorkspaceLayout(const QString &workspace) {
  if (!m_configuration || workspace.isEmpty())
    return;
  auto layouts = m_configuration->section("desktopLayouts");
  layouts.remove(workspace);
  m_configuration->setSection("desktopLayouts", layouts);
  emit workspaceLayoutReset(workspace);
}

void DesktopApplication::invokeCommand(const QString &commandId) {
  for (const QVariant &value : commands()) {
    const QVariantMap command = value.toMap();
    if (command.value("id").toString() != commandId)
      continue;
    if (!command.value("enabled").toBool())
      return;
    const QString destination = command.value("destination").toString();
    if (!destination.isEmpty())
      setCurrentDestination(destination);
    if (commandId == "radio.stop")
      globalStop();
    else if (commandId == "radio.disconnect")
      m_radio.disconnectRadio();
    else if (commandId == "view.resetLayout")
      resetWorkspaceLayout(m_currentDestination);
    else if (commandId == "edit.delete") {
      if (QObject *focus = QGuiApplication::focusObject()) {
        QKeyEvent press(QEvent::KeyPress, Qt::Key_Delete, Qt::NoModifier);
        QKeyEvent release(QEvent::KeyRelease, Qt::Key_Delete, Qt::NoModifier);
        QCoreApplication::sendEvent(focus, &press);
        QCoreApplication::sendEvent(focus, &release);
      }
    } else if (commandId.startsWith("edit.")) {
      QObject *focus = QGuiApplication::focusObject();
      const QByteArray method = commandId.mid(5).toUtf8();
      if (focus)
        QMetaObject::invokeMethod(focus, method.constData());
    }
    else if (commandId == "app.quit")
      emit quitRequested();
    emit commandInvoked(commandId);
    return;
  }
}

QString DesktopApplication::localFilePath(const QUrl &url) const {
  return url.isLocalFile() ? url.toLocalFile() : QString{};
}
bool DesktopApplication::prepareGalleryTci(const QUrl &endpoint) {
  if (!m_demoMode || !endpoint.isValid())
    return false;
  return m_radio.saveTciProfile({{"id", "gallery-tci"},
                                 {"displayName", "Deterministic fake TCI"},
                                 {"endpoint", endpoint.toString()},
                                 {"preferredIqSampleRate", 96000},
                                 {"preferredReceiver", 0},
                                 {"autoConnect", false},
                                 {"rxAudioOutputRoute", QString{}}});
}
void DesktopApplication::setGalleryVariant(const QString &workspace,
                                           int variant) {
  if (!m_demoMode)
    return;
  if (m_galleryVariant != variant) {
    m_galleryVariant = variant;
    emit galleryVariantChanged();
  }
  if (workspace == "Band Maps")
    m_parity.setGalleryBandMapLayout(variant);
  else if (workspace == "Radio") {
    if (variant == 0)
      m_radio.disconnectRadio();
    else {
      if (m_radio.backend() != "tci")
        m_radio.connectTciProfile("gallery-tci");
      if (m_radio.receiverCount() >= 2) {
        if (variant == 2) {
          m_radio.selectActiveReceiver("tci:0");
          m_radio.selectListeningReceiver("tci:1");
        } else if (variant >= 3) {
          m_radio.selectActiveReceiver("tci:1");
          m_radio.selectListeningReceiver("tci:0");
        }
      }
    }
  } else if (workspace == "Panadapter") {
    m_panadapter.setDisplayMode("Spectrum + waterfall");
    m_panadapter.setPeakHold(true);
    m_panadapter.setPaused(false);
    if (variant == 2) {
      m_panadapter.setFitAutoContrast(false);
      m_panadapter.setManualFloorDb(-110);
      m_panadapter.setManualTopDb(-20);
    } else
      m_panadapter.setFitAutoContrast(true);
    if (variant == 4 && m_panadapter.receiverIds().contains("tci:0"))
      m_panadapter.setCurrentReceiverId("tci:0");
  } else if (workspace == "Intelligence") {
    m_rfObservations.resetFilters();
    if (variant == 1)
      m_rfObservations.setFilter("band", "20m");
    else if (variant == 3)
      m_rfObservations.setFilter("band", "15m");
    else if (variant == 4) {
      m_rfObservations.setFilter("band", "20m");
      m_rfObservations.setSelectedId("demo-live-ja");
    } else if (variant == 5) {
      m_rfObservations.setSelectedId({});
      m_rfObservations.setFilter("source", "Unavailable provider");
    }
  }
}

QVariantMap DesktopApplication::health() const {
  QString projectionError;
  const bool projection =
      m_database && m_database->verifyProjection(&projectionError);
  return {
      {"database", m_database ? "Open / schema 16" : "Unavailable"},
      {"databaseRevision",
       m_database ? QVariant::fromValue<qulonglong>(m_database->revision())
                  : 0},
      {"projection", projection ? "Verified" : projectionError},
      {"domainStores", m_parity.databaseHealth()},
      {"wavelog", m_wavelog ? m_wavelog->state() : "Unavailable"},
      {"cluster", m_cluster.state()},
      {"radio", m_radio.health()},
      {"rotator", m_rotator.state()},
      {"panadapter", m_panadapter.health()},
      {"rfObservations",
       QVariantMap{
           {"count", m_rfObservations.rowCount()},
           {"storedCount", m_rfObservations.storedCount()},
           {"rendererRecordCap", 4096},
           {"droppedObservations", QVariant::fromValue<qulonglong>(
                                       m_rfObservations.droppedObservations())},
           {"sourceFreshness", m_rfObservations.filterSummary()},
           {"renderer", "Flat and globe public Qt scene graph ready"},
           {"selectedId", m_rfObservations.selectedId()}}},
      {"configuration", m_configuration ? "Loaded" : "Unavailable"},
      {"providers", QStringLiteral("%1 registered; disabled by default")
                        .arg(m_parity.providers()->rowCount())}};
}
QVariantMap DesktopApplication::buildInformation() const {
  return {{"buildSha", QString::fromLatin1(RIGWEAVE_BUILD_SHA).isEmpty()
                           ? "local-uncommitted-build"
                           : QString::fromLatin1(RIGWEAVE_BUILD_SHA)},
          {"qtVersion", QString::fromLatin1(qVersion())},
          {"coreVersion", QString::fromLatin1(rw_core_version())},
          {"databaseSchema", QsoDatabase::SchemaVersion},
          {"licence", "GPL-3.0-only"},
          {"hamlib",
           "4.7.2 pinned source; operational only when linked in this build"}};
}
QVariantMap DesktopApplication::intelligence() const {
  return m_database ? m_database->intelligenceSummary() : QVariantMap{};
}

bool DesktopApplication::saveFastEntry(const QVariantMap &values) {
  if (!m_database)
    return false;
  QsoRecord q;
  q.id = QUuid::createUuid().toString(QUuid::WithoutBraces);
  q.callsign = values.value("callsign").toString();
  q.frequencyHz = values.value("frequencyHz").toLongLong();
  q.frequencyRxHz = values.value("frequencyRxHz").toLongLong();
  q.band = values.value("band").toString();
  q.mode = values.value("mode").toString();
  q.submode = values.value("submode").toString();
  q.rstSent = values.value("rstSent", "59").toString();
  q.rstReceived = values.value("rstReceived", "59").toString();
  q.grid = values.value("grid").toString();
  q.comment = values.value("comment").toString();
  q.stationProfileId = values.value("stationProfileId").toString();
  q.stationCallsign = values.value("stationCallsign").toString();
  q.operatorCallsign = values.value("operatorCallsign").toString();
  q.contestId = values.value("contestId").toString();
  q.satelliteName = values.value("satelliteName").toString();
  q.satelliteMode = values.value("satelliteMode").toString();
  q.potaRef = values.value("potaRef").toString();
  q.sotaRef = values.value("sotaRef").toString();
  q.iota = values.value("iota").toString();
  q.wwffRef = values.value("wwffRef").toString();
  q.extraAdif = QJsonObject::fromVariantMap(values.value("extraAdif").toMap());
  q.createdAt = QDateTime::currentSecsSinceEpoch();
  QString error;
  if (!m_database->save(q, &error)) {
    emit this->error(error);
    return false;
  }
  m_logbook->firstPage();
  if (m_wavelog && m_wavelog->binding())
    m_wavelog->enqueue(q.id, "CREATE");
  return true;
}

void DesktopApplication::globalStop() {
  m_parity.globalStop();
  m_radio.globalStop();
  m_rotator.stop();
  m_panadapter.stop();
  qInfo("Global STOP requested: pending radio mutations and receive streams "
        "stopped; rotator stop sent when connected; PTT/TUNE are unavailable");
}
void DesktopApplication::shutdown() {
  if (m_shuttingDown)
    return;
  m_shuttingDown = true;
  emit shuttingDownChanged();
  if (m_adif)
    m_adif->cancel();
  if (m_wavelog)
    m_wavelog->close();
  m_cluster.disconnectProfile();
  m_parity.close();
  m_panadapter.stop();
  m_rotator.stop();
  m_rotator.disconnectRotator();
  m_radio.disconnectRadio();
  m_supportBundle.close();
  if (m_configuration)
    m_configuration->save();
  BoundedLogger::shutdown();
}

} // namespace rigweave::desktop

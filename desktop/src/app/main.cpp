#include "rigweave/desktop/DesktopApplication.hpp"
#include "rigweave/desktop/DesktopPlatform.hpp"
#include "rigweave/desktop/PanadapterSceneItem.hpp"
#include "rigweave/desktop/RfMapItem.hpp"
#include "rigweave/tci.hpp"

#include <QCommandLineOption>
#include <QCommandLineParser>
#include <QDir>
#include <QFont>
#include <QFontDatabase>
#include <QApplication>
#include <QAction>
#include <QMenu>
#include <QMenuBar>
#include <QHostAddress>
#include <QPointer>
#include <QQmlApplicationEngine>
#include <QQuickWindow>
#include <QTimer>
#include <QWebSocket>
#include <QWebSocketServer>
#include <cmath>
#include <functional>
#include <memory>
#include <qqml.h>

using namespace rigweave::desktop;

namespace {

class GalleryTciServer final : public QObject {
public:
  explicit GalleryTciServer(QObject *parent = nullptr)
      : QObject(parent),
        m_server(QStringLiteral("RigWeave deterministic gallery TCI"),
                 QWebSocketServer::NonSecureMode, this) {
    m_timer.setInterval(45);
    connect(&m_timer, &QTimer::timeout, this, [this] { sendIq(); });
    connect(&m_server, &QWebSocketServer::newConnection, this, [this] {
      while (m_server.hasPendingConnections()) {
        QWebSocket *peer = m_server.nextPendingConnection();
        peer->setParent(this);
        m_peers.push_back(peer);
        connect(peer, &QWebSocket::textMessageReceived, peer,
                [peer](const QString &message) {
                  if (!message.contains("trx:0,true") &&
                      !message.contains("trx:1,true") &&
                      !message.contains("tune:0,true") &&
                      !message.contains("tune:1,true"))
                    peer->sendTextMessage(message);
                });
        connect(peer, &QWebSocket::disconnected, peer, &QObject::deleteLater);
        QTimer::singleShot(10, peer, [peer] {
          peer->sendTextMessage(
              "start;protocol:GalleryFixture,1.0;device:Deterministic fake "
              "TCI;trx_count:2;channels_count:2;dds:0,14074000;dds:1,7074000;"
              "vfo:0,0,14074000;vfo:1,0,7074000;modulation:0,digu;modulation:1,"
              "cw;rx_enable:0,true;rx_enable:1,true;ready;");
        });
        QTimer::singleShot(40, this, [this] {
          if (!m_timer.isActive())
            m_timer.start();
        });
      }
    });
  }
  bool start() { return m_server.listen(QHostAddress::LocalHost, 0); }
  QUrl url() const {
    return QUrl(QStringLiteral("ws://127.0.0.1:%1").arg(m_server.serverPort()));
  }

private:
  void sendIq() {
    m_peers.removeIf(
        [](const QPointer<QWebSocket> &peer) { return peer.isNull(); });
    for (int receiver = 0; receiver < 2; ++receiver) {
      std::vector<float> values(8192);
      for (int sample = 0; sample < 4096; ++sample) {
        const double phase = 2.0 * 3.141592653589793 *
                                 (receiver ? 311.0 : 173.0) * sample / 4096.0 +
                             double(m_sequence) * .025;
        values[2 * sample] = float(std::cos(phase) * .28);
        values[2 * sample + 1] = float(std::sin(phase) * .28);
      }
      const auto bytes = rigweave::tci::build_binary_for_test(
          rigweave::tci::DataType::Iq, static_cast<std::uint32_t>(receiver),
          96'000U, 2U, values);
      const QByteArray frame(reinterpret_cast<const char *>(bytes.data()),
                             static_cast<qsizetype>(bytes.size()));
      for (const auto &peer : m_peers)
        if (peer && peer->state() == QAbstractSocket::ConnectedState)
          peer->sendBinaryMessage(frame);
    }
    ++m_sequence;
  }
  QWebSocketServer m_server;
  QTimer m_timer;
  QList<QPointer<QWebSocket>> m_peers;
  quint64 m_sequence{};
};

bool installPlatformUiFont(QGuiApplication &app) {
#ifdef Q_OS_WIN
  const QString fonts =
      qEnvironmentVariable("WINDIR", QStringLiteral("C:/Windows")) +
      QStringLiteral("/Fonts/");
  const QStringList candidates{fonts + QStringLiteral("segoeui.ttf"),
                               fonts + QStringLiteral("arial.ttf")};
  for (const QString &path : candidates) {
    const int id = QFontDatabase::addApplicationFont(path);
    if (id < 0)
      continue;
    const QStringList families = QFontDatabase::applicationFontFamilies(id);
    if (families.isEmpty())
      continue;
    app.setFont(QFont(families.first()));
    return true;
  }
  qWarning(
      "Windows UI font could not be loaded; glyph evidence may be incomplete");
  return false;
#else
  app.setFont(QFontDatabase::systemFont(QFontDatabase::GeneralFont));
  return true;
#endif
}

#ifdef Q_OS_MACOS
std::unique_ptr<QMenuBar> buildNativeMenuBar(DesktopApplication &desktop) {
  auto menuBar = std::make_unique<QMenuBar>();
  menuBar->setNativeMenuBar(true);
  const auto command = [&desktop](QMenu *menu, const QString &commandId,
                                  QAction::MenuRole role = QAction::NoRole) {
    for (const QVariant &value : desktop.commands()) {
      const QVariantMap item = value.toMap();
      if (item.value("id").toString() != commandId)
        continue;
      auto *action = menu->addAction(item.value("label").toString());
      action->setEnabled(item.value("enabled").toBool());
      action->setMenuRole(role);
      const QString key = item.value("shortcut").toString();
      if (!key.isEmpty())
        action->setShortcut(QKeySequence(key));
      QObject::connect(action, &QAction::triggered, &desktop,
                       [&desktop, commandId] { desktop.invokeCommand(commandId); });
      return action;
    }
    return static_cast<QAction *>(nullptr);
  };

  QMenu *appMenu = menuBar->addMenu(QStringLiteral("RigWeave"));
  command(appMenu, "nav.about", QAction::AboutRole)->setText(QStringLiteral("About RigWeave"));
  command(appMenu, "nav.settings", QAction::PreferencesRole)->setText(QStringLiteral("Settings…"));
  appMenu->addSeparator();
  command(appMenu, "app.quit", QAction::QuitRole);

  QMenu *file = menuBar->addMenu(QStringLiteral("File"));
  command(file, "file.fastEntry");
  file->addSeparator();
  command(file, "file.importAdif");
  command(file, "file.exportAdif");
  command(file, "file.importConfig");
  command(file, "file.exportConfig");
  file->addSeparator();
  command(file, "file.close");

  QMenu *edit = menuBar->addMenu(QStringLiteral("Edit"));
  command(edit, "edit.undo");
  command(edit, "edit.redo");
  edit->addSeparator();
  command(edit, "edit.cut");
  command(edit, "edit.copy");
  command(edit, "edit.paste");
  command(edit, "edit.delete");
  command(edit, "edit.selectAll");
  edit->addSeparator();
  command(edit, "edit.find");

  QMenu *view = menuBar->addMenu(QStringLiteral("View"));
  command(view, "view.sidebarToggle");
  command(view, "view.sidebarMode");
  view->addSeparator();
  command(view, "view.fullScreen");
  command(view, "view.shack");
  command(view, "view.resetLayout");

  QMenu *radio = menuBar->addMenu(QStringLiteral("Radio"));
  command(radio, "radio.connect");
  command(radio, "radio.disconnect");
  command(radio, "radio.review");
  radio->addSeparator();
  command(radio, "radio.stop");

  QMenu *navigate = menuBar->addMenu(QStringLiteral("Navigate"));
  for (const QVariant &value : desktop.commands()) {
    const QVariantMap item = value.toMap();
    if (item.value("rail").toBool())
      command(navigate, item.value("id").toString());
  }
  navigate->addSeparator();
  command(navigate, "tools.palette");

  QMenu *window = menuBar->addMenu(QStringLiteral("Window"));
  QAction *minimize = window->addAction(QStringLiteral("Minimize"));
  minimize->setShortcut(QKeySequence(QStringLiteral("Meta+M")));
  QObject::connect(minimize, &QAction::triggered, qApp, [] {
    if (QWindow *window = QGuiApplication::focusWindow())
      window->showMinimized();
  });
  command(window, "view.fullScreen")->setText(QStringLiteral("Zoom / Full Screen"));

  QMenu *help = menuBar->addMenu(QStringLiteral("Help"));
  command(help, "help.guide");
  command(help, "help.shortcuts");
  command(help, "nav.health");
  command(help, "tools.support");
  help->addSeparator();
  command(help, "help.licences");
  return menuBar;
}
#endif

struct GalleryFrame {
  QString destination;
  QString fileName;
  int variant{-1};
};

void captureGallery(QGuiApplication &app, DesktopApplication &desktop,
                    QQuickWindow *window, const QString &directory, int width,
                    int height) {
  const QList<GalleryFrame> frames = {
      {"Home", "Home"},
      {"Home", "Shack"},
      {"Radio", "Radio-native"},
      {"Radio", "Radio-generic"},
      {"Radio", "TCI-disconnected-profile", 0},
      {"Radio", "TCI-connected-fake-server", 1},
      {"Radio", "TCI-two-receivers", 2},
      {"Radio", "TCI-receiver-switch", 3},
      {"Digi", "Digi"},
      {"Panadapter", "Panadapter-spectrum-waterfall", 0},
      {"Panadapter", "Panadapter-FIT-auto-contrast", 1},
      {"Panadapter", "Panadapter-manual-floor-top", 2},
      {"Panadapter", "Panadapter-peak-passband", 3},
      {"Panadapter", "Panadapter-dual-receiver", 4},
      {"EQ", "EQ"},
      {"Logbook", "Logbook"},
      {"Intelligence", "RF-Flat-Filters", 0},
      {"Intelligence", "RF-Flat-20m-Observed-Heat", 1},
      {"Intelligence", "RF-Globe-Default", 2},
      {"Intelligence", "RF-Globe-15m", 3},
      {"Intelligence", "RF-Globe-Selected-Path", 4},
      {"Intelligence", "RF-Empty-Stale-Offline", 5},
      {"Sync", "Sync"},
      {"Contest", "Contest"},
      {"Band Maps", "Band-Maps-vertical", 0},
      {"Band Maps", "Band-Maps-horizontal", 1},
      {"Band Maps", "Band-Maps-grid", 2},
      {"Band Maps", "Band-Maps-expanded", 3},
      {"DX", "DX-Neural"},
      {"Portable", "Portable"},
      {"Operations", "Operations-Planner", 0},
      {"Operations", "Operations-Satellite", 1},
      {"Operations", "Operations-QO100", 2},
      {"Groups.io", "Groups-io"},
      {"Rotator", "Rotator"},
      {"Settings", "Settings"},
      {"Health", "Health"},
      {"About", "About"}};
  if (!QDir().mkpath(directory)) {
    qCritical("Cannot create UI gallery directory");
    app.exit(4);
    return;
  }
  window->setWidth(width);
  window->setHeight(height);
  window->show();
  auto index = std::make_shared<int>(0);
  auto step = std::make_shared<std::function<void()>>();
  *step = [&app, &desktop, window, directory, frames, index, step] {
    if (*index >= frames.size()) {
      app.quit();
      return;
    }
    const GalleryFrame frame = frames.at(*index);
    window->setProperty("shackMode", frame.fileName == "Shack");
    const int radioBackend = frame.fileName.startsWith("TCI-")
                                 ? 9
                                 : (frame.fileName == "Radio-generic" ? 7 : 0);
    window->setProperty("galleryRadioBackend", radioBackend);
    desktop.setGalleryVariant(frame.destination, frame.variant);
    desktop.setCurrentDestination(frame.destination);
    QTimer::singleShot(
        350, window, [window, directory, frame, index, step, &app, &desktop] {
          desktop.setGalleryVariant(frame.destination, frame.variant);
          if (QObject *backend = window->findChild<QObject *>("radioBackend"))
            backend->setProperty(
                "currentIndex",
                frame.fileName.startsWith("TCI-")
                    ? 9
                    : (frame.fileName == "Radio-generic" ? 7 : 0));
          if (frame.destination == "Operations")
            if (QObject *tabs = window->findChild<QObject *>("operationsTabs"))
              tabs->setProperty("currentIndex", frame.variant);
          if (frame.destination == "Band Maps")
            if (QObject *layout = window->findChild<QObject *>("bandMapLayout"))
              layout->setProperty("currentIndex", frame.variant);
          if (frame.destination == "Intelligence") {
            for (QObject *tabs :
                 window->findChildren<QObject *>("intelligenceTabs"))
              tabs->setProperty("currentIndex", 2);
            for (QObject *projection :
                 window->findChildren<QObject *>("rfProjection"))
              projection->setProperty(
                  "currentIndex",
                  frame.variant >= 2 && frame.variant <= 4 ? 1 : 0);
            for (QObject *map : window->findChildren<QObject *>("rfMapScene")) {
              map->setProperty("zoom", frame.variant == 4 ? 1.35 : 1.0);
              map->setProperty("longitude", frame.variant == 4 ? 55.0 : 0.0);
              map->setProperty("latitude", frame.variant == 4 ? 12.0 : 0.0);
            }
          }
          QTimer::singleShot(
              150, window, [window, directory, frame, index, step, &app] {
                const QImage image = window->grabWindow();
                if (image.isNull() ||
                    !image.save(directory + "/" + frame.fileName + ".png")) {
                  qCritical("UI gallery frame failed: %s",
                            qPrintable(frame.fileName));
                  app.exit(4);
                  return;
                }
                ++*index;
                (*step)();
              });
        });
  };
  QTimer::singleShot(500, window, [step] { (*step)(); });
}

} // namespace

int main(int argc, char *argv[]) {
  QApplication app(argc, argv);
  qmlRegisterType<PanadapterSceneItem>("RigWeave.Controls", 1, 0,
                                       "PanadapterScene");
  qmlRegisterType<RfMapItem>("RigWeave.Controls", 1, 0, "RfMapScene");
  const bool platformFontReady = installPlatformUiFont(app);
  QCoreApplication::setOrganizationName(QStringLiteral("RigWeave"));
  QCoreApplication::setOrganizationDomain(QStringLiteral("rigweave.app"));
  QCoreApplication::setApplicationName(QStringLiteral("RigWeave Desktop"));
  QCoreApplication::setApplicationVersion(QStringLiteral("1.0.0-parity.1"));

  QCommandLineParser parser;
  parser.addHelpOption();
  parser.addOption({"smoke-test", "Exit after a bounded launch smoke."});
  parser.addOption(
      {"gallery-dir", "Capture the deterministic UI gallery.", "directory"});
  parser.addOption(
      {"gallery-width", "Gallery width in pixels.", "width", "1920"});
  parser.addOption(
      {"gallery-height", "Gallery height in pixels.", "height", "1080"});
  parser.process(app);
  const bool gallery = parser.isSet("gallery-dir");
  if (gallery && !platformFontReady)
    return 4;
  if (gallery)
    qputenv("RIGWEAVE_DESKTOP_DEMO", "1");
  const bool demo = qEnvironmentVariableIntValue("RIGWEAVE_DESKTOP_DEMO") == 1;

  SingleInstance single(demo
                            ? QStringLiteral("app.rigweave.desktop.parity.demo")
                            : QStringLiteral("app.rigweave.desktop"));
  if (!single.acquire())
    return 0;

  DesktopApplication desktop;
  QString error;
  if (!desktop.initialize(&error)) {
    qCritical("Desktop initialization failed: %s", qPrintable(error));
    return 2;
  }
  QObject::connect(&desktop, &DesktopApplication::quitRequested, &app,
                   &QCoreApplication::quit);
#ifdef Q_OS_MACOS
  std::unique_ptr<QMenuBar> nativeMenuBar = buildNativeMenuBar(desktop);
#endif
  std::unique_ptr<GalleryTciServer> galleryTci;
  if (gallery) {
    galleryTci = std::make_unique<GalleryTciServer>(&app);
    if (!galleryTci->start() || !desktop.prepareGalleryTci(galleryTci->url())) {
      qCritical("Cannot start isolated deterministic gallery TCI fixture");
      return 4;
    }
  }
  QQmlApplicationEngine engine;
  desktop.expose(engine);
  QObject::connect(
      &engine, &QQmlApplicationEngine::objectCreationFailed, &app,
      [] { QCoreApplication::exit(3); }, Qt::QueuedConnection);
  QObject::connect(
      &single, &SingleInstance::activationRequested, &app, [&engine] {
        if (engine.rootObjects().isEmpty())
          return;
        auto *window =
            qobject_cast<QQuickWindow *>(engine.rootObjects().first());
        if (window) {
          window->show();
          window->raise();
          window->requestActivate();
        }
      });
  QObject::connect(&app, &QCoreApplication::aboutToQuit, &desktop,
                   &DesktopApplication::shutdown);
  engine.load(QUrl(QStringLiteral("qrc:/RigWeave/App/Main.qml")));
  if (engine.rootObjects().isEmpty())
    return 3;

  if (parser.isSet("smoke-test"))
    QTimer::singleShot(1500, &app, &QCoreApplication::quit);
  if (gallery) {
    bool widthValid = false;
    bool heightValid = false;
    const int width = parser.value("gallery-width").toInt(&widthValid);
    const int height = parser.value("gallery-height").toInt(&heightValid);
    auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
    if (!window || !widthValid || !heightValid || width < 1280 || height < 720)
      return 4;
    captureGallery(app, desktop, window, parser.value("gallery-dir"), width,
                   height);
  }
  return app.exec();
}

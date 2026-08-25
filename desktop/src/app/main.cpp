#include "rigweave/desktop/DesktopApplication.hpp"
#include "rigweave/desktop/DesktopPlatform.hpp"

#include <QCommandLineOption>
#include <QCommandLineParser>
#include <QDir>
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQuickWindow>
#include <QTimer>
#include <functional>
#include <memory>

using namespace rigweave::desktop;

namespace {

struct GalleryFrame {
    QString destination;
    QString fileName;
    int variant{-1};
};

void captureGallery(QGuiApplication &app, DesktopApplication &desktop, QQuickWindow *window,
                    const QString &directory, int width, int height) {
    const QList<GalleryFrame> frames = {
        {"Home", "Home"}, {"Home", "Shack"}, {"Radio", "Radio-native"},
        {"Radio", "Radio-generic"}, {"Digi", "Digi"}, {"Panadapter", "Panadapter"},
        {"EQ", "EQ"}, {"Logbook", "Logbook"}, {"Intelligence", "Intelligence"},
        {"Sync", "Sync"}, {"Contest", "Contest"},
        {"Band Maps", "Band-Maps-vertical", 0}, {"Band Maps", "Band-Maps-horizontal", 1},
        {"Band Maps", "Band-Maps-grid", 2}, {"Band Maps", "Band-Maps-expanded", 3},
        {"DX", "DX-Neural"}, {"Portable", "Portable"},
        {"Operations", "Operations-Planner", 0}, {"Operations", "Operations-Satellite", 1},
        {"Operations", "Operations-QO100", 2}, {"Groups.io", "Groups-io"},
        {"Rotator", "Rotator"}, {"Settings", "Settings"}, {"Health", "Health"},
        {"About", "About"}
    };
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
        window->setProperty("galleryRadioBackend", frame.fileName == "Radio-generic" ? 7 : 0);
        desktop.setGalleryVariant(frame.destination, frame.variant);
        desktop.setCurrentDestination(frame.destination);
        QTimer::singleShot(180, window, [window, directory, frame, index, step, &app] {
            if (QObject *backend = window->findChild<QObject *>("radioBackend"))
                backend->setProperty("currentIndex", frame.fileName == "Radio-generic" ? 7 : 0);
            if (frame.destination == "Operations")
                if (QObject *tabs = window->findChild<QObject *>("operationsTabs")) tabs->setProperty("currentIndex", frame.variant);
            if (frame.destination == "Band Maps")
                if (QObject *layout = window->findChild<QObject *>("bandMapLayout")) layout->setProperty("currentIndex", frame.variant);
            QTimer::singleShot(100, window, [window, directory, frame, index, step, &app] {
                const QImage image = window->grabWindow();
                if (image.isNull() || !image.save(directory + "/" + frame.fileName + ".png")) {
                    qCritical("UI gallery frame failed: %s", qPrintable(frame.fileName));
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
    QGuiApplication app(argc, argv);
    QCoreApplication::setOrganizationName(QStringLiteral("RigWeave"));
    QCoreApplication::setOrganizationDomain(QStringLiteral("rigweave.app"));
    QCoreApplication::setApplicationName(QStringLiteral("RigWeave Desktop"));
    QCoreApplication::setApplicationVersion(QStringLiteral("1.0.0-parity.1"));

    QCommandLineParser parser;
    parser.addHelpOption();
    parser.addOption({"smoke-test", "Exit after a bounded launch smoke."});
    parser.addOption({"gallery-dir", "Capture the deterministic UI gallery.", "directory"});
    parser.addOption({"gallery-width", "Gallery width in pixels.", "width", "1920"});
    parser.addOption({"gallery-height", "Gallery height in pixels.", "height", "1080"});
    parser.process(app);
    const bool gallery = parser.isSet("gallery-dir");
    if (gallery) qputenv("RIGWEAVE_DESKTOP_DEMO", "1");
    const bool demo = qEnvironmentVariableIntValue("RIGWEAVE_DESKTOP_DEMO") == 1;

    SingleInstance single(demo ? QStringLiteral("app.rigweave.desktop.parity.demo")
                               : QStringLiteral("app.rigweave.desktop"));
    if (!single.acquire()) return 0;

    DesktopApplication desktop;
    QString error;
    if (!desktop.initialize(&error)) {
        qCritical("Desktop initialization failed: %s", qPrintable(error));
        return 2;
    }
    QQmlApplicationEngine engine;
    desktop.expose(engine);
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreationFailed,
                     &app, [] { QCoreApplication::exit(3); }, Qt::QueuedConnection);
    QObject::connect(&single, &SingleInstance::activationRequested, &app, [&engine] {
        if (engine.rootObjects().isEmpty()) return;
        auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
        if (window) {
            window->show();
            window->raise();
            window->requestActivate();
        }
    });
    QObject::connect(&app, &QCoreApplication::aboutToQuit,
                     &desktop, &DesktopApplication::shutdown);
    engine.load(QUrl(QStringLiteral("qrc:/RigWeave/App/Main.qml")));
    if (engine.rootObjects().isEmpty()) return 3;

    if (parser.isSet("smoke-test")) QTimer::singleShot(1500, &app, &QCoreApplication::quit);
    if (gallery) {
        bool widthValid = false;
        bool heightValid = false;
        const int width = parser.value("gallery-width").toInt(&widthValid);
        const int height = parser.value("gallery-height").toInt(&heightValid);
        auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
        if (!window || !widthValid || !heightValid || width < 1280 || height < 720) return 4;
        captureGallery(app, desktop, window, parser.value("gallery-dir"), width, height);
    }
    return app.exec();
}

#include "rigweave/desktop/DesktopApplication.hpp"
#include "rigweave/desktop/DesktopPlatform.hpp"

#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQuickWindow>
#include <QTimer>

using namespace rigweave::desktop;

int main(int argc,char*argv[]){QGuiApplication app(argc,argv);QCoreApplication::setOrganizationName(QStringLiteral("RigWeave"));QCoreApplication::setOrganizationDomain(QStringLiteral("rigweave.app"));QCoreApplication::setApplicationName(QStringLiteral("RigWeave Desktop Alpha"));QCoreApplication::setApplicationVersion(QStringLiteral("0.1.0-alpha.1"));
    SingleInstance single(QStringLiteral("app.rigweave.desktop.alpha"));if(!single.acquire())return 0;
    DesktopApplication desktop;QString error;if(!desktop.initialize(&error)){qCritical("Desktop initialization failed: %s",qPrintable(error));return 2;}
    QQmlApplicationEngine engine;desktop.expose(engine);QObject::connect(&engine,&QQmlApplicationEngine::objectCreationFailed,&app,[]{QCoreApplication::exit(3);},Qt::QueuedConnection);QObject::connect(&single,&SingleInstance::activationRequested,&app,[&engine]{if(engine.rootObjects().isEmpty())return;auto*window=qobject_cast<QQuickWindow*>(engine.rootObjects().first());if(window){window->show();window->raise();window->requestActivate();}});QObject::connect(&app,&QCoreApplication::aboutToQuit,&desktop,&DesktopApplication::shutdown);engine.loadFromModule(QStringLiteral("RigWeave.App"),QStringLiteral("Main"));if(app.arguments().contains(QStringLiteral("--smoke-test")))QTimer::singleShot(1500,&app,&QCoreApplication::quit);return app.exec();}

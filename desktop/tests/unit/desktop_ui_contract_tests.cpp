#include "rigweave/desktop/DesktopApplication.hpp"

#include <QFile>
#include <QSet>
#include <QtTest>

using namespace rigweave::desktop;

class DesktopUiContractTests final : public QObject {
  Q_OBJECT
private slots:
  void commandRegistryIsCompleteAndUnique();
  void shellUsesCanonicalCommandsAndNativeMenus();
  void originalIconFamilyCoversEveryWorkspaceDestination();
};

void DesktopUiContractTests::commandRegistryIsCompleteAndUnique() {
  DesktopApplication desktop;
  const QVariantList commands = desktop.commands();
  QSet<QString> ids;
  QSet<QString> destinations;
  int workspaceCount = 0;
  for (const QVariant &value : commands) {
    const QVariantMap command = value.toMap();
    const QString id = command.value("id").toString();
    QVERIFY2(!id.isEmpty(), "Every command needs a stable ID");
    QVERIFY2(!ids.contains(id), qPrintable(QString("Duplicate command: %1").arg(id)));
    ids.insert(id);
    QVERIFY(!command.value("label").toString().isEmpty());
    QVERIFY(!command.value("icon").toString().isEmpty());
    if (command.value("workspace").toBool()) {
      ++workspaceCount;
      destinations.insert(command.value("destination").toString());
      QVERIFY(!command.value("category").toString().isEmpty());
    }
  }
  QCOMPARE(workspaceCount, 19);
  QCOMPARE(destinations.size(), 19);
  for (const QString &required : {QStringLiteral("radio.stop"),
                                  QStringLiteral("tools.palette"),
                                  QStringLiteral("file.fastEntry"),
                                  QStringLiteral("view.fullScreen")})
    QVERIFY2(ids.contains(required), qPrintable(required));
}

void DesktopUiContractTests::shellUsesCanonicalCommandsAndNativeMenus() {
  QFile file(QStringLiteral(RIGWEAVE_DESKTOP_QML_DIR "/App/Main.qml"));
  QVERIFY(file.open(QIODevice::ReadOnly));
  const QByteArray qml = file.readAll();
  QVERIFY(qml.contains("Desktop.invokeCommand"));
  QVERIFY(qml.contains("Qt.platform.os === \"osx\""));
  QVERIFY(!qml.contains("menuBar: MenuBar"));
  QVERIFY(!qml.contains("sidebar"));
  QVERIFY(!qml.contains("SplitView"));
  QFile appSource(QStringLiteral(RIGWEAVE_DESKTOP_APP_DIR "/main.cpp"));
  QVERIFY(appSource.open(QIODevice::ReadOnly));
  const QByteArray nativeMenus = appSource.readAll();
  QVERIFY(nativeMenus.contains("buildNativeMenuBar"));
  QVERIFY(nativeMenus.contains("WindowsNativeMenu"));
  QVERIFY(nativeMenus.contains("addMenu(L\"&File\")"));
  QVERIFY(nativeMenus.contains("addMenu(L\"&Navigate\")"));
  QVERIFY(qml.contains("Accessible.name"));
  QVERIFY(!qml.contains("RigWeave Windows Desktop"));
}

void DesktopUiContractTests::originalIconFamilyCoversEveryWorkspaceDestination() {
  DesktopApplication desktop;
  for (const QVariant &value : desktop.commands()) {
    const QVariantMap command = value.toMap();
    if (!command.value("workspace").toBool())
      continue;
    const QString path = QStringLiteral(RIGWEAVE_DESKTOP_ICON_DIR "/") +
                         command.value("icon").toString() + QStringLiteral(".svg");
    QFile file(path);
    QVERIFY2(file.open(QIODevice::ReadOnly), qPrintable(path));
    const QByteArray svg = file.readAll();
    QVERIFY(svg.contains("<svg"));
    QVERIFY(svg.contains("viewBox=\"0 0 24 24\""));
    QVERIFY(!svg.contains("emoji"));
  }
}

QTEST_GUILESS_MAIN(DesktopUiContractTests)
#include "desktop_ui_contract_tests.moc"

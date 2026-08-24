#include "rigweave/desktop/DesktopPanadapter.hpp"
#include "rigweave/desktop/DesktopPlatform.hpp"
#include "rigweave/desktop/DesktopRadioController.hpp"
#include "rigweave/desktop/DesktopRotatorController.hpp"

#include <QJsonDocument>
#include <QTemporaryDir>
#include <QtTest>
#include <cmath>

using namespace rigweave::desktop;

class DesktopPlatformSafetyTests final : public QObject {
    Q_OBJECT
private slots:
    void credentialFakeUpdatesDeletesWithoutPersistence() { FakeCredentialVault vault;QVERIFY(vault.write("a","label","secret"));QCOMPARE(vault.read("a").value(),QString("secret"));QVERIFY(vault.write("a","label","new"));QCOMPARE(vault.read("a").value(),QString("new"));QVERIFY(vault.remove("a"));QVERIFY(!vault.read("a")); }
    void configurationWhitelistRestoresNoAuthority() { QTemporaryDir dir;DesktopConfigurationManager config(dir.filePath("config.json"));QString error;QVERIFY(config.load(&error));config.setSection("radioProfiles",{{"model",1},{"connected",true},{"ptt",true},{"pendingCommands",QStringList{"set_freq"}}});QVERIFY(config.exportBundle(dir.filePath("export.json"),&error));QFile file(dir.filePath("export.json"));QVERIFY(file.open(QIODevice::ReadOnly));const QByteArray bytes=file.readAll();QVERIFY(!bytes.contains("credential"));QVERIFY(!bytes.contains("pendingCommands"));QVERIFY(!bytes.contains("connected"));QVERIFY(!bytes.contains("ptt")); }
    void radioAndRotatorRestoreSafe() { DesktopRadioController radio;DesktopRotatorController rotator;QCOMPARE(radio.state(),QString("Disconnected"));QVERIFY(!radio.pttAvailable());QVERIFY(!radio.tuneAvailable());QCOMPARE(rotator.state(),QString("Disconnected / automation disarmed"));QVERIFY(!rotator.automationArmed()); }
    void panadapterRejectsMissingRouteAndAcceptsDeterministicStereoTone() { DesktopPanadapter pan;QVERIFY(!pan.start());QByteArray pcm;pcm.resize(96000/10*4);auto*samples=reinterpret_cast<qint16*>(pcm.data());for(int i=0;i<9600;i++){const qint16 value=qint16(std::sin(2.0*3.141592653589793*1000.0*i/96000.0)*12000);samples[2*i]=value;samples[2*i+1]=qint16(std::cos(2.0*3.141592653589793*1000.0*i/96000.0)*12000);}QVERIFY(pan.processPcmForTest(pcm));QVERIFY(!pan.trace().isEmpty());QVERIFY(pan.peakDb()>-80.0); }
};
QTEST_MAIN(DesktopPlatformSafetyTests)
#include "desktop_platform_safety_tests.moc"

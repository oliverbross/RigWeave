#include "rigweave/desktop/DesktopModels.hpp"
#include "rigweave/desktop/DesktopParityPlatform.hpp"
#include "rigweave/desktop/DesktopPanadapter.hpp"

#include <QElapsedTimer>
#include <QTemporaryDir>
#include <QtTest>

using namespace rigweave::desktop;

class DesktopScaleSoakTests final : public QObject {
    Q_OBJECT
private slots:
    void domainScaleMatrix();
    void spotCapacityAndLifecycleChurn();
    void panadapterFloatIqBoundedStress();
};

void DesktopScaleSoakTests::domainScaleMatrix() {
    QTemporaryDir root;
    QVERIFY(root.isValid());
    DesktopParityPlatform platform;
    QString error;
    QVERIFY2(platform.open(root.path() + "/db", root.path() + "/cache", false, &error), qPrintable(error));
    QElapsedTimer total;
    total.start();
    const QVariantMap result = platform.runDeterministicScaleProbe(&error);
    QVERIFY2(!result.isEmpty(), qPrintable(error));
    QCOMPARE(result.value("Neural").toMap().value("rows").toInt(), 2880);
    QCOMPARE(result.value("Digi").toMap().value("rows").toInt(), 20000);
    QCOMPARE(result.value("Groups.io").toMap().value("rows").toInt(), 30000);
    QCOMPARE(result.value("Contest").toMap().value("rows").toInt(), 10000);
    QCOMPARE(result.value("DX Chaser").toMap().value("rows").toInt(), 20000);
    QVERIFY2(total.elapsed() < 120000, "Deterministic domain scale matrix exceeded two minutes");
}

void DesktopScaleSoakTests::panadapterFloatIqBoundedStress() {
    DesktopPanadapter panadapter;
    panadapter.setFftSize(1024);
    panadapter.setWaterfallRows(96);
    QVector<float> iq(2048);
    QElapsedTimer elapsed;
    elapsed.start();
    for (int frame = 0; frame < 600; ++frame) {
        for (int sample = 0; sample < 1024; ++sample) {
            const float phase = float(2.0 * 3.141592653589793 * (31 + frame % 17) * sample / 1024.0);
            iq[2 * sample] = std::cos(phase) * .25F;
            iq[2 * sample + 1] = std::sin(phase) * .25F;
        }
        panadapter.pushFloatIq(frame % 2 ? "tci:1" : "tci:0", 96'000, iq,
                               frame % 2 ? 7'074'000U : 14'074'000U, frame % 199 == 0);
    }
    QCOMPARE(panadapter.receiverIds().size(), 2);
    for (const QString &id : panadapter.receiverIds()) {
        const auto rendered = panadapter.renderFrame(id);
        QCOMPARE(rendered.trace.size(), 1024);
        QCOMPARE(rendered.waterfall.size(), QSize(1024, 96));
        QVERIFY(rendered.waterfall.sizeInBytes() <= 1024U * 96U * 4U);
        QVERIFY(rendered.sequence > 100);
    }
    QVERIFY2(elapsed.elapsed() < 30'000, "Bounded dual-receiver float32 stress exceeded 30 seconds");
}

void DesktopScaleSoakTests::spotCapacityAndLifecycleChurn() {
    SpotRepository spots;
    const qint64 now = QDateTime::currentSecsSinceEpoch();
    for (int index = 0; index < 20001; ++index) {
        spots.ingest({quint64(14000000 + index), QStringLiteral("T%1DX").arg(index),
                      "SCALE", "bounded spot", "20m", "FT8", "SCALE",
                      now - index, false, false, false});
    }
    QCOMPARE(spots.rowCount(), 20000);

    QTemporaryDir root;
    QVERIFY(root.isValid());
    for (int cycle = 0; cycle < 25; ++cycle) {
        DesktopParityPlatform platform;
        QString error;
        QVERIFY2(platform.open(root.path() + "/db", root.path() + "/cache", cycle % 2, &error), qPrintable(error));
        QCOMPARE(platform.databaseHealth().size(), 5);
        platform.globalStop();
        platform.close();
    }
}

QTEST_GUILESS_MAIN(DesktopScaleSoakTests)
#include "desktop_scale_soak_tests.moc"

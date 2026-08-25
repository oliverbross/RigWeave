import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Rectangle {
    id: root
    color: "#101315"
    property date now: new Date()
    Timer { interval: 1000; running: true; repeat: true; onTriggered: root.now = new Date() }
    ColumnLayout {
        anchors.fill: parent; anchors.margins: 28; spacing: 18
        RowLayout { Layout.fillWidth: true
            Label { text: Qt.formatDateTime(root.now, "HH:mm:ss"); color: "#f2efe7"; font.pixelSize: 64; font.family: "monospace"; font.bold: true }
            Label { text: "UTC"; color: "#d38b22"; font.pixelSize: 24 }
            Item { Layout.fillWidth: true }
            StatusChip { text: Parity.safetyState; kind: "healthy" }
        }
        GridLayout { Layout.fillWidth: true; Layout.fillHeight: true; columns: 3; rowSpacing: 14; columnSpacing: 14
            MetricTile { label: "Station"; value: "DEMO"; truth: "Private deterministic profile" }
            MetricTile { label: "Radio"; value: "OFF LINE"; truth: Radio.state }
            MetricTile { label: "DX observations"; value: Spots.count; truth: "One repository" }
            MetricTile { label: "Neural opportunity"; value: Parity.neuralOpportunities.count > 0 ? Parity.neuralOpportunities.item(0).title : "—"; truth: "Empirical evidence / no CAT" }
            MetricTile { label: "Next pass"; value: Parity.satellitePasses.count > 0 ? Parity.satellitePasses.item(0).title : "—"; truth: "Local SGP4 receive preview" }
            MetricTile { label: "Portable"; value: Parity.portableActivity.count; truth: "Provider cache" }
            InstrumentPanel { Layout.columnSpan: 2; Layout.fillWidth: true; Layout.fillHeight: true; title: "DX / propagation watch"
                WorkspaceList { Layout.fillWidth: true; Layout.fillHeight: true; sourceModel: Parity.neuralOpportunities; actionsEnabled: false }
            }
            InstrumentPanel { Layout.fillWidth: true; Layout.fillHeight: true; title: "Alerts & health"
                Label { text: "No critical alerts\nProvider caches isolated\nTX controls intentionally absent"; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
            }
        }
        Label { text: "SHACK DISPLAY · operator-selected modules · Esc returns with Global STOP"; color: "#68727a"; Layout.alignment: Qt.AlignHCenter }
    }
}

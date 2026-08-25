import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    workspaceKey: "Home"

    CanvasPanel {
        panelKey: "safety"
        title: "Startup safety state"
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: 96
        panelMinimumHeight: 90
        SafetyBanner {
            anchors.fill: parent
            text: "Startup truth: radio disconnected, PTT/TUNE unavailable, rotator disarmed, and no audio route selected. Nothing connects automatically."
        }
    }

    CanvasPanel {
        panelKey: "station-overview"
        title: "Station overview"
        defaultY: 108
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: 178
        Flow {
            anchors.fill: parent
            spacing: 12
            MetricTile { label: "Station"; value: "OM0RX"; truth: "Configured operator default" }
            MetricTile { label: "Local QSOs"; value: Desktop.intelligence().qsos ?? 0; truth: "Canonical desktop database" }
            MetricTile { label: "Radio"; value: Radio.state.startsWith("Connected") ? "ON LINE" : "OFF LINE"; truth: Radio.state }
            MetricTile { label: "Cluster"; value: Spots.count; truth: Cluster.state + " / shared repository" }
            MetricTile { label: "Wavelog"; value: Wavelog.pendingCount; truth: Wavelog.state + " / pending" }
            MetricTile { label: "Next satellite"; value: Parity.satellitePasses.count > 0 ? Parity.satellitePasses.item(0).title : "—"; truth: "Local SGP4 / no automatic action" }
            MetricTile { label: "RF paths"; value: RfObservations.count; truth: RfObservations.filterSummary }
            Button { text: "Open Live RF / Outlook"; onClicked: Desktop.currentDestination = "Intelligence" }
        }
    }

    CanvasPanel {
        panelKey: "home-modules"
        title: "Home / HamClock modules"
        defaultY: 298
        defaultWidth: parent ? (parent.width - 12) * 0.5 : 600
        defaultHeight: 330
        ColumnLayout {
            anchors.fill: parent
            RowLayout {
                Layout.fillWidth: true
                Label { text: "VISIBLE MODULES"; color: "#d38b22"; font.bold: true }
                Item { Layout.fillWidth: true }
                StatusChip { text: Parity.workspaceSummary("Home").status; kind: "healthy" }
            }
            GridView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                cellWidth: 220
                cellHeight: 52
                model: Parity.homeModules
                clip: true
                delegate: CheckDelegate {
                    required property var item
                    width: 210
                    height: 44
                    text: item.title
                    checked: item.enabled
                    palette.text: "#f2efe7"
                    onToggled: Parity.setHomeModuleVisible(item.key, checked)
                }
            }
        }
    }

    CanvasPanel {
        panelKey: "dx-watchlist"
        title: "Current DX / watchlist"
        defaultX: parent ? (parent.width + 12) * 0.5 : 612
        defaultY: 298
        defaultWidth: parent ? (parent.width - 12) * 0.5 : 600
        defaultHeight: 330
        ListView {
            anchors.fill: parent
            model: Spots
            clip: true
            delegate: RowLayout {
                required property string callsign
                required property double frequencyHz
                required property string mode
                required property int ageSeconds
                width: ListView.view.width
                height: 32
                Label { text: callsign; color: "#f2efe7"; font.bold: true; Layout.preferredWidth: 110 }
                Label { text: (frequencyHz / 1000).toFixed(1) + " kHz"; color: "#e3c765"; Layout.preferredWidth: 130 }
                Label { text: mode; color: "#98a0a6"; Layout.preferredWidth: 80 }
                Label { text: ageSeconds + " s"; color: "#98a0a6" }
            }
            footer: EmptyState {
                visible: Spots.count === 0
                width: ListView.view.width
                title: "No observed DX spots"
                detail: "Connect the single DX Cluster controller explicitly. No fixture or fabricated spot is shown."
            }
        }
    }
}

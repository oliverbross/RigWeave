import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

ScrollView { contentWidth: availableWidth
    ColumnLayout { width: parent.width; spacing: 14; anchors.margins: 18
        SafetyBanner { Layout.fillWidth: true; text: "Startup truth: radio disconnected, PTT/TUNE unavailable, rotator disarmed, and no audio route selected. Nothing connects automatically." }
        Flow { Layout.fillWidth: true; spacing: 12
            MetricTile { label: "Station"; value: "OM0RX"; truth: "Configured operator default" }
            MetricTile { label: "Local QSOs"; value: Desktop.intelligence().qsos ?? 0; truth: "Canonical desktop database" }
            MetricTile { label: "Radio"; value: Radio.state.startsWith("Connected") ? "ON LINE" : "OFF LINE"; truth: Radio.state }
            MetricTile { label: "Cluster"; value: Spots.count; truth: Cluster.state + " / shared repository" }
            MetricTile { label: "Wavelog"; value: Wavelog.pendingCount; truth: Wavelog.state + " / pending" }
            MetricTile { label: "Next satellite"; value: Parity.satellitePasses.count > 0 ? Parity.satellitePasses.item(0).title : "—"; truth: "Local SGP4 / no automatic action" }
        }
        Rectangle { Layout.fillWidth: true; implicitHeight: 250; color: "#22272b"; border.color: "#3a4147"; radius: 4
            ColumnLayout { anchors.fill: parent; anchors.margins: 12
                RowLayout { Layout.fillWidth: true
                    Label { text: "HOME / HAMCLOCK MODULES"; color: "#d38b22"; font.bold: true }
                    Item { Layout.fillWidth: true }
                    StatusChip { text: Parity.workspaceSummary("Home").status; kind: "healthy" }
                }
                GridView { Layout.fillWidth: true; Layout.fillHeight: true; cellWidth: 220; cellHeight: 52; model: Parity.homeModules; clip: true
                    delegate: CheckDelegate { required property var item; width: 210; height: 44; text: item.title; checked: item.enabled; palette.text: "#f2efe7"; onToggled: Parity.setHomeModuleVisible(item.key, checked) }
                }
            }
        }
        Rectangle { Layout.fillWidth: true; implicitHeight: 210; color: "#22272b"; border.color: "#3a4147"; radius: 4
            ColumnLayout { anchors.fill: parent; anchors.margins: 14
                Label { text: "CURRENT DX / WATCHLIST"; color: "#d38b22"; font.bold: true }
                ListView { Layout.fillWidth: true; Layout.fillHeight: true; model: Spots; clip: true
                    delegate: RowLayout { required property string callsign; required property double frequencyHz; required property string mode; required property int ageSeconds; width: ListView.view.width; height: 32
                        Label { text: callsign; color: "#f2efe7"; font.bold: true; Layout.preferredWidth: 110 }
                        Label { text: (frequencyHz / 1000).toFixed(1) + " kHz"; color: "#e3c765"; font.family: "monospace"; Layout.preferredWidth: 130 }
                        Label { text: mode; color: "#98a0a6"; Layout.preferredWidth: 80 }
                        Label { text: ageSeconds + " s"; color: "#98a0a6" }
                    }
                    footer: EmptyState { visible: Spots.count === 0; width: ListView.view.width; title: "No observed DX spots"; detail: "Connect the single DX Cluster controller explicitly. No fixture or fabricated spot is shown." }
                }
            }
        }
    }
}

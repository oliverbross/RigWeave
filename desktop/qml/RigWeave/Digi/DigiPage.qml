import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    workspaceKey: "Digi"

    CanvasPanel {
        panelKey: "safety"
        title: "Digi safety and state"
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: 132
        ColumnLayout { anchors.fill: parent
            SafetyBanner { Layout.fillWidth: true; text: "Digi restore is RX-only. Exact audio identity is required; external spots never start TX; every transmit-capable profile remains acceptance pending." }
            RowLayout { Layout.fillWidth: true
                StatusChip { text: Parity.workspaceSummary("Digi").status; kind: "hold" }
                Label { text: "Mode truth and sequencer source are active; physical audio, decode identity and TX acceptance remain pending."; color: "#98a0a6"; Layout.fillWidth: true; wrapMode: Text.WordWrap }
                Button { text: "STOP"; highlighted: true; onClicked: Desktop.globalStop() }
            }
        }
    }

    CanvasPanel {
        panelKey: "mode-workbench"
        title: "Digital mode workbench"
        defaultY: 144
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: parent ? parent.height - 144 : 500
        ColumnLayout { anchors.fill: parent
            TabBar { id: tabs; Layout.fillWidth: true
                TabButton { text: "Modes" }
                TabButton { text: "DX Chaser" }
                TabButton { text: "WSJT-X" }
                TabButton { text: "SSTV" }
            }
            StackLayout { Layout.fillWidth: true; Layout.fillHeight: true; currentIndex: tabs.currentIndex
                WorkspaceList { sourceModel: Parity.digiModes; actionText: "Prepare"; onActionRequested: item => Parity.prepareReceiveReview("Digi " + item.key, item) }
                ColumnLayout {
                    SafetyBanner { Layout.fillWidth: true; text: "Assist and Dry Run may score observations. Chase requires an operator start and a fresh local decode; this desktop build exposes no external-spot-only transmit path." }
                    WorkspaceList { Layout.fillWidth: true; Layout.fillHeight: true; sourceModel: Parity.neuralOpportunities; actionText: "Dry Run"; emptyTitle: "No eligible local decode"; emptyDetail: "Connect a reviewed receive source. No chase engagement is inferred from an external spot."; onActionRequested: item => Parity.prepareReceiveReview("DX Chaser dry run", item) }
                }
                ColumnLayout {
                    Label { text: "Bounded loopback companion"; color: "#f2efe7"; font.pixelSize: 20; font.bold: true }
                    CheckBox { text: "Enable loopback UDP companion for this session"; checked: false }
                    Label { text: "Heartbeat, status, decode and QSO-logged frames are accepted. Inbound halt/clear/replay remains bounded and cannot operate the radio directly."; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                    Item { Layout.fillHeight: true }
                }
                ColumnLayout {
                    Label { text: "Private SSTV gallery"; color: "#f2efe7"; font.pixelSize: 20; font.bold: true }
                    Button { text: "Choose prepared image…"; enabled: false; ToolTip.text: "Physical audio acceptance pending"; ToolTip.visible: hovered }
                    Label { text: "Receive, metadata, export and delete are source-wired. One-shot transmit stays unavailable until an accepted radio/audio profile is present."; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                    Item { Layout.fillHeight: true }
                }
            }
            Label { visible: Parity.activeReview.length > 0; text: Parity.activeReview; color: "#e3c765"; Layout.fillWidth: true }
        }
    }
}

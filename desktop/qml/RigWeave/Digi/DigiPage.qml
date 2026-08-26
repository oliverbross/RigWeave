import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    id: root
    workspaceKey: "Digi"
    property int cockpitMode: 0

    CanvasPanel {
        panelKey: "safety"
        title: "Digi route and safety truth"
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: 94
        panelMinimumHeight: 90
        RowLayout {
            anchors.fill: parent
            StatusChip { text: Parity.workspaceSummary("Digi").status; kind: "hold" }
            Label { text: "RX-only restore · exact audio identity required · external spots never create TX eligibility"; color: "#e3c765"; Layout.fillWidth: true; elide: Text.ElideRight }
            Button { text: "STOP"; palette.button: "#8f1d24"; palette.buttonText: "white"; onClicked: Desktop.globalStop() }
        }
    }

    CanvasPanel {
        id: routePanel
        panelKey: "route-mode-session"
        title: "Route · mode · session"
        defaultY: 106
        defaultWidth: 286
        defaultHeight: parent ? parent.height - 106 : 620
        panelMinimumWidth: 270
        ColumnLayout {
            anchors.fill: parent
            spacing: 9
            Label { text: "WORKSPACE"; color: "#e9a72b"; font.weight: Font.Bold }
            TabBar {
                Layout.fillWidth: true
                currentIndex: root.cockpitMode
                onCurrentIndexChanged: root.cockpitMode = currentIndex
                TabButton { text: "Digi" }
                TabButton { text: "DX Chaser" }
            }
            Label { text: "MODE FAMILY"; color: "#e9a72b"; font.weight: Font.Bold }
            Repeater {
                model: ["FT / JS8","CW","RTTY","PSK","SSTV"]
                Button { required property string modelData; Layout.fillWidth: true; text: modelData; enabled: false; checkable: true; checked: index === 0; Accessible.description: "Mode review only; no validated receive route" }
            }
            Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 1; color: "#3a4147" }
            Label { text: "SESSION TRUTH"; color: "#e9a72b"; font.weight: Font.Bold }
            GridLayout {
                Layout.fillWidth: true
                columns: 2
                Label { text: "RADIO"; color: "#929ba2" }
                Label { text: Radio.state.startsWith("Connected") ? "RX ONLY" : Radio.state; color: Radio.state.startsWith("Connected") ? "#42c77b" : "#f4c94e"; Layout.alignment: Qt.AlignRight }
                Label { text: "RX"; color: "#929ba2" }
                Label { text: "STOPPED"; color: "#f4f0e7"; Layout.alignment: Qt.AlignRight }
                Label { text: "AUDIO"; color: "#929ba2" }
                Label { text: "NO ROUTE"; color: "#f4c94e"; Layout.alignment: Qt.AlignRight }
                Label { text: "TX"; color: "#929ba2" }
                Label { text: "SAFE"; color: "#42c77b"; Layout.alignment: Qt.AlignRight }
            }
            Item { Layout.fillHeight: true }
            Button { Layout.fillWidth: true; text: "Setup reviewed route"; enabled: false; ToolTip.visible: hovered; ToolTip.text: "A desktop route owner is not yet exposed" }
            Label { Layout.fillWidth: true; text: "Unavailable controls stay restrained until the owning service exposes a validated route."; color: "#aeb5ba"; wrapMode: Text.WordWrap; font.pixelSize: 11 }
        }
    }

    CanvasPanel {
        id: evidencePanel
        panelKey: "decode-evidence"
        title: root.cockpitMode === 0 ? "Decode and waterfall evidence" : "Fresh local decode candidates"
        defaultX: 298
        defaultY: 106
        defaultWidth: parent ? Math.max(470, (parent.width - 322) * 0.60) : 540
        defaultHeight: parent ? parent.height - 218 : 500
        panelMinimumWidth: 440
        ColumnLayout {
            anchors.fill: parent
            spacing: 8
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 64
                color: "#101316"
                border.color: "#3a4147"
                Label {
                    anchors.centerIn: parent
                    text: root.cockpitMode === 0 ? "NO OBSERVED DECODE STREAM · deterministic lists are labelled gallery evidence" : "Only fresh current-slot local decodes can become call-eligible"
                    color: "#aeb5ba"
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                    width: parent.width - 24
                }
            }
            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: root.cockpitMode
                WorkspaceList {
                    sourceModel: Parity.digiModes
                    actionText: "Review route"
                    actionsEnabled: Parity.workspaceSummary("Digi").status === "SOURCE_COMPLETE"
                    stateOverride: "REVIEW ONLY"
                    emptyTitle: "No digital modes"
                    emptyDetail: "No production decode is fabricated."
                    onActionRequested: item => Parity.prepareReceiveReview("Digi " + item.key, item)
                }
                WorkspaceList {
                    sourceModel: Parity.neuralOpportunities
                    actionText: "Dry run"
                    actionsEnabled: Parity.workspaceSummary("Digi").status === "SOURCE_COMPLETE"
                    stateOverride: "EVIDENCE ONLY"
                    emptyTitle: "No eligible local decode"
                    emptyDetail: "An external spot cannot create eligibility."
                    onActionRequested: item => Parity.prepareReceiveReview("DX Chaser dry run", item)
                }
            }
        }
    }

    CanvasPanel {
        panelKey: "sequence-safety"
        title: "Sequence · target · safety"
        defaultX: evidencePanel.defaultX + evidencePanel.defaultWidth + 12
        defaultY: 106
        defaultWidth: parent ? parent.width - evidencePanel.defaultX - evidencePanel.defaultWidth - 12 : 330
        defaultHeight: parent ? parent.height - 106 : 620
        panelMinimumWidth: 286
        ColumnLayout {
            anchors.fill: parent
            spacing: 10
            Label { text: root.cockpitMode === 0 ? "RX WORKFLOW" : "ASSIST / DRY RUN"; color: "#e9a72b"; font.weight: Font.Bold }
            Label {
                Layout.fillWidth: true
                text: root.cockpitMode === 0 ? "Select a supported mode, acquire an exact route, then start an explicit receive session. This foundation has no fake waterfall or decoder output." : "Assist may rank evidence. Chase remains inactive until an operator starts it with a fresh local decode and accepted radio/audio path."
                color: "#f4f0e7"
                wrapMode: Text.WordWrap
            }
            GroupBox {
                title: "Target"
                Layout.fillWidth: true
                GridLayout {
                    anchors.fill: parent
                    columns: 2
                    Label { text: "Callsign" } Label { text: "—"; color: "#aeb5ba" }
                    Label { text: "Grid" } Label { text: "—"; color: "#aeb5ba" }
                    Label { text: "Freshness" } Label { text: "NO LOCAL DECODE"; color: "#f4c94e" }
                    Label { text: "TX authority" } Label { text: "NOT ARMED"; color: "#42c77b" }
                }
            }
            GroupBox {
                title: "One-shot sequence"
                Layout.fillWidth: true
                ColumnLayout {
                    anchors.fill: parent
                    Repeater { model: ["Prepare","Review","Arm once","Transmit","Return RX"]; Label { required property string modelData; text: modelData; color: index < 2 ? "#f4f0e7" : "#929ba2" } }
                }
            }
            Item { Layout.fillHeight: true }
            Button { Layout.fillWidth: true; text: "Start RX"; enabled: false; ToolTip.visible: hovered; ToolTip.text: "Exact audio route acceptance pending" }
            Button { Layout.fillWidth: true; text: "Clear review"; onClicked: Parity.globalStop() }
        }
    }

    CanvasPanel {
        panelKey: "macro-strip"
        title: "Macro and action strip"
        defaultX: 298
        defaultY: parent ? parent.height - 100 : 626
        defaultWidth: evidencePanel.defaultWidth
        defaultHeight: 100
        panelMinimumHeight: 94
        RowLayout {
            anchors.fill: parent
            Repeater { model: Parity.keyerMacros; Button { required property var item; text: item.title; enabled: false; ToolTip.visible: hovered; ToolTip.text: "TX acceptance required" } }
            Item { Layout.fillWidth: true }
            Label { text: Parity.activeReview.length > 0 ? Parity.activeReview : "No active review"; color: "#f4c94e"; elide: Text.ElideRight; Layout.maximumWidth: 260 }
        }
    }
}

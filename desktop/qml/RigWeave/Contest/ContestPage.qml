import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    workspaceKey: "Contest"

    CanvasPanel {
        panelKey: "safety"
        title: "Contest safety state"
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: 96
        SafetyBanner { anchors.fill: parent; text: "Contest restore is inactive. N1MM peers, network packets and F-keys cannot operate radio, Digi or Keyer without the explicit foreground workflow." }
    }

    CanvasPanel {
        panelKey: "session"
        title: "Contest session and keyer"
        defaultY: 108
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: 142
        ColumnLayout { anchors.fill: parent
            RowLayout { Layout.fillWidth: true
                ComboBox { model: Parity.contestDefinitions; textRole: "title"; Layout.preferredWidth: 280 }
                TextField { placeholderText: "Single-line QSO entry"; Layout.fillWidth: true }
                Button { text: "Stage QSO"; enabled: false; ToolTip.text: "Start an explicit contest session first"; ToolTip.visible: hovered }
                Button { text: "Global STOP"; onClicked: Desktop.globalStop() }
            }
            RowLayout { Layout.fillWidth: true
                Repeater { model: Parity.keyerMacros; Button { required property var item; text: item.title; enabled: false; ToolTip.text: "Foreground keyer is stopped; transmit acceptance pending"; ToolTip.visible: hovered } }
                Item { Layout.fillWidth: true }
                StatusChip { text: "N1MM UNARMED"; kind: "hold" }
            }
        }
    }

    CanvasPanel {
        panelKey: "staging-log"
        title: "Contest staging log"
        defaultY: 262
        defaultWidth: parent ? parent.width - 322 : 850
        defaultHeight: parent ? parent.height - 262 : 360
        WorkspaceList { anchors.fill: parent; sourceModel: Parity.contestLog; actionText: "Review"; emptyTitle: "Staging log is empty"; emptyDetail: "Start a session and stage QSOs before an explicit canonical merge."; onActionRequested: item => Parity.prepareContestMerge({id: item.key}) }
    }

    CanvasPanel {
        panelKey: "score"
        title: "Score and rate"
        defaultX: parent ? parent.width - 310 : 862
        defaultY: 262
        defaultWidth: 310
        defaultHeight: parent ? parent.height - 262 : 360
        ColumnLayout { anchors.fill: parent
            MetricTile { label: "QSOs"; value: Parity.contestLog.count; truth: "Temporary schema-2 staging log" }
            MetricTile { label: "Merge"; value: "REVIEW"; truth: "Idempotent ledger → canonical QSO owner" }
            Label { text: "SCP is downloaded at runtime only. Cabrillo and ADIF export remain session-scoped."; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
            Item { Layout.fillHeight: true }
        }
    }
}

import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item {
    ColumnLayout {
        anchors.fill: parent; anchors.margins: 18; spacing: 12
        SafetyBanner { Layout.fillWidth: true; text: "Contest restore is inactive. N1MM peers, network packets and F-keys cannot operate radio, Digi or Keyer without the explicit foreground workflow." }
        RowLayout { Layout.fillWidth: true
            ComboBox { model: Parity.contestDefinitions; textRole: "title"; Layout.preferredWidth: 280 }
            TextField { placeholderText: "Single-line QSO entry"; Layout.fillWidth: true }
            Button { text: "Stage QSO"; enabled: false; ToolTip.text: "Start an explicit contest session first"; ToolTip.visible: hovered }
            Button { text: "Global STOP"; onClicked: Desktop.globalStop() }
        }
        RowLayout { Layout.fillWidth: true
            Repeater { model: Parity.keyerMacros
                Button { required property var item; text: item.title; enabled: false; ToolTip.text: "Foreground keyer is stopped; transmit acceptance pending"; ToolTip.visible: hovered }
            }
            Item { Layout.fillWidth: true }
            StatusChip { text: "N1MM UNARMED"; kind: "hold" }
        }
        SplitView { Layout.fillWidth: true; Layout.fillHeight: true
            WorkspaceList { SplitView.fillWidth: true; sourceModel: Parity.contestLog; actionText: "Review"; emptyTitle: "Staging log is empty"; emptyDetail: "Start a session and stage QSOs before an explicit canonical merge."; onActionRequested: item => Parity.prepareContestMerge({id: item.key}) }
            Rectangle { SplitView.preferredWidth: 310; color: "#22272b"; border.color: "#3a4147"
                ColumnLayout { anchors.fill: parent; anchors.margins: 14
                    Label { text: "Score & rate"; color: "#d38b22"; font.bold: true }
                    MetricTile { label: "QSOs"; value: Parity.contestLog.count; truth: "Temporary schema-2 staging log" }
                    MetricTile { label: "Merge"; value: "REVIEW"; truth: "Idempotent ledger → canonical QSO owner" }
                    Label { text: "SCP is downloaded at runtime only. Cabrillo and ADIF export remain session-scoped."; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                    Item { Layout.fillHeight: true }
                }
            }
        }
    }
}

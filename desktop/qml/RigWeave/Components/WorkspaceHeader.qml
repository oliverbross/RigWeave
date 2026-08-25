import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: root
    property string title: "Workspace"
    property string subtitle: ""
    property bool compact: false
    implicitHeight: compact ? 62 : 72; color: "#22272b"
    border.color: "#343a40"
    RowLayout { anchors.fill: parent; anchors.leftMargin: 16; anchors.rightMargin: 12; anchors.topMargin: 8; anchors.bottomMargin: 8; spacing: compact ? 9 : 14
        ColumnLayout { Layout.fillWidth: true; spacing: 2
            Label { text: root.title; color: "#f2efe7"; font.pixelSize: root.compact ? 20 : 24; font.weight: Font.DemiBold }
            Label { text: root.subtitle; color: "#aeb5ba"; font.pixelSize: 12; elide: Text.ElideRight; Layout.fillWidth: true }
        }
        StatusChip { visible: !root.compact; text: "RADIO " + Radio.state; kind: Radio.state.startsWith("Connected") ? "healthy" : "neutral" }
        StatusChip { visible: !root.compact; text: "CLUSTER " + Cluster.state; kind: Cluster.state.startsWith("Connected") ? "healthy" : Cluster.state === "Error" ? "danger" : "neutral" }
        Button { text: "GLOBAL STOP"; palette.button: "#8c2525"; palette.buttonText: "white"; font.weight: Font.Bold; onClicked: Desktop.invokeCommand("radio.stop"); Accessible.name: "Global Stop"; Accessible.description: "Immediately cancels radio mutations and returns receive systems to their safe state" }
    }
}

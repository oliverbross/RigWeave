import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    property string title: "Workspace"
    property string subtitle: ""
    implicitHeight: 72; color: "#22272b"
    RowLayout { anchors.fill: parent; anchors.margins: 16; spacing: 16
        ColumnLayout { Layout.fillWidth: true; spacing: 2
            Label { text: title; color: "#f2efe7"; font.pixelSize: 24; font.weight: Font.DemiBold }
            Label { text: subtitle; color: "#98a0a6"; font.pixelSize: 13; elide: Text.ElideRight; Layout.fillWidth: true }
        }
        StatusChip { text: Radio.state; kind: Radio.state.startsWith("Connected") ? "healthy" : "neutral" }
        StatusChip { text: Cluster.state; kind: Cluster.state.startsWith("Connected") ? "healthy" : Cluster.state === "Error" ? "danger" : "neutral" }
        Button { text: "STOP"; palette.button: "#8c2525"; palette.buttonText: "white"; font.weight: Font.Bold; onClicked: Desktop.globalStop(); Accessible.description: "Global safety stop; distinct from Escape" }
    }
}

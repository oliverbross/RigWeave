import QtQuick
import QtQuick.Controls

Rectangle {
    id: root
    property string text: "Unknown"
    property string kind: "neutral"
    implicitHeight: 28; implicitWidth: label.implicitWidth + 24; radius: 4
    color: kind === "healthy" ? "#18452d" : kind === "danger" ? "#4f2020" : kind === "hold" ? "#493c18" : "#2c3338"
    border.color: kind === "healthy" ? "#4ec47b" : kind === "danger" ? "#e05252" : kind === "hold" ? "#e3c765" : "#68727a"
    Label { id: label; anchors.centerIn: parent; text: root.text; color: "#f2efe7"; font.weight: Font.DemiBold }
    Accessible.name: text + " status"
}

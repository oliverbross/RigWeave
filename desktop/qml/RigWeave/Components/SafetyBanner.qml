import QtQuick
import QtQuick.Controls

Rectangle {
    property string text: "Safety state"
    implicitHeight: label.implicitHeight + 20; color: "#352b18"; border.color: "#e3c765"; radius: 4
    Label { id: label; anchors.fill: parent; anchors.margins: 10; text: parent.text; color: "#f2efe7"; wrapMode: Text.WordWrap }
}

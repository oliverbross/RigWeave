import QtQuick
import QtQuick.Controls

Item {
    property string title: "Nothing observed"
    property string detail: "No source data is available."
    implicitHeight: 160
    Column { anchors.centerIn: parent; spacing: 8
        Label { anchors.horizontalCenter: parent.horizontalCenter; text: title; color: "#f2efe7"; font.pixelSize: 18 }
        Label { width: 520; text: detail; color: "#98a0a6"; wrapMode: Text.WordWrap; horizontalAlignment: Text.AlignHCenter }
    }
}

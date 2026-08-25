import QtQuick

Item {
    id: root
    property string name: "home"
    property color color: "#b7bec3"
    implicitWidth: 22
    implicitHeight: 22
    Accessible.ignored: true

    Image {
        anchors.fill: parent
        source: "qrc:/RigWeave/App/Icons/" + root.name + ".svg"
        sourceSize.width: Math.round(root.width * Screen.devicePixelRatio)
        sourceSize.height: Math.round(root.height * Screen.devicePixelRatio)
        fillMode: Image.PreserveAspectFit
        smooth: true
        opacity: root.color === "#b7bec3" ? 0.82 : 1.0
    }
}

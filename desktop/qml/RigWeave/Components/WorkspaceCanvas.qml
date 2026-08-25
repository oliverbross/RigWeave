import QtQuick

Item {
    id: root

    property string workspaceKey
    property int highestZ: 1
    signal resetRequested()

    clip: true
    Accessible.role: Accessible.Pane
    Accessible.name: workspaceKey + " freeform workspace canvas"

    function raisePanel(panel) {
        highestZ += 1
        panel.z = highestZ
    }

    Rectangle {
        anchors.fill: parent
        color: "#15181b"
        z: -100
    }

    Connections {
        target: Desktop
        function onWorkspaceLayoutReset(workspace) {
            if (workspace === root.workspaceKey)
                root.resetRequested()
        }
    }
}

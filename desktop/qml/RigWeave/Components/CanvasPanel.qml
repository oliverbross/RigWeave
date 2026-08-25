import QtQuick
import QtQuick.Controls

Item {
    id: root

    required property string panelKey
    required property string title
    property real defaultX: 0
    property real defaultY: 0
    property real defaultWidth: 420
    property real defaultHeight: 280
    property real panelMinimumWidth: 240
    property real panelMinimumHeight: 150
    property bool geometryReady: false
    property point dragOrigin
    property rect dragGeometry
    readonly property var canvas: parent
    readonly property string workspaceKey: canvas ? canvas.workspaceKey : ""
    default property alias contentData: contentHost.data

    objectName: "canvasPanel-" + panelKey
    x: defaultX
    y: defaultY
    width: defaultWidth
    height: defaultHeight
    z: 1
    Accessible.role: Accessible.Pane
    Accessible.name: title + " movable panel"
    Accessible.description: "Drag the title bar to move. Drag an edge or corner to resize."

    function bounded(value, lower, upper) {
        return Math.max(lower, Math.min(value, upper))
    }

    function applyGeometry(nextX, nextY, nextWidth, nextHeight) {
        if (!parent)
            return
        const availableWidth = Math.max(panelMinimumWidth, parent.width)
        const availableHeight = Math.max(panelMinimumHeight, parent.height)
        width = bounded(nextWidth, Math.min(panelMinimumWidth, availableWidth), availableWidth)
        height = bounded(nextHeight, Math.min(panelMinimumHeight, availableHeight), availableHeight)
        x = bounded(nextX, 0, Math.max(0, parent.width - width))
        y = bounded(nextY, 0, Math.max(0, parent.height - height))
    }

    function restoreDefaults() {
        geometryReady = false
        applyGeometry(defaultX, defaultY, defaultWidth, defaultHeight)
        geometryReady = true
    }

    function restoreSavedGeometry() {
        const fallback = {"x": defaultX, "y": defaultY,
                          "width": defaultWidth, "height": defaultHeight}
        const saved = Desktop.panelGeometry(workspaceKey, panelKey, fallback)
        geometryReady = false
        applyGeometry(saved.x, saved.y, saved.width, saved.height)
        geometryReady = true
    }

    function raisePanel() {
        if (canvas)
            canvas.raisePanel(root)
        forceActiveFocus()
    }

    function schedulePersist() {
        if (geometryReady)
            persistTimer.restart()
    }

    function persistGeometry() {
        if (!geometryReady || workspaceKey.length === 0)
            return
        Desktop.savePanelGeometry(workspaceKey, panelKey,
                                  {"x": Math.round(x), "y": Math.round(y),
                                   "width": Math.round(width), "height": Math.round(height)})
    }

    function beginPointer(handle, mouse) {
        const scenePoint = handle.mapToItem(root.parent, mouse.x, mouse.y)
        dragOrigin = Qt.point(scenePoint.x, scenePoint.y)
        dragGeometry = Qt.rect(x, y, width, height)
        raisePanel()
    }

    function resizeFrom(handle, mouse, edges) {
        const scenePoint = handle.mapToItem(root.parent, mouse.x, mouse.y)
        const dx = scenePoint.x - dragOrigin.x
        const dy = scenePoint.y - dragOrigin.y
        let nextX = dragGeometry.x
        let nextY = dragGeometry.y
        let nextWidth = dragGeometry.width
        let nextHeight = dragGeometry.height
        if (edges.left) {
            nextX += dx
            nextWidth -= dx
            if (nextWidth < panelMinimumWidth) {
                nextX = dragGeometry.x + dragGeometry.width - panelMinimumWidth
                nextWidth = panelMinimumWidth
            }
        }
        if (edges.right)
            nextWidth += dx
        if (edges.top) {
            nextY += dy
            nextHeight -= dy
            if (nextHeight < panelMinimumHeight) {
                nextY = dragGeometry.y + dragGeometry.height - panelMinimumHeight
                nextHeight = panelMinimumHeight
            }
        }
        if (edges.bottom)
            nextHeight += dy
        applyGeometry(nextX, nextY, nextWidth, nextHeight)
    }

    Component.onCompleted: restoreSavedGeometry()
    onXChanged: schedulePersist()
    onYChanged: schedulePersist()
    onWidthChanged: schedulePersist()
    onHeightChanged: schedulePersist()

    Connections {
        target: root.canvas
        function onResetRequested() { root.restoreDefaults() }
    }

    Connections {
        target: root.parent
        function onWidthChanged() {
            root.applyGeometry(root.x, root.y, root.width, root.height)
        }
        function onHeightChanged() {
            root.applyGeometry(root.x, root.y, root.width, root.height)
        }
    }

    Timer {
        id: persistTimer
        interval: 240
        repeat: false
        onTriggered: root.persistGeometry()
    }

    Rectangle {
        anchors.fill: parent
        radius: 5
        color: "#22272b"
        border.width: root.activeFocus ? 2 : 1
        border.color: root.activeFocus ? "#e3c765" : "#3a4147"
    }

    Rectangle {
        id: titleBar
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        height: 34
        radius: 5
        color: headerDrag.containsMouse || root.activeFocus ? "#34302a" : "#292f34"
        border.color: root.activeFocus ? "#d38b22" : "#3a4147"

        Label {
            anchors.left: parent.left
            anchors.leftMargin: 12
            anchors.right: resetButton.left
            anchors.rightMargin: 8
            anchors.verticalCenter: parent.verticalCenter
            text: root.title
            color: "#f2efe7"
            font.pixelSize: 12
            font.weight: Font.DemiBold
            elide: Text.ElideRight
        }

        MouseArea {
            id: headerDrag
            anchors.fill: parent
            cursorShape: Qt.SizeAllCursor
            hoverEnabled: true
            onPressed: root.beginPointer(headerDrag, mouse)
            onPositionChanged: {
                if (!pressed)
                    return
                const point = mapToItem(root.parent, mouse.x, mouse.y)
                root.applyGeometry(root.dragGeometry.x + point.x - root.dragOrigin.x,
                                   root.dragGeometry.y + point.y - root.dragOrigin.y,
                                   root.dragGeometry.width, root.dragGeometry.height)
            }
            onReleased: root.persistGeometry()
        }

        ToolButton {
            id: resetButton
            anchors.right: parent.right
            anchors.rightMargin: 4
            anchors.verticalCenter: parent.verticalCenter
            width: 28
            height: 28
            z: 2
            Accessible.name: "Restore " + root.title + " panel"
            ToolTip.visible: hovered
            ToolTip.text: Accessible.name
            onClicked: {
                root.raisePanel()
                root.restoreDefaults()
                root.persistGeometry()
            }
            contentItem: FlightlineIcon {
                name: "reset"
                color: parent.hovered ? "#f2efe7" : "#aeb5ba"
            }
        }
    }

    Item {
        id: contentHost
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: titleBar.bottom
        anchors.bottom: parent.bottom
        anchors.margins: 10
        clip: true
    }

    Repeater {
        model: [
            {"left": true,  "top": false, "right": false, "bottom": false, "cursor": Qt.SizeHorCursor},
            {"left": false, "top": false, "right": true,  "bottom": false, "cursor": Qt.SizeHorCursor},
            {"left": false, "top": true,  "right": false, "bottom": false, "cursor": Qt.SizeVerCursor},
            {"left": false, "top": false, "right": false, "bottom": true,  "cursor": Qt.SizeVerCursor},
            {"left": true,  "top": true,  "right": false, "bottom": false, "cursor": Qt.SizeFDiagCursor},
            {"left": false, "top": true,  "right": true,  "bottom": false, "cursor": Qt.SizeBDiagCursor},
            {"left": true,  "top": false, "right": false, "bottom": true,  "cursor": Qt.SizeBDiagCursor},
            {"left": false, "top": false, "right": true,  "bottom": true,  "cursor": Qt.SizeFDiagCursor}
        ]
        delegate: MouseArea {
            required property var modelData
            z: 30
            hoverEnabled: true
            cursorShape: modelData.cursor
            x: modelData.left ? -4 : modelData.right ? root.width - width + 4 : 10
            y: modelData.top ? -4 : modelData.bottom ? root.height - height + 4 : 10
            width: modelData.left || modelData.right ? 10 : root.width - 20
            height: modelData.top || modelData.bottom ? 10 : root.height - 20
            onPressed: root.beginPointer(this, mouse)
            onPositionChanged: {
                if (pressed)
                    root.resizeFrom(this, mouse, modelData)
            }
            onReleased: root.persistGeometry()
        }
    }
}

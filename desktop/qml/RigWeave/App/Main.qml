import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "Components"

ApplicationWindow {
    id: window
    FlightlinePalette { id: flightline }
    property bool shackMode: false
    property int galleryRadioBackend: 0
    readonly property bool isMac: Qt.platform.os === "osx"
    width: 1440
    height: 900
    minimumWidth: 1180
    minimumHeight: 720
    visible: true
    title: isMac ? "RigWeave" : "RigWeave Desktop — " + Desktop.currentDestination
    color: flightline.graphiteDeep

    Instantiator {
        model: Desktop.commands
        delegate: Shortcut {
            required property var modelData
            sequence: modelData.shortcut || ""
            enabled: !window.isMac && modelData.enabled === true && sequence.length > 0 && modelData.id !== "radio.stop"
            context: Qt.ApplicationShortcut
            onActivated: Desktop.invokeCommand(modelData.id)
        }
    }
    Shortcut {
        sequence: "Escape"
        context: Qt.ApplicationShortcut
        onActivated: {
            Desktop.invokeCommand("radio.stop")
            window.shackMode = false
            commandPalette.close()
            workspaceLoader.forceActiveFocus()
        }
    }

    Connections {
        target: Desktop
        function onQuitRequested() { window.close() }
        function onCommandInvoked(commandId) {
            if (commandId === "tools.palette") {
                commandPalette.open()
                commandSearch.forceActiveFocus()
            } else if (commandId === "view.fullScreen") {
                window.visibility = window.visibility === Window.FullScreen ? Window.Windowed : Window.FullScreen
            } else if (commandId === "view.shack") {
                window.shackMode = !window.shackMode
            } else if (commandId === "view.resetLayout") {
                window.shackMode = false
            } else if (commandId === "file.close") {
                window.close()
            } else if (commandId === "help.licences") {
                Desktop.currentDestination = "About"
            } else if (commandId === "help.shortcuts") {
                shortcutHelp.open()
            } else if (["file.fastEntry", "file.importAdif", "file.exportAdif", "file.exportConfig"].includes(commandId)) {
                const target = commandId === "file.exportConfig" ? "Settings" : "Logbook"
                Desktop.currentDestination = target
                Qt.callLater(function() {
                    if (workspaceLoader.item && workspaceLoader.item.handleCommand)
                        workspaceLoader.item.handleCommand(commandId)
                })
            }
        }
    }

    Item {
        anchors.fill: parent
        ColumnLayout {
            anchors.fill: parent
            spacing: 0
            WorkspaceHeader {
                visible: !window.shackMode
                Layout.fillWidth: true
                compact: window.width < 1360
                title: Desktop.currentDestination
                subtitle: "Local-first • restore disconnected and disarmed • UTC"
            }
            Loader {
                id: workspaceLoader
                objectName: "workspaceLoader"
                Layout.fillWidth: true
                Layout.fillHeight: true
                source: {
                    if (window.shackMode) return "Home/ShackDisplay.qml"
                    const map = {"Home":"Home/HomePage.qml","Radio":"Radio/RadioPage.qml","Digi":"Digi/DigiPage.qml","Panadapter":"Panadapter/PanadapterPage.qml","EQ":"EQ/EqPage.qml","Logbook":"Logbook/LogbookPage.qml","Intelligence":"Intelligence/IntelligencePage.qml","Sync":"Sync/SyncPage.qml","Contest":"Contest/ContestPage.qml","Band Maps":"qrc:/RigWeave/App/BandMaps/BandMapsPage.qml","Presets":"Presets/PresetsPage.qml","DX":"DX/DxPage.qml","Portable":"Portable/PortablePage.qml","Operations":"Operations/OperationsPage.qml","Groups.io":"Groups/GroupsPage.qml","Rotator":"Rotator/RotatorPage.qml","Settings":"Settings/SettingsPage.qml","Health":"Health/HealthPage.qml","About":"Settings/AboutPage.qml"}
                    return map[Desktop.currentDestination]
                }
            }
        }
    }

    Popup {
        id: commandPalette
        anchors.centerIn: Overlay.overlay
        width: Math.min(680, window.width - 80)
        height: Math.min(520, window.height - 100)
        modal: true
        focus: true
        closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
        Accessible.name: "Command palette"
        background: Rectangle { color: "#20252a"; border.color: flightline.amberBright; border.width: 1; radius: 6 }
        contentItem: ColumnLayout {
            spacing: 8
            TextField { id: commandSearch; Layout.fillWidth: true; placeholderText: "Find a workspace or command"; Accessible.name: "Command search" }
            ListView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                model: Desktop.commands.filter(function(command) {
                    return command.enabled && (commandSearch.text.length === 0 || (command.label + " " + command.category).toLowerCase().includes(commandSearch.text.toLowerCase()))
                })
                delegate: ItemDelegate {
                    required property var modelData
                    width: ListView.view.width
                    height: 44
                    onClicked: { Desktop.invokeCommand(modelData.id); commandPalette.close() }
                    contentItem: RowLayout {
                        FlightlineIcon { name: modelData.icon; color: flightline.amberBright; Layout.preferredWidth: 20; Layout.preferredHeight: 20 }
                        Label { text: modelData.label; color: "#f2efe7"; Layout.fillWidth: true }
                        Label { text: modelData.category; color: flightline.subdued; font.pixelSize: 11 }
                        Label { text: modelData.shortcut || ""; color: "#b7bec3"; font.pixelSize: 11 }
                    }
                }
            }
        }
    }

    Dialog {
        id: shortcutHelp
        title: "Keyboard shortcuts"
        modal: true
        standardButtons: Dialog.Close
        width: Math.min(620, window.width - 80)
        contentItem: ListView {
            implicitHeight: 420
            model: Desktop.commands.filter(function(command) { return command.shortcut && command.enabled })
            delegate: RowLayout {
                required property var modelData
                width: ListView.view.width
                height: 34
                Label { text: modelData.label; color: "#f2efe7"; Layout.fillWidth: true }
                Label { text: modelData.shortcut; color: "#e3c765" }
            }
        }
    }

    onClosing: function(close) { Desktop.shutdown(); close.accepted = true }
}

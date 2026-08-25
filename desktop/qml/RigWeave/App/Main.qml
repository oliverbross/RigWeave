import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "Components"

ApplicationWindow {
    id: window
    FlightlinePalette { id: flightline }
    property bool shackMode: false
    property bool sidebarShown: false
    property int galleryRadioBackend: 0
    readonly property bool isMac: Qt.platform.os === "osx"
    readonly property bool compactShell: width < flightline.navBreakpoint
    readonly property bool railExpanded: sidebarShown && Desktop.sidebarExpanded && !compactShell
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
            navigation.forceActiveFocus()
        }
    }

    Connections {
        target: Desktop
        function onQuitRequested() { window.close() }
        function onCommandInvoked(commandId) {
            if (commandId === "tools.palette") {
                commandPalette.open()
                commandSearch.forceActiveFocus()
            } else if (commandId === "view.sidebarToggle") {
                window.sidebarShown = false
            } else if (commandId === "view.fullScreen") {
                window.visibility = window.visibility === Window.FullScreen ? Window.Windowed : Window.FullScreen
            } else if (commandId === "view.shack") {
                window.shackMode = !window.shackMode
            } else if (commandId === "view.resetLayout") {
                window.sidebarShown = false
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

    SplitView {
        anchors.fill: parent
        orientation: Qt.Horizontal
        handle: Rectangle { implicitWidth: navigation.visible ? 1 : 0; color: "#343a40" }

        Rectangle {
            id: navigation
            visible: window.sidebarShown && !window.shackMode
            SplitView.preferredWidth: visible ? (window.railExpanded ? 238 : 64) : 0
            SplitView.minimumWidth: visible ? (window.railExpanded ? 220 : 64) : 0
            SplitView.maximumWidth: visible ? (window.railExpanded ? 264 : 64) : 0
            color: flightline.graphiteRail
            border.color: "#30363c"
            Accessible.name: "Workspace navigation"

            ColumnLayout {
                anchors.fill: parent
                spacing: 0
                RowLayout {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 62
                    Layout.leftMargin: window.railExpanded ? 16 : 0
                    Layout.rightMargin: window.railExpanded ? 8 : 0
                    spacing: 10
                    FlightlineIcon { name: "radio"; color: flightline.amberBright; Layout.preferredWidth: 24; Layout.preferredHeight: 24; Layout.alignment: Qt.AlignHCenter }
                    Label { visible: window.railExpanded; text: "RIGWEAVE"; color: "#f2efe7"; font.pixelSize: 17; font.weight: Font.DemiBold; font.letterSpacing: 1.1; Layout.fillWidth: true }
                    ToolButton {
                        visible: window.railExpanded
                        Accessible.name: Desktop.sidebarExpanded ? "Collapse sidebar" : "Expand sidebar"
                        onClicked: Desktop.invokeCommand("view.sidebarMode")
                        contentItem: FlightlineIcon { name: "sidebar"; color: parent.hovered ? "#f2efe7" : "#98a0a6" }
                        ToolTip.visible: hovered
                        ToolTip.text: Accessible.name
                    }
                }
                Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 1; color: "#343a40" }
                ListView {
                    id: navList
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    model: Desktop.commands.filter(function(command) { return command.rail })
                    section.property: "category"
                    section.criteria: ViewSection.FullString
                    section.delegate: Item {
                        required property string section
                        width: navList.width
                        height: window.railExpanded ? 30 : 8
                        Label {
                            visible: window.railExpanded
                            anchors.left: parent.left
                            anchors.leftMargin: 16
                            anchors.bottom: parent.bottom
                            anchors.bottomMargin: 5
                            text: parent.section
                            color: flightline.subdued
                            font.pixelSize: 10
                            font.weight: Font.DemiBold
                            font.letterSpacing: 0.9
                        }
                    }
                    delegate: ItemDelegate {
                        required property var modelData
                        width: navList.width
                        height: 42
                        leftPadding: window.railExpanded ? 16 : 0
                        rightPadding: window.railExpanded ? 10 : 0
                        Accessible.name: modelData.label + " workspace"
                        Accessible.description: Desktop.currentDestination === modelData.destination ? "Current workspace" : "Open workspace"
                        onClicked: Desktop.invokeCommand(modelData.id)
                        background: Rectangle {
                            color: parent.highlighted ? flightline.amberDark : parent.hovered ? flightline.graphiteHover : "transparent"
                            border.width: parent.activeFocus ? 2 : 0
                            border.color: "#e3c765"
                        }
                        highlighted: Desktop.currentDestination === modelData.destination
                        contentItem: RowLayout {
                            spacing: 11
                            FlightlineIcon { name: modelData.icon; color: parent.parent.highlighted ? "#e3c765" : parent.parent.hovered ? "#f2efe7" : "#aab1b6"; Layout.preferredWidth: 22; Layout.preferredHeight: 22; Layout.alignment: Qt.AlignHCenter }
                            Label { visible: window.railExpanded; text: modelData.label; color: parent.parent.highlighted ? "#f3d98b" : "#dde1e4"; font.pixelSize: 13; font.weight: parent.parent.highlighted ? Font.DemiBold : Font.Normal; Layout.fillWidth: true }
                            Rectangle { visible: window.railExpanded && modelData.destination === "Radio"; width: 7; height: 7; radius: 4; color: Radio.state.startsWith("Connected") ? "#4ec47b" : "#737d85"; Accessible.ignored: true }
                        }
                        ToolTip.visible: !window.railExpanded && hovered
                        ToolTip.text: modelData.label
                    }
                }
                ToolButton {
                    visible: !window.railExpanded
                    Layout.fillWidth: true
                    Layout.preferredHeight: 48
                    Accessible.name: "Expand sidebar"
                    onClicked: { window.sidebarShown = true; Desktop.sidebarExpanded = true }
                    contentItem: FlightlineIcon { name: "sidebar"; color: parent.hovered ? "#f2efe7" : "#98a0a6"; anchors.centerIn: parent }
                    ToolTip.visible: hovered
                    ToolTip.text: Accessible.name
                }
            }
        }

        Item {
            SplitView.fillWidth: true
            SplitView.minimumWidth: 900
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

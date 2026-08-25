import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "Components"

ApplicationWindow {
    id: window
    property bool shackMode: false
    property int galleryRadioBackend: 0
    width: 1440; height: 900; minimumWidth: 1280; minimumHeight: 720; visible: true
    title: "RigWeave Windows Desktop — " + Desktop.currentDestination
    color: "#171a1d"
    menuBar: MenuBar {
        Menu { title: "File"
            Action { text: "Export ADIF…"; onTriggered: Desktop.currentDestination = "Logbook" }
            MenuSeparator {}
            Action { text: "Exit"; shortcut: StandardKey.Quit; onTriggered: window.close() }
        }
        Menu { title: "Navigate"
            Action { text: "Home"; shortcut: "Ctrl+1"; onTriggered: Desktop.currentDestination = "Home" }
            Action { text: "Logbook"; shortcut: "Ctrl+L"; onTriggered: Desktop.currentDestination = "Logbook" }
            Action { text: "Radio"; shortcut: "Ctrl+R"; onTriggered: Desktop.currentDestination = "Radio" }
            Action { text: "Health"; shortcut: "Ctrl+H"; onTriggered: Desktop.currentDestination = "Health" }
        }
        Menu { title: "View"; Action { text: "Shack Display"; shortcut: "F11"; onTriggered: { Desktop.currentDestination = "Home"; window.shackMode = true } } }
        Menu { title: "Safety"; Action { text: "Global STOP"; shortcut: "Ctrl+Shift+S"; onTriggered: Desktop.globalStop() } }
        Menu { title: "Help"; Action { text: "About / Licences"; onTriggered: Desktop.currentDestination = "About" } }
    }
    Shortcut { sequence: "Escape"; onActivated: { Desktop.globalStop(); window.shackMode = false; commandPalette.close(); navigation.forceActiveFocus() } }
    Shortcut { sequence: "Ctrl+K"; onActivated: { commandPalette.open(); commandSearch.forceActiveFocus() } }
    onClosing: function(close) { Desktop.shutdown(); close.accepted = true }

    SplitView { anchors.fill: parent; orientation: Qt.Horizontal
        Rectangle { id: navigation; visible: !window.shackMode; SplitView.preferredWidth: visible ? 226 : 0; SplitView.minimumWidth: visible ? 176 : 0; SplitView.maximumWidth: visible ? 330 : 0; color: "#20252a"
            ColumnLayout { anchors.fill: parent; spacing: 0
                Label { text: "RIGWEAVE"; color: "#d38b22"; font.pixelSize: 20; font.bold: true; Layout.margins: 18; Layout.bottomMargin: 10 }
                ListView { Layout.fillWidth: true; Layout.fillHeight: true; clip: true
                    model: ["Home","Radio","Digi","Panadapter","EQ","Logbook","Intelligence","Sync","Contest","Band Maps","Presets","DX","Portable","Operations","Groups.io","Rotator","Settings","Health"]
                    delegate: ItemDelegate { required property string modelData; width: ListView.view.width; height: 42; text: modelData; highlighted: Desktop.currentDestination === modelData; palette.text: "#f2efe7"; palette.highlightedText: "#171a1d"; palette.highlight: "#d38b22"; onClicked: Desktop.currentDestination = modelData }
                }
                ItemDelegate { Layout.fillWidth: true; text: "About / Licences"; palette.text: "#98a0a6"; onClicked: Desktop.currentDestination = "About" }
            }
        }
        Item { SplitView.fillWidth: true
            ColumnLayout { anchors.fill: parent; spacing: 0
                WorkspaceHeader { visible: !window.shackMode; Layout.fillWidth: true; title: Desktop.currentDestination; subtitle: "Local-first desktop • restore is disconnected and disarmed • UTC" }
                Loader { objectName: "workspaceLoader"; Layout.fillWidth: true; Layout.fillHeight: true; source: {
                    if (window.shackMode) return "Home/ShackDisplay.qml"
                    const map = {"Home":"Home/HomePage.qml","Radio":"Radio/RadioPage.qml","Digi":"Digi/DigiPage.qml","Panadapter":"Panadapter/PanadapterPage.qml","EQ":"EQ/EqPage.qml","Logbook":"Logbook/LogbookPage.qml","Intelligence":"Intelligence/IntelligencePage.qml","Sync":"Sync/SyncPage.qml","Contest":"Contest/ContestPage.qml","Band Maps":"qrc:/RigWeave/App/BandMaps/BandMapsPage.qml","Presets":"Presets/PresetsPage.qml","DX":"DX/DxPage.qml","Portable":"Portable/PortablePage.qml","Operations":"Operations/OperationsPage.qml","Groups.io":"Groups/GroupsPage.qml","Rotator":"Rotator/RotatorPage.qml","Settings":"Settings/SettingsPage.qml","Health":"Health/HealthPage.qml","About":"Settings/AboutPage.qml"};
                    return map[Desktop.currentDestination]
                } }
            }
        }
    }
    Popup { id: commandPalette; anchors.centerIn: parent; width: 620; height: 430; modal: true; focus: true; closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
        background: Rectangle { color: "#20252a"; border.color: "#d38b22"; radius: 6 }
        contentItem: ColumnLayout {
            TextField { id: commandSearch; Layout.fillWidth: true; placeholderText: "Go to a workspace…" }
            ListView { Layout.fillWidth: true; Layout.fillHeight: true; model: ["Home","Radio","Digi","Panadapter","EQ","Logbook","Intelligence","Sync","Contest","Band Maps","Presets","DX","Portable","Operations","Groups.io","Rotator","Settings","Health","About"]
                delegate: ItemDelegate { required property string modelData; width: ListView.view.width; visible: commandSearch.text.length === 0 || modelData.toLowerCase().includes(commandSearch.text.toLowerCase()); text: modelData; palette.text: "#f2efe7"; onClicked: { Desktop.currentDestination = modelData; commandPalette.close() } }
            }
        }
    }
}

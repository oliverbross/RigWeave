import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "Components"

ApplicationWindow {
    id: window
    width: 1440; height: 900; minimumWidth: 1280; minimumHeight: 720; visible: true
    title: "RigWeave Windows Desktop Alpha — " + Desktop.currentDestination
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
        Menu { title: "Safety"; Action { text: "Global STOP"; shortcut: "Ctrl+Shift+S"; onTriggered: Desktop.globalStop() } }
        Menu { title: "Help"; Action { text: "About / Licences"; onTriggered: Desktop.currentDestination = "About" } }
    }
    Shortcut { sequence: "Escape"; onActivated: navigation.forceActiveFocus() }
    onClosing: function(close) { Desktop.shutdown(); close.accepted = true }

    SplitView { anchors.fill: parent; orientation: Qt.Horizontal
        Rectangle { id: navigation; SplitView.preferredWidth: 226; SplitView.minimumWidth: 176; SplitView.maximumWidth: 330; color: "#20252a"
            ColumnLayout { anchors.fill: parent; spacing: 0
                Label { text: "RIGWEAVE"; color: "#d38b22"; font.pixelSize: 20; font.bold: true; Layout.margins: 18; Layout.bottomMargin: 10 }
                ListView { Layout.fillWidth: true; Layout.fillHeight: true; clip: true
                    model: ["Home","Radio","Panadapter","Logbook","Intelligence","Sync","DX","Band Maps","Rotator","Settings","Health","Digi","Contest","Portable","Operations","Groups.io","Satellite/QO-100"]
                    delegate: ItemDelegate { required property string modelData; width: ListView.view.width; height: 42; text: modelData; highlighted: Desktop.currentDestination === modelData; palette.text: "#f2efe7"; palette.highlightedText: "#171a1d"; palette.highlight: "#d38b22"; onClicked: Desktop.currentDestination = modelData }
                }
                ItemDelegate { Layout.fillWidth: true; text: "About / Licences"; palette.text: "#98a0a6"; onClicked: Desktop.currentDestination = "About" }
            }
        }
        Item { SplitView.fillWidth: true
            ColumnLayout { anchors.fill: parent; spacing: 0
                WorkspaceHeader { Layout.fillWidth: true; title: Desktop.currentDestination; subtitle: "Local-first desktop • restore is disconnected and disarmed • UTC" }
                Loader { Layout.fillWidth: true; Layout.fillHeight: true; source: {
                    const map = {"Home":"../Home/HomePage.qml","Radio":"../Radio/RadioPage.qml","Panadapter":"../Panadapter/PanadapterPage.qml","Logbook":"../Logbook/LogbookPage.qml","Intelligence":"../Intelligence/IntelligencePage.qml","Sync":"../Sync/SyncPage.qml","DX":"../DX/DxPage.qml","Band Maps":"../BandMaps/BandMapsPage.qml","Rotator":"../Rotator/RotatorPage.qml","Settings":"../Settings/SettingsPage.qml","Health":"../Health/HealthPage.qml","About":"../Settings/AboutPage.qml"};
                    return map[Desktop.currentDestination] || "../Foundations/FoundationPage.qml"
                } }
            }
        }
    }
}

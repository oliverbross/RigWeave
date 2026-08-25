import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import "../Components"

Item {
    id: root
    property string currentCategory: "station"
    readonly property var categories: [
        {id:"station", label:"Station", icon:"home"},
        {id:"radio", label:"Radio", icon:"radio"},
        {id:"audio", label:"Audio / Panadapter", icon:"panadapter"},
        {id:"digi", label:"Digi", icon:"digi"},
        {id:"keyer", label:"Keyer", icon:"keyboard"},
        {id:"cluster", label:"Cluster", icon:"dx"},
        {id:"alerts", label:"Alerts", icon:"health"},
        {id:"contest", label:"Contest", icon:"contest"},
        {id:"bandmaps", label:"Band Maps", icon:"bandmaps"},
        {id:"wavelog", label:"Wavelog", icon:"sync"},
        {id:"groups", label:"Groups.io", icon:"groups"},
        {id:"providers", label:"Providers / Portable", icon:"portable"},
        {id:"operations", label:"Operations / Satellite", icon:"operations"},
        {id:"rotator", label:"Rotator", icon:"rotator"},
        {id:"appearance", label:"Appearance / Accessibility", icon:"settings"},
        {id:"health", label:"Health / About", icon:"about"}
    ]
    function handleCommand(commandId) {
        if (commandId === "file.exportConfig") exportBundle.open()
    }
    function categoryLabel() {
        const category = categories.find(function(item) { return item.id === currentCategory })
        return category ? category.label : "Settings"
    }
    function categoryDestination() {
        const map = {digi:"Digi", keyer:"Radio", cluster:"DX", alerts:"Health", contest:"Contest", bandmaps:"Band Maps", wavelog:"Sync", groups:"Groups.io", operations:"Operations", rotator:"Rotator"}
        return map[currentCategory] || "Settings"
    }

    FileDialog { id: importBundle; title: "Preview configuration bundle"; nameFilters: ["RigWeave JSON (*.json)"] }
    FileDialog { id: exportBundle; title: "Export safe configuration"; fileMode: FileDialog.SaveFile; nameFilters: ["RigWeave JSON (*.json)"]; onAccepted: DesktopConfig.exportBundle(selectedFile.toString().replace("file://", "")) }

    SplitView {
        anchors.fill: parent
        anchors.margins: 14
        orientation: Qt.Horizontal
        handle: Rectangle { implicitWidth: 1; color: "#3a4147" }

        Rectangle {
            SplitView.preferredWidth: 250
            SplitView.minimumWidth: 210
            SplitView.maximumWidth: 310
            color: "#1c2024"
            border.color: "#3a4147"
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 10
                spacing: 8
                TextField {
                    id: categorySearch
                    Layout.fillWidth: true
                    placeholderText: "Find a setting category"
                    Accessible.name: "Search settings categories"
                }
                ListView {
                    id: categoryList
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    model: root.categories.filter(function(category) { return category.label.toLowerCase().includes(categorySearch.text.toLowerCase()) })
                    delegate: ItemDelegate {
                        required property var modelData
                        width: categoryList.width
                        height: 40
                        highlighted: root.currentCategory === modelData.id
                        Accessible.name: modelData.label + " settings"
                        onClicked: root.currentCategory = modelData.id
                        background: Rectangle {
                            color: parent.highlighted ? "#4b351c" : parent.hovered ? "#292f34" : "transparent"
                            border.width: parent.activeFocus ? 2 : 0
                            border.color: "#e3c765"
                        }
                        contentItem: RowLayout {
                            spacing: 10
                            FlightlineIcon { name: modelData.icon; Layout.preferredWidth: 20; Layout.preferredHeight: 20 }
                            Label { text: modelData.label; color: parent.parent.highlighted ? "#f3d98b" : "#f2efe7"; Layout.fillWidth: true; elide: Text.ElideRight }
                        }
                    }
                }
            }
        }

        ScrollView {
            SplitView.fillWidth: true
            SplitView.minimumWidth: 620
            contentWidth: availableWidth
            ColumnLayout {
                width: parent.width
                spacing: 12
                Label { text: root.categoryLabel(); color: "#f2efe7"; font.pixelSize: 22; font.weight: Font.DemiBold }
                Label { Layout.fillWidth: true; text: "Changes autosave when a real owner exposes a safe preference. Connection, transmit, automation and movement state never restore armed."; color: "#aeb5ba"; wrapMode: Text.WordWrap }
                SafetyBanner { Layout.fillWidth: true; text: "Configuration bundles exclude credentials, QSO data, active radio state, PTT/TUNE, rotator motion/arm, pending commands, live spots and provider bodies. Import restores disconnected and disarmed." }

                GroupBox {
                    visible: root.currentCategory === "station"
                    title: "Station and configuration recovery"
                    Layout.fillWidth: true
                    GridLayout {
                        anchors.fill: parent; columns: 2
                        Label { text: "Last destination" } Label { text: DesktopConfig.lastDestination; color: "#f2efe7" }
                        Label { text: "Restore contract" } Label { text: "Disconnected · TX off · automation disarmed"; color: "#4ec47b" }
                        Label { text: "Configuration" }
                        RowLayout {
                            Button { text: "Choose import for preview"; onClicked: importBundle.open() }
                            Button { text: "Export safe bundle"; onClicked: exportBundle.open() }
                        }
                    }
                }
                GroupBox {
                    visible: root.currentCategory === "radio"
                    title: "Radio profiles and safety"
                    Layout.fillWidth: true
                    GridLayout {
                        anchors.fill: parent; columns: 2
                        Label { text: "State" } StatusChip { text: Radio.state; kind: Radio.state.startsWith("Connected") ? "healthy" : "neutral" }
                        Label { text: "TCI profiles" } Label { text: Radio.tciProfiles.length + " saved · explicit connect · PTT/TUNE locked"; color: "#f2efe7" }
                        Label { text: "Receiver authority" } Label { text: "Control " + (Radio.activeReceiverId || "—") + " · listening " + (Radio.listeningReceiverId || "—") + " · TX " + (Radio.transmitReceiverId || "—"); color: "#aeb5ba" }
                        Label { text: "Credentials" } Label { text: "Aliases only; secret values remain in the platform credential vault"; color: "#aeb5ba" }
                    }
                }
                GroupBox {
                    visible: root.currentCategory === "audio"
                    title: "Audio and Panadapter"
                    Layout.fillWidth: true
                    GridLayout {
                        anchors.fill: parent; columns: 2
                        Label { text: "Spectrum" } Label { text: Panadapter.fftSize + " FFT · " + Panadapter.waterfallRows + " rows · " + Panadapter.colourMap; color: "#f2efe7" }
                        Label { text: "Contrast" } Label { text: Panadapter.fitAutoContrast ? "FIT auto contrast" : "Manual floor / top"; color: "#aeb5ba" }
                        Label { text: "Audio route" } Label { text: "Stable platform identity required; microphone fallback for I/Q prohibited"; color: "#e3c765"; wrapMode: Text.WordWrap }
                    }
                }
                GroupBox {
                    visible: ["digi","keyer","cluster","alerts","contest","bandmaps","wavelog","groups","operations","rotator"].includes(root.currentCategory)
                    title: root.categoryLabel() + " lifecycle"
                    Layout.fillWidth: true
                    ColumnLayout {
                        anchors.fill: parent
                        Label { Layout.fillWidth: true; text: Parity.workspaceSummary(root.currentCategory === "bandmaps" ? "Band Maps" : root.currentCategory === "groups" ? "Groups.io" : root.currentCategory === "operations" ? "Operations" : root.currentCategory.charAt(0).toUpperCase() + root.currentCategory.slice(1)).detail || "Settings are exposed only when the owning service supplies a safe, validated preference."; color: "#aeb5ba"; wrapMode: Text.WordWrap }
                        Label { Layout.fillWidth: true; text: root.currentCategory === "rotator" ? "Rotator remains disconnected and disarmed; no target, tracking or movement state restores." : root.currentCategory === "keyer" ? "Keyer restore is stopped; foreground shortcut and transmit acceptance are required." : "No unavailable capability is guessed or represented by fabricated state."; color: "#e3c765"; wrapMode: Text.WordWrap }
                        Button { text: "Open " + root.categoryLabel() + " workspace"; onClicked: Desktop.currentDestination = root.categoryDestination() }
                    }
                }
                GroupBox {
                    visible: root.currentCategory === "providers"
                    title: "Provider lifecycle"
                    Layout.fillWidth: true
                    ColumnLayout {
                        anchors.fill: parent
                        Label { Layout.fillWidth: true; text: "Disabled by default, one in-flight request per key, explicit enable/refresh, and written CURRENT / STALE / OFFLINE_CACHE / EMPTY / ERROR / UNAVAILABLE state."; color: "#aeb5ba"; wrapMode: Text.WordWrap }
                        ListView {
                            Layout.fillWidth: true
                            implicitHeight: 340
                            model: Parity.providers
                            clip: true
                            delegate: RowLayout {
                                required property var item
                                width: ListView.view.width
                                height: 38
                                CheckBox { checked: item.enabled; text: item.title; Layout.preferredWidth: 280; onToggled: Parity.setProviderEnabled(item.key, checked) }
                                StatusChip { text: item.state; kind: item.state === "CURRENT" ? "healthy" : item.state === "ERROR" ? "danger" : "neutral" }
                                Label { text: item.detail; color: "#aeb5ba"; elide: Text.ElideRight; Layout.fillWidth: true }
                                Button { text: "Refresh"; enabled: item.enabled; onClicked: Parity.refreshProvider(item.key) }
                            }
                        }
                    }
                }
                GroupBox {
                    visible: root.currentCategory === "appearance"
                    title: "Appearance and accessibility"
                    Layout.fillWidth: true
                    GridLayout {
                        anchors.fill: parent; columns: 2
                        Label { text: "Sidebar" } CheckBox { text: "Expanded when space permits"; checked: Desktop.sidebarExpanded; onToggled: Desktop.sidebarExpanded = checked }
                        Label { text: "Scale evidence" } Label { text: "1366×768 · 1440×900 · 1512×982 · 1920×1080 · 2560×1440 · 150%"; color: "#aeb5ba"; wrapMode: Text.WordWrap }
                        Label { text: "Status semantics" } Label { text: "Written state plus colour; keyboard focus and collapsed-rail tooltips"; color: "#4ec47b"; wrapMode: Text.WordWrap }
                    }
                }
                GroupBox {
                    visible: root.currentCategory === "health"
                    title: "Health, support and acknowledgements"
                    Layout.fillWidth: true
                    RowLayout {
                        anchors.fill: parent
                        Button { text: "System Health"; onClicked: Desktop.currentDestination = "Health" }
                        Button { text: "About / Licences"; onClicked: Desktop.currentDestination = "About" }
                        Item { Layout.fillWidth: true }
                        Label { Layout.fillWidth: true; text: "Support bundle UI remains disabled until chooser/result lifecycle is complete"; color: "#e3c765"; wrapMode: Text.WordWrap }
                    }
                }
                Item { Layout.fillWidth: true; Layout.preferredHeight: 20 }
            }
        }
    }
}

import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    id: root
    workspaceKey: "Radio"
    property int selectedModel: -1

    CanvasPanel {
        panelKey: "backend"
        title: "Radio backend"
        defaultWidth: 360
        defaultHeight: parent ? parent.height : 620
        ColumnLayout {
            anchors.fill: parent
            ComboBox { id: backend; objectName: "radioBackend"; Layout.fillWidth: true; currentIndex: ApplicationWindow.window ? ApplicationWindow.window.galleryRadioBackend : 0; model: ["Native Elecraft KX3","Native Elecraft KX2","Native FlexRadio","Native QMX","Native QMX+","Native RGO ONE V6","Conservative RGO legacy","Embedded Hamlib 4.7.2","Hamlib network","TCI receive-only SDR"] }
            Label { Layout.fillWidth: true; text: backend.currentIndex < 7 ? "Native adapter source present · physical readback acceptance pending" : (backend.currentIndex === 9 ? "TCI WebSocket · bounded multi-receiver IQ · transmit locked" : "Capability-driven Hamlib catalogue"); color: "#e3c765"; wrapMode: Text.WordWrap }
            Label { text: "HAMLIB 4.7.2 MODEL REGISTRY"; color: "#d38b22"; font.bold: true; visible: backend.currentIndex >= 7 && backend.currentIndex < 9 }
            TextField { Layout.fillWidth: true; visible: backend.currentIndex >= 7 && backend.currentIndex < 9; placeholderText: "Manufacturer, model, backend"; onTextChanged: RadioModels.setSearch(text) }
            ListView { Layout.fillWidth: true; Layout.fillHeight: true; visible: backend.currentIndex >= 7 && backend.currentIndex < 9; model: RadioModels; clip: true
                delegate: ItemDelegate { required property int modelId; required property string manufacturer; required property string model; required property string backend; required property string transport; width: ListView.view.width; text: manufacturer + "  " + model + "\n" + backend + " • " + transport; highlighted: root.selectedModel === modelId; onClicked: root.selectedModel = modelId }
            }
            Label { text: "TCI PROFILES"; color: "#d38b22"; font.bold: true; visible: backend.currentIndex === 9 }
            TextField { id: tciId; Layout.fillWidth: true; visible: backend.currentIndex === 9; placeholderText: "Stable profile ID" }
            TextField { id: tciName; Layout.fillWidth: true; visible: backend.currentIndex === 9; placeholderText: "Display name" }
            TextField { id: tciEndpoint; Layout.fillWidth: true; visible: backend.currentIndex === 9; placeholderText: "ws://127.0.0.1:40001" }
            Button { visible: backend.currentIndex === 9; text: "Save profile"; enabled: tciId.text.length > 0 && tciName.text.length > 0 && tciEndpoint.text.length > 0; onClicked: Radio.saveTciProfile({id:tciId.text, displayName:tciName.text, endpoint:tciEndpoint.text, preferredIqSampleRate:96000, preferredReceiver:0, autoConnect:false, rxAudioOutputRoute:""}) }
            ListView { Layout.fillWidth: true; Layout.fillHeight: true; visible: backend.currentIndex === 9; model: Radio.tciProfiles; clip: true
                delegate: ItemDelegate { width: ListView.view.width; text: modelData.displayName + "\n" + modelData.endpoint; onClicked: Radio.connectTciProfile(modelData.id) }
            }
        }
    }

    CanvasPanel {
        panelKey: "connection"
        title: "Connection and safety"
        defaultX: 372
        defaultWidth: parent ? parent.width - 372 : 820
        defaultHeight: 132
        ColumnLayout {
            anchors.fill: parent
            SafetyBanner { Layout.fillWidth: true; text: "Explicit connect only. Native profiles require proven Windows transport identity and readback. PTT/TUNE remain acceptance pending; no physical transmit test is authorized." }
            RowLayout { Layout.fillWidth: true
                TextField { id: route; Layout.fillWidth: true; placeholderText: "COM port or Hamlib network route" }
                SpinBox { id: baud; from: 1200; to: 921600; value: 38400; editable: true }
                Button { text: "Connect"; enabled: backend.currentIndex >= 7 && backend.currentIndex < 9 && root.selectedModel >= 0 && route.text.length > 0; onClicked: Radio.connectRadio(root.selectedModel, route.text, baud.value) }
                Button { text: "Disconnect"; onClicked: Radio.disconnectRadio() }
            }
        }
    }

    CanvasPanel {
        panelKey: "receivers"
        title: "Receivers · explicit control and listening"
        defaultX: 372
        defaultY: 144
        defaultWidth: parent ? parent.width - 372 : 820
        defaultHeight: 170
        visible: Radio.receiverCount > 0
        ListView { anchors.fill: parent; model: Radio.receivers; clip: true
            delegate: RowLayout { required property string receiverId; required property string displayLabel; required property bool activeControl; required property bool activeListening; required property real effectiveReceiveHz; required property string mode; width: ListView.view.width; height: 38
                Label { Layout.fillWidth: true; text: displayLabel + "  " + (effectiveReceiveHz ? (effectiveReceiveHz / 1000).toFixed(3) + " kHz" : "—") + "  " + (mode || "—") }
                Button { text: activeControl ? "CONTROL" : "Control"; onClicked: Radio.selectActiveReceiver(receiverId) }
                Button { text: activeListening ? "LISTENING" : "Listen"; onClicked: Radio.selectListeningReceiver(receiverId) }
            }
        }
    }

    CanvasPanel {
        panelKey: "state"
        title: "Observed radio state"
        defaultX: 372
        defaultY: Radio.receiverCount > 0 ? 326 : 144
        defaultWidth: parent ? parent.width - 372 : 820
        defaultHeight: 150
        Flow { anchors.fill: parent; spacing: 12
            MetricTile { label: "State"; value: Radio.state.startsWith("Connected") ? "CONNECTED" : "DISCONNECTED"; truth: Radio.state }
            MetricTile { label: "Frequency"; value: Radio.frequencyHz ? (Radio.frequencyHz / 1000).toFixed(3) : "—"; truth: "kHz observed from Hamlib" }
            MetricTile { label: "Mode"; value: Radio.mode || "—"; truth: "Capability-gated snapshot" }
            MetricTile { label: "Transmit"; value: "DISABLED"; truth: "PTT and TUNE unavailable" }
        }
    }

    CanvasPanel {
        panelKey: "receive-review"
        title: "Explicit receive-review action"
        defaultX: 372
        defaultY: 488
        defaultWidth: parent ? (parent.width - 384) * 0.56 : 460
        defaultHeight: 126
        RowLayout { anchors.fill: parent
            TextField { id: frequency; Layout.fillWidth: true; placeholderText: "Frequency Hz"; validator: DoubleValidator { bottom: 100000; top: 10500000000 } }
            ComboBox { id: mode; model: ["CW","USB","LSB","AM","FM","DIGU","DIGL"] }
            Button { text: "Apply RX"; enabled: Radio.state.startsWith("Connected"); onClicked: { Radio.requestFrequency(Number(frequency.text)); Radio.requestMode(mode.currentText) } }
        }
    }

    CanvasPanel {
        panelKey: "keyer"
        title: "CW / voice keyer"
        defaultX: parent ? 384 + (parent.width - 384) * 0.56 : 844
        defaultY: 488
        defaultWidth: parent ? (parent.width - 384) * 0.44 : 360
        defaultHeight: 126
        RowLayout { anchors.fill: parent
            Repeater { model: Parity.keyerMacros; Button { required property var item; text: item.title; enabled: false; ToolTip.text: "Keyer stopped; foreground shortcut and TX acceptance required"; ToolTip.visible: hovered } }
            Item { Layout.fillWidth: true }
            Button { text: "Stop"; onClicked: Desktop.globalStop() }
        }
    }

    CanvasPanel {
        panelKey: "capability-gate"
        title: "Capability-gated controls"
        defaultX: 372
        defaultY: 626
        defaultWidth: parent ? parent.width - 372 : 820
        defaultHeight: 180
        EmptyState { anchors.fill: parent; title: "Capability controls are hidden until connected"; detail: "VFO A/B, filter, split, RIT/XIT, meters, AF/RF gain, power, preamp/attenuator, ATU, macros, EQ and presets appear only after the selected backend proves capability/readback." }
    }
}

import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item { id: root
    property int selectedModel: -1
    RowLayout { anchors.fill: parent; anchors.margins: 18; spacing: 16
        Rectangle { Layout.preferredWidth: 390; Layout.fillHeight: true; color: "#22272b"; border.color: "#3a4147"
            ColumnLayout { anchors.fill: parent; anchors.margins: 12
                Label { text: "HAMLIB 4.7.2 MODEL REGISTRY"; color: "#d38b22"; font.bold: true }
                TextField { Layout.fillWidth: true; placeholderText: "Manufacturer, model, backend"; onTextChanged: RadioModels.setSearch(text) }
                ListView { Layout.fillWidth: true; Layout.fillHeight: true; model: RadioModels; clip: true
                    delegate: ItemDelegate { required property int modelId; required property string manufacturer; required property string model; required property string backend; required property string transport; width: ListView.view.width; text: manufacturer + "  " + model + "\n" + backend + " • " + transport; highlighted: root.selectedModel === modelId; onClicked: root.selectedModel = modelId }
                }
            }
        }
        ColumnLayout { Layout.fillWidth: true; Layout.fillHeight: true; spacing: 12
            SafetyBanner { Layout.fillWidth: true; text: "Explicit connect only. PTT and TUNE remain disabled in Windows Alpha. No physical transmit test is authorized." }
            RowLayout { Layout.fillWidth: true
                TextField { id: route; Layout.fillWidth: true; placeholderText: "COM port or Hamlib network route" }
                SpinBox { id: baud; from: 1200; to: 921600; value: 38400; editable: true }
                Button { text: "Connect"; enabled: root.selectedModel >= 0 && route.text.length > 0; onClicked: Radio.connectRadio(root.selectedModel, route.text, baud.value) }
                Button { text: "Disconnect"; onClicked: Radio.disconnectRadio() }
            }
            Flow { Layout.fillWidth: true; spacing: 12
                MetricTile { label: "State"; value: Radio.state.startsWith("Connected") ? "CONNECTED" : "DISCONNECTED"; truth: Radio.state }
                MetricTile { label: "Frequency"; value: Radio.frequencyHz ? (Radio.frequencyHz / 1000).toFixed(3) : "—"; truth: "kHz observed from Hamlib" }
                MetricTile { label: "Mode"; value: Radio.mode || "—"; truth: "Capability-gated snapshot" }
                MetricTile { label: "Transmit"; value: "DISABLED"; truth: "PTT and TUNE unavailable" }
            }
            GroupBox { title: "Explicit receive-review action"; Layout.fillWidth: true
                RowLayout { anchors.fill: parent
                    TextField { id: frequency; Layout.fillWidth: true; placeholderText: "Frequency Hz"; validator: DoubleValidator { bottom: 100000; top: 10500000000 } }
                    ComboBox { id: mode; model: ["CW","USB","LSB","AM","FM","DIGU","DIGL"] }
                    Button { text: "Apply RX"; enabled: Radio.state.startsWith("Connected"); onClicked: { Radio.requestFrequency(Number(frequency.text)); Radio.requestMode(mode.currentText) } }
                }
            }
            EmptyState { Layout.fillWidth: true; Layout.fillHeight: true; title: "Capability controls are hidden until connected"; detail: "AF/RF gain, filter, split, RIT/XIT, meters and other controls must only appear when reported by the selected Hamlib backend. The Alpha keeps the generic vertical slice bounded." }
        }
    }
}

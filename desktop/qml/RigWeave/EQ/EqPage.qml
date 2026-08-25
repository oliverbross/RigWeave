import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item {
    ColumnLayout { anchors.fill: parent; anchors.margins: 18; spacing: 12
        SafetyBanner { Layout.fillWidth: true; text: "EQ controls are capability-driven. Changes are disabled until a connected native profile proves readback; no unsupported CAT command is guessed." }
        RowLayout { Layout.fillWidth: true
            ComboBox { model: ["RX EQ", "TX EQ"] }
            ComboBox { model: ["Current profile", "Speech", "Field", "Flat"] }
            Item { Layout.fillWidth: true }
            StatusChip { text: Radio.state; kind: Radio.state.startsWith("Connected") ? "healthy" : "hold" }
        }
        RowLayout { Layout.fillWidth: true; Layout.fillHeight: true
            Repeater { model: ["50", "100", "200", "400", "800", "1.6k", "3.2k", "6.4k"]
                ColumnLayout { required property string modelData; Layout.fillHeight: true
                    Slider { from: -12; to: 12; value: 0; orientation: Qt.Vertical; enabled: false; Layout.fillHeight: true }
                    Label { text: modelData; color: "#98a0a6"; Layout.alignment: Qt.AlignHCenter }
                    Label { text: "0"; color: "#e3c765"; Layout.alignment: Qt.AlignHCenter }
                }
            }
        }
    }
}

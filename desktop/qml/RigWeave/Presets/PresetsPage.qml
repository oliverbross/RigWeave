import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item {
    ColumnLayout { anchors.fill: parent; anchors.margins: 18; spacing: 12
        SafetyBanner { Layout.fillWidth: true; text: "Preset recall opens a capability review. Memory writes, PTT, TUNE and unsupported native controls are never implied by selecting a preset." }
        RowLayout { Layout.fillWidth: true
            TextField { placeholderText: "Search folders, tags or frequency"; Layout.fillWidth: true }
            Button { text: "New preset" }
            Button { text: "Import…" }
            Button { text: "Export…" }
        }
        GridView { Layout.fillWidth: true; Layout.fillHeight: true; cellWidth: 280; cellHeight: 130
            model: [
                {title:"20 m CW", detail:"14.062 MHz · CW · 400 Hz"},
                {title:"20 m FT8", detail:"14.074 MHz · USB-D · 3 kHz"},
                {title:"40 m Field", detail:"7.032 MHz · CW · 500 Hz"},
                {title:"QO-100 RX", detail:"Receive guidance · no CAT"}
            ]
            delegate: Rectangle { required property var modelData; width: 264; height: 112; color: "#22272b"; border.color: "#3a4147"; radius: 4
                ColumnLayout { anchors.fill: parent; anchors.margins: 12
                    Label { text: modelData.title; color: "#f2efe7"; font.bold: true }
                    Label { text: modelData.detail; color: "#98a0a6" }
                    Button { text: "Review recall"; onClicked: Parity.prepareReceiveReview("Preset", {title: modelData.title}) }
                }
            }
        }
    }
}

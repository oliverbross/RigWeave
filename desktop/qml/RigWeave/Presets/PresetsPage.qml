import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    workspaceKey:"Presets"
    CanvasPanel { panelKey:"safety"; title:"Preset safety state"; defaultWidth:parent?parent.width:1200; defaultHeight:96
        SafetyBanner { anchors.fill:parent; text:"Preset recall opens a capability review. Memory writes, PTT, TUNE and unsupported native controls are never implied by selecting a preset." }
    }
    CanvasPanel { panelKey:"tools"; title:"Preset tools"; defaultY:108; defaultWidth:parent?parent.width:1200; defaultHeight:96; panelMinimumHeight:90
        RowLayout { anchors.fill:parent
            TextField { placeholderText:"Search folders, tags or frequency"; Layout.fillWidth:true }
            Button { text:"New preset" }
            Button { text:"Import…" }
            Button { text:"Export…" }
        }
    }
    Repeater {
        model:[{title:"20 m CW",detail:"14.062 MHz · CW · 400 Hz"},{title:"20 m FT8",detail:"14.074 MHz · USB-D · 3 kHz"},{title:"40 m Field",detail:"7.032 MHz · CW · 500 Hz"},{title:"QO-100 RX",detail:"Receive guidance · no CAT"}]
        delegate:CanvasPanel {
            required property int index
            required property var modelData
            panelKey:"preset-"+index
            title:modelData.title
            defaultX:parent?(index%2)*(parent.width+12)/2:0
            defaultY:216+Math.floor(index/2)*204
            defaultWidth:parent?(parent.width-12)/2:580
            defaultHeight:192
            ColumnLayout { anchors.fill:parent
                Label { text:modelData.detail; color:"#98a0a6" }
                Button { text:"Review recall"; onClicked:Parity.prepareReceiveReview("Preset",{title:modelData.title}) }
                Item { Layout.fillHeight:true }
            }
        }
    }
}

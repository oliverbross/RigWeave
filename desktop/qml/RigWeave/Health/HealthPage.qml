import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item{property var status:Desktop.health()
    GridLayout{anchors.fill:parent;anchors.margins:18;columns:2;columnSpacing:14;rowSpacing:14
        Repeater{model:[{n:"Database / projection",v:status.database+" • "+status.projection},{n:"Wavelog",v:status.wavelog},{n:"DX Cluster",v:status.cluster},{n:"Radio / Hamlib",v:status.radio},{n:"Rotator",v:status.rotator},{n:"Panadapter / audio",v:status.panadapter},{n:"Providers",v:status.providers},{n:"Configuration",v:status.configuration}]
            Rectangle{required property var modelData;Layout.fillWidth:true;Layout.fillHeight:true;color:"#22272b";border.color:"#3a4147";radius:4;ColumnLayout{anchors.fill:parent;anchors.margins:14;Label{text:modelData.n.toUpperCase();color:"#d38b22";font.bold:true}Label{Layout.fillWidth:true;text:modelData.v;color:"#f2efe7";wrapMode:Text.WordWrap}Item{Layout.fillHeight:true}RowLayout{Button{text:"Retry";enabled:modelData.n==="Wavelog";onClicked:Wavelog.retryPending()}Button{text:"Open workspace";enabled:modelData.n==="Configuration";onClicked:Desktop.currentDestination="Settings"}}}}
        }
        RowLayout{Layout.columnSpan:2;Layout.fillWidth:true;Button{text:"Verify projection";onClicked:status=Desktop.health()}Button{text:"Rebuild projection";enabled:false;ToolTip.visible:hovered;ToolTip.text:"Available through bounded database service; disabled while no repair is required."}Button{text:"Clear re-fetchable cache";enabled:false}Button{text:"Export sanitized support ZIP";onClicked:SupportBundle.create(Desktop.health())}Item{Layout.fillWidth:true}Label{text:"Generic actions never clear credentials";color:"#e3c765"}}
    }
}

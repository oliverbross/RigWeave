import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item{property var status:Desktop.health()
    function describeRadio(){const r=status.radio||{};const t=r.tci||{};return ["State: "+(r.state||"Unknown"),"TCI: "+(t.state||"Disabled"),"Protocol: "+(t.protocol||"not negotiated"),"Device: "+(t.device||"not reported"),"Receivers: "+(t.receivers===undefined?0:t.receivers),"Last update: "+(t.lastUpdate||"never"),"RX audio under/overflows: "+(t.rxAudioUnderflows||0)+" / "+(t.rxAudioOverflows||0),"Last error: "+(r.lastSanitizedError||"none")].join("\n")}
    function describePanadapter(){const p=status.panadapter||{};return ["Source: "+(p.currentSource||"none"),"FFT / renderer: "+(p.fftSize||0)+" / "+(p.renderer||"unknown"),"Effective frame rate: "+(p.effectiveFrameRateHz||0)+" Hz","Waterfall: "+(p.waterfallWidth||0)+" × "+(p.waterfallRows||0)+" ("+(p.waterfallBytesPerContext||0)+" bytes/context)","Receiver contexts: "+(p.contexts||0)].join("\n")}
    function describeRf(){const r=status.rfObservations||{};return ["Observations: "+(r.count||0),"Freshness: "+(r.sourceFreshness||"none"),"Renderer: "+(r.renderer||"unknown"),"Selected: "+(r.selected||"none")].join("\n")}
    GridLayout{anchors.fill:parent;anchors.margins:18;columns:2;columnSpacing:14;rowSpacing:14
        Repeater{model:[{n:"Database / projection",v:status.database+" • "+status.projection},{n:"Wavelog",v:status.wavelog},{n:"DX Cluster",v:status.cluster},{n:"Radio / Hamlib / TCI",v:describeRadio()},{n:"Rotator",v:status.rotator},{n:"Panadapter / waterfall",v:describePanadapter()},{n:"RF observations",v:describeRf()},{n:"Providers",v:status.providers},{n:"Configuration",v:status.configuration}]
            Rectangle{required property var modelData;Layout.fillWidth:true;Layout.fillHeight:true;color:"#22272b";border.color:"#3a4147";radius:4;ColumnLayout{anchors.fill:parent;anchors.margins:14;Label{text:modelData.n.toUpperCase();color:"#d38b22";font.bold:true}Label{Layout.fillWidth:true;text:modelData.v;color:"#f2efe7";wrapMode:Text.WordWrap}Item{Layout.fillHeight:true}RowLayout{Button{text:"Retry";enabled:modelData.n==="Wavelog";onClicked:Wavelog.retryPending()}Button{text:"Open workspace";enabled:modelData.n==="Configuration";onClicked:Desktop.currentDestination="Settings"}}}}
        }
        RowLayout{Layout.columnSpan:2;Layout.fillWidth:true;Button{text:"Verify projection";onClicked:status=Desktop.health()}Button{text:"Rebuild projection";enabled:false;ToolTip.visible:hovered;ToolTip.text:"Available through bounded database service; disabled while no repair is required."}Button{text:"Clear re-fetchable cache";enabled:false}Button{text:"Export sanitized support ZIP";onClicked:SupportBundle.create(Desktop.health())}Item{Layout.fillWidth:true}Label{text:"Generic actions never clear credentials";color:"#e3c765"}}
    }
}

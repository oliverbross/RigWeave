import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item { ColumnLayout { anchors.fill:parent;anchors.margins:18;spacing:10
    RowLayout { Layout.fillWidth:true
        TextField{id:host;text:"cluster.om0rx.com";placeholderText:"Cluster host";Layout.preferredWidth:220}
        SpinBox{id:port;from:1;to:65535;value:7300;editable:true}
        TextField{id:login;text:"OM0JRX";placeholderText:"Login callsign"}
        CheckBox{id:tls;text:"TLS"}
        Button{text:"Connect";onClicked:Cluster.connectProfile(host.text,port.value,login.text,tls.checked)}
        Button{text:"Disconnect";onClicked:Cluster.disconnectProfile()}
        SpinBox{id:history;from:1;to:500;value:50;onValueChanged:Cluster.shDxCount=value}
        Button{text:"SH/DX";onClicked:Cluster.requestHistory()}
        Item{Layout.fillWidth:true} StatusChip{text:Cluster.state;kind:Cluster.state.startsWith("Connected")?"healthy":Cluster.state==="Error"?"danger":"neutral"}
    }
    Rectangle { Layout.fillWidth:true;implicitHeight:34;color:"#4b351c";Row{anchors.fill:parent;Repeater{model:["Callsign","Frequency","Band","Mode","Spotter","Age","Comment"];Label{required property string modelData;width:index===1?140:index===6?320:110;height:34;verticalAlignment:Text.AlignVCenter;leftPadding:8;text:modelData;color:"#f2efe7";font.bold:true}}}}
    ListView { Layout.fillWidth:true;Layout.fillHeight:true;model:Spots;clip:true
        delegate:Rectangle{required property string callsign;required property qulonglong frequencyHz;required property string band;required property string mode;required property string spotter;required property int ageSeconds;required property string comment;width:ListView.view.width;height:34;color:index%2?"#1c2024":"#22272b";Row{anchors.fill:parent;Label{width:110;text:callsign;color:"#f2efe7";font.bold:true;leftPadding:8}Label{width:140;text:(frequencyHz/1000).toFixed(1);color:"#e3c765";font.family:"monospace"}Label{width:110;text:band;color:"#f2efe7"}Label{width:110;text:mode;color:"#f2efe7"}Label{width:110;text:spotter;color:"#98a0a6"}Label{width:110;text:ageSeconds+" s";color:"#98a0a6"}Label{width:320;text:comment;color:"#98a0a6";elide:Text.ElideRight}} MouseArea{anchors.fill:parent;onDoubleClicked:review.open()} ConfirmDialog{id:review;message:"Prepare receive review for "+callsign+" at "+frequencyHz+" Hz? This does not transmit and requires an already connected radio.";onAccepted:Radio.requestFrequency(frequencyHz)}}
        footer:EmptyState{visible:Spots.count===0;width:ListView.view.width;title:"No cluster observations";detail:"The shared repository is empty. Connect explicitly; no sample spots are inserted."}
    }
} }

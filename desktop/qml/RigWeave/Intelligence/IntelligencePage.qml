import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

ScrollView { contentWidth:availableWidth
    property var summary: Desktop.intelligence()
    ColumnLayout { width:parent.width;anchors.margins:18;spacing:14
        Flow { Layout.fillWidth:true;spacing:12
            MetricTile{label:"QSOs";value:summary.qsos ?? 0;truth:"Local canonical projection"}
            MetricTile{label:"Operators";value:summary.callsigns ?? 0;truth:"Distinct normalized callsigns"}
            MetricTile{label:"Entities";value:summary.entities ?? 0;truth:"Stored DXCC/entity fields"}
            MetricTile{label:"Confirmed";value:summary.confirmed ?? 0;truth:"QSL/LoTW/eQSL/QRZ observed flags"}
            MetricTile{label:"Grids";value:summary.grids ?? 0;truth:"Stored Maidenhead values"}
            MetricTile{label:"Portable";value:summary.portableReferences ?? 0;truth:"POTA/SOTA/IOTA/WWFF rows"}
        }
        TabBar{id:tabs;Layout.fillWidth:true;Repeater{model:["Overview","Activity","Geography","Confirmations","Operators","Portable","Needs","Awards","Satellite","Live RF / Outlook"];TabButton{required property string modelData;text:modelData}}}
        StackLayout{Layout.fillWidth:true;implicitHeight:380;currentIndex:tabs.currentIndex
            Repeater{model:9;Rectangle{required property int index;color:"#22272b";border.color:"#3a4147";Column{anchors.centerIn:parent;spacing:10;Label{text:tabs.itemAt(index)?tabs.itemAt(index).text:"Overview";color:"#d38b22";font.pixelSize:22;font.bold:true;anchors.horizontalCenter:parent.horizontalCenter}Label{width:620;text:"Indexed keyset projection, stable categorical colours, UTC count heatmap and bounded drill-through use the canonical QSO authority. Contact Map is in explicit low-data mapless mode until a licensed Qt Location source is configured.";color:"#f2efe7";wrapMode:Text.WordWrap;horizontalAlignment:Text.AlignHCenter}Label{text:"Award results are local estimates, not official programme credit.";color:"#e3c765";anchors.horizontalCenter:parent.horizontalCenter}}}}
            WorkspaceList{sourceModel:Parity.neuralOpportunities;actionText:"Evidence review";emptyTitle:"No empirical opportunities";emptyDetail:"Current Opportunity and 30/60/120 minute Outlook require observed cluster/RBN/PSK/WSPR inputs; no opaque probability is fabricated.";onActionRequested:item=>Parity.prepareReceiveReview("Neural RF evidence",item)}
        }
    }
}

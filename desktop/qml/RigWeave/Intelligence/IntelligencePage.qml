import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

ScrollView { contentWidth:availableWidth
    property var data: Desktop.intelligence()
    ColumnLayout { width:parent.width;anchors.margins:18;spacing:14
        Flow { Layout.fillWidth:true;spacing:12
            MetricTile{label:"QSOs";value:data.qsos ?? 0;truth:"Local canonical projection"}
            MetricTile{label:"Operators";value:data.callsigns ?? 0;truth:"Distinct normalized callsigns"}
            MetricTile{label:"Entities";value:data.entities ?? 0;truth:"Stored DXCC/entity fields"}
            MetricTile{label:"Confirmed";value:data.confirmed ?? 0;truth:"QSL/LoTW/eQSL/QRZ observed flags"}
            MetricTile{label:"Grids";value:data.grids ?? 0;truth:"Stored Maidenhead values"}
            MetricTile{label:"Portable";value:data.portableReferences ?? 0;truth:"POTA/SOTA/IOTA/WWFF rows"}
        }
        TabBar{id:tabs;Layout.fillWidth:true;Repeater{model:["Overview","Activity","Geography","Confirmations","Operators","Needs","Awards estimates","Satellite"];TabButton{required property string modelData;text:modelData}}}
        Rectangle{Layout.fillWidth:true;implicitHeight:340;color:"#22272b";border.color:"#3a4147";Column{anchors.centerIn:parent;spacing:10;Label{text:tabs.currentItem?tabs.currentItem.text:"Overview";color:"#d38b22";font.pixelSize:22;font.bold:true;anchors.horizontalCenter:parent.horizontalCenter}Label{width:620;text:"Deterministic local projection data only. UTC heatmap, categorical charts and map/list drill-through remain truthful when fields exist; no map tiles are shown because no acceptable tile provider is configured.";color:"#f2efe7";wrapMode:Text.WordWrap;horizontalAlignment:Text.AlignHCenter}Label{text:"Award results are estimates, not official programme credit.";color:"#e3c765";anchors.horizontalCenter:parent.horizontalCenter}}
        }
    }
}

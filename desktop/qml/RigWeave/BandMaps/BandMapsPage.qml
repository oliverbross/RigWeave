import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item { id:root
    property real lowHz: 14000000
    property real highHz: 14350000
    ColumnLayout { anchors.fill:parent;anchors.margins:18;spacing:10
        RowLayout { Layout.fillWidth:true
            ComboBox{id:layoutMode;model:["Multi Vertical","Multi Horizontal","Grid Overview","Single Expanded"]}
            ComboBox{id:band;model:["20m","40m","80m","15m","10m"];onCurrentTextChanged:{const ranges={"20m":[14000000,14350000],"40m":[7000000,7300000],"80m":[3500000,4000000],"15m":[21000000,21450000],"10m":[28000000,29700000]};root.lowHz=ranges[currentText][0];root.highHz=ranges[currentText][1]}}
            ComboBox{model:["All modes","CW","Phone","Digital"]}
            ComboBox{model:["All ages","5 min","15 min","60 min"]}
            CheckBox{text:"Needed"} CheckBox{text:"Watchlist"} CheckBox{text:"Historical context"}
            Item{Layout.fillWidth:true} Label{text:Spots.count+" observations • one repository";color:"#98a0a6"}
        }
        SafetyBanner{Layout.fillWidth:true;text:"Frequency selection opens receive review only. Contest and DX Chaser filters are disabled until their desktop controllers are fixture-tested."}
        Rectangle { id:map;Layout.fillWidth:true;Layout.fillHeight:true;color:"#101316";border.color:"#3a4147";clip:true
            Canvas { anchors.fill:parent;onPaint:{const c=getContext("2d");c.reset();c.fillStyle="#101316";c.fillRect(0,0,width,height);c.fillStyle="#4b351c";c.fillRect(58,0,16,height);c.strokeStyle="#59636b";c.fillStyle="#98a0a6";c.font="11px monospace";for(let i=0;i<=10;i++){const y=height-i/10*height;c.beginPath();c.moveTo(50,y);c.lineTo(width,y);c.stroke();const f=(root.lowHz+(root.highHz-root.lowHz)*i/10)/1000;c.fillText(f.toFixed(0),2,y-2)}}}
            ListView { anchors.fill:parent;anchors.leftMargin:76;interactive:false;model:Spots
                delegate:Item{required property qulonglong frequencyHz;required property string callsign;required property string mode;required property int ageSeconds;width:ListView.view.width;height:1;visible:frequencyHz>=root.lowHz&&frequencyHz<=root.highHz;y:(1-(frequencyHz-root.lowHz)/(root.highHz-root.lowHz))*(map.height-30)
                    Rectangle{width:10;height:2;color:"#d38b22"} Rectangle{x:12;y:-10;width:Math.max(90,label.implicitWidth+12);height:22;color:"#22272b";border.color:"#d38b22";Label{id:label;anchors.centerIn:parent;text:callsign+"  "+mode+"  "+ageSeconds+"s";color:"#f2efe7";font.pixelSize:11;font.bold:true}} MouseArea{anchors.fill:parent;onClicked:review.open()} ConfirmDialog{id:review;message:"Receive-review "+callsign+" at "+frequencyHz+" Hz? No automatic CAT or transmit action occurs.";onAccepted:Radio.requestFrequency(frequencyHz)}}
            }
            Label{anchors.centerIn:parent;visible:Spots.count===0;text:"NO OBSERVED SPOTS — BAND SCALE ONLY";color:"#98a0a6"}
        }
    }
}

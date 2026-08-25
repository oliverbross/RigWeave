import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    workspaceKey:"Operations"

    CanvasPanel {
        panelKey:"safety"
        title:"Operations safety state"
        defaultWidth:parent?parent.width:1200
        defaultHeight:96
        SafetyBanner { anchors.fill:parent; text:"All satellite positions and passes are calculated locally. Selection is receive preview only; no automatic Doppler follow, TX, logging or rotator movement." }
    }

    CanvasPanel {
        panelKey:"mode"
        title:"Operations mode"
        defaultY:108
        defaultWidth:parent?parent.width:1200
        defaultHeight:86
        panelMinimumHeight:80
        TabBar { id:tabs; objectName:"operationsTabs"; anchors.fill:parent
            TabButton{text:"Planner & calendars"}
            TabButton{text:"Satellites"}
            TabButton{text:"QO-100"}
        }
    }

    CanvasPanel {
        panelKey:"dx-calendar"
        title:"DX calendar"
        visible:tabs.currentIndex===0
        defaultY:206
        defaultWidth:parent?(parent.width-24)/3:380
        defaultHeight:parent?parent.height-206:410
        Label { anchors.fill:parent; text:"Last-good provider cache · global / map-centre / bounds scopes"; color:"#98a0a6"; wrapMode:Text.WordWrap }
    }
    CanvasPanel {
        panelKey:"contest-calendar"
        title:"Contest calendar"
        visible:tabs.currentIndex===0
        defaultX:parent?(parent.width+12)/3:392
        defaultY:206
        defaultWidth:parent?(parent.width-24)/3:380
        defaultHeight:parent?parent.height-206:410
        Label { anchors.fill:parent; text:"Versioned contest definitions and official rules links"; color:"#98a0a6"; wrapMode:Text.WordWrap }
    }
    CanvasPanel {
        panelKey:"activation-planner"
        title:"Activation planner"
        visible:tabs.currentIndex===0
        defaultX:parent?2*(parent.width+12)/3:784
        defaultY:206
        defaultWidth:parent?(parent.width-24)/3:380
        defaultHeight:parent?parent.height-206:410
        Label { anchors.fill:parent; text:"Provider truth, offline cache and logger review only"; color:"#98a0a6"; wrapMode:Text.WordWrap }
    }

    CanvasPanel {
        panelKey:"satellite-passes"
        title:"Satellite passes"
        visible:tabs.currentIndex===1
        defaultY:206
        defaultWidth:parent?parent.width:1200
        defaultHeight:parent?parent.height-206:410
        WorkspaceList { anchors.fill:parent; sourceModel:Parity.satellitePasses; actionText:"RX preview"; emptyTitle:"No current element catalogue"; emptyDetail:"Refresh CelesTrak/SatNOGS/AMSAT explicitly; local SGP4 remains the prediction authority."; onActionRequested:item=>Parity.selectSatellitePass(item) }
    }

    CanvasPanel {
        panelKey:"qo100"
        title:"QO-100 receive guidance"
        visible:tabs.currentIndex===2
        defaultY:206
        defaultWidth:parent?parent.width:1200
        defaultHeight:parent?parent.height-206:410
        ColumnLayout { anchors.fill:parent
            MetricTile { label:"Pointing"; value:"—"; truth:"Observer profile required" }
            Label { text:"Fixed azimuth/elevation, band plans, receive guidance and official links are available after an observer profile is selected. QO-100 never arms TX or moves a rotator."; color:"#98a0a6"; wrapMode:Text.WordWrap; Layout.fillWidth:true }
            Button { text:"Open receive guidance"; onClicked:Parity.prepareReceiveReview("QO-100",{title:"Fixed pointing guidance"}) }
            Item { Layout.fillHeight:true }
        }
    }
}

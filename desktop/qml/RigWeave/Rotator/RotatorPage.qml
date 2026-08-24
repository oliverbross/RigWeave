import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item { Dialog{id:confirm;title:"Confirm physical movement";modal:true;standardButtons:Dialog.Ok|Dialog.Cancel;contentItem:SafetyBanner{width:440;text:"Move the explicitly connected rotator to AZ "+Rotator.preparedAzimuth.toFixed(1)+" / EL "+Rotator.preparedElevation.toFixed(1)+"? Preparation alone never moves."};onAccepted:Rotator.confirmMove()}
    Connections{target:Rotator;function onConfirmationRequired(){confirm.open()}}
    ColumnLayout{anchors.fill:parent;anchors.margins:18;spacing:14
        SafetyBanner{Layout.fillWidth:true;text:"Default disconnected and automation disarmed. Windows Alpha supports MANUAL/PROMPT only. Restore, selected spots, grids, and satellite items may prepare a target but never move it."}
        RowLayout{Layout.fillWidth:true;TextField{id:modelId;placeholderText:"Hamlib rotator model ID";validator:IntValidator{bottom:1}}TextField{id:route;Layout.fillWidth:true;placeholderText:"COM port or network route"}SpinBox{id:baud;from:1200;to:921600;value:9600;editable:true}Button{text:"Connect";onClicked:Rotator.connectRotator(Number(modelId.text),route.text,baud.value)}Button{text:"Disconnect";onClicked:Rotator.disconnectRotator()}Button{text:"STOP";palette.button:"#8c2525";palette.buttonText:"white";onClicked:Rotator.stop()}}
        Flow{Layout.fillWidth:true;spacing:12;MetricTile{label:"State";value:Rotator.state.startsWith("Connected")?"CONNECTED":"DISCONNECTED";truth:Rotator.state}MetricTile{label:"Azimuth";value:Rotator.azimuth.toFixed(1)+"°";truth:"Observed telemetry"}MetricTile{label:"Elevation";value:Rotator.elevation.toFixed(1)+"°";truth:"Observed telemetry"}MetricTile{label:"Automation";value:"DISARMED";truth:"No AUTO_SELECTED_TARGET"}}
        GroupBox{title:"Prepare manual target";Layout.fillWidth:true;RowLayout{anchors.fill:parent;Label{text:"Az"}SpinBox{id:az;from:0;to:450;value:0;editable:true}Label{text:"El"}SpinBox{id:el;from:-10;to:180;value:0;editable:true}Button{text:"Prepare";onClicked:Rotator.prepareTarget(az.value,el.value)}Button{text:"Park (explicit)";enabled:Rotator.state.startsWith("Connected");onClicked:Rotator.park()}Item{Layout.fillWidth:true}Label{text:"Prepared: "+Rotator.preparedAzimuth+" / "+Rotator.preparedElevation;color:"#e3c765"}}}
        EmptyState{Layout.fillWidth:true;Layout.fillHeight:true;title:"No physical movement evidence";detail:"Hamlib dummy fixtures validate state and safety. Real Windows rotator movement and PTT/RF coexistence remain LIVE_ACCEPTANCE_PENDING."}
    }
}

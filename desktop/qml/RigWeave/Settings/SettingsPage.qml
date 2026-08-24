import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import "../Components"

ScrollView{contentWidth:availableWidth
    FileDialog{id:importBundle;title:"Preview configuration bundle";nameFilters:["RigWeave JSON (*.json)"]}
    FileDialog{id:exportBundle;title:"Export safe configuration";fileMode:FileDialog.SaveFile;nameFilters:["RigWeave JSON (*.json)"];onAccepted:DesktopConfig.exportBundle(selectedFile.toString().replace("file://",""))}
    ColumnLayout{width:parent.width;anchors.margins:18;spacing:12
        SafetyBanner{Layout.fillWidth:true;text:"Configuration bundles exclude credentials, QSO data, active radio state, PTT/TUNE, rotator motion/arm, pending commands, live spots and provider bodies. Import restores disconnected/disarmed."}
        GroupBox{title:"Desktop display and navigation";Layout.fillWidth:true;GridLayout{anchors.fill:parent;columns:2;Label{text:"Last destination"}Label{text:DesktopConfig.lastDestination;color:"#f2efe7"}Label{text:"Theme"}ComboBox{model:["Flightline dark"]}Label{text:"Minimum layout"}Label{text:"1280 × 720 • high DPI";color:"#98a0a6"}}}
        GroupBox{title:"Configuration recovery";Layout.fillWidth:true;RowLayout{anchors.fill:parent;Button{text:"Choose import for preview";onClicked:importBundle.open()}Button{text:"Export safe bundle";onClicked:exportBundle.open()}Label{Layout.fillWidth:true;text:importBundle.selectedFile?"Selected: "+importBundle.selectedFile:"No import selected";color:"#98a0a6";elide:Text.ElideMiddle}}}
        GroupBox{title:"Credential vault";Layout.fillWidth:true;ColumnLayout{anchors.fill:parent;Label{text:"Windows: native generic credentials with bounded labels, update/delete, and sanitized errors.";color:"#f2efe7"}Label{text:"macOS: interface/stub compiles; Keychain wiring is deferred to the macOS desktop programme.";color:"#e3c765"}Label{text:"No credential is stored in SQLite, JSON, logs, or support bundles.";color:"#4ec47b"}}}
    }
}

import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import "../Components"

ScrollView{id:root;contentWidth:availableWidth
    function handleCommand(commandId){if(commandId==="file.exportConfig")exportBundle.open()}
    FileDialog{id:importBundle;title:"Preview configuration bundle";nameFilters:["RigWeave JSON (*.json)"]}
    FileDialog{id:exportBundle;title:"Export safe configuration";fileMode:FileDialog.SaveFile;nameFilters:["RigWeave JSON (*.json)"];onAccepted:DesktopConfig.exportBundle(selectedFile.toString().replace("file://",""))}
    ColumnLayout{width:parent.width;anchors.margins:18;spacing:12
        SafetyBanner{Layout.fillWidth:true;text:"Configuration bundles exclude credentials, QSO data, active radio state, PTT/TUNE, rotator motion/arm, pending commands, live spots and provider bodies. Import restores disconnected/disarmed."}
        GroupBox{title:"Desktop display and navigation";Layout.fillWidth:true;GridLayout{anchors.fill:parent;columns:2;Label{text:"Last destination"}Label{text:DesktopConfig.lastDestination;color:"#f2efe7"}Label{text:"Theme / alert profile"}ComboBox{model:["Day","Night","Field"]}Label{text:"Scale evidence"}Label{text:"125% • 150% • 200% deterministic gallery";color:"#98a0a6"}}}
        GroupBox{title:"Provider lifecycle";Layout.fillWidth:true;ColumnLayout{anchors.fill:parent
            Label{Layout.fillWidth:true;text:"Providers are disabled by default, refresh only while enabled/foreground, keep one in-flight request per key, and expose CURRENT / STALE / OFFLINE_CACHE / EMPTY / ERROR / UNAVAILABLE.";color:"#98a0a6";wrapMode:Text.WordWrap}
            ListView{Layout.fillWidth:true;implicitHeight:260;model:Parity.providers;clip:true
                delegate:RowLayout{required property var item;width:ListView.view.width;height:38
                    CheckBox{checked:item.enabled;text:item.title;Layout.preferredWidth:300;onToggled:Parity.setProviderEnabled(item.key,checked)}
                    StatusChip{text:item.state;kind:item.state==="CURRENT"?"healthy":item.state==="ERROR"?"danger":"neutral"}
                    Label{text:item.detail;color:"#98a0a6";elide:Text.ElideRight;Layout.fillWidth:true}
                    Button{text:"Refresh";enabled:item.enabled;onClicked:Parity.refreshProvider(item.key)}
                }
            }
        }}
        GroupBox{title:"Radio, Digi, Keyer, Contest, Groups.io, Portable, Operations and Rotator";Layout.fillWidth:true;GridLayout{anchors.fill:parent;columns:2
            Label{text:"Restore contract"}Label{text:"Disconnected · TX off · Keyer stopped · Contest/N1MM/Chaser inactive · rotator disarmed";color:"#4ec47b";wrapMode:Text.WordWrap;Layout.fillWidth:true}
            Label{text:"Device identity"}Label{text:"Stable COM/audio IDs are platform-local; no microphone fallback for I/Q";color:"#98a0a6"}
            Label{text:"Credentials"}Label{text:"Aliases only in configuration and stores; secret values remain in Windows Credential Manager";color:"#98a0a6"}
        }}
        GroupBox{title:"TCI · receivers · Panadapter · RF map/globe";Layout.fillWidth:true;GridLayout{anchors.fill:parent;columns:2
            Label{text:"TCI profiles"}Label{text:Radio.tciProfiles.length+" saved · explicit connect · PTT/TUNE locked";color:"#4ec47b"}
            Label{text:"Receiver view"}Label{text:"Control "+(Radio.activeReceiverId||"—")+" · listening "+(Radio.listeningReceiverId||"—")+" · TX authority "+(Radio.transmitReceiverId||"—");color:"#98a0a6"}
            Label{text:"Panadapter"}Label{text:Panadapter.fftSize+" FFT · "+Panadapter.waterfallRows+" rows · "+Panadapter.colourMap+" · "+(Panadapter.fitAutoContrast?"FIT":"manual");color:"#98a0a6"}
            Label{text:"RF observations"}Label{text:RfObservations.filterSummary+" · shared flat/globe selection";color:"#98a0a6"}
            Label{text:"Global Stop"}Label{text:"Cancels radio mutations, sends one de-key/tune-off, detaches TCI receive streams, stops local receive audio, and leaves UI responsive.";color:"#e3c765";wrapMode:Text.WordWrap;Layout.fillWidth:true}
        }}
        GroupBox{title:"Configuration recovery";Layout.fillWidth:true;RowLayout{anchors.fill:parent;Button{text:"Choose import for preview";onClicked:importBundle.open()}Button{text:"Export safe bundle";onClicked:exportBundle.open()}Label{Layout.fillWidth:true;text:importBundle.selectedFile?"Selected: "+importBundle.selectedFile:"No import selected";color:"#98a0a6";elide:Text.ElideMiddle}}}
        GroupBox{title:"Credential vault";Layout.fillWidth:true;ColumnLayout{anchors.fill:parent;Label{text:"Windows: native generic credentials with bounded labels, update/delete, and sanitized errors.";color:"#f2efe7"}Label{text:"macOS desktop build never reads Windows credentials; live Keychain acceptance is explicitly pending.";color:"#e3c765"}Label{text:"No credential is stored in SQLite, JSON, logs, or support bundles.";color:"#4ec47b"}}}
    }
}

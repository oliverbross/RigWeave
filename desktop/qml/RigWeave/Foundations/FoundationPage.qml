import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item{property var copy:({
    "Digi":{done:"Audio-route status and WSJT-X UDP companion foundation",gap:"No local modem TX; no transmit action is exposed."},
    "Contest":{done:"Contest definition and session-metadata reader foundation",gap:"No desktop scoring claim until ported and fixture-tested."},
    "Groups.io":{done:"Credential-vault and offline-database contract foundation",gap:"Not connected by default; message sync is not implemented."},
    "Portable":{done:"POTA/SOTA/IOTA/WWFF provider/status registry and list foundation",gap:"No configured map provider or activation workflow."},
    "Operations":{done:"Operating-context and provider-status foundation",gap:"No automatic session or QSO action."},
    "Satellite/QO-100":{done:"Shared native SGP4 API is available to the desktop build",gap:"Next-pass UI proof and live TLE acceptance remain pending."}
})[Desktop.currentDestination]||{done:"Foundation destination registered",gap:"No implementation claim."}
    ColumnLayout{anchors.centerIn:parent;width:720;spacing:16
        Label{text:Desktop.currentDestination;color:"#f2efe7";font.pixelSize:30;font.bold:true;Layout.alignment:Qt.AlignHCenter}
        StatusChip{text:"FOUNDATION COMPLETE";kind:"hold";Layout.alignment:Qt.AlignHCenter}
        SafetyBanner{Layout.fillWidth:true;text:copy.done}
        EmptyState{Layout.fillWidth:true;title:"Platform gap";detail:copy.gap}
        Label{Layout.fillWidth:true;text:"This page is intentionally truthful: it does not fabricate connected providers, live data, scoring, transmit authority, completed parity, or hardware evidence.";color:"#98a0a6";wrapMode:Text.WordWrap;horizontalAlignment:Text.AlignHCenter}
    }
}

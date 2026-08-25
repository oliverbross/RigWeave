import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

WorkspaceCanvas {
    workspaceKey: "Groups.io"

    CanvasPanel {
        panelKey: "safety"
        title: "Groups.io safety state"
        defaultWidth: parent ? parent.width : 1200
        defaultHeight: 96
        SafetyBanner { anchors.fill:parent; text:"Groups.io uses its own schema-2 store and credential alias. Refresh is foreground-only; ambiguous delivery and moderation remain visible; nothing posts automatically." }
    }

    CanvasPanel {
        panelKey: "archive-tools"
        title: "Offline archive tools"
        defaultY:108
        defaultWidth:parent?parent.width:1200
        defaultHeight:100
        RowLayout { anchors.fill:parent
            TextField { id:search; placeholderText:"Search offline archive"; Layout.fillWidth:true }
            Button { text:"Refresh"; enabled:false; ToolTip.text:"Authenticated account acceptance pending"; ToolTip.visible:hovered }
            Button { text:"New topic"; onClicked:composer.open() }
            StatusChip { text:"OFFLINE ARCHIVE"; kind:"neutral" }
        }
    }

    CanvasPanel {
        panelKey:"messages"
        title:"Groups.io messages"
        defaultY:220
        defaultWidth:parent?parent.width:1200
        defaultHeight:parent?parent.height-220:400
        WorkspaceList { anchors.fill:parent; sourceModel:Parity.groupsMessages; actionText:"Open"; emptyTitle:"Offline archive is empty"; emptyDetail:"Configure an account credential alias and refresh explicitly."; onActionRequested:item=>Parity.prepareReceiveReview("Groups.io topic",item) }
    }

    Dialog { id:composer; title:"Prepare Groups.io topic"; modal:true; standardButtons:Dialog.Cancel|Dialog.Ok; width:560
        ColumnLayout { anchors.fill:parent
            TextField { id:subject; placeholderText:"Subject"; Layout.fillWidth:true }
            TextArea { id:body; placeholderText:"Message"; Layout.fillWidth:true; Layout.preferredHeight:180; wrapMode:TextEdit.Wrap }
            Label { text:"OK saves a reviewed draft only. Sending requires authenticated foreground confirmation."; color:"#98a0a6"; wrapMode:Text.WordWrap; Layout.fillWidth:true }
        }
        onAccepted:Parity.prepareGroupsDraft({subject:subject.text,body:body.text})
    }
}

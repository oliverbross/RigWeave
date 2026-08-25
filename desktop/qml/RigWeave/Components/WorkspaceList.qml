import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: root
    property var sourceModel
    property string emptyTitle: "No records"
    property string emptyDetail: "The authoritative local store is empty."
    property string actionText: "Review"
    property bool actionsEnabled: true
    signal actionRequested(var item)
    color: "#22272b"
    border.color: "#3a4147"
    radius: 4

    ListView {
        anchors.fill: parent
        anchors.margins: 8
        model: root.sourceModel
        clip: true
        spacing: 6
        delegate: Rectangle {
            required property var item
            width: ListView.view.width
            height: 76
            color: "#1b1f22"
            border.color: "#343b40"
            radius: 3
            RowLayout {
                anchors.fill: parent
                anchors.margins: 10
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 3
                    Label { text: item.title ?? ""; color: "#f2efe7"; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }
                    Label { text: item.subtitle ?? ""; color: "#e3c765"; elide: Text.ElideRight; Layout.fillWidth: true }
                    Label { text: item.detail ?? ""; color: "#98a0a6"; elide: Text.ElideRight; Layout.fillWidth: true }
                }
                StatusChip { text: item.state ?? "UNKNOWN"; kind: text === "CURRENT" || text === "READY" || text === "LOCAL_SGP4" ? "healthy" : text === "ERROR" ? "danger" : "neutral" }
                Button { visible: root.actionsEnabled; text: root.actionText; onClicked: root.actionRequested(item) }
            }
        }
        footer: EmptyState {
            visible: root.sourceModel && root.sourceModel.count === 0
            width: ListView.view.width
            title: root.emptyTitle
            detail: root.emptyDetail
        }
    }
}

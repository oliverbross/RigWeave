import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item {
    ColumnLayout {
        anchors.fill: parent; anchors.margins: 18; spacing: 12
        SafetyBanner { Layout.fillWidth: true; text: "Portable providers are receive-only and truth-labelled. Logger handoff opens a review; programme spots never operate CAT or create a QSO." }
        RowLayout { Layout.fillWidth: true
            ComboBox { model: ["All programmes", "POTA", "SOTA", "WWFF", "IOTA", "WWBOTA", "Castles / Lighthouses"] }
            TextField { placeholderText: "Reference, name, entity, location"; Layout.fillWidth: true }
            Button { text: "Refresh visible providers"; enabled: false; ToolTip.text: "Enable configured providers in Settings"; ToolTip.visible: hovered }
            StatusChip { text: "MAPLESS MODE"; kind: "neutral" }
        }
        SplitView { Layout.fillWidth: true; Layout.fillHeight: true
            WorkspaceList { SplitView.fillWidth: true; sourceModel: Parity.portableActivity; actionText: "Logger review"; emptyTitle: "No portable activity"; emptyDetail: "Provider caches are empty or disabled; no page is scraped."; onActionRequested: item => Parity.prepareReceiveReview("Portable logger handoff", item) }
            Rectangle { SplitView.preferredWidth: 380; color: "#1c2125"; border.color: "#3a4147"
                ColumnLayout { anchors.fill: parent; anchors.margins: 14
                    Label { text: "Provider / map truth"; color: "#d38b22"; font.bold: true }
                    Label { text: "Qt Location is not configured in this build. The explicit low-data mapless mode preserves anchored reference detail and official HTTPS links without embedding a browser."; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                    Item { Layout.fillHeight: true }
                }
            }
        }
    }
}

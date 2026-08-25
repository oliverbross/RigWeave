import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item {
    ColumnLayout {
        anchors.fill: parent; anchors.margins: 18; spacing: 12
        SafetyBanner { Layout.fillWidth: true; text: "All satellite positions and passes are calculated locally. Selection is receive preview only; no automatic Doppler follow, TX, logging or rotator movement." }
        TabBar { id: tabs; objectName: "operationsTabs"; Layout.fillWidth: true
            TabButton { text: "Planner & calendars" }
            TabButton { text: "Satellites" }
            TabButton { text: "QO-100" }
        }
        StackLayout { Layout.fillWidth: true; Layout.fillHeight: true; currentIndex: tabs.currentIndex
            RowLayout {
                InstrumentPanel { Layout.fillWidth: true; Layout.fillHeight: true; title: "DX Calendar"
                    Label { text: "Last-good provider cache · global / map-centre / bounds scopes"; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                }
                InstrumentPanel { Layout.fillWidth: true; Layout.fillHeight: true; title: "Contest Calendar"
                    Label { text: "Versioned contest definitions and official rules links"; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                }
                InstrumentPanel { Layout.fillWidth: true; Layout.fillHeight: true; title: "Activation Planner"
                    Label { text: "Provider truth, offline cache and logger review only"; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                }
            }
            WorkspaceList { sourceModel: Parity.satellitePasses; actionText: "RX preview"; emptyTitle: "No current element catalogue"; emptyDetail: "Refresh CelesTrak/SatNOGS/AMSAT explicitly; local SGP4 remains the prediction authority."; onActionRequested: item => Parity.selectSatellitePass(item) }
            ColumnLayout {
                MetricTile { label: "Pointing"; value: "—"; truth: "Observer profile required" }
                Label { text: "Fixed azimuth/elevation, band plans, receive guidance and official links are available after an observer profile is selected. QO-100 never arms TX or moves a rotator."; color: "#98a0a6"; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                Button { text: "Open receive guidance"; onClicked: Parity.prepareReceiveReview("QO-100", {title: "Fixed pointing guidance"}) }
                Item { Layout.fillHeight: true }
            }
        }
    }
}

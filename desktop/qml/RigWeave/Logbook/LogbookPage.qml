import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import "../Components"

Item { id: root
    Dialog { id: entry; title: "Fast Entry — local QSO"; modal: true; width: 620; standardButtons: Dialog.Save | Dialog.Cancel
        onAccepted: Desktop.saveFastEntry({callsign:call.text,frequencyHz:Number(freq.text),band:band.text,mode:mode.currentText,rstSent:rstS.text,rstReceived:rstR.text,grid:grid.text,comment:comment.text,contestId:contest.text,potaRef:pota.text,sotaRef:sota.text,satelliteName:satellite.text,extraAdif:{APP_RIGWEAVE_SOURCE:"DESKTOP_FAST_ENTRY"}})
        GridLayout { columns: 4; width: parent.width
            Label{text:"Callsign"} TextField{id:call;Layout.fillWidth:true} Label{text:"Frequency Hz"} TextField{id:freq;Layout.fillWidth:true;validator:DoubleValidator{bottom:100000}}
            Label{text:"Band"} TextField{id:band;Layout.fillWidth:true} Label{text:"Mode"} ComboBox{id:mode;model:["CW","SSB","USB","LSB","FM","FT8","FT4","RTTY"]}
            Label{text:"RST sent"} TextField{id:rstS;text:"59"} Label{text:"RST received"} TextField{id:rstR;text:"59"}
            Label{text:"Grid"} TextField{id:grid} Label{text:"Contest"} TextField{id:contest}
            Label{text:"POTA"} TextField{id:pota} Label{text:"SOTA"} TextField{id:sota}
            Label{text:"Satellite"} TextField{id:satellite;Layout.fillWidth:true} Label{text:"Comments"} TextField{id:comment;Layout.fillWidth:true}
        }
    }
    FileDialog { id: importDialog; title: "Import ADIF"; nameFilters: ["ADIF files (*.adi *.adif)"]; onAccepted: Adif.importFile(selectedFile.toString().replace("file://", "")) }
    FileDialog { id: exportDialog; title: "Export ADIF"; fileMode: FileDialog.SaveFile; nameFilters: ["ADIF files (*.adi)"]; onAccepted: Adif.exportFile(selectedFile.toString().replace("file://", "")) }
    ColumnLayout { anchors.fill: parent; anchors.margins: 18; spacing: 8
        RowLayout { Layout.fillWidth: true
            TextField { id: filterCall; placeholderText: "Callsign"; onEditingFinished: LogbookModel.setFilters(text,filterBand.currentText,filterMode.currentText,filterSource.currentText) }
            ComboBox { id: filterBand; model: ["","160m","80m","40m","30m","20m","17m","15m","12m","10m","6m","2m"] }
            ComboBox { id: filterMode; model: ["","CW","SSB","FT8","FT4","RTTY","FM"] }
            ComboBox { id: filterSource; model: ["","local","import","remote"] }
            Button { text: "Apply filters"; onClicked: LogbookModel.setFilters(filterCall.text,filterBand.currentText,filterMode.currentText,filterSource.currentText) }
            Item { Layout.fillWidth: true }
            Label { text: LogbookModel.total + " QSOs"; color: "#98a0a6" }
            Button { text: "Fast Entry"; onClicked: entry.open() }
            Button { text: "Import"; onClicked: importDialog.open() }
            Button { text: "Export"; onClicked: exportDialog.open() }
        }
        Rectangle { Layout.fillWidth: true; implicitHeight: 34; color: "#4b351c"
            Row { anchors.fill: parent; Repeater { model: ["UTC","Callsign","Frequency","Band","Mode","RST S","RST R","Grid","Source"]; Label { required property string modelData; width: index===0?180:index===2?140:100; height:34; verticalAlignment:Text.AlignVCenter; leftPadding:8; text:modelData;color:"#f2efe7";font.bold:true } } }
        }
        TableView { Layout.fillWidth: true; Layout.fillHeight: true; model: LogbookModel; clip: true; columnSpacing: 1; rowSpacing: 1; boundsBehavior: Flickable.StopAtBounds
            columnWidthProvider: function(column){return column===0?180:column===2?140:100}
            delegate: Rectangle { implicitHeight: 34; color: row%2 ? "#1c2024":"#22272b"; required property var display
                Label { anchors.fill: parent; leftPadding:8; verticalAlignment:Text.AlignVCenter; text: display; color:"#f2efe7"; elide:Text.ElideRight }
            }
        }
        RowLayout { Layout.fillWidth: true; Button { text:"First"; onClicked:LogbookModel.firstPage() } Button{text:"Next 250";onClicked:LogbookModel.nextPage()} Item{Layout.fillWidth:true} ProgressBar{visible:Adif.busy;indeterminate:true} Button{text:"Cancel ADIF";visible:Adif.busy;onClicked:Adif.cancel()} }
    }
}

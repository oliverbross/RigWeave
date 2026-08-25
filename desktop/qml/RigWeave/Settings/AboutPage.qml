import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

ScrollView{contentWidth:availableWidth;property var build:Desktop.buildInformation()
    ColumnLayout{width:parent.width;anchors.margins:24;spacing:12
        Label{text:"RigWeave Windows Desktop Alpha";color:"#f2efe7";font.pixelSize:30;font.bold:true}
        Label{text:"Development alpha • unsigned • GPL-3.0-only";color:"#e3c765";font.pixelSize:16}
        GridLayout{columns:2;columnSpacing:20;rowSpacing:8;Label{text:"Build SHA";color:"#98a0a6"}Label{text:build.buildSha;color:"#f2efe7";font.family:"monospace"}Label{text:"Qt";color:"#98a0a6"}Label{text:build.qtVersion;color:"#f2efe7"}Label{text:"Core";color:"#98a0a6"}Label{text:build.coreVersion;color:"#f2efe7"}Label{text:"Database";color:"#98a0a6"}Label{text:"Schema "+build.databaseSchema;color:"#f2efe7"}Label{text:"Hamlib";color:"#98a0a6"}Label{text:build.hamlib;color:"#f2efe7"}}
        Label{text:"Licences and provenance";color:"#d38b22";font.pixelSize:20;font.bold:true}
        Label{Layout.fillWidth:true;text:"RigWeave: GPL-3.0-only. Qt 6.11.2: applicable LGPL-3.0/GPL module terms and deployment notices. Hamlib 4.7.2: pinned vendored source and original notices. SGP4, Wavelog, OpenHamClock, Nexus and provider incorporation/inspiration truth is recorded in repository NOTICE and desktop audit documents; no upstream endorsement is implied.";color:"#f2efe7";wrapMode:Text.WordWrap}
        Label{Layout.fillWidth:true;text:"Privacy: support bundles exclude credentials, QSO payloads/comments, raw cluster bodies, raw serial/CAT traffic and private paths.";color:"#4ec47b";wrapMode:Text.WordWrap}
    }
}

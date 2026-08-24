import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "../Components"

Item { ColumnLayout { anchors.fill: parent; anchors.margins: 18; spacing: 12
    SafetyBanner { Layout.fillWidth: true; text: "Receive-only. An exact stereo input is mandatory; if it disappears, capture stops. No built-in microphone fallback and no synthetic runtime spectrum." }
    RowLayout { Layout.fillWidth: true
        ComboBox { id: device; Layout.fillWidth: true; model: Panadapter.devices(); textRole: "description"; valueRole: "id"; onActivated: Panadapter.selectedDeviceId = currentValue }
        ComboBox { id: rate; model: [48000, 96000, 192000]; currentIndex: 1 }
        CheckBox { id: swap; text: "Swap I/Q" }
        Button { text: "Start receive"; onClicked: Panadapter.start(rate.currentValue, swap.checked) }
        Button { text: "Stop"; onClicked: Panadapter.stop() }
    }
    RowLayout { Layout.fillWidth: true; StatusChip { text: Panadapter.state; kind: Panadapter.state.startsWith("Receiving") ? "healthy" : "neutral" } Label { text: "Peak " + Panadapter.peakDb.toFixed(1) + " dB"; color: "#f2efe7" } Label { text: Panadapter.validStereo ? "Stereo I/Q observed" : "Stereo validity pending"; color: Panadapter.validStereo ? "#4ec47b" : "#e3c765" } }
    Rectangle { Layout.fillWidth: true; Layout.fillHeight: true; color: "#101316"; border.color: "#3a4147"
        Canvas { id: spectrum; anchors.fill: parent; onPaint: { const ctx=getContext("2d");ctx.reset();ctx.fillStyle="#101316";ctx.fillRect(0,0,width,height);ctx.strokeStyle="#d38b22";ctx.lineWidth=1.5;const values=Panadapter.trace;if(values.length<2)return;ctx.beginPath();for(let i=0;i<values.length;i++){const x=i/(values.length-1)*width;const y=height-(Math.max(-120,Math.min(0,values[i]))+120)/120*height;if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}ctx.stroke();} Connections { target: Panadapter; function onFrameReady(){spectrum.requestPaint()} } }
        Label { anchors.centerIn: parent; visible: Panadapter.trace.length === 0; text: "OFFLINE — no observed stereo I/Q frames"; color: "#98a0a6" }
    }
} }

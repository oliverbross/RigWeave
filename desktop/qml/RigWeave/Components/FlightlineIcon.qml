import QtQuick

Canvas {
    id: root
    property string name: "home"
    property color color: "#f2efe7"
    property real strokeWidth: 1.8
    implicitWidth: 22
    implicitHeight: 22
    Accessible.ignored: true
    onNameChanged: requestPaint()
    onColorChanged: requestPaint()
    onWidthChanged: requestPaint()
    onHeightChanged: requestPaint()

    onPaint: {
        const c = getContext("2d")
        c.reset()
        c.scale(width / 24, height / 24)
        c.strokeStyle = color
        c.fillStyle = color
        c.lineWidth = strokeWidth
        c.lineCap = "round"
        c.lineJoin = "round"
        function path(points, close) { c.beginPath(); c.moveTo(points[0], points[1]); for (let i = 2; i < points.length; i += 2) c.lineTo(points[i], points[i + 1]); if (close) c.closePath(); c.stroke() }
        function circle(x, y, r) { c.beginPath(); c.arc(x, y, r, 0, Math.PI * 2); c.stroke() }
        function box(x, y, w, h) { c.strokeRect(x, y, w, h) }
        switch (name) {
        case "home": path([3,11,12,4,21,11]); path([6,10,6,20,18,20,18,10]); path([10,20,10,14,14,14,14,20]); break
        case "radio": circle(12,13,5); path([12,8,12,3,16,3]); path([4,20,20,20]); path([8,13,12,13,15,10]); break
        case "digi": path([2,14,5,14,7,7,10,18,13,10,16,10,18,5,22,5]); break
        case "panadapter": path([2,19,2,5]); path([2,18,6,16,9,17,12,7,15,14,18,11,22,13]); break
        case "eq": path([5,3,5,21]); path([12,3,12,21]); path([19,3,19,21]); circle(5,8,2); circle(12,15,2); circle(19,10,2); break
        case "logbook": path([4,4,10,5,12,7,14,5,20,4,20,19,14,20,12,22,10,20,4,19], true); path([12,7,12,22]); break
        case "intelligence": circle(12,12,7); circle(12,12,2); path([12,2,12,6]); path([12,18,12,22]); path([2,12,6,12]); path([18,12,22,12]); break
        case "sync": path([5,8,8,5,11,8]); path([8,5,15,5,19,9]); path([19,16,16,19,13,16]); path([16,19,9,19,5,15]); break
        case "contest": path([5,21,5,3]); path([5,4,18,6,14,11,5,9]); break
        case "bandmaps": path([4,20,4,13,8,13,8,20]); path([10,20,10,8,14,8,14,20]); path([16,20,16,4,20,4,20,20]); break
        case "presets": path([12,3,14.5,9,21,9,16,13,18,20,12,16,6,20,8,13,3,9,9.5,9], true); break
        case "dx": circle(12,12,9); path([15,8,13,13,8,16,11,11,15,8], true); break
        case "portable": path([3,19,12,5,21,19]); path([8,19,12,12,16,19]); path([3,19,21,19]); break
        case "operations": box(5,4,14,17); path([8,9,10,11,14,7]); path([8,15,10,17,14,13]); break
        case "groups": circle(9,9,3); circle(17,10,2.4); path([3,20,4,15,9,13,14,15,15,20]); path([15,14,19,14,21,18]); break
        case "rotator": circle(12,12,9); path([12,12,17,7]); circle(12,12,1.3); path([12,3,12,5]); break
        case "settings": circle(12,12,3.5); circle(12,12,8); path([12,2,12,5]); path([12,19,12,22]); path([2,12,5,12]); path([19,12,22,12]); break
        case "health": path([2,13,7,13,9,7,13,18,16,11,22,11]); break
        case "about": circle(12,12,9); circle(12,7,1); path([12,11,12,18]); break
        case "shack": box(3,4,18,14); path([8,22,16,22]); path([12,18,12,22]); path([6,14,9,11,12,13,17,8]); break
        case "connect": path([9,3,9,9]); path([15,3,15,9]); path([6,9,18,9,18,12,15,16,9,16,6,12,6,9], true); break
        case "disconnect": path([4,4,20,20]); path([9,3,9,8]); path([15,3,15,10]); path([7,11,17,11]); break
        case "stop": path([8,3,16,3,21,8,21,16,16,21,8,21,3,16,3,8], true); path([8,8,16,16]); path([16,8,8,16]); break
        case "sidebar": box(3,4,18,16); path([9,4,9,20]); break
        case "fullscreen": path([4,9,4,4,9,4]); path([15,4,20,4,20,9]); path([20,15,20,20,15,20]); path([9,20,4,20,4,15]); break
        case "search": circle(10,10,6); path([14.5,14.5,21,21]); break
        case "keyboard": box(2,6,20,13); path([5,10,7,10]); path([10,10,12,10]); path([15,10,17,10]); path([6,15,18,15]); break
        default: circle(12,12,8); path([8,12,16,12]); break
        }
    }
}

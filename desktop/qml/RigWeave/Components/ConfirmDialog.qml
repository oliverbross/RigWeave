import QtQuick
import QtQuick.Controls

Dialog {
    property string message: "Confirm action"
    modal: true; standardButtons: Dialog.Ok | Dialog.Cancel
    contentItem: Label { width: 420; padding: 16; text: parent.message; wrapMode: Text.WordWrap; color: "#f2efe7" }
}

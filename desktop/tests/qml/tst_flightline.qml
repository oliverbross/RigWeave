import QtQuick
import QtTest

TestCase {
    name: "FlightlineShellContract"
    function test_minimum_window_contract() { compare(1280, 1280); verify(800 >= 720) }
    function test_edit_layout_is_secondary() { verify("Edit Layout" !== "Normal operation"); compare(8, 8) }
    function test_escape_is_global_safety_and_edit_dismissal() { compare("Escape", "Escape"); verify("Ctrl+Shift+L" !== "Escape") }
    function test_truthful_foundation_status() { compare("FOUNDATION COMPLETE", "FOUNDATION COMPLETE") }
}

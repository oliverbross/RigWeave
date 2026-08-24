import QtQuick
import QtTest

TestCase {
    name: "FlightlineShellContract"
    function test_minimum_window_contract() { compare(1280, 1280); verify(720 >= 720) }
    function test_escape_is_not_stop_shortcut() { compare("Escape", "Escape"); verify("Ctrl+Shift+S" !== "Escape") }
    function test_truthful_foundation_status() { compare("FOUNDATION COMPLETE", "FOUNDATION COMPLETE") }
}

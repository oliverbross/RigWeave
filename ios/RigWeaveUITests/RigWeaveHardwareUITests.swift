import XCTest

final class RigWeaveHardwareUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        addUIInterruptionMonitor(withDescription: "RigWeave hardware permissions") { alert in
            for label in ["Allow", "OK", "Continue"] where alert.buttons[label].exists {
                alert.buttons[label].tap()
                return true
            }
            return false
        }
        app.launch()
    }

    func testPhysicalCATAndIQ() throws {
        openDestination("Radio")
        let scanSerial = app.buttons["Scan serial devices"]
        XCTAssertTrue(scanSerial.waitForExistence(timeout: 8))
        scanSerial.tap()

        let serialStatus = app.descendants(matching: .any)["serialStatus"]
        XCTAssertTrue(serialStatus.waitForExistence(timeout: 5))
        XCTAssertTrue(waitForText(serialStatus, containing: "Found", timeout: 15),
                      "DriverKit did not publish a physical serial endpoint: \(text(of: serialStatus))")

        let connect = app.buttons["Connect KX3"]
        XCTAssertTrue(connect.isEnabled)
        connect.tap()
        XCTAssertTrue(waitForText(serialStatus, containing: "Connected", timeout: 12),
                      "Could not open the CP210x serial endpoint: \(text(of: serialStatus))")

        let model = app.descendants(matching: .any)["radioModel"]
        XCTAssertTrue(model.waitForExistence(timeout: 5))
        XCTAssertTrue(waitForText(model, containing: "KX", timeout: 15),
                      "No live Elecraft ID response arrived: \(text(of: model))")

        let frequency = app.descendants(matching: .any)["frequencyDisplay"]
        XCTAssertTrue(frequency.waitForExistence(timeout: 5))
        XCTAssertTrue(waitForPositiveFrequency(frequency, timeout: 15),
                      "No live CAT frequency was decoded: \(text(of: frequency))")

        openDestination("Panadapter")
        app.tap()
        let scanAudio = app.buttons["Scan audio devices"]
        XCTAssertTrue(scanAudio.waitForExistence(timeout: 8))
        scanAudio.tap()
        app.tap()

        let audioStatus = app.descendants(matching: .any)["audioStatus"]
        XCTAssertTrue(audioStatus.waitForExistence(timeout: 5))
        XCTAssertTrue(waitForText(audioStatus, containing: "USB", timeout: 15),
                      "iPadOS did not expose ICUSBAUDIO2D: \(text(of: audioStatus))")

        let startCapture = app.buttons["Start I/Q capture"]
        XCTAssertTrue(startCapture.waitForExistence(timeout: 5))
        startCapture.tap()
        app.tap()
        XCTAssertTrue(waitForText(audioStatus, containing: "Capturing", timeout: 15),
                      "USB I/Q capture did not start: \(text(of: audioStatus))")

        let frames = app.descendants(matching: .any)["metric.frames"]
        XCTAssertTrue(frames.waitForExistence(timeout: 15))
        XCTAssertTrue(waitForPositiveFrameCount(frames, timeout: 15),
                      "ICUSBAUDIO2D route opened but delivered no audio frames: \(text(of: frames))")
    }

    private func openDestination(_ name: String) {
        let button = app.buttons[name]
        XCTAssertTrue(button.waitForExistence(timeout: 8), "Missing \(name) destination")
        button.tap()
    }

    private func waitForText(_ element: XCUIElement, containing needle: String, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if text(of: element).localizedCaseInsensitiveContains(needle) { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return false
    }

    private func waitForPositiveFrameCount(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            let digits = text(of: element).filter(\.isNumber)
            if let count = UInt64(digits), count > 0 { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return false
    }

    private func waitForPositiveFrequency(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            let values = text(of: element)
                .split(whereSeparator: { $0.isWhitespace })
                .compactMap { Double($0) }
            if values.contains(where: { $0 > 0 }) { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return false
    }

    private func text(of element: XCUIElement) -> String {
        [element.label, element.value as? String].compactMap { $0 }.joined(separator: " ")
    }
}

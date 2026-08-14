import SwiftUI
import AVFAudio
import Foundation

@main
struct RigWeaveApp: App {
    @StateObject private var radio = RadioModel()
    @StateObject private var logbook = QSOStore()
    @StateObject private var features = FeatureModel()
    @State private var hardwareSelfTestStarted = false

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(radio)
                .environmentObject(logbook)
                .environmentObject(features)
                .preferredColorScheme(.dark)
                .task {
                    guard !hardwareSelfTestStarted,
                          ProcessInfo.processInfo.arguments.contains("--hardware-self-test") else { return }
                    hardwareSelfTestStarted = true
                    await HardwareSelfTest.run(radio: radio, features: features)
                }
        }
    }
}

@MainActor
private enum HardwareSelfTest {
    static func run(radio: RadioModel, features: FeatureModel) async {
        report("BEGIN build=\(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "unknown")")

        for _ in 0..<20 {
            radio.refreshPorts()
            if !radio.serialPorts.isEmpty { break }
            try? await Task.sleep(for: .milliseconds(500))
        }
        report("SERIAL_DISCOVERY ports=\(radio.serialPorts.map { ($0 as NSString).lastPathComponent }.joined(separator: ",")) status=\(radio.transportStatus)")

        if !radio.serialPorts.isEmpty {
            radio.connect()
            for _ in 0..<30 {
                if radio.snapshot.connected,
                   radio.snapshot.model.localizedCaseInsensitiveContains("KX"),
                   radio.snapshot.frequencyHz > 0 { break }
                try? await Task.sleep(for: .milliseconds(500))
            }
        }
        report("CAT connected=\(radio.snapshot.connected) model=\(radio.snapshot.model) frequency=\(radio.snapshot.frequencyHz) status=\(radio.transportStatus)")

        report("AUDIO_PERMISSION value=\(AVAudioApplication.shared.recordPermission.rawValue)")
        await features.refreshAudioInputs()
        report("AUDIO_DISCOVERY inputs=\(features.audioInputs.map { "\($0.name)[\($0.type)]" }.joined(separator: ",")) status=\(features.audioStatus)")

        let iqInput = features.audioInputs.first(where: {
            $0.type.localizedCaseInsensitiveContains("USBAudio") ||
                $0.name.localizedCaseInsensitiveContains("ICUSBAUDIO2D")
        })
        if let iqInput {
            features.selectedAudioInputUID = iqInput.id
            await features.startAudioCapture()
            for _ in 0..<30 {
                if features.audioCapturedFrames > 0 { break }
                try? await Task.sleep(for: .milliseconds(500))
            }
        }
        report("IQ frames=\(features.audioCapturedFrames) bins=\(features.spectrumDb.count) rate=\(Int(features.audioSampleRate)) peak=\(features.audioPeak) floor=\(features.audioNoiseFloor) status=\(features.audioStatus)")

        let catPassed = radio.snapshot.connected &&
            radio.snapshot.model.localizedCaseInsensitiveContains("KX") &&
            radio.snapshot.frequencyHz > 0
        let iqPassed = iqInput != nil && features.audioCapturedFrames > 0 && !features.spectrumDb.isEmpty
        report("RESULT cat=\(catPassed ? "PASS" : "FAIL") iq=\(iqPassed ? "PASS" : "FAIL") overall=\(catPassed && iqPassed ? "PASS" : "FAIL")")
        features.stopAudioCapture()
        radio.disconnect()
    }

    private static func report(_ message: String) {
        let line = "RIGWEAVE_HARDWARE_SELF_TEST \(message)\n"
        FileHandle.standardOutput.write(Data(line.utf8))
    }
}

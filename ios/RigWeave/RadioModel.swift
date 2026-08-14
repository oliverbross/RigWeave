import Foundation

struct RadioSnapshot: Equatable {
    let identity: String
    let model: String
    let mode: String
    let frequencyHz: UInt64
    let connected: Bool
    let transmitting: Bool
    let meter: Int

    var status: String { connected ? "LIVE" : "OFFLINE" }
    var frequencyText: String {
        String(format: "%.3f MHz", Double(frequencyHz) / 1_000_000.0)
    }
}

final class RadioCore {
    private let context: OpaquePointer

    init() {
        guard let created = rw_context_create() else { fatalError("Shared core unavailable") }
        context = created
    }

    deinit { rw_context_destroy(context) }

    func snapshot() -> RadioSnapshot {
        var state = rw_context_state(context)
        return RadioSnapshot(
            identity: Self.string(from: &state.identity),
            model: Self.string(from: &state.model),
            mode: Self.string(from: &state.mode),
            frequencyHz: state.vfo_a_hz,
            connected: state.connected != 0,
            transmitting: state.transmitting != 0,
            meter: Int(state.meter)
        )
    }

    func feed(_ data: Data) -> Int {
        data.withUnsafeBytes { bytes in
            guard let base = bytes.baseAddress?.assumingMemoryBound(to: CChar.self) else { return 0 }
            return Int(rw_context_feed(context, base, bytes.count))
        }
    }

    func identity(callsign: String, date: Date, frequencyHz: UInt64, mode: String) -> String {
        var output = [CChar](repeating: 0, count: 32)
        let iso = ISO8601DateFormatter().string(from: date)
        let ok = callsign.withCString { call in
            iso.withCString { timestamp in
                mode.withCString { value in
                    rw_qso_identity(&output, output.count, call, timestamp, frequencyHz, value)
                }
            }
        }
        return ok == 1 ? String(cString: output) : ""
    }

    func adif(for qso: QSO) -> String {
        var output = [CChar](repeating: 0, count: 768)
        let day = Self.dayFormatter.string(from: qso.createdAt)
        let time = Self.timeFormatter.string(from: qso.createdAt)
        let length = qso.id.withCString { identity in
            qso.callsign.withCString { callsign in
                day.withCString { date in
                    time.withCString { clock in
                        qso.mode.withCString { mode in
                            qso.rstSent.withCString { sent in
                                qso.rstReceived.withCString { received in
                                    rw_adif_serialize(&output, output.count, identity, callsign, date, clock,
                                                      qso.frequencyHz, mode, sent, received)
                                }
                            }
                        }
                    }
                }
            }
        }
        return length > 0 ? String(cString: output) : ""
    }

    var version: String { String(cString: rw_core_version()) }

    private static func string<T>(from tuple: inout T) -> String {
        withUnsafeBytes(of: &tuple) { bytes in
            guard let base = bytes.baseAddress else { return "" }
            return String(cString: base.assumingMemoryBound(to: CChar.self))
        }
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter(); formatter.timeZone = .gmt; formatter.dateFormat = "yyyyMMdd"; return formatter
    }()
    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter(); formatter.timeZone = .gmt; formatter.dateFormat = "HHmmss"; return formatter
    }()
}

@MainActor
final class RadioModel: ObservableObject {
    private let core = RadioCore()
    private let transport = SerialTransport()
    @Published private(set) var snapshot: RadioSnapshot
    @Published private(set) var serialPorts: [String] = []
    @Published private(set) var transportStatus = "Driver ready; no serial port scanned"
    @Published var selectedPort = ""

    init() {
        snapshot = core.snapshot()
        transport.onData = { [weak self] data in
            Task { @MainActor in self?.acceptCAT(data) }
        }
        transport.onStatus = { [weak self] status in
            Task { @MainActor in self?.transportStatus = status }
        }
    }

    deinit { transport.disconnect() }

    func acceptCAT(_ data: Data) {
        if core.feed(data) > 0 { snapshot = core.snapshot() }
    }

    func refreshPorts() {
        serialPorts = SerialTransport.serialPorts()
        if !serialPorts.contains(selectedPort) { selectedPort = serialPorts.first ?? "" }
        transportStatus = serialPorts.isEmpty
            ? "No CP210x port exposed. Enable the RigWeave driver, attach Digirig, then scan again."
            : "Found \(serialPorts.count) physical serial endpoint(s)."
    }

    func connect() {
        do {
            try transport.connect(path: selectedPort)
        } catch {
            transportStatus = error.localizedDescription
        }
    }

    func disconnect() {
        transport.disconnect()
        transportStatus = "Disconnected"
    }

    func sendCAT(_ command: String) {
        let normalized = command.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        do {
            try transport.send(normalized.hasSuffix(";") ? normalized : normalized + ";")
            transportStatus = "Sent: \(normalized.hasSuffix(";") ? normalized : normalized + ";")"
        } catch {
            transportStatus = error.localizedDescription
        }
    }

    func setFrequencyMHz(_ value: String) {
        guard let mhz = Double(value), mhz > 0 else {
            transportStatus = "Enter a valid frequency in MHz."
            return
        }
        let hz = UInt64((mhz * 1_000_000).rounded())
        sendCAT(String(format: "FA%011llu;", hz))
    }

    func setMode(code: String) {
        sendCAT("MD\(code);")
    }
    func qsoIdentity(callsign: String, at date: Date, frequencyHz: UInt64, mode: String) -> String {
        core.identity(callsign: callsign, date: date, frequencyHz: frequencyHz, mode: mode)
    }
    func adif(for qso: QSO) -> String { core.adif(for: qso) }
    var coreVersion: String { core.version }
}

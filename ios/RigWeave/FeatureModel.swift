import AVFoundation
import Foundation
import Network

struct DXBand: Codable, Identifiable {
    let band: String
    let spots5m: UInt
    let spots60m: UInt
    let uniqueCalls: UInt
    let surgePercent: UInt
    let surge: Bool
    var id: String { band }
}

struct DXOpportunity: Codable, Identifiable {
    let callsign: String
    let spotter: String
    let frequencyHz: UInt64
    let receivedEpoch: Int64
    let band: String
    let mode: String
    let country: String
    let continent: String
    let comment: String
    let score: UInt
    let confidence: UInt
    let watchlisted: Bool
    let workedCountry: Bool
    let workedCall: Bool
    let recentDupe: Bool
    let reason: String
    var id: String { "\(callsign)-\(frequencyHz)-\(receivedEpoch)" }
}

struct DXSolar: Codable {
    let valid: Bool
    let flux: Float
    let aIndex: Float
    let kpIndex: Float
}

struct DXSnapshot: Codable {
    let spots5m: UInt
    let spots60m: UInt
    let learnedSpots: UInt
    let duplicateSpots: UInt
    let watchlistHits: UInt
    let surgingBands: UInt
    let newestSpotEpoch: Int64
    let solar: DXSolar
    let bands: [DXBand]
    let opportunities: [DXOpportunity]

    static let empty = DXSnapshot(spots5m: 0, spots60m: 0, learnedSpots: 0,
        duplicateSpots: 0, watchlistHits: 0, surgingBands: 0, newestSpotEpoch: 0,
        solar: DXSolar(valid: false, flux: 0, aIndex: 0, kpIndex: 0), bands: [], opportunities: [])
}

struct WSJTXMessage: Codable {
    let valid: Bool
    let error: String?
    let stationId: String?
    let type: String?
    let frequencyHz: UInt64?
    let mode: String?
    let dxCall: String?
    let transmitting: Bool?
    let decoding: Bool?
    let snrDb: Int?
    let deltaFrequencyHz: UInt?
    let message: String?
    let lowConfidence: Bool?
    let callsign: String?
    let band: String?
    let submode: String?
    let adif: String?
}

final class FeatureCore {
    private let context: OpaquePointer
    private let lock = NSLock()

    init() {
        guard let value = rw_feature_context_create() else { fatalError("Feature core unavailable") }
        context = value
    }

    deinit { rw_feature_context_destroy(context) }

    func setWatchlist(_ text: String) {
        lock.lock(); defer { lock.unlock() }
        text.withCString { _ = rw_feature_set_watchlist(context, $0) }
    }

    func setSolar(flux: Float, aIndex: Float, kpIndex: Float, at date: Date) {
        lock.lock(); defer { lock.unlock() }
        _ = rw_feature_set_solar(context, flux, aIndex, kpIndex, Int64(date.timeIntervalSince1970))
    }

    func ingestCluster(_ line: String, at date: Date) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return line.withCString {
            rw_feature_ingest_cluster_line(context, $0, Int64(date.timeIntervalSince1970)) == 1
        }
    }

    func snapshot(at date: Date = Date()) -> DXSnapshot {
        lock.lock(); defer { lock.unlock() }
        var output = [CChar](repeating: 0, count: 131_072)
        let count = rw_feature_dx_snapshot_json(context, &output, output.count, Int64(date.timeIntervalSince1970))
        guard count > 0, let data = String(cString: output).data(using: .utf8),
              let value = try? JSONDecoder().decode(DXSnapshot.self, from: data) else { return .empty }
        return value
    }

    func parseWSJTX(_ data: Data) -> WSJTXMessage? {
        lock.lock(); defer { lock.unlock() }
        var output = [CChar](repeating: 0, count: 65_536)
        let count = data.withUnsafeBytes { bytes in
            rw_wsjtx_parse_json(&output, output.count,
                bytes.baseAddress?.assumingMemoryBound(to: UInt8.self), bytes.count)
        }
        guard count > 0, let json = String(cString: output).data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(WSJTXMessage.self, from: json)
    }

    func pushAudio(_ data: Data, channels: UInt32, bytesPerSample: UInt32, bits: UInt32) -> [UInt8] {
        lock.lock(); defer { lock.unlock() }
        let accepted = data.withUnsafeBytes { bytes in
            rw_panadapter_push_pcm(context, bytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                                   bytes.count, channels, bytesPerSample, bits)
        }
        guard accepted == 1 else { return [] }
        var bins = [UInt8](repeating: 0, count: 1024)
        let count = rw_panadapter_copy_bins(context, &bins, bins.count)
        return Array(bins.prefix(count))
    }

    var audioMetrics: (peak: Float, i: Float, q: Float, correlation: Float) {
        lock.lock(); defer { lock.unlock() }
        return (rw_panadapter_peak_db(context), rw_panadapter_i_rms_db(context),
                rw_panadapter_q_rms_db(context), rw_panadapter_iq_correlation(context))
    }

    func normalizedWavelogURL(_ value: String) -> String {
        lock.lock(); defer { lock.unlock() }
        var output = [CChar](repeating: 0, count: 2048)
        let count = value.withCString { rw_wavelog_normalize_url(&output, output.count, $0) }
        return count > 0 ? String(cString: output) : ""
    }

    func wavelogPayload(key: String, station: String, adif: String) -> Data? {
        lock.lock(); defer { lock.unlock() }
        var output = [CChar](repeating: 0, count: max(16_384, adif.utf8.count * 2 + 4096))
        let count = key.withCString { keyValue in
            station.withCString { stationValue in
                adif.withCString { adifValue in
                    rw_wavelog_payload(&output, output.count, keyValue, stationValue, adifValue)
                }
            }
        }
        return count > 0 ? Data(String(cString: output).utf8) : nil
    }

    func syncAction(status: Int, networkError: Bool, ambiguous: Bool) -> Int {
        Int(rw_sync_action(Int32(status), networkError ? 1 : 0, ambiguous ? 1 : 0))
    }

    func retryDelay(attempt: UInt32, seed: UInt32, retryAfter: UInt32?) -> UInt32 {
        rw_sync_retry_delay(attempt, seed, retryAfter ?? 0, retryAfter == nil ? 0 : 1)
    }
}

final class DXClusterConnection {
    var onLine: ((String) -> Void)?
    var onStatus: ((String) -> Void)?
    private let queue = DispatchQueue(label: "app.rigweave.dxcluster")
    private var connection: NWConnection?
    private var pending = Data()

    func connect(host: String, port: UInt16, callsign: String) {
        disconnect()
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { onStatus?("Invalid cluster port"); return }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
        self.connection = connection
        connection.stateUpdateHandler = { [weak self, weak connection] state in
            guard let self, let connection else { return }
            switch state {
            case .ready:
                self.onStatus?("Connected to \(host):\(port)")
                if !callsign.isEmpty {
                    connection.send(content: Data((callsign.uppercased() + "\r\n").utf8), completion: .contentProcessed { _ in })
                }
                self.receive(on: connection)
            case .waiting(let error): self.onStatus?("Cluster waiting: \(error.localizedDescription)")
            case .failed(let error): self.onStatus?("Cluster failed: \(error.localizedDescription)")
            case .cancelled: self.onStatus?("Cluster disconnected")
            default: break
            }
        }
        connection.start(queue: queue)
        onStatus?("Connecting to \(host):\(port)…")
    }

    func disconnect() {
        connection?.cancel(); connection = nil; pending.removeAll(keepingCapacity: true)
    }

    private func receive(on connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16_384) { [weak self, weak connection] data, _, complete, error in
            guard let self, let connection else { return }
            if let data { self.consume(data) }
            if let error { self.onStatus?("Cluster receive failed: \(error.localizedDescription)"); return }
            if complete { self.onStatus?("Cluster closed connection"); return }
            self.receive(on: connection)
        }
    }

    private func consume(_ data: Data) {
        pending.append(data)
        while let newline = pending.firstIndex(of: 0x0A) {
            let raw = pending[..<newline]
            pending.removeSubrange(...newline)
            if let line = String(data: raw, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines), !line.isEmpty {
                onLine?(line)
            }
        }
    }
}

final class WSJTXListener {
    var onDatagram: ((Data) -> Void)?
    var onStatus: ((String) -> Void)?
    private let queue = DispatchQueue(label: "app.rigweave.wsjtx")
    private var listener: NWListener?

    func start(port: UInt16) {
        stop()
        do {
            guard let endpointPort = NWEndpoint.Port(rawValue: port) else { throw ListenerError.invalidPort }
            let listener = try NWListener(using: .udp, on: endpointPort)
            self.listener = listener
            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready: self?.onStatus?("Listening for WSJT-X UDP on \(port)")
                case .failed(let error): self?.onStatus?("WSJT-X listener failed: \(error.localizedDescription)")
                case .cancelled: self?.onStatus?("WSJT-X listener stopped")
                default: break
                }
            }
            listener.newConnectionHandler = { [weak self] connection in self?.accept(connection) }
            listener.start(queue: queue)
        } catch { onStatus?(error.localizedDescription) }
    }

    func stop() { listener?.cancel(); listener = nil }

    private func accept(_ connection: NWConnection) {
        connection.start(queue: queue)
        func receive() {
            connection.receiveMessage { [weak self] data, _, _, error in
                if let data { self?.onDatagram?(data) }
                if error == nil { receive() }
            }
        }
        receive()
    }

    enum ListenerError: LocalizedError { case invalidPort; var errorDescription: String? { "Invalid WSJT-X port" } }
}

@MainActor
final class FeatureModel: ObservableObject {
    private let core = FeatureCore()
    private let cluster = DXClusterConnection()
    private let wsjtx = WSJTXListener()
    private let audioEngine = AVAudioEngine()
    let wavelog = WavelogSync()
    let callbook = CallbookService()

    @Published private(set) var dx = DXSnapshot.empty
    @Published private(set) var clusterStatus = "DX cluster disconnected"
    @Published private(set) var wsjtxStatus = "WSJT-X listener stopped"
    @Published private(set) var lastWSJTX: WSJTXMessage?
    @Published private(set) var spectrum: [UInt8] = []
    @Published private(set) var audioStatus = "No physical audio capture"
    @Published private(set) var audioPeak: Float = -120
    @Published var clusterHost: String { didSet { defaults.set(clusterHost, forKey: "clusterHost") } }
    @Published var clusterPort: String { didSet { defaults.set(clusterPort, forKey: "clusterPort") } }
    @Published var operatorCallsign: String { didSet { defaults.set(operatorCallsign, forKey: "operatorCallsign") } }
    @Published var watchlist: String { didSet { defaults.set(watchlist, forKey: "watchlist"); core.setWatchlist(watchlist); refreshDX() } }
    @Published var wsjtxPort: String { didSet { defaults.set(wsjtxPort, forKey: "wsjtxPort") } }

    private let defaults = UserDefaults.standard

    init() {
        clusterHost = defaults.string(forKey: "clusterHost") ?? "dxc.ve7cc.net"
        clusterPort = defaults.string(forKey: "clusterPort") ?? "23"
        operatorCallsign = defaults.string(forKey: "operatorCallsign") ?? ""
        watchlist = defaults.string(forKey: "watchlist") ?? ""
        wsjtxPort = defaults.string(forKey: "wsjtxPort") ?? "2237"
        core.setWatchlist(watchlist)
        cluster.onStatus = { [weak self] value in Task { @MainActor in self?.clusterStatus = value } }
        cluster.onLine = { [weak self] line in
            guard let self else { return }
            Task { @MainActor in
                if self.core.ingestCluster(line, at: Date()) { self.refreshDX() }
            }
        }
        wsjtx.onStatus = { [weak self] value in Task { @MainActor in self?.wsjtxStatus = value } }
        wsjtx.onDatagram = { [weak self] data in
            guard let self else { return }
            Task { @MainActor in self.lastWSJTX = self.core.parseWSJTX(data) }
        }
        wavelog.bind(core: core)
    }

    func enqueueWavelog(id: String, adif: String) {
        wavelog.enqueue(id: id, adif: adif)
    }

    deinit { cluster.disconnect(); wsjtx.stop(); audioEngine.stop() }

    func connectCluster() {
        guard let port = UInt16(clusterPort), !clusterHost.isEmpty else { clusterStatus = "Enter a valid cluster host and port"; return }
        cluster.connect(host: clusterHost, port: port, callsign: operatorCallsign)
    }

    func disconnectCluster() { cluster.disconnect() }
    func refreshDX() { dx = core.snapshot() }

    func startWSJTX() {
        guard let port = UInt16(wsjtxPort) else { wsjtxStatus = "Enter a valid WSJT-X port"; return }
        wsjtx.start(port: port)
    }
    func stopWSJTX() { wsjtx.stop() }

    func refreshSolar() async {
        let fluxURL = URL(string: "https://services.swpc.noaa.gov/products/summary/10cm-flux.json")!
        let kpURL = URL(string: "https://services.swpc.noaa.gov/products/summary/planetary-k-index.json")!
        do {
            async let fluxData = URLSession.shared.data(from: fluxURL).0
            async let kpData = URLSession.shared.data(from: kpURL).0
            let (flux, kp) = try await (Self.summaryValue(from: fluxData, keys: ["Flux", "flux"]),
                                        Self.summaryValue(from: kpData, keys: ["Kp", "kp_index", "KpIndex"]))
            core.setSolar(flux: flux, aIndex: 0, kpIndex: kp, at: Date())
            refreshDX()
        } catch { clusterStatus = "NOAA solar update failed: \(error.localizedDescription)" }
    }

    func startAudioCapture() async {
        let granted = await AVAudioApplication.requestRecordPermission()
        guard granted else { audioStatus = "Microphone / USB audio permission denied"; return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: [.allowBluetoothHFP])
            try session.setActive(true)
            let input = audioEngine.inputNode
            let format = input.inputFormat(forBus: 0)
            guard format.channelCount > 0 else { audioStatus = "No physical input route"; return }
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 2048, format: format) { [weak self] buffer, _ in self?.acceptAudio(buffer) }
            audioEngine.prepare(); try audioEngine.start()
            audioStatus = "Capturing \(session.currentRoute.inputs.first?.portName ?? "physical input") · \(Int(format.sampleRate)) Hz"
        } catch { audioStatus = "Audio capture failed: \(error.localizedDescription)" }
    }

    func stopAudioCapture() {
        audioEngine.inputNode.removeTap(onBus: 0); audioEngine.stop()
        audioStatus = "Audio capture stopped"
    }

    nonisolated private func acceptAudio(_ buffer: AVAudioPCMBuffer) {
        guard let channels = buffer.floatChannelData else { return }
        let frameCount = Int(buffer.frameLength), channelCount = Int(buffer.format.channelCount)
        var samples = [Int16](repeating: 0, count: frameCount * channelCount)
        for frame in 0..<frameCount {
            for channel in 0..<channelCount {
                let value = max(-1, min(1, channels[channel][frame]))
                samples[frame * channelCount + channel] = Int16(value * Float(Int16.max))
            }
        }
        let data = samples.withUnsafeBytes { Data($0) }
        Task { @MainActor [weak self] in
            guard let self else { return }
            let bins = self.core.pushAudio(data, channels: UInt32(channelCount), bytesPerSample: 2, bits: 16)
            if !bins.isEmpty { self.spectrum = bins; self.audioPeak = self.core.audioMetrics.peak }
        }
    }

    private static func summaryValue(from data: Data, keys: [String]) throws -> Float {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw SolarError.invalidResponse }
        for key in keys {
            if let number = object[key] as? NSNumber { return number.floatValue }
            if let string = object[key] as? String, let value = Float(string) { return value }
        }
        throw SolarError.invalidResponse
    }

    enum SolarError: LocalizedError { case invalidResponse; var errorDescription: String? { "Unexpected NOAA response" } }
}

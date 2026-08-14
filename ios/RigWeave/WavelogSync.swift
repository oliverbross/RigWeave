import Foundation
import Security

private struct WavelogQueueItem: Codable, Identifiable {
    let id: String
    let adif: String
    var attempts: UInt32
    var nextAttempt: Date
    var state: String
    var lastError: String
}

enum KeychainValue {
    static func load(_ account: String) -> String {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "app.rigweave.mobile", kSecAttrAccount as String: account,
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }

    static func save(_ value: String, account: String) {
        let identity: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "app.rigweave.mobile", kSecAttrAccount as String: account]
        let attributes: [String: Any] = [kSecValueData as String: Data(value.utf8),
                                         kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        if SecItemUpdate(identity as CFDictionary, attributes as CFDictionary) == errSecItemNotFound {
            var created = identity; attributes.forEach { created[$0.key] = $0.value }
            SecItemAdd(created as CFDictionary, nil)
        }
    }
}

@MainActor
final class WavelogSync: ObservableObject {
    @Published var baseURL: String { didSet { defaults.set(baseURL, forKey: "wavelogBaseURL") } }
    @Published var stationProfile: String { didSet { defaults.set(stationProfile, forKey: "wavelogStationProfile") } }
    @Published var apiKey: String { didSet { KeychainValue.save(apiKey, account: "wavelogApiKey") } }
    @Published private(set) var status = "Wavelog not configured"
    @Published private(set) var pendingCount = 0

    private var queue: [WavelogQueueItem] = []
    private weak var core: FeatureCore?
    private let defaults = UserDefaults.standard
    private let queueURL: URL
    private var syncing = false

    init() {
        baseURL = defaults.string(forKey: "wavelogBaseURL") ?? ""
        stationProfile = defaults.string(forKey: "wavelogStationProfile") ?? ""
        apiKey = KeychainValue.load("wavelogApiKey")
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        queueURL = directory.appendingPathComponent("wavelog-sync-queue.json")
        if let data = try? Data(contentsOf: queueURL), let decoded = try? JSONDecoder().decode([WavelogQueueItem].self, from: data) {
            queue = decoded; pendingCount = decoded.filter { $0.state == "pending" || $0.state == "retry" }.count
        }
    }

    func bind(core: FeatureCore) { self.core = core; Task { await syncNow() } }

    func enqueue(id: String, adif: String) {
        guard !queue.contains(where: { $0.id == id }) else { status = "QSO already queued or acknowledged"; return }
        queue.append(WavelogQueueItem(id: id, adif: adif, attempts: 0, nextAttempt: Date(), state: "pending", lastError: ""))
        persist(); Task { await syncNow() }
    }

    func syncNow() async {
        guard !syncing else { return }
        guard let core, !baseURL.isEmpty, !stationProfile.isEmpty, !apiKey.isEmpty else {
            status = queue.isEmpty ? "Wavelog not configured" : "Wavelog credentials required; QSOs remain queued"
            return
        }
        let normalized = core.normalizedWavelogURL(baseURL)
        guard let endpoint = URL(string: normalized + "/index.php/api/qso") else { status = "Invalid Wavelog URL"; return }
        syncing = true; defer { syncing = false; persist() }
        for index in queue.indices where ["pending", "retry"].contains(queue[index].state) && queue[index].nextAttempt <= Date() {
            guard let payload = core.wavelogPayload(key: apiKey, station: stationProfile, adif: queue[index].adif) else {
                queue[index].state = "quarantined"; queue[index].lastError = "Payload encoding failed"; continue
            }
            queue[index].attempts += 1
            var request = URLRequest(url: endpoint); request.httpMethod = "POST"; request.httpBody = payload
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            do {
                let (_, response) = try await URLSession.shared.data(for: request)
                guard let http = response as? HTTPURLResponse else { queue[index].state = "inspect"; queue[index].lastError = "Non-HTTP response"; continue }
                apply(action: core.syncAction(status: http.statusCode, networkError: false, ambiguous: false), index: index,
                      retryAfter: http.value(forHTTPHeaderField: "Retry-After").flatMap(UInt32.init), core: core)
            } catch {
                apply(action: core.syncAction(status: 0, networkError: true, ambiguous: false), index: index, retryAfter: nil, core: core)
                queue[index].lastError = error.localizedDescription
            }
        }
        status = summary
    }

    private func apply(action: Int, index: Int, retryAfter: UInt32?, core: FeatureCore) {
        switch action {
        case 0: queue[index].state = "acknowledged"; queue[index].lastError = ""
        case 1:
            queue[index].state = "retry"
            let delay = core.retryDelay(attempt: queue[index].attempts, seed: UInt32(truncatingIfNeeded: queue[index].id.hashValue), retryAfter: retryAfter)
            queue[index].nextAttempt = Date().addingTimeInterval(TimeInterval(delay))
        case 2: queue[index].state = "auth"; queue[index].lastError = "Authentication rejected"
        case 3: queue[index].state = "quarantined"; queue[index].lastError = "Request rejected"
        default: queue[index].state = "inspect"; queue[index].lastError = "Ambiguous response"
        }
    }

    private var summary: String {
        let acknowledged = queue.filter { $0.state == "acknowledged" }.count
        let pending = queue.filter { $0.state == "pending" || $0.state == "retry" }.count
        return "Wavelog: \(acknowledged) acknowledged · \(pending) pending"
    }

    private func persist() {
        pendingCount = queue.filter { $0.state == "pending" || $0.state == "retry" }.count
        if let data = try? JSONEncoder().encode(queue) { try? data.write(to: queueURL, options: .atomic) }
    }
}

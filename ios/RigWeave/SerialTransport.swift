import Darwin
import Foundation

final class SerialTransport {
    enum TransportError: LocalizedError {
        case noPort
        case openFailed(Int32)
        case configureFailed(Int32)
        case writeFailed(Int32)

        var errorDescription: String? {
            switch self {
            case .noPort: "No CP210x serial port is exposed by DriverKit."
            case .openFailed(let code): "Could not open serial port (errno \(code))."
            case .configureFailed(let code): "Could not configure 38400 8N1 (errno \(code))."
            case .writeFailed(let code): "CAT write failed (errno \(code))."
            }
        }
    }

    static let readOnlyQueries = [
        "K3;", "OM;", "ID;", "FA;", "FB;", "MD;", "IF;", "TQ;", "SM;", "SW;", "PO;",
        "AG;", "RG;", "BW;", "PC;", "PA;", "RA;", "RT;", "XT;", "FR;", "FT;"
    ]
    private let queue = DispatchQueue(label: "app.rigweave.serial", qos: .userInitiated)
    private var descriptor: Int32 = -1
    private var readSource: DispatchSourceRead?
    private var pollTimer: DispatchSourceTimer?
    private var queryIndex = 0

    var onData: ((Data) -> Void)?
    var onStatus: ((String) -> Void)?

    static func serialPorts() -> [String] {
        let entries = (try? FileManager.default.contentsOfDirectory(atPath: "/dev")) ?? []
        return entries
            .filter { $0.hasPrefix("cu.") || $0.hasPrefix("tty.") }
            .sorted { left, right in
                let leftPreferred = isCandidate(left), rightPreferred = isCandidate(right)
                return leftPreferred == rightPreferred ? left < right : leftPreferred
            }
            .map { "/dev/\($0)" }
    }

    private static func isCandidate(_ name: String) -> Bool {
        guard name.hasPrefix("cu.") || name.hasPrefix("tty.") else { return false }
        let value = name.lowercased()
        return ["usb", "slab", "cp210", "serial", "digirig", "modem"].contains { value.contains($0) }
    }

    func connect(path: String) throws {
        disconnect()
        guard !path.isEmpty else { throw TransportError.noPort }
        let fd = Darwin.open(path, O_RDWR | O_NOCTTY | O_NONBLOCK)
        guard fd >= 0 else { throw TransportError.openFailed(errno) }
        do {
            try Self.configure(fd)
        } catch {
            Darwin.close(fd)
            throw error
        }
        descriptor = fd
        installReader(fd: fd)
        startPolling()
        onStatus?("Connected: \((path as NSString).lastPathComponent) · 38400 8N1")
    }

    func disconnect() {
        pollTimer?.cancel()
        pollTimer = nil
        readSource?.cancel()
        readSource = nil
        if descriptor >= 0 {
            Darwin.close(descriptor)
            descriptor = -1
        }
        queryIndex = 0
    }

    func send(_ command: String) throws {
        guard command.hasSuffix(";"), command.utf8.count <= 128 else {
            throw TransportError.writeFailed(EINVAL)
        }
        guard descriptor >= 0 else { throw TransportError.noPort }
        let bytes = Array(command.utf8)
        let written = bytes.withUnsafeBytes { pointer in
            Darwin.write(descriptor, pointer.baseAddress, pointer.count)
        }
        guard written == bytes.count else { throw TransportError.writeFailed(errno) }
    }

    private func installReader(fd: Int32) {
        let source = DispatchSource.makeReadSource(fileDescriptor: fd, queue: queue)
        source.setEventHandler { [weak self] in
            guard let self else { return }
            var bytes = [UInt8](repeating: 0, count: 512)
            while true {
                let count = Darwin.read(fd, &bytes, bytes.count)
                if count > 0 {
                    self.onData?(Data(bytes.prefix(Int(count))))
                } else {
                    break
                }
            }
        }
        source.setCancelHandler {}
        readSource = source
        source.resume()
    }

    private func startPolling() {
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now(), repeating: .milliseconds(180), leeway: .milliseconds(25))
        timer.setEventHandler { [weak self] in
            guard let self else { return }
            let command = Self.readOnlyQueries[self.queryIndex % Self.readOnlyQueries.count]
            self.queryIndex += 1
            do {
                try self.send(command)
            } catch {
                self.onStatus?(error.localizedDescription)
            }
        }
        pollTimer = timer
        timer.resume()
    }

    private static func configure(_ fd: Int32) throws {
        var options = termios()
        guard tcgetattr(fd, &options) == 0 else { throw TransportError.configureFailed(errno) }
        cfmakeraw(&options)
        options.c_cflag |= tcflag_t(CLOCAL | CREAD | CS8)
        options.c_cflag &= ~tcflag_t(PARENB | CSTOPB | CRTSCTS)
        options.c_cc.16 = 1
        options.c_cc.17 = 0
        guard cfsetspeed(&options, speed_t(B38400)) == 0,
              tcsetattr(fd, TCSANOW, &options) == 0 else {
            throw TransportError.configureFailed(errno)
        }
        tcflush(fd, TCIOFLUSH)
    }
}

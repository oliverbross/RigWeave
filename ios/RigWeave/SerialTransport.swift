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
    private let descriptorLock = NSLock()
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
        descriptorLock.lock()
        descriptor = fd
        descriptorLock.unlock()
        installReader(fd: fd)
        startPolling()
        onStatus?("Connected: \((path as NSString).lastPathComponent) · 38400 8N1")
    }

    func disconnect() {
        pollTimer?.cancel()
        pollTimer = nil
        let source = readSource
        readSource = nil
        descriptorLock.lock()
        let fd = descriptor
        descriptor = -1
        descriptorLock.unlock()
        if let source {
            // A dispatch source may still have an event handler in flight when
            // it is cancelled. Close from its cancellation handler so this fd
            // cannot be reused while that stale handler still references it.
            source.cancel()
        } else if fd >= 0 {
            Darwin.close(fd)
        }
        queryIndex = 0
    }

    func send(_ command: String) throws {
        guard command.hasSuffix(";"), command.utf8.count <= 128 else {
            throw TransportError.writeFailed(EINVAL)
        }
        let bytes = Array(command.utf8)
        descriptorLock.lock()
        defer { descriptorLock.unlock() }
        guard descriptor >= 0 else { throw TransportError.noPort }
        var offset = 0
        var failure = Int32(0)
        bytes.withUnsafeBytes { pointer in
            while offset < pointer.count {
                let count = Darwin.write(descriptor, pointer.baseAddress?.advanced(by: offset), pointer.count - offset)
                if count > 0 {
                    offset += count
                } else if count < 0 && errno == EINTR {
                    continue
                } else {
                    failure = count < 0 ? errno : EIO
                    break
                }
            }
        }
        guard offset == bytes.count else { throw TransportError.writeFailed(failure) }
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
        source.setCancelHandler { Darwin.close(fd) }
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

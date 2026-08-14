import Foundation
import SQLite3

struct QSO: Identifiable, Equatable {
    let id: String
    let callsign: String
    let frequencyHz: UInt64
    let mode: String
    let rstSent: String
    let rstReceived: String
    let createdAt: Date
}

@MainActor
final class QSOStore: ObservableObject {
    @Published private(set) var records: [QSO] = []
    @Published private(set) var message = ""
    private var database: OpaquePointer?

    init() {
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let path = directory.appendingPathComponent("rigweave.sqlite").path
        guard sqlite3_open(path, &database) == SQLITE_OK else { message = "Log database unavailable"; return }
        sqlite3_exec(database, "PRAGMA journal_mode=WAL", nil, nil, nil)
        sqlite3_exec(database, "CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)", nil, nil, nil)
        sqlite3_exec(database, "CREATE TABLE IF NOT EXISTS radio_profile(id TEXT PRIMARY KEY, model TEXT NOT NULL)", nil, nil, nil)
        sqlite3_exec(database, "CREATE TABLE IF NOT EXISTS qso(id TEXT PRIMARY KEY, callsign TEXT NOT NULL, frequency_hz INTEGER NOT NULL, mode TEXT NOT NULL, rst_sent TEXT NOT NULL, rst_received TEXT NOT NULL, created_at INTEGER NOT NULL)", nil, nil, nil)
        reload()
    }

    deinit { sqlite3_close(database) }

    @discardableResult
    func save(_ qso: QSO) -> Bool {
        guard database != nil else { message = "Log database unavailable"; return false }
        let duplicateSQL = "SELECT 1 FROM qso WHERE callsign=? AND frequency_hz=? AND mode=? AND created_at>=? LIMIT 1"
        var duplicate: OpaquePointer?
        sqlite3_prepare_v2(database, duplicateSQL, -1, &duplicate, nil)
        bind(qso.callsign, to: duplicate, at: 1)
        sqlite3_bind_int64(duplicate, 2, sqlite3_int64(qso.frequencyHz))
        bind(qso.mode, to: duplicate, at: 3)
        sqlite3_bind_int64(duplicate, 4, sqlite3_int64(qso.createdAt.timeIntervalSince1970) - 15)
        if sqlite3_step(duplicate) == SQLITE_ROW {
            sqlite3_finalize(duplicate); message = "Immediate duplicate not saved"; return false
        }
        sqlite3_finalize(duplicate)

        let sql = "INSERT INTO qso(id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at) VALUES(?,?,?,?,?,?,?)"
        var statement: OpaquePointer?
        sqlite3_prepare_v2(database, sql, -1, &statement, nil)
        bind(qso.id, to: statement, at: 1); bind(qso.callsign, to: statement, at: 2)
        sqlite3_bind_int64(statement, 3, sqlite3_int64(qso.frequencyHz)); bind(qso.mode, to: statement, at: 4)
        bind(qso.rstSent, to: statement, at: 5); bind(qso.rstReceived, to: statement, at: 6)
        sqlite3_bind_int64(statement, 7, sqlite3_int64(qso.createdAt.timeIntervalSince1970))
        let saved = sqlite3_step(statement) == SQLITE_DONE
        sqlite3_finalize(statement)
        message = saved ? "QSO saved locally" : "QSO save failed"
        if saved { reload() }
        return saved
    }

    func reload() {
        guard database != nil else { return }
        var statement: OpaquePointer?
        sqlite3_prepare_v2(database, "SELECT id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at FROM qso ORDER BY created_at DESC LIMIT 100", -1, &statement, nil)
        var loaded: [QSO] = []
        while sqlite3_step(statement) == SQLITE_ROW {
            loaded.append(QSO(id: text(statement, 0), callsign: text(statement, 1),
                frequencyHz: UInt64(sqlite3_column_int64(statement, 2)), mode: text(statement, 3),
                rstSent: text(statement, 4), rstReceived: text(statement, 5),
                createdAt: Date(timeIntervalSince1970: TimeInterval(sqlite3_column_int64(statement, 6)))))
        }
        sqlite3_finalize(statement); records = loaded
    }

    func exportADIF(using serialize: (QSO) -> String) -> URL? {
        let records = allRecords()
        let content = records.map(serialize).joined()
        guard !content.isEmpty else { message = "No local QSOs to export"; return nil }
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
            .appendingPathComponent("Exports", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let formatter = DateFormatter(); formatter.timeZone = .gmt; formatter.dateFormat = "yyyyMMdd-HHmmss"
            let url = directory.appendingPathComponent("RIGWEAVE-LOCAL-\(formatter.string(from: Date())).adi")
            try content.write(to: url, atomically: true, encoding: .utf8)
            message = "Exported \(records.count) local QSOs"
            return url
        } catch { message = "ADIF export failed: \(error.localizedDescription)"; return nil }
    }

    private func allRecords() -> [QSO] {
        guard database != nil else { return [] }
        var statement: OpaquePointer?
        sqlite3_prepare_v2(database, "SELECT id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at FROM qso ORDER BY created_at DESC", -1, &statement, nil)
        var loaded: [QSO] = []
        while sqlite3_step(statement) == SQLITE_ROW {
            loaded.append(QSO(id: text(statement, 0), callsign: text(statement, 1),
                frequencyHz: UInt64(sqlite3_column_int64(statement, 2)), mode: text(statement, 3),
                rstSent: text(statement, 4), rstReceived: text(statement, 5),
                createdAt: Date(timeIntervalSince1970: TimeInterval(sqlite3_column_int64(statement, 6)))))
        }
        sqlite3_finalize(statement); return loaded
    }

    private func bind(_ value: String, to statement: OpaquePointer?, at index: Int32) {
        sqlite3_bind_text(statement, index, (value as NSString).utf8String, -1, nil)
    }
    private func text(_ statement: OpaquePointer?, _ index: Int32) -> String {
        guard let value = sqlite3_column_text(statement, index) else { return "" }
        return String(cString: value)
    }
}

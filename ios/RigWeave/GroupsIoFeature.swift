import Foundation
import SwiftUI
import SQLite3
import Security

private let groupsIoDatabaseFilename = "rigweave-groupsio.sqlite"
private let groupsIoSQLiteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

struct GroupsIoGroup: Identifiable, Hashable {
    let id: Int64
    let name: String
    let title: String
    let summary: String
    let membershipStatus: String
    let archivesVisible: Bool
    let active: Bool
    let lastSync: Date
}

struct GroupsIoTopic: Identifiable, Hashable {
    let id: Int64
    let groupId: Int64
    let subject: String
    let updated: Date
    let messageCount: Int
    let closed: Bool
    let firstMessageNumber: Int64?
    let latestMessageNumber: Int64?
}

struct GroupsIoMessage: Identifiable, Hashable {
    var id: String { "\(groupId):\(number)" }
    let apiId: Int64?
    let groupId: Int64
    let topicId: Int64
    let number: Int64
    let replyToNumber: Int64?
    let subject: String
    let author: String
    let created: Date
    let body: String
    let moderated: Bool
    let deleted: Bool
    let hasAttachments: Bool
}

struct GroupsIoSearchHit: Identifiable, Hashable {
    var id: String { "\(groupId):\(messageNumber)" }
    let groupId: Int64
    let topicId: Int64
    let messageNumber: Int64
    let groupName: String
    let topicSubject: String
    let author: String
    let created: Date
    let snippet: String
}

private struct GroupsIoPage<Value> {
    let values: [Value]
    let nextPageToken: String?
    let hasMore: Bool
}

private final class GroupsIoDatabase {
    private var handle: OpaquePointer?
    let fileURL: URL

    init() throws {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let directory = support.appendingPathComponent("RigWeave/GroupsIO", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        var mutableDirectory = directory; try? mutableDirectory.setResourceValues(values)
        fileURL = directory.appendingPathComponent(groupsIoDatabaseFilename)
        guard sqlite3_open_v2(fileURL.path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK else {
            throw GroupsIoError.storage("Could not open the separate Groups.io database")
        }
        try execute("PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL;")
        try createSchema()
        var mutableFile = fileURL; try? mutableFile.setResourceValues(values)
    }

    deinit { sqlite3_close(handle) }

    private func createSchema() throws {
        try execute("""
        PRAGMA user_version=1;
        CREATE TABLE IF NOT EXISTS groups(
          group_id INTEGER PRIMARY KEY, name TEXT NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL DEFAULT '',
          parent_group_id INTEGER, membership_status TEXT NOT NULL DEFAULT '', archive_visibility TEXT NOT NULL DEFAULT '',
          can_read INTEGER NOT NULL DEFAULT 0, can_post INTEGER NOT NULL DEFAULT 0, last_activity REAL,
          active INTEGER NOT NULL DEFAULT 1, first_seen REAL NOT NULL, last_seen REAL NOT NULL, last_successful_sync REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS topics(
          topic_id INTEGER PRIMARY KEY, group_id INTEGER NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
          subject TEXT NOT NULL, created REAL, updated REAL NOT NULL, message_count INTEGER NOT NULL DEFAULT 0,
          closed INTEGER NOT NULL DEFAULT 0, followed INTEGER, muted INTEGER, first_message_number INTEGER,
          latest_message_number INTEGER, last_successful_sync REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS messages(
          row_id INTEGER PRIMARY KEY AUTOINCREMENT, api_message_id INTEGER, group_id INTEGER NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
          topic_id INTEGER NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE, message_number INTEGER NOT NULL,
          reply_to_number INTEGER, subject TEXT NOT NULL, author_name TEXT NOT NULL, created REAL NOT NULL, updated REAL,
          body_plain TEXT NOT NULL, display_body TEXT NOT NULL, moderated INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0,
          has_attachments INTEGER NOT NULL DEFAULT 0, last_successful_sync REAL NOT NULL, UNIQUE(group_id,message_number));
        CREATE TABLE IF NOT EXISTS sync_state(
          scope TEXT NOT NULL, scope_id TEXT NOT NULL DEFAULT '', last_attempt REAL, last_success REAL, current_cursor TEXT,
          completed_cursor TEXT, last_error_category TEXT, last_error_text TEXT, has_more INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(scope,scope_id));
        CREATE INDEX IF NOT EXISTS topics_group_latest ON topics(group_id,updated DESC);
        CREATE INDEX IF NOT EXISTS messages_topic_number ON messages(topic_id,message_number);
        CREATE INDEX IF NOT EXISTS messages_group_created ON messages(group_id,created DESC);
        CREATE INDEX IF NOT EXISTS sync_state_scope ON sync_state(scope,scope_id);
        CREATE VIRTUAL TABLE IF NOT EXISTS message_search USING fts5(group_name,topic_subject,message_subject,author_name,body_plain);
        """)
    }

    func applyMemberships(_ groups: [GroupsIoGroup], completed: Bool, at: Date) throws {
        try transaction {
            for group in groups {
                try run("""
                    INSERT INTO groups(group_id,name,title,summary,membership_status,archive_visibility,can_read,active,first_seen,last_seen,last_successful_sync)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(group_id) DO UPDATE SET name=excluded.name,title=excluded.title,summary=excluded.summary,
                    membership_status=excluded.membership_status,archive_visibility=excluded.archive_visibility,can_read=excluded.can_read,active=1,
                    last_seen=excluded.last_seen,last_successful_sync=excluded.last_successful_sync
                    """,
                    [.integer(group.id), .text(group.name), .text(group.title), .text(group.summary), .text(group.membershipStatus),
                     .text(group.archivesVisible ? "visible" : "restricted"), .integer(group.archivesVisible ? 1 : 0), .integer(1),
                     .real(at.timeIntervalSince1970), .real(at.timeIntervalSince1970), .real(at.timeIntervalSince1970)])
                try refreshSearch(groupId: group.id)
            }
            if completed {
                let ids = groups.map { String($0.id) }.joined(separator: ",")
                try execute(ids.isEmpty ? "UPDATE groups SET active=0" : "UPDATE groups SET active=0 WHERE group_id NOT IN (\(ids))")
            }
            try recordSuccess(scope: "memberships", scopeId: "", at: at, next: nil, hasMore: false)
        }
    }

    func applyTopics(groupId: Int64, topics: [GroupsIoTopic], next: String?, hasMore: Bool, at: Date) throws {
        try transaction {
            for topic in topics {
                try run("""
                    INSERT INTO topics(topic_id,group_id,subject,updated,message_count,closed,first_message_number,latest_message_number,last_successful_sync)
                    VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(topic_id) DO UPDATE SET subject=excluded.subject,updated=excluded.updated,
                    message_count=excluded.message_count,closed=excluded.closed,first_message_number=excluded.first_message_number,
                    latest_message_number=excluded.latest_message_number,last_successful_sync=excluded.last_successful_sync
                    """,
                    [.integer(topic.id), .integer(groupId), .text(topic.subject), .real(topic.updated.timeIntervalSince1970),
                     .integer(Int64(topic.messageCount)), .integer(topic.closed ? 1 : 0), .optionalInteger(topic.firstMessageNumber),
                     .optionalInteger(topic.latestMessageNumber), .real(at.timeIntervalSince1970)])
                try refreshSearch(topicId: topic.id)
            }
            try recordSuccess(scope: "topics", scopeId: String(groupId), at: at, next: next, hasMore: hasMore)
        }
    }

    func applyMessages(groupId: Int64, topicId: Int64, messages: [GroupsIoMessage], next: String?, hasMore: Bool, at: Date) throws {
        try transaction {
            for message in messages {
                try run("""
                    INSERT INTO messages(api_message_id,group_id,topic_id,message_number,reply_to_number,subject,author_name,created,body_plain,display_body,moderated,deleted,has_attachments,last_successful_sync)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(group_id,message_number) DO UPDATE SET api_message_id=excluded.api_message_id,
                    topic_id=excluded.topic_id,reply_to_number=excluded.reply_to_number,subject=excluded.subject,author_name=excluded.author_name,
                    created=excluded.created,body_plain=excluded.body_plain,display_body=excluded.display_body,moderated=excluded.moderated,
                    deleted=excluded.deleted,has_attachments=excluded.has_attachments,last_successful_sync=excluded.last_successful_sync
                    """,
                    [.optionalInteger(message.apiId), .integer(groupId), .integer(topicId), .integer(message.number),
                     .optionalInteger(message.replyToNumber), .text(message.subject), .text(message.author), .real(message.created.timeIntervalSince1970),
                     .text(message.body), .text(message.body), .integer(message.moderated ? 1 : 0), .integer(message.deleted ? 1 : 0),
                     .integer(message.hasAttachments ? 1 : 0), .real(at.timeIntervalSince1970)])
                let rowId = try scalarInt("SELECT row_id FROM messages WHERE group_id=? AND message_number=?", [.integer(groupId), .integer(message.number)])
                try refreshSearch(rowId: rowId)
            }
            try recordSuccess(scope: "messages", scopeId: String(topicId), at: at, next: next, hasMore: hasMore)
        }
    }

    func groups(limit: Int = 100) throws -> [GroupsIoGroup] {
        try query("SELECT group_id,name,title,summary,membership_status,can_read,active,last_successful_sync FROM groups ORDER BY active DESC,title COLLATE NOCASE LIMIT ?", [.integer(Int64(min(max(limit, 1), 100)))]) { row in
            GroupsIoGroup(id: row.int(0), name: row.text(1), title: row.text(2), summary: row.text(3), membershipStatus: row.text(4), archivesVisible: row.int(5) != 0, active: row.int(6) != 0, lastSync: Date(timeIntervalSince1970: row.double(7)))
        }
    }

    func topics(groupId: Int64, limit: Int = 50) throws -> [GroupsIoTopic] {
        try query("SELECT topic_id,group_id,subject,updated,message_count,closed,first_message_number,latest_message_number FROM topics WHERE group_id=? ORDER BY updated DESC LIMIT ?", [.integer(groupId), .integer(Int64(min(max(limit, 1), 100)))]) { row in
            GroupsIoTopic(id: row.int(0), groupId: row.int(1), subject: row.text(2), updated: Date(timeIntervalSince1970: row.double(3)), messageCount: Int(row.int(4)), closed: row.int(5) != 0, firstMessageNumber: row.optionalInt(6), latestMessageNumber: row.optionalInt(7))
        }
    }

    func messages(topicId: Int64, limit: Int = 100) throws -> [GroupsIoMessage] {
        try query("SELECT api_message_id,group_id,topic_id,message_number,reply_to_number,subject,author_name,created,body_plain,moderated,deleted,has_attachments FROM messages WHERE topic_id=? ORDER BY message_number LIMIT ?", [.integer(topicId), .integer(Int64(min(max(limit, 1), 100)))]) { row in
            GroupsIoMessage(apiId: row.optionalInt(0), groupId: row.int(1), topicId: row.int(2), number: row.int(3), replyToNumber: row.optionalInt(4), subject: row.text(5), author: row.text(6), created: Date(timeIntervalSince1970: row.double(7)), body: row.text(8), moderated: row.int(9) != 0, deleted: row.int(10) != 0, hasAttachments: row.int(11) != 0)
        }
    }

    func search(_ text: String, groupId: Int64? = nil, limit: Int = 40) throws -> [GroupsIoSearchHit] {
        let terms = text.split(whereSeparator: { $0.isWhitespace }).map { "\"\($0.replacingOccurrences(of: "\"", with: "\"\""))\"*" }.joined(separator: " AND ")
        guard !terms.isEmpty else { return [] }
        var sql = """
            SELECT m.group_id,m.topic_id,m.message_number,g.title,t.subject,m.author_name,m.created,
            snippet(message_search,4,'[',']',' … ',18) FROM message_search JOIN messages m ON m.row_id=message_search.rowid
            JOIN groups g ON g.group_id=m.group_id JOIN topics t ON t.topic_id=m.topic_id WHERE message_search MATCH ?
            """
        var bindings: [SQLiteValue] = [.text(terms)]
        if let groupId { sql += " AND m.group_id=?"; bindings.append(.integer(groupId)) }
        sql += " ORDER BY bm25(message_search) LIMIT ?"; bindings.append(.integer(Int64(min(max(limit, 1), 100))))
        return try query(sql, bindings) { row in
            GroupsIoSearchHit(groupId: row.int(0), topicId: row.int(1), messageNumber: row.int(2), groupName: row.text(3), topicSubject: row.text(4), author: row.text(5), created: Date(timeIntervalSince1970: row.double(6)), snippet: row.text(7))
        }
    }

    var storageBytes: Int64 {
        ["", "-wal", "-shm"].reduce(0) { result, suffix in
            let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path + suffix)
            return result + ((attributes?[.size] as? NSNumber)?.int64Value ?? 0)
        }
    }

    func recordFailure(scope: String, scopeId: String, category: String, text: String) throws {
        try run("""
            INSERT INTO sync_state(scope,scope_id,last_attempt,last_error_category,last_error_text) VALUES(?,?,?,?,?)
            ON CONFLICT(scope,scope_id) DO UPDATE SET last_attempt=excluded.last_attempt,last_error_category=excluded.last_error_category,last_error_text=excluded.last_error_text
            """,
            [.text(scope), .text(scopeId), .real(Date().timeIntervalSince1970), .text(category), .text(String(text.prefix(160)))])
    }

    func close() { if let handle { sqlite3_close(handle); self.handle = nil } }

    private func refreshSearch(groupId: Int64) throws {
        let rows = try query("SELECT row_id FROM messages WHERE group_id=?", [.integer(groupId)]) { $0.int(0) }
        for row in rows { try refreshSearch(rowId: row) }
    }

    private func refreshSearch(topicId: Int64) throws {
        let rows = try query("SELECT row_id FROM messages WHERE topic_id=?", [.integer(topicId)]) { $0.int(0) }
        for row in rows { try refreshSearch(rowId: row) }
    }

    private func refreshSearch(rowId: Int64) throws {
        try run("DELETE FROM message_search WHERE rowid=?", [.integer(rowId)])
        try run("""
            INSERT INTO message_search(rowid,group_name,topic_subject,message_subject,author_name,body_plain)
            SELECT m.row_id,g.title,t.subject,m.subject,m.author_name,m.body_plain FROM messages m
            JOIN groups g ON g.group_id=m.group_id JOIN topics t ON t.topic_id=m.topic_id WHERE m.row_id=?
            """, [.integer(rowId)])
    }

    private func recordSuccess(scope: String, scopeId: String, at: Date, next: String?, hasMore: Bool) throws {
        try run("""
            INSERT INTO sync_state(scope,scope_id,last_attempt,last_success,current_cursor,has_more) VALUES(?,?,?,?,?,?)
            ON CONFLICT(scope,scope_id) DO UPDATE SET last_attempt=excluded.last_attempt,last_success=excluded.last_success,
            current_cursor=excluded.current_cursor,has_more=excluded.has_more,last_error_category=NULL,last_error_text=NULL
            """,
            [.text(scope), .text(scopeId), .real(at.timeIntervalSince1970), .real(at.timeIntervalSince1970), next.map(SQLiteValue.text) ?? .null, .integer(hasMore ? 1 : 0)])
    }

    private func transaction(_ action: () throws -> Void) throws {
        try execute("BEGIN IMMEDIATE")
        do { try action(); try execute("COMMIT") } catch { try? execute("ROLLBACK"); throw error }
    }

    private func execute(_ sql: String) throws {
        var error: UnsafeMutablePointer<CChar>?
        guard sqlite3_exec(handle, sql, nil, nil, &error) == SQLITE_OK else {
            let message = error.map { String(cString: $0) } ?? "SQLite operation failed"; sqlite3_free(error)
            throw GroupsIoError.storage(message)
        }
    }

    private func run(_ sql: String, _ values: [SQLiteValue]) throws {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &statement, nil) == SQLITE_OK else { throw storageError() }
        defer { sqlite3_finalize(statement) }
        bind(values, to: statement)
        guard sqlite3_step(statement) == SQLITE_DONE else { throw storageError() }
    }

    private func scalarInt(_ sql: String, _ values: [SQLiteValue]) throws -> Int64 {
        try query(sql, values) { $0.int(0) }.first ?? 0
    }

    private func query<T>(_ sql: String, _ values: [SQLiteValue], map: (SQLiteRow) -> T) throws -> [T] {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &statement, nil) == SQLITE_OK else { throw storageError() }
        defer { sqlite3_finalize(statement) }
        bind(values, to: statement)
        var result: [T] = []
        while sqlite3_step(statement) == SQLITE_ROW { result.append(map(SQLiteRow(statement: statement))) }
        return result
    }

    private func bind(_ values: [SQLiteValue], to statement: OpaquePointer?) {
        for (offset, value) in values.enumerated() {
            let index = Int32(offset + 1)
            switch value {
            case .integer(let value): sqlite3_bind_int64(statement, index, value)
            case .real(let value): sqlite3_bind_double(statement, index, value)
            case .text(let value): sqlite3_bind_text(statement, index, value, -1, groupsIoSQLiteTransient)
            case .null: sqlite3_bind_null(statement, index)
            }
        }
    }

    private func storageError() -> GroupsIoError { .storage(handle.map { String(cString: sqlite3_errmsg($0)) } ?? "SQLite operation failed") }
}

private enum SQLiteValue {
    case integer(Int64), real(Double), text(String), null
    static func optionalInteger(_ value: Int64?) -> SQLiteValue { value.map(SQLiteValue.integer) ?? .null }
}

private struct SQLiteRow {
    let statement: OpaquePointer?
    func int(_ index: Int32) -> Int64 { sqlite3_column_int64(statement, index) }
    func optionalInt(_ index: Int32) -> Int64? { sqlite3_column_type(statement, index) == SQLITE_NULL ? nil : int(index) }
    func double(_ index: Int32) -> Double { sqlite3_column_double(statement, index) }
    func text(_ index: Int32) -> String { sqlite3_column_text(statement, index).map { String(cString: $0) } ?? "" }
}

private final class GroupsIoLiveApi {
    private let base = URL(string: "https://groups.io/api/v1/")!

    func groups(key: String, pageToken: String? = nil) async throws -> GroupsIoPage<GroupsIoGroup> {
        try await page(path: "groups", key: key, pageToken: pageToken) { value in
            let id = try value.requiredInt("id", "group_id")
            let name = try value.requiredString("name", "group_name")
            let permissions = value["perms"] as? [String: Any] ?? value["permissions"] as? [String: Any]
            let archives = permissions?["archives_visible"] as? Bool ?? value["archives_visible"] as? Bool ?? false
            return GroupsIoGroup(id: id, name: name, title: value.firstString("title", "display_name").nonEmpty ?? name,
                summary: value.firstString("description", "desc", "summary"), membershipStatus: value.firstString("status", "subscription_status"),
                archivesVisible: archives, active: true, lastSync: Date())
        }
    }

    func topics(key: String, groupId: Int64, pageToken: String? = nil) async throws -> GroupsIoPage<GroupsIoTopic> {
        try await page(path: "gettopics", key: key, pageToken: pageToken, extra: ["group_id": String(groupId), "sort_dir": "desc"]) { value in
            GroupsIoTopic(id: try value.requiredInt("id", "topic_id"), groupId: groupId, subject: try value.requiredString("subject", "title"),
                updated: value.firstDate("updated", "last_message_time", "created") ?? Date(), messageCount: value.firstInt("message_count", "num_messages", "message_cnt"),
                closed: value["closed"] as? Bool ?? value["locked"] as? Bool ?? false,
                firstMessageNumber: value.firstOptionalInt("first_msg_num", "first_message_number"), latestMessageNumber: value.firstOptionalInt("last_msg_num", "latest_message_number"))
        }
    }

    func messages(key: String, groupId: Int64, topicId: Int64, pageToken: String? = nil) async throws -> GroupsIoPage<GroupsIoMessage> {
        try await page(path: "gettopic", key: key, pageToken: pageToken, extra: ["topic_id": String(topicId), "sort_dir": "asc"]) { value in
            let body = normaliseGroupsIoBody(value.firstString("body", "html_body", "text", "snippet"))
            return GroupsIoMessage(apiId: value.firstOptionalInt("id", "message_id"), groupId: groupId, topicId: topicId,
                number: try value.requiredInt("msg_num", "message_number", "num"), replyToNumber: value.firstOptionalInt("reply_to", "reply_to_msg_num"),
                subject: value.firstString("subject"), author: value.firstString("name", "author_name", "sender_name", "from_name").nonEmpty ?? "Unknown author",
                created: value.firstDate("created", "date") ?? Date(), body: body, moderated: value["moderated"] as? Bool ?? false,
                deleted: value["deleted"] as? Bool ?? false, hasAttachments: value["has_attachments"] as? Bool ?? !(value["attachments"] as? [Any] ?? []).isEmpty)
        }
    }

    private func page<T>(path: String, key: String, pageToken: String?, extra: [String: String] = [:], map: ([String: Any]) throws -> T) async throws -> GroupsIoPage<T> {
        var components = URLComponents(url: base.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        var query = [URLQueryItem(name: "limit", value: "50")]
        query += extra.map { URLQueryItem(name: $0.key, value: $0.value) }
        if let pageToken { query.append(URLQueryItem(name: "page_token", value: pageToken)) }
        components.queryItems = query
        var request = URLRequest(url: components.url!, timeoutInterval: 25)
        request.httpMethod = "GET"; request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw GroupsIoError.server("Invalid Groups.io response") }
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            if !(200..<300).contains(http.statusCode) { throw GroupsIoError.http(status: http.statusCode, type: json?["type"] as? String ?? "") }
            guard let json, json["object"] as? String != "error", let data = json["data"] as? [[String: Any]] else {
                if json?["object"] as? String == "error" { throw GroupsIoError.http(status: http.statusCode, type: json?["type"] as? String ?? "") }
                throw GroupsIoError.compatibility
            }
            let values = try data.map(map)
            let hasMore = json["has_more"] as? Bool ?? false
            let next = json["next_page_token"].map { String(describing: $0) }.flatMap { ($0 == "0" || $0 == "<null>" || $0.isEmpty) ? nil : $0 }
            if hasMore && next == nil { throw GroupsIoError.compatibility }
            return GroupsIoPage(values: values, nextPageToken: next, hasMore: hasMore)
        } catch is CancellationError { throw GroupsIoError.cancelled }
        catch let error as GroupsIoError { throw error }
        catch let error as URLError where error.code == .notConnectedToInternet || error.code == .cannotFindHost { throw GroupsIoError.network }
        catch let error as URLError where error.code == .timedOut { throw GroupsIoError.temporary }
        catch { throw GroupsIoError.server("Groups.io request failed") }
    }
}

private enum GroupsIoError: LocalizedError {
    case network, credential, permission, rateLimited, temporary, compatibility, cancelled, storage(String), server(String)
    static func http(status: Int, type: String) -> GroupsIoError {
        if status == 401 || type == "unauthorized_error" { return .credential }
        if status == 403 || type == "inadequate_permissions" { return .permission }
        if status == 429 { return .rateLimited }
        if status >= 500 { return .temporary }
        return .server("Groups.io request failed (HTTP \(status))")
    }
    var category: String {
        switch self { case .network: "network"; case .credential: "credential"; case .permission: "permission"; case .rateLimited: "rate_limited"; case .temporary: "temporary"; case .compatibility: "compatibility"; case .cancelled: "cancelled"; case .storage: "storage"; case .server: "server" }
    }
    var errorDescription: String? {
        switch self {
        case .network: "No network connection"; case .credential: "API key is invalid or revoked"; case .permission: "Groups.io permission is inadequate"
        case .rateLimited: "Groups.io rate limit reached; try later"; case .temporary: "Groups.io is temporarily unavailable"
        case .compatibility: "Groups.io response format changed"; case .cancelled: "Operation cancelled"
        case .storage(let text), .server(let text): text
        }
    }
}

private extension Dictionary where Key == String, Value == Any {
    func firstString(_ names: String...) -> String { names.compactMap { self[$0] as? String }.first(where: { !$0.isEmpty }) ?? "" }
    func requiredString(_ names: String...) throws -> String { let value = names.compactMap { self[$0] as? String }.first(where: { !$0.isEmpty }) ?? ""; guard !value.isEmpty else { throw GroupsIoError.compatibility }; return value }
    func requiredInt(_ names: String...) throws -> Int64 { guard let value = names.lazy.compactMap({ (self[$0] as? NSNumber)?.int64Value ?? Int64(self[$0] as? String ?? "") }).first else { throw GroupsIoError.compatibility }; return value }
    func firstOptionalInt(_ names: String...) -> Int64? { names.lazy.compactMap { (self[$0] as? NSNumber)?.int64Value ?? Int64(self[$0] as? String ?? "") }.first }
    func firstInt(_ names: String...) -> Int { Int(names.lazy.compactMap { (self[$0] as? NSNumber)?.int64Value ?? Int64(self[$0] as? String ?? "") }.first ?? 0) }
    func firstDate(_ names: String...) -> Date? { names.lazy.compactMap { name in (self[name] as? String).flatMap { ISO8601DateFormatter().date(from: $0) } }.first }
}

private extension String { var nonEmpty: String? { isEmpty ? nil : self } }
private func normaliseGroupsIoBody(_ source: String) -> String {
    source.replacingOccurrences(of: "(?is)<(script|style|iframe|form)[^>]*>.*?</\\1>", with: "", options: .regularExpression)
        .replacingOccurrences(of: "(?i)<br\\s*/?>|</p>|</div>|</blockquote>", with: "\n", options: .regularExpression)
        .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        .replacingOccurrences(of: "&nbsp;", with: " ").replacingOccurrences(of: "&amp;", with: "&")
        .replacingOccurrences(of: "&lt;", with: "<").replacingOccurrences(of: "&gt;", with: ">")
        .replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&#39;", with: "'")
        .replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
}

@MainActor
final class GroupsIoController: ObservableObject {
    @Published private(set) var enabled: Bool
    @Published private(set) var connected: Bool
    @Published private(set) var busy = false
    @Published private(set) var status: String
    @Published private(set) var lastSync: Date?
    @Published private(set) var groups: [GroupsIoGroup] = []
    @Published private(set) var topics: [GroupsIoTopic] = []
    @Published private(set) var messages: [GroupsIoMessage] = []
    @Published private(set) var searchResults: [GroupsIoSearchHit] = []
    @Published private(set) var selectedGroupId: Int64?
    @Published private(set) var selectedTopicId: Int64?
    @Published private(set) var storageBytes: Int64 = 0
    @Published private(set) var topicsHaveMore = false
    @Published private(set) var messagesHaveMore = false
    private let defaults = UserDefaults.standard
    private let api = GroupsIoLiveApi()
    private var database: GroupsIoDatabase?
    private var operation: Task<Void, Never>?
    private var topicNextToken: String?
    private var messageNextToken: String?
    private let keyAccount = "groupsIoApiKey"

    init() {
        let hasCredential = !KeychainValue.load(keyAccount).isEmpty
        enabled = defaults.bool(forKey: "groupsIoEnabled")
        connected = hasCredential
        lastSync = defaults.object(forKey: "groupsIoLastSync") as? Date
        status = hasCredential ? "Connected · cached content available" : "Not connected"
    }

    func updateEnabled(_ value: Bool) {
        enabled = value; defaults.set(value, forKey: "groupsIoEnabled")
        if value { loadCachedGroups() } else { operation?.cancel(); busy = false; status = "Groups.io disabled · downloaded data preserved" }
    }

    func loadCachedGroups() {
        guard enabled else { return }
        do { let store = try db(); groups = try store.groups(); storageBytes = store.storageBytes }
        catch { status = error.localizedDescription }
    }

    func connectAndVerify(_ candidate: String) {
        replaceOperation { [weak self] in
            guard let self else { return }
            let key = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !key.isEmpty else { throw GroupsIoError.credential }
            var page = try await api.groups(key: key); var all = page.values
            while page.hasMore { try Task.checkCancellation(); page = try await api.groups(key: key, pageToken: page.nextPageToken); all += page.values }
            let now = Date(); try db().applyMemberships(all, completed: true, at: now)
            KeychainValue.save(key, account: keyAccount); defaults.set(now, forKey: "groupsIoLastSync")
            let store = try db(); connected = true; groups = try store.groups(); lastSync = now; storageBytes = store.storageBytes; status = "Connected · memberships synced"
        }
    }

    func syncMemberships() {
        replaceOperation { [weak self] in
            guard let self else { return }
            let key = KeychainValue.load(keyAccount); guard !key.isEmpty else { throw GroupsIoError.credential }
            var page = try await api.groups(key: key); var all = page.values
            while page.hasMore { try Task.checkCancellation(); page = try await api.groups(key: key, pageToken: page.nextPageToken); all += page.values }
            let now = Date(); try db().applyMemberships(all, completed: true, at: now); defaults.set(now, forKey: "groupsIoLastSync")
            let store = try db(); groups = try store.groups(); lastSync = now; storageBytes = store.storageBytes; status = "Memberships synced"
        }
    }

    func selectGroup(_ id: Int64) {
        selectedGroupId = id; selectedTopicId = nil; messages = []; topicsHaveMore = false; topicNextToken = nil; operation?.cancel()
        operation = Task {
            do {
                topics = try db().topics(groupId: id); status = topics.isEmpty ? "No downloaded topics" : "Showing downloaded topics"
                guard connected else { return }
                let page = try await api.topics(key: KeychainValue.load(keyAccount), groupId: id)
                try db().applyTopics(groupId: id, topics: page.values, next: page.nextPageToken, hasMore: page.hasMore, at: Date())
                let store = try db(); topics = try store.topics(groupId: id); topicNextToken = page.nextPageToken; topicsHaveMore = page.hasMore; storageBytes = store.storageBytes; status = "Newest topics synced"
            } catch { preserveFailure(scope: "topics", scopeId: String(id), error: error) }
        }
    }

    func selectTopic(_ id: Int64) {
        guard let groupId = selectedGroupId else { return }
        selectedTopicId = id; messagesHaveMore = false; messageNextToken = nil; operation?.cancel()
        operation = Task {
            do {
                messages = try db().messages(topicId: id); status = messages.isEmpty ? "No downloaded messages" : "Showing downloaded messages"
                guard connected else { return }
                let page = try await api.messages(key: KeychainValue.load(keyAccount), groupId: groupId, topicId: id)
                try db().applyMessages(groupId: groupId, topicId: id, messages: page.values, next: page.nextPageToken, hasMore: page.hasMore, at: Date())
                let store = try db(); messages = try store.messages(topicId: id); messageNextToken = page.nextPageToken; messagesHaveMore = page.hasMore; storageBytes = store.storageBytes; status = "Thread synced"
            } catch { preserveFailure(scope: "messages", scopeId: String(id), error: error) }
        }
    }

    func loadOlderTopics() {
        guard let groupId = selectedGroupId, let token = topicNextToken else { return }
        replaceOperation { [weak self] in
            guard let self else { return }
            let page = try await api.topics(key: KeychainValue.load(keyAccount), groupId: groupId, pageToken: token)
            let store = try db(); try store.applyTopics(groupId: groupId, topics: page.values, next: page.nextPageToken, hasMore: page.hasMore, at: Date())
            topics = try store.topics(groupId: groupId, limit: 100); topicNextToken = page.nextPageToken; topicsHaveMore = page.hasMore; storageBytes = store.storageBytes; status = "Older topic page downloaded"
        }
    }

    func loadMoreMessages() {
        guard let groupId = selectedGroupId, let topicId = selectedTopicId, let token = messageNextToken else { return }
        replaceOperation { [weak self] in
            guard let self else { return }
            let page = try await api.messages(key: KeychainValue.load(keyAccount), groupId: groupId, topicId: topicId, pageToken: token)
            let store = try db(); try store.applyMessages(groupId: groupId, topicId: topicId, messages: page.values, next: page.nextPageToken, hasMore: page.hasMore, at: Date())
            messages = try store.messages(topicId: topicId, limit: 100); messageNextToken = page.nextPageToken; messagesHaveMore = page.hasMore; storageBytes = store.storageBytes; status = "Additional message page downloaded"
        }
    }

    func search(_ text: String, currentGroupOnly: Bool = false) {
        operation?.cancel(); operation = Task {
            try? await Task.sleep(for: .milliseconds(250)); guard !Task.isCancelled else { return }
            do { searchResults = try db().search(text, groupId: currentGroupOnly ? selectedGroupId : nil); status = "Searching downloaded content · \(searchResults.count) results" }
            catch { status = error.localizedDescription }
        }
    }

    func openSearchResult(_ hit: GroupsIoSearchHit) {
        selectedGroupId = hit.groupId; selectedTopicId = hit.topicId
        do { topics = try db().topics(groupId: hit.groupId); messages = try db().messages(topicId: hit.topicId); searchResults = [] }
        catch { status = error.localizedDescription }
    }

    func disconnect() {
        operation?.cancel(); deleteKeychainValue(account: keyAccount); connected = false; busy = false
        status = "Disconnected · downloaded data preserved"
    }

    func deleteDownloadedData() {
        operation?.cancel(); database?.close(); database = nil
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let directory = support.appendingPathComponent("RigWeave/GroupsIO", isDirectory: true)
        for suffix in ["", "-wal", "-shm"] { try? FileManager.default.removeItem(at: directory.appendingPathComponent(groupsIoDatabaseFilename + suffix)) }
        let attachments = directory.appendingPathComponent("attachments", isDirectory: true)
        let staging = directory.appendingPathComponent("import-staging", isDirectory: true)
        try? FileManager.default.removeItem(at: attachments); try? FileManager.default.removeItem(at: staging)
        groups = []; topics = []; messages = []; searchResults = []; selectedGroupId = nil; selectedTopicId = nil; storageBytes = 0
        status = "Downloaded Groups.io data deleted · credential preserved"
    }

    private func db() throws -> GroupsIoDatabase { if let database { return database }; let value = try GroupsIoDatabase(); database = value; return value }

    private func replaceOperation(_ work: @escaping @MainActor () async throws -> Void) {
        operation?.cancel(); operation = Task {
            busy = true; status = "Contacting Groups.io…"
            do { try await work() } catch { preserveFailure(scope: "memberships", scopeId: "", error: error) }
            busy = false
        }
    }

    private func preserveFailure(scope: String, scopeId: String, error: Error) {
        let typed = error as? GroupsIoError
        try? database?.recordFailure(scope: scope, scopeId: scopeId, category: typed?.category ?? "server", text: error.localizedDescription)
        status = "\(error.localizedDescription) · downloaded content preserved"
    }

    private func deleteKeychainValue(account: String) {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "app.rigweave.mobile", kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
    }
}

struct GroupsIoView: View {
    @EnvironmentObject private var controller: GroupsIoController
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var search = ""

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading) { Text("Groups.io").font(.largeTitle.bold()); Text(controller.status).font(.caption).foregroundStyle(.secondary) }
                Spacer(); Button("Sync Now") { controller.syncMemberships() }.disabled(!controller.connected || controller.busy)
            }
            TextField("Search downloaded content", text: $search).textFieldStyle(.roundedBorder)
                .onChange(of: search) { _, value in controller.search(value) }
            if !controller.connected { Text("Connect an API key in Settings → Integrations. Downloaded content remains available offline.").foregroundStyle(.secondary) }
            if !controller.searchResults.isEmpty { searchList }
            else if sizeClass == .regular { HStack(spacing: 0) { groupList.frame(minWidth: 240, idealWidth: 290); Divider(); topicList.frame(minWidth: 280, idealWidth: 330); Divider(); messageList.frame(minWidth: 420) } }
            else if controller.selectedTopicId != nil { messageList }
            else if controller.selectedGroupId != nil { topicList }
            else { groupList }
        }
        .padding().navigationTitle("Groups.io").onAppear { controller.loadCachedGroups() }
    }

    private var groupList: some View { List(controller.groups) { group in
        Button { controller.selectGroup(group.id) } label: { VStack(alignment: .leading) { Text(group.title); Text(group.active ? group.name : "\(group.name) · cached/inactive").font(.caption).foregroundStyle(.secondary) } }
    }.overlay { if controller.groups.isEmpty { ContentUnavailableView("No downloaded memberships", systemImage: "person.3", description: Text("Connect and sync, or reconnect when online.")) } } }

    private var topicList: some View { List {
        ForEach(controller.topics) { topic in
            Button { controller.selectTopic(topic.id) } label: { VStack(alignment: .leading) { Text(topic.subject); Text("\(topic.messageCount) messages\(topic.closed ? " · closed" : "")").font(.caption).foregroundStyle(.secondary) } }
        }
        if controller.topicsHaveMore { Button("Load Older Topics") { controller.loadOlderTopics() }.disabled(controller.busy) }
    }
    .overlay { if controller.selectedGroupId == nil { ContentUnavailableView("Select a group", systemImage: "text.bubble") } } }

    private var messageList: some View { List {
        ForEach(controller.messages) { message in
            VStack(alignment: .leading, spacing: 8) {
                HStack { Text(message.author).bold(); Spacer(); Text("#\(message.number)").font(.caption).foregroundStyle(.secondary) }
                if !message.subject.isEmpty { Text(message.subject).foregroundStyle(RigTheme.amber) }
                Text(message.deleted ? "Message unavailable or deleted" : message.body).textSelection(.enabled)
                if message.hasAttachments { Label("Attachments present · not downloaded in Phase 1", systemImage: "paperclip").font(.caption).foregroundStyle(.yellow) }
            }.padding(.vertical, 6)
        }
        if controller.messagesHaveMore { Button("Load More Messages") { controller.loadMoreMessages() }.disabled(controller.busy) }
    }
    .overlay { if controller.selectedTopicId == nil { ContentUnavailableView("Select a topic", systemImage: "text.bubble.fill") } } }

    private var searchList: some View { List(controller.searchResults) { hit in
        Button { search = ""; controller.openSearchResult(hit) } label: { VStack(alignment: .leading) { Text(hit.topicSubject); Text("\(hit.groupName) · \(hit.author)").font(.caption).foregroundStyle(.secondary); Text(hit.snippet).lineLimit(3) } }
    } }
}

struct GroupsIoSettingsSection: View {
    @EnvironmentObject private var controller: GroupsIoController
    @Environment(\.openURL) private var openURL
    @State private var candidate = ""
    @State private var confirmDelete = false

    var body: some View {
        Section("Groups.io") {
            Toggle("Groups.io enabled", isOn: Binding(get: { controller.enabled }, set: controller.updateEnabled))
            Text("Disabled by default. When disabled, no Groups.io network or startup sync occurs; downloaded data and the credential remain intact.").font(.caption).foregroundStyle(.secondary)
            Text("Use a Groups.io API key, not your password. The key carries your account permissions; revoke it at Groups.io if compromised.").font(.caption)
            SecureField("Groups.io API key", text: $candidate).disabled(!controller.enabled || controller.connected)
            Button("Get an API key in Groups.io") { openURL(URL(string: "https://groups.io/settings/apikeys")!) }
            HStack {
                Button("Connect and Verify") { controller.connectAndVerify(candidate); candidate = "" }.disabled(!controller.enabled || controller.connected || candidate.isEmpty || controller.busy)
                Button("Sync Now") { controller.syncMemberships() }.disabled(!controller.enabled || !controller.connected || controller.busy)
            }
            LabeledContent("State", value: controller.connected ? "Connected" : "Disconnected")
            if let date = controller.lastSync { LabeledContent("Last membership sync", value: date.formatted()) }
            LabeledContent("Downloaded storage", value: ByteCountFormatter.string(fromByteCount: controller.storageBytes, countStyle: .file))
            Text(controller.status).font(.caption).foregroundStyle(controller.connected ? RigTheme.green : .secondary)
            if controller.enabled { NavigationLink("Open Groups.io") { GroupsIoView() } }
            Button("Disconnect Groups.io", role: .destructive) { controller.disconnect() }.disabled(!controller.connected)
            Button("Delete Downloaded Groups.io Data", role: .destructive) { confirmDelete = true }
        }
        .confirmationDialog("Delete downloaded Groups.io data?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete Downloaded Data", role: .destructive) { controller.deleteDownloadedData() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Only the separate Groups.io database and feature-owned files are deleted. The API key remains stored.") }
    }
}

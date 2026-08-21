import Foundation

@main
struct FastEntryGoldenTests {
    static func main() throws {
        let fixtureURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("fixtures/wavelog/fast_entry_golden.json")
        let root = try JSONSerialization.jsonObject(with: Data(contentsOf: fixtureURL)) as! [String: Any]
        let fixtures = root["cases"] as! [[String: Any]]
        let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX"); formatter.timeZone = .gmt
        formatter.dateFormat = "yyyy-MM-dd"
        let iso = ISO8601DateFormatter()
        for fixture in fixtures {
            let name = fixture["name"] as! String
            let result = AppleFastEntryParser.parse(fixture["input"] as! String,
                defaults: .init(date: formatter.date(from: fixture["date"] as! String)!))
            let expectedRows = fixture["rows"] as! [[String: String]], expectedErrors = fixture["errors"] as! [String]
            require(result.rows.count == expectedRows.count, "\(name): expected \(expectedRows.count) rows, got \(result.rows.count); \(result.issues)")
            require(result.errors.map(\.message) == expectedErrors, "\(name): errors differ: \(result.errors)")
            for (row, expected) in zip(result.rows, expectedRows) {
                require(row.qso.callsign == expected["call"], "\(name): callsign")
                require(row.qso.band == expected["band"], "\(name): band")
                require(row.qso.mode == expected["mode"], "\(name): mode")
                require(row.qso.submode == expected["submode"], "\(name): submode")
                require(row.qso.rstSent == expected["rst_sent"], "\(name): RST")
                require(iso.string(from: row.qso.createdAt) == expected["utc"], "\(name): UTC")
                if let value = expected["pota"] { require(row.qso.fields["POTA_REF"] == value, "\(name): POTA") }
                if let value = expected["wwff"] { require(row.qso.fields["WWFF_REF"] == value, "\(name): WWFF") }
                if let value = expected["contest"] { require(row.qso.fields["CONTEST_ID"] == value, "\(name): contest") }
                if let value = expected["satellite"] { require(row.qso.fields["SAT_NAME"] == value, "\(name): satellite") }
                if let value = expected["comment"] { require(row.qso.fields["COMMENT"] == value, "\(name): comment") }
            }
        }
        print("Apple Fast Entry golden corpus: \(fixtures.count) cases passed")
    }

    private static func require(_ condition: @autoclosure () -> Bool, _ message: String) {
        if !condition() { fatalError(message) }
    }
}

import SwiftUI

enum Destination: String, CaseIterable, Identifiable {
    case home = "Home", radio = "Radio", log = "Log", panadapter = "Panadapter", settings = "Settings"
    var id: String { rawValue }
    var icon: String {
        switch self { case .home: "house"; case .radio: "antenna.radiowaves.left.and.right"; case .log: "list.clipboard"; case .panadapter: "waveform.path.ecg.rectangle"; case .settings: "gearshape" }
    }
}

struct ContentView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var selection: Destination = .home

    var body: some View {
        Group {
            if sizeClass == .compact {
                TabView(selection: $selection) {
                    ForEach(Destination.allCases) { destination in
                        NavigationStack { destinationView(destination) }
                            .tabItem { Label(destination.rawValue, systemImage: destination.icon) }
                            .tag(destination)
                    }
                }
            } else {
                NavigationSplitView {
                    List {
                        ForEach(Destination.allCases) { destination in
                            Button { selection = destination } label: {
                                Label(destination.rawValue, systemImage: destination.icon)
                                    .foregroundStyle(selection == destination ? RigTheme.amber : .primary)
                            }
                        }
                    }
                    .navigationTitle("RigWeave")
                } detail: {
                    destinationView(selection)
                }
            }
        }
        .tint(RigTheme.amber)
    }

    @ViewBuilder private func destinationView(_ destination: Destination) -> some View {
        switch destination {
        case .home: HomeView()
        case .radio: RadioView()
        case .log: LogView()
        case .panadapter: PanadapterView()
        case .settings: SettingsView()
        }
    }
}

enum RigTheme {
    static let amber = Color(red: 0.96, green: 0.61, blue: 0.16)
    static let panel = Color(red: 0.10, green: 0.12, blue: 0.14)
    static let green = Color(red: 0.27, green: 0.83, blue: 0.52)
}

struct BrandHeader: View {
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("RIGWEAVE").font(.title.bold()).tracking(2).foregroundStyle(RigTheme.amber)
            Spacer()
            Text("Radio. Spectrum. Spots. Logs.").font(.caption).foregroundStyle(.secondary)
        }
    }
}

struct StatusBadge: View {
    let status: String
    var body: some View {
        Text(status).font(.caption.bold()).padding(.horizontal, 10).padding(.vertical, 6)
            .background(status == "LIVE" ? RigTheme.green.opacity(0.2) : Color.red.opacity(0.2))
            .foregroundStyle(status == "LIVE" ? RigTheme.green : .red).clipShape(Capsule())
    }
}

struct RadioSummary: View {
    @EnvironmentObject private var radio: RadioModel
    var body: some View {
        let state = radio.snapshot
        VStack(alignment: .leading, spacing: 18) {
            HStack { StatusBadge(status: state.status); Spacer(); Text(state.model).font(.headline) }
            Text(state.connected ? state.frequencyText : "—.——— MHz").font(.system(size: 54, weight: .semibold, design: .monospaced)).minimumScaleFactor(0.55)
            HStack(spacing: 24) {
                Label(state.mode, systemImage: "waveform")
                Label(state.transmitting ? "TX" : "RX", systemImage: state.transmitting ? "dot.radiowaves.left.and.right" : "arrow.down.circle")
                Label(state.identity, systemImage: "cpu")
            }.foregroundStyle(.secondary)
            ProgressView(value: Double(state.meter), total: 100).tint(RigTheme.amber)
            Text("Signal / status \(state.meter)%").font(.caption).foregroundStyle(.secondary)
        }
        .padding(24).background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct HomeView: View {
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 22) {
            BrandHeader(); RadioSummary()
            Text("Waiting for a real read-only radio connection. This build contains no demo radio state and no transmit path.")
                .foregroundStyle(.secondary)
        }.padding() }.navigationTitle("Home")
    }
}

struct RadioView: View {
    @EnvironmentObject private var radio: RadioModel
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 22) {
            BrandHeader(); RadioSummary()
            GroupBox("Connection") {
                LabeledContent("Transport", value: "No entitled Apple USB transport")
                LabeledContent("Radio", value: radio.snapshot.model)
                LabeledContent("Safety", value: "Read-only milestone")
            }
        }.padding() }.navigationTitle("Radio")
    }
}

struct LogView: View {
    @EnvironmentObject private var radio: RadioModel
    @EnvironmentObject private var logbook: QSOStore
    @State private var callsign = ""
    @State private var rstSent = "59"
    @State private var rstReceived = "59"
    @State private var frequencyMHz = ""
    @State private var mode = ""

    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 18) {
            BrandHeader()
            GroupBox("New local QSO") {
                TextField("Callsign", text: $callsign).textInputAutocapitalization(.characters).autocorrectionDisabled()
                HStack { TextField("RST sent", text: $rstSent); TextField("RST received", text: $rstReceived) }
                TextField("Frequency MHz", text: $frequencyMHz).keyboardType(.decimalPad)
                TextField("Mode", text: $mode).textInputAutocapitalization(.characters).autocorrectionDisabled()
                Button("Save QSO") { save() }.buttonStyle(.borderedProminent).disabled(callsign.trimmingCharacters(in: .whitespaces).isEmpty)
                if !logbook.message.isEmpty { Text(logbook.message).font(.caption).foregroundStyle(.secondary) }
            }
            Text("Recent QSOs").font(.headline)
            if logbook.records.isEmpty { ContentUnavailableView("No QSOs yet", systemImage: "list.clipboard") }
            ForEach(logbook.records) { qso in
                VStack(alignment: .leading, spacing: 5) {
                    HStack { Text(qso.callsign).font(.headline); Spacer(); Text(qso.mode) }
                    Text(String(format: "%.3f MHz", Double(qso.frequencyHz) / 1_000_000)).foregroundStyle(.secondary)
                    ShareLink(item: radio.adif(for: qso), preview: SharePreview("\(qso.callsign).adi")) { Label("Export ADIF", systemImage: "square.and.arrow.up") }.font(.caption)
                }.padding().background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }.padding() }.navigationTitle("Log")
    }

    private func save() {
        let now = Date(); let clean = callsign.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let enteredHz = UInt64((Double(frequencyMHz) ?? 0) * 1_000_000)
        let actualHz = radio.snapshot.connected ? radio.snapshot.frequencyHz : enteredHz
        let actualMode = radio.snapshot.connected ? radio.snapshot.mode : mode.uppercased()
        guard actualHz > 0, !actualMode.isEmpty else { return }
        let qso = QSO(id: radio.qsoIdentity(callsign: clean, at: now, frequencyHz: actualHz, mode: actualMode), callsign: clean,
                      frequencyHz: actualHz, mode: actualMode,
                      rstSent: rstSent, rstReceived: rstReceived, createdAt: now)
        if logbook.save(qso) { callsign = "" }
    }
}

struct PanadapterView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            BrandHeader(); StatusBadge(status: "OFFLINE")
            ContentUnavailableView("No physical audio source", systemImage: "waveform.slash",
                description: Text("Spectrum remains blank until a real USB audio route supplies samples. No generated waveform is shown."))
                .frame(maxWidth: .infinity, minHeight: 360).background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 16))
            Spacer()
        }.padding().navigationTitle("Panadapter")
    }
}

struct SettingsView: View {
    @EnvironmentObject private var radio: RadioModel
    var body: some View {
        Form {
            Section("Radio profile") {
                LabeledContent("Radio", value: radio.snapshot.connected ? radio.snapshot.model : "Awaiting ID response")
                LabeledContent("Mode", value: "Real hardware only")
            }
            Section("Physical Apple hardware") {
                LabeledContent("PL2303 / KXUSB", value: "Blocked: 0 USBDriverKit-entitled profiles")
                Text("No CAT command is sent by this build.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Software") { LabeledContent("Shared core", value: radio.coreVersion) }
        }.navigationTitle("Settings")
    }
}

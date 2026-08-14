import SwiftUI

enum Destination: String, CaseIterable, Identifiable {
    case home = "Home", radio = "Radio", spots = "Spots", dx = "DX", log = "Log"
    case panadapter = "Panadapter", digital = "Digital", lookup = "Lookup", settings = "Settings"
    var id: String { rawValue }
    var icon: String {
        switch self { case .home: "house"; case .radio: "antenna.radiowaves.left.and.right"; case .spots: "dot.radiowaves.up.forward"; case .dx: "globe.americas"; case .log: "list.clipboard"; case .panadapter: "waveform.path.ecg.rectangle"; case .digital: "waveform.badge.magnifyingglass"; case .lookup: "person.text.rectangle"; case .settings: "gearshape" }
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
        case .spots: SpotsView()
        case .dx: DXView()
        case .log: LogView()
        case .panadapter: PanadapterView()
        case .digital: DigitalView()
        case .lookup: LookupView()
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
            Text("Waiting for a real KX3 connection. This build contains no demo or simulated radio state.")
                .foregroundStyle(.secondary)
        }.padding() }.navigationTitle("Home")
    }
}

struct RadioView: View {
    @EnvironmentObject private var radio: RadioModel
    @State private var frequencyMHz = ""
    @State private var modeCode = "2"
    @State private var rawCAT = ""
    @State private var cwText = ""
    @State private var power = ""
    @State private var filterHz = ""
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 22) {
            BrandHeader(); KX3ControlDeck(state: radio.snapshot, send: radio.sendCAT)
            GroupBox("Connection") {
                VStack(alignment: .leading, spacing: 12) {
                    Picker("Serial endpoint", selection: $radio.selectedPort) {
                        if radio.serialPorts.isEmpty { Text("No serial endpoints").tag("") }
                        ForEach(radio.serialPorts, id: \.self) { Text(($0 as NSString).lastPathComponent).tag($0) }
                    }
                    HStack {
                        Button("Scan serial devices") { radio.refreshPorts() }
                        Button("Connect KX3") { radio.connect() }.buttonStyle(.borderedProminent).disabled(radio.selectedPort.isEmpty)
                        Button("Disconnect") { radio.disconnect() }.buttonStyle(.bordered)
                    }
                    LabeledContent("Transport", value: radio.transportStatus)
                    LabeledContent("Radio", value: radio.snapshot.model)
                    LabeledContent("CAT polling", value: "ID · FA · FB · MD · IF · TQ · meters · gains · flags")
                }
            }
            GroupBox("CAT control") {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        TextField("Frequency MHz", text: $frequencyMHz).keyboardType(.decimalPad)
                        Button("Set VFO A") { radio.setFrequencyMHz(frequencyMHz) }
                    }
                    HStack {
                        Picker("Mode", selection: $modeCode) {
                            Text("LSB").tag("1"); Text("USB").tag("2"); Text("CW").tag("3")
                            Text("FM").tag("4"); Text("AM").tag("5"); Text("DATA").tag("6")
                            Text("CW-REV").tag("7"); Text("DATA-REV").tag("9")
                        }
                        Button("Set Mode") { radio.setMode(code: modeCode) }
                    }
                    HStack {
                        TextField("Raw KX3 CAT command", text: $rawCAT).textInputAutocapitalization(.characters).autocorrectionDisabled()
                        Button("Send") { radio.sendCAT(rawCAT); rawCAT = "" }.disabled(rawCAT.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            GroupBox("Power, filter and keyer") {
                VStack(alignment: .leading, spacing: 10) {
                    HStack { TextField("PC value", text: $power).keyboardType(.numberPad); Button("Set power") { radio.sendCAT("PC\(power);") } }
                    HStack { TextField("Filter Hz", text: $filterHz).keyboardType(.numberPad); Button("Set filter") { radio.sendCAT("BW\(filterHz);") } }
                    HStack { TextField("CW keyer text", text: $cwText).textInputAutocapitalization(.characters).autocorrectionDisabled(); Button("Send KY") { radio.sendCAT("KY\(cwText);"); cwText = "" } }
                    Text("Values and switch commands are sent directly to the connected radio; the app does not clamp them.").font(.caption).foregroundStyle(.secondary)
                }
            }
        }.padding() }.navigationTitle("Radio").task { radio.refreshPorts() }
    }
}

private struct KX3ControlDeck: View {
    let state: RadioSnapshot
    let send: (String) -> Void
    private let controls = [
        ("RIT", "SWT18;"), ("XIT", "SWT26;"), ("A/B", "SWT24;"), ("A→B", "SWT25;"),
        ("SPLIT", "SWH25;"), ("RATE", "SWT12;"), ("MODE", "SWT14;"), ("DATA", "SWT17;"),
        ("PBT I/II", "SWT33;"), ("CLR", "SWH35;"), ("ATU", "SWT44;"), ("ANT", "SWH44;"),
        ("VFO +", "UP;"), ("VFO −", "DN;"), ("CWT", "SWT28;")
    ]

    var body: some View {
        VStack(spacing: 14) {
            VStack(spacing: 10) {
                HStack(alignment: .top, spacing: 16) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("VFO A").font(.caption.weight(.bold)).foregroundStyle(RigTheme.amber)
                        Text(state.connected ? state.frequencyText : "—.——— MHz")
                            .font(.system(.largeTitle, design: .monospaced).weight(.semibold)).minimumScaleFactor(0.55)
                        Text(state.frequencyBHz > 0 ? String(format: "VFO B  %.3f MHz", Double(state.frequencyBHz) / 1_000_000) : "VFO B  —.——— MHz")
                            .font(.system(.subheadline, design: .monospaced)).foregroundStyle(state.split ? Color.red : .secondary)
                    }
                    Spacer(minLength: 4)
                    VStack(spacing: 5) {
                        Text("CWT").font(.caption.bold())
                        HStack(alignment: .bottom, spacing: 3) {
                            ForEach(0..<7, id: \.self) { index in
                                Capsule().fill(index == 3 ? RigTheme.amber : Color.secondary.opacity(0.45))
                                    .frame(width: 5, height: CGFloat(8 + (3 - abs(3 - index)) * 4))
                            }
                        }.accessibilityHidden(true)
                    }.frame(minWidth: 88).padding(10).background(Color.black.opacity(0.28)).clipShape(RoundedRectangle(cornerRadius: 8))
                    StatusBadge(status: state.status)
                }
                HStack(spacing: 12) {
                    Text(state.transmitting ? "TX" : "RX").foregroundStyle(state.transmitting ? .red : RigTheme.green).fontWeight(.bold)
                    Text(state.mode).font(.system(.body, design: .monospaced).bold())
                    if state.split { flag("SPLIT", active: true) }
                    flag("RIT", active: state.rit); flag("XIT", active: state.xit)
                    flag("PRE", active: state.preamp); flag("ATT", active: state.attenuator)
                    Spacer(); Text("BW \(state.bandwidthHz) Hz · PWR \(state.powerW) W").font(.caption).foregroundStyle(.secondary)
                }
                KX3Meter(label: state.transmitting ? "RF OUTPUT" : "S-METER", value: state.transmitting ? Double(max(0, state.rfOutputTenths)) : Double(state.meter),
                    maximum: state.transmitting ? 120 : 30, annotation: state.transmitting && state.swrTenths >= 0 ? String(format: "SWR %.1f:1", Double(state.swrTenths) / 10) : "S 1  3  5  7  9  +10  +20  +30")
                HStack { Text("AF \(state.afGain)"); Spacer(); Text("RF \(state.rfGain)") }.font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
            .padding(18).background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 14))

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 86), spacing: 8)], spacing: 8) {
                ForEach(controls, id: \.0) { control in
                    Button(control.0) { send(control.1) }
                        .buttonStyle(.bordered).frame(minHeight: 44)
                }
                Button(state.preamp ? "PRE OFF" : "PRE ON") { send(state.preamp ? "PA0;" : "PA1;") }.buttonStyle(.bordered).frame(minHeight: 44)
                Button(state.attenuator ? "ATT OFF" : "ATT ON") { send(state.attenuator ? "RA00;" : "RA01;") }.buttonStyle(.bordered).frame(minHeight: 44)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Elecraft KX3 radio control deck")
    }

    private func flag(_ text: String, active: Bool) -> some View {
        Text(text).font(.caption2.bold()).foregroundStyle(active ? RigTheme.amber : .secondary)
    }
}

private struct KX3Meter: View {
    let label: String
    let value: Double
    let maximum: Double
    let annotation: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack { Text(label).font(.caption.bold()); Spacer(); Text(annotation).font(.caption2.monospacedDigit()).foregroundStyle(.secondary) }
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.09))
                    Capsule().fill(RigTheme.amber).frame(width: geometry.size.width * max(0, min(1, value / maximum)))
                }
            }.frame(height: 10)
        }.accessibilityElement(children: .ignore).accessibilityLabel(label).accessibilityValue("\(Int(value)) of \(Int(maximum))")
    }
}

private enum LogScope: String, CaseIterable, Identifiable { case local = "Local ADIF", wavelog = "Wavelog"; var id: String { rawValue } }

struct LogView: View {
    @EnvironmentObject private var radio: RadioModel
    @EnvironmentObject private var logbook: QSOStore
    @EnvironmentObject private var features: FeatureModel
    @State private var callsign = ""
    @State private var rstSent = "59"
    @State private var rstReceived = "59"
    @State private var frequencyMHz = ""
    @State private var mode = ""
    @State private var scope: LogScope = .local
    @State private var search = ""
    @State private var exportURL: URL?

    var body: some View {
        ScrollView { LazyVStack(alignment: .leading, spacing: 18) {
            BrandHeader()
            Picker("Log source", selection: $scope) { ForEach(LogScope.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented)
            if scope == .local { localLog } else { wavelogLog }
        }.padding() }.navigationTitle("Log")
            .searchable(text: $search, prompt: "Callsign, band, mode or country")
    }

    private var localLog: some View {
        Group {
            GroupBox("New local QSO") {
                TextField("Callsign", text: $callsign).textInputAutocapitalization(.characters).autocorrectionDisabled()
                HStack { TextField("RST sent", text: $rstSent); TextField("RST received", text: $rstReceived) }
                TextField("Frequency MHz", text: $frequencyMHz).keyboardType(.decimalPad)
                TextField("Mode", text: $mode).textInputAutocapitalization(.characters).autocorrectionDisabled()
                Button("Save QSO") { save() }.buttonStyle(.borderedProminent).disabled(callsign.trimmingCharacters(in: .whitespaces).isEmpty)
                if !logbook.message.isEmpty { Text(logbook.message).font(.caption).foregroundStyle(.secondary) }
            }
            HStack {
                Text("Local log · \(logbook.records.count) recent").font(.headline)
                Spacer()
                Button("Build ADIF export") { exportURL = logbook.exportADIF(using: radio.adif) }
                if let exportURL { ShareLink(item: exportURL) { Label("Share ADIF", systemImage: "square.and.arrow.up") } }
            }
            if filteredLocal.isEmpty { ContentUnavailableView("No matching local QSOs", systemImage: "list.clipboard") }
            ForEach(filteredLocal) { qso in localRow(qso) }
        }
    }

    private var wavelogLog: some View {
        Group {
            GroupBox("Wavelog station log") {
                HStack { Text(features.wavelog.status).font(.subheadline); Spacer(); if features.wavelog.pendingCount > 0 { Text("\(features.wavelog.pendingCount) queued").foregroundStyle(RigTheme.amber) } }
                HStack {
                    Button("Load stations") { Task { await features.wavelog.loadStations() } }
                    Button("Full log refresh") { Task { await features.wavelog.fullSync() } }.buttonStyle(.borderedProminent)
                }
                if !features.wavelog.stations.isEmpty {
                    Picker("Station", selection: Binding(get: { features.wavelog.stationProfile }, set: features.wavelog.selectStation)) {
                        ForEach(features.wavelog.stations) { Text($0.label).tag($0.id) }
                    }
                }
                LabeledContent("Cached QSOs", value: "\(features.wavelog.contacts.count)")
                LabeledContent("Sync pages", value: "\(features.wavelog.syncPages)")
            }
            if filteredWavelog.isEmpty {
                ContentUnavailableView("No cached Wavelog QSOs", systemImage: "arrow.triangle.2.circlepath",
                    description: Text("Load stations, select one, then run Full log refresh."))
            }
            ForEach(filteredWavelog) { contact in
                VStack(alignment: .leading, spacing: 5) {
                    HStack { Text(contact.callsign).font(.headline); Spacer(); Text([contact.mode, contact.submode].filter { !$0.isEmpty }.joined(separator: " / ")) }
                    Text([contact.band, contact.frequency, contact.country].filter { !$0.isEmpty }.joined(separator: " · ")).foregroundStyle(.secondary)
                    Text("\(contact.date) \(contact.time) · RST \(contact.rstSent)/\(contact.rstReceived)").font(.caption).foregroundStyle(.secondary)
                }.padding().background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private var filteredLocal: [QSO] {
        let term = search.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return term.isEmpty ? logbook.records : logbook.records.filter { $0.callsign.contains(term) || $0.mode.contains(term) }
    }

    private var filteredWavelog: [WavelogContact] {
        let term = search.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return term.isEmpty ? features.wavelog.contacts : features.wavelog.contacts.filter {
            [$0.callsign, $0.band, $0.mode, $0.submode, $0.country].contains { $0.uppercased().contains(term) }
        }
    }

    private func localRow(_ qso: QSO) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack { Text(qso.callsign).font(.headline); Spacer(); Text(qso.mode) }
            Text(String(format: "%.3f MHz", Double(qso.frequencyHz) / 1_000_000)).foregroundStyle(.secondary)
            ShareLink(item: radio.adif(for: qso), preview: SharePreview("\(qso.callsign).adi")) { Label("Share record ADIF", systemImage: "square.and.arrow.up") }.font(.caption)
        }.padding().background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 12))
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
        if logbook.save(qso) {
            features.enqueueWavelog(id: qso.id, adif: radio.adif(for: qso))
            callsign = ""
        }
    }
}

struct PanadapterView: View {
    @EnvironmentObject private var features: FeatureModel
    @EnvironmentObject private var radio: RadioModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                BrandHeader()
                HStack(spacing: 12) {
                    StatusBadge(status: features.spectrumDb.isEmpty ? "OFFLINE" : "LIVE")
                    Text(features.audioStatus).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                    Spacer()
                    if !features.spectrumDb.isEmpty {
                        Text(String(format: "%.0f kHz I/Q", features.audioSampleRate / 1_000))
                            .font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary)
                    }
                }
            if features.spectrumDb.isEmpty {
                ContentUnavailableView("No physical audio source", systemImage: "waveform.slash",
                    description: Text("Connect the stereo I/Q USB interface, then start capture. RigWeave never substitutes generated spectrum data."))
                    .frame(maxWidth: .infinity, minHeight: 430).background(Color(red: 0.025, green: 0.035, blue: 0.05)).clipShape(RoundedRectangle(cornerRadius: 14))
            } else {
                PanadapterInstrument(bins: features.spectrumDb, waterfall: features.waterfallImage,
                    centreHz: radio.snapshot.frequencyHz, sampleRate: features.audioSampleRate,
                    noiseFloor: features.audioNoiseFloor, floorOffset: Float(features.panFloorOffsetDb),
                    rangeDb: Float(features.panRangeDb))
                    .frame(maxWidth: .infinity, minHeight: 500)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                HStack(spacing: 22) {
                    measurement("PEAK", String(format: "%.1f dBFS", features.audioPeak))
                    measurement("FLOOR", String(format: "%.1f dBFS", features.audioNoiseFloor))
                    measurement("SPAN", String(format: "±%.0f kHz", features.audioSampleRate / 2_000))
                    Spacer()
                }.padding(.horizontal, 4)
            }
                GroupBox("Display") {
                    VStack(spacing: 12) {
                        Picker("Waterfall palette", selection: $features.panPalette) {
                            ForEach(PanPalette.allCases) { Text($0.rawValue).tag($0) }
                        }.pickerStyle(.segmented)
                        LabeledContent("Dynamic range", value: "\(Int(features.panRangeDb)) dB")
                        Slider(value: $features.panRangeDb, in: 40...110, step: 2)
                        LabeledContent("Black level", value: String(format: "%+.0f dB from floor", features.panFloorOffsetDb))
                        Slider(value: $features.panFloorOffsetDb, in: -20...10, step: 1)
                        Toggle("Reverse I/Q spectrum", isOn: $features.reverseSpectrum)
                    }
                }
                GroupBox("Physical I/Q input") {
                    VStack(alignment: .leading, spacing: 10) {
                        Picker("Audio input", selection: $features.selectedAudioInputUID) {
                            if features.audioInputs.isEmpty { Text("No audio inputs").tag("") }
                            ForEach(features.audioInputs) { input in
                                Text("\(input.name) · \(input.type)").tag(input.id)
                            }
                        }
                        Button("Scan audio devices") { Task { await features.refreshAudioInputs() } }
                        Text(features.audioStatus).font(.caption).foregroundStyle(.secondary)
                    }
                }
                HStack {
                    Button("Start I/Q capture") { Task { await features.startAudioCapture() } }.buttonStyle(.borderedProminent)
                    Button("Stop capture") { features.stopAudioCapture() }.buttonStyle(.bordered)
                }
            }
            .padding()
        }.navigationTitle("Panadapter").task { await features.refreshAudioInputs() }
    }

    private func measurement(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
            Text(value).font(.system(.callout, design: .monospaced).weight(.medium))
        }
    }
}

struct PanadapterInstrument: View {
    let bins: [Float]
    let waterfall: CGImage?
    let centreHz: UInt64
    let sampleRate: Double
    let noiseFloor: Float
    let floorOffset: Float
    let rangeDb: Float

    var body: some View {
        Canvas(rendersAsynchronously: true) { context, size in
            guard bins.count > 1 else { return }
            let spectrumHeight = size.height * 0.41
            let axisHeight: CGFloat = 30
            let waterfallTop = spectrumHeight + axisHeight
            let waterfallRect = CGRect(x: 0, y: waterfallTop, width: size.width, height: size.height - waterfallTop)
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(red: 0.018, green: 0.025, blue: 0.038)))

            if let waterfall {
                context.draw(Image(decorative: waterfall, scale: 1), in: waterfallRect)
            }

            let floor = noiseFloor + floorOffset
            for step in 0...4 {
                let y = spectrumHeight * CGFloat(step) / 4
                var line = Path(); line.move(to: CGPoint(x: 0, y: y)); line.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(line, with: .color(.white.opacity(step == 4 ? 0.16 : 0.08)), lineWidth: 0.5)
                let db = floor + rangeDb * Float(4 - step) / 4
                context.draw(Text(String(format: "%+.0f", db)).font(.system(size: 10, design: .monospaced)).foregroundStyle(.white.opacity(0.62)), at: CGPoint(x: 22, y: max(8, y - 7)), anchor: .leading)
            }
            for step in 0...8 {
                let x = size.width * CGFloat(step) / 8
                var line = Path(); line.move(to: CGPoint(x: x, y: 0)); line.addLine(to: CGPoint(x: x, y: size.height))
                context.stroke(line, with: .color(.white.opacity(step == 4 ? 0.22 : 0.065)), lineWidth: step == 4 ? 1 : 0.5)
            }

            var path = Path()
            for (index, bin) in bins.enumerated() {
                let x = size.width * CGFloat(index) / CGFloat(bins.count - 1)
                let normalized = max(0, min(1, (bin - floor) / rangeDb))
                let y = spectrumHeight * (1 - CGFloat(normalized))
                if index == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
            }
            var fill = path; fill.addLine(to: CGPoint(x: size.width, y: spectrumHeight)); fill.addLine(to: CGPoint(x: 0, y: spectrumHeight)); fill.closeSubpath()
            context.fill(fill, with: .linearGradient(Gradient(colors: [RigTheme.amber.opacity(0.30), RigTheme.amber.opacity(0.015)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: spectrumHeight)))
            context.stroke(path, with: .color(Color(red: 1.0, green: 0.69, blue: 0.24)), lineWidth: 1.35)

            context.fill(Path(CGRect(x: 0, y: spectrumHeight, width: size.width, height: axisHeight)), with: .color(Color(red: 0.04, green: 0.055, blue: 0.075)))
            let halfSpan = sampleRate / 2
            let frequencies = [Double(centreHz) - halfSpan, Double(centreHz), Double(centreHz) + halfSpan]
            let anchors: [UnitPoint] = [.leading, .center, .trailing]
            let positions: [CGFloat] = [8, size.width / 2, size.width - 8]
            for index in 0..<3 {
                context.draw(Text(String(format: "%.3f", frequencies[index] / 1_000_000)).font(.system(size: 11, weight: index == 1 ? .semibold : .regular, design: .monospaced)).foregroundStyle(index == 1 ? RigTheme.amber : .white.opacity(0.68)), at: CGPoint(x: positions[index], y: spectrumHeight + axisHeight / 2), anchor: anchors[index])
            }
            context.draw(Text("MHz").font(.system(size: 9, weight: .medium)).foregroundStyle(.white.opacity(0.42)), at: CGPoint(x: size.width - 8, y: spectrumHeight + axisHeight - 4), anchor: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Live I Q panadapter and waterfall")
        .accessibilityValue(String(format: "Center %.3f megahertz, peak %.1f decibels full scale", Double(centreHz) / 1_000_000, bins.max() ?? -140))
    }
}

struct SpotsView: View {
    @EnvironmentObject private var features: FeatureModel
    @EnvironmentObject private var radio: RadioModel
    var body: some View {
        List {
            Section {
                HStack { StatusBadge(status: features.clusterStatus.hasPrefix("Connected") ? "LIVE" : "OFFLINE"); Text(features.clusterStatus).font(.caption) }
            }
            Section("Live cluster opportunities") {
                if features.dx.opportunities.isEmpty {
                    ContentUnavailableView("No live spots", systemImage: "dot.radiowaves.up.forward", description: Text("Connect the configured DX cluster. No fixture spots are loaded."))
                }
                ForEach(features.dx.opportunities) { spot in
                    VStack(alignment: .leading, spacing: 5) {
                        HStack { Text(spot.callsign).font(.headline); if spot.watchlisted { Image(systemName: "star.fill").foregroundStyle(RigTheme.amber) }; Spacer(); Text(spot.band) }
                        Text(String(format: "%.3f MHz · %@ · %@", Double(spot.frequencyHz) / 1_000_000, spot.mode, spot.country)).foregroundStyle(.secondary)
                        if !spot.comment.isEmpty { Text(spot.comment).font(.caption) }
                        HStack { Text("via \(spot.spotter)").font(.caption2).foregroundStyle(.secondary); Spacer(); Button("Tune") { radio.sendCAT(String(format: "FA%011llu;", spot.frequencyHz)) } }
                    }.padding(.vertical, 5)
                }
            }
        }.navigationTitle("Spots")
    }
}

private enum DXSurface: String, CaseIterable, Identifiable {
    case live = "LIVE", smart = "SMART", bandmap = "BANDMAP", pulse = "PULSE", world = "WORLD", watch = "WATCH"
    var id: String { rawValue }
}

struct DXView: View {
    @EnvironmentObject private var features: FeatureModel
    @EnvironmentObject private var radio: RadioModel
    @State private var surface: DXSurface = .smart
    @State private var selected: DXOpportunity?
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 18) {
            BrandHeader()
            HStack {
                metric("5 min", features.dx.spots5m)
                metric("60 min", features.dx.spots60m)
                metric("Watch", features.dx.watchlistHits)
                metric("Surges", features.dx.surgingBands)
            }
            GroupBox("NOAA space weather") {
                if features.dx.solar.valid {
                    HStack { LabeledContent("SFI", value: String(format: "%.1f", features.dx.solar.flux)); LabeledContent("Kp", value: String(format: "%.1f", features.dx.solar.kpIndex)) }
                } else { Text("No current NOAA reading loaded").foregroundStyle(.secondary) }
                Button("Refresh NOAA") { Task { await features.refreshSolar() } }
            }
            Picker("DX view", selection: $surface) { ForEach(DXSurface.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented)
            dxContent
        }.padding() }.navigationTitle("DX Watcher")
            .sheet(item: $selected) { opportunity in DXOpportunityDetail(opportunity: opportunity, tune: tune) }
    }
    private func metric(_ name: String, _ value: UInt) -> some View {
        VStack { Text("\(value)").font(.title2.bold()); Text(name).font(.caption).foregroundStyle(.secondary) }.frame(maxWidth: .infinity).padding().background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder private var dxContent: some View {
        switch surface {
        case .live: opportunityList(features.dx.liveSpots)
        case .smart: opportunityList(features.dx.opportunities)
        case .watch: opportunityList(features.dx.watchActivity)
        case .bandmap: DXBandmap(bands: features.dx.bands)
        case .pulse: DXPulse(bands: features.dx.bands, timeline: features.dx.bandTimeline)
        case .world: DXWorld(grid: features.dx.worldGrid, regions: features.dx.regions)
        }
    }

    private func opportunityList(_ rows: [DXOpportunity]) -> some View {
        LazyVStack(spacing: 8) {
            if rows.isEmpty { ContentUnavailableView("Learning from live cluster history", systemImage: "scope", description: Text("Connect the DX cluster; no generated spots are displayed.")) }
            ForEach(rows) { row in
                Button { selected = row } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack { Text(row.callsign).font(.headline); if row.watchlisted { Image(systemName: "star.fill").foregroundStyle(RigTheme.amber) }; Text(row.band).font(.caption.monospaced()).foregroundStyle(.secondary) }
                            Text(String(format: "%.3f MHz · %@ · %@", Double(row.frequencyHz) / 1_000_000, row.mode, row.country)).foregroundStyle(.secondary)
                            Text(row.reason).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }
                        Spacer()
                        VStack(alignment: .trailing) { Text("\(row.score)").font(.title3.bold()); Text("\(row.confidence)%").font(.caption).foregroundStyle(.secondary) }
                    }.contentShape(Rectangle())
                }.buttonStyle(.plain).padding(12).background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private func tune(_ row: DXOpportunity) { radio.sendCAT(String(format: "FA%011llu;", row.frequencyHz)) }
}

private struct DXBandmap: View {
    let bands: [DXBand]
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Band activity · last 60 minutes").font(.headline)
            let maximum = max(1, bands.map(\.spots60m).max() ?? 1)
            ForEach(bands) { band in
                HStack(spacing: 10) {
                    Text(band.band).font(.system(.body, design: .monospaced).bold()).frame(width: 42, alignment: .leading)
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.secondary.opacity(0.12))
                            Capsule().fill(band.surge ? Color.red : RigTheme.amber).frame(width: geometry.size.width * CGFloat(band.spots60m) / CGFloat(maximum))
                        }
                    }.frame(height: 12)
                    Text("\(band.spots5m)/\(band.spots60m)").font(.caption.monospacedDigit()).frame(width: 58, alignment: .trailing)
                    if band.surge { Text("SURGE").font(.caption2.bold()).foregroundStyle(.red) }
                }.frame(minHeight: 30)
            }
        }.padding(16).background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct DXPulse: View {
    let bands: [DXBand]
    let timeline: [[UInt]]
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack { Text("Activity pulse").font(.headline); Spacer(); Text("60 min ago → now").font(.caption).foregroundStyle(.secondary) }
            let maximum = max(1, timeline.flatMap { $0 }.max() ?? 1)
            ForEach(Array(bands.enumerated()), id: \.element.id) { index, band in
                HStack(spacing: 6) {
                    Text(band.band).font(.caption.monospaced().bold()).frame(width: 38, alignment: .leading)
                    let row = index < timeline.count ? timeline[index] : []
                    ForEach(Array(row.enumerated()), id: \.offset) { _, value in
                        RoundedRectangle(cornerRadius: 3).fill(heat(value, maximum: maximum)).frame(maxWidth: .infinity, minHeight: 26)
                            .overlay(Text(value == 0 ? "" : "\(value)").font(.caption2).foregroundStyle(.white))
                    }
                }
            }
        }.padding(16).background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 14))
    }
    private func heat(_ value: UInt, maximum: UInt) -> Color {
        let level = Double(value) / Double(maximum)
        return level == 0 ? Color.white.opacity(0.04) : RigTheme.amber.opacity(0.25 + level * 0.75)
    }
}

private struct DXWorld: View {
    let grid: [[UInt]]
    let regions: [DXRegion]
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("World activity · 60 minutes · west → east").font(.headline)
            let maximum = max(1, grid.flatMap { $0 }.max() ?? 1)
            VStack(spacing: 5) {
                ForEach(Array(grid.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: 5) {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, value in
                            RoundedRectangle(cornerRadius: 4).fill(value == 0 ? Color.white.opacity(0.035) : RigTheme.amber.opacity(0.22 + 0.78 * Double(value) / Double(maximum)))
                                .aspectRatio(1.25, contentMode: .fit).overlay(Text(value == 0 ? "" : "\(value)").font(.caption2))
                        }
                    }
                }
            }
            Text("Region pulse · 15m : 60m").font(.headline)
            ForEach(regions) { region in
                VStack(alignment: .leading, spacing: 4) {
                    HStack { Text(region.region).font(.subheadline.bold()); Spacer(); Text("\(region.spots15m) : \(region.spots60m) · \(region.uniqueCalls) calls").font(.caption.monospacedDigit()); if region.anomaly { Text("ANOMALY").font(.caption2.bold()).foregroundStyle(.red) } }
                    ProgressView(value: Double(min(region.activityPercent, 300)), total: 300).tint(region.anomaly ? .red : RigTheme.amber)
                }
            }
        }.padding(16).background(RigTheme.panel).clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct DXOpportunityDetail: View {
    let opportunity: DXOpportunity
    let tune: (DXOpportunity) -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section("Target") {
                    LabeledContent("Callsign", value: opportunity.callsign)
                    LabeledContent("Frequency", value: String(format: "%.3f MHz", Double(opportunity.frequencyHz) / 1_000_000))
                    LabeledContent("Mode / band", value: "\(opportunity.mode) · \(opportunity.band)")
                    LabeledContent("Entity", value: opportunity.country)
                    LabeledContent("Path", value: opportunity.pathState.isEmpty ? "Unknown" : opportunity.pathState)
                    LabeledContent("Bearing / distance", value: "\(opportunity.bearingDegrees)° · \(opportunity.distanceKm) km")
                }
                Section("Operator interpretation") {
                    Text(opportunity.reason)
                    LabeledContent("Score", value: "\(opportunity.score)")
                    LabeledContent("Confidence", value: "\(opportunity.confidence)%")
                    LabeledContent("Worked", value: [opportunity.workedCountry ? "entity" : nil, opportunity.workedCall ? "call" : nil, opportunity.workedBand ? "band" : nil, opportunity.workedMode ? "mode" : nil, opportunity.workedBandMode ? "band+mode" : nil].compactMap { $0 }.joined(separator: ", ").ifEmpty("No match"))
                }
                Button("Tune VFO A") { tune(opportunity); dismiss() }.buttonStyle(.borderedProminent)
            }.navigationTitle(opportunity.callsign).toolbar { ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } } }
        }
    }
}

private extension String { func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self } }

struct DigitalView: View {
    @EnvironmentObject private var features: FeatureModel
    var body: some View {
        Form {
            Section("WSJT-X UDP") {
                Text(features.wsjtxStatus)
                HStack { Button("Listen") { features.startWSJTX() }; Button("Stop") { features.stopWSJTX() } }
            }
            Section("Last real datagram") {
                if let message = features.lastWSJTX {
                    LabeledContent("Valid", value: message.valid ? "Yes" : "No")
                    LabeledContent("Station", value: message.stationId ?? "—")
                    LabeledContent("Type", value: message.type ?? "—")
                    LabeledContent("Mode", value: message.mode ?? "—")
                    if let call = message.dxCall ?? message.callsign { LabeledContent("Call", value: call) }
                    if let text = message.message { Text(text).font(.system(.body, design: .monospaced)) }
                    if let error = message.error { Text(error).foregroundStyle(.red) }
                } else { Text("No WSJT-X datagram received").foregroundStyle(.secondary) }
            }
        }.navigationTitle("Digital")
    }
}

struct LookupView: View {
    @EnvironmentObject private var features: FeatureModel
    @State private var callsign = ""
    var body: some View {
        Form {
            Section("Live callbook lookup") {
                HStack {
                    TextField("Callsign", text: $callsign).textInputAutocapitalization(.characters).autocorrectionDisabled()
                    Button("Lookup") { Task { await features.callbook.lookup(callsign) } }
                }
                Text(features.callbook.status).font(.caption).foregroundStyle(.secondary)
            }
            if let result = features.callbook.result {
                Section(result.callsign) {
                    LabeledContent("Name", value: result.name)
                    LabeledContent("Location", value: result.location)
                    LabeledContent("Country", value: result.country)
                    LabeledContent("Grid", value: result.grid)
                    if !result.latitude.isEmpty { LabeledContent("Coordinates", value: "\(result.latitude), \(result.longitude)") }
                }
            }
        }.navigationTitle("Lookup")
    }
}

struct SettingsView: View {
    @EnvironmentObject private var radio: RadioModel
    @EnvironmentObject private var features: FeatureModel
    var body: some View {
        Form {
            Section("Radio profile") {
                LabeledContent("Radio", value: radio.snapshot.connected ? radio.snapshot.model : "Awaiting ID response")
                LabeledContent("Mode", value: "Real hardware only")
            }
            Section("Physical Apple hardware") {
                LabeledContent("Driver", value: "CP2102 / Digirig · DriverKit")
                Picker("Serial port", selection: $radio.selectedPort) {
                    if radio.serialPorts.isEmpty { Text("No physical port").tag("") }
                    ForEach(radio.serialPorts, id: \.self) { Text(($0 as NSString).lastPathComponent).tag($0) }
                }
                HStack {
                    Button("Scan") { radio.refreshPorts() }
                    Button("Connect") { radio.connect() }.disabled(radio.selectedPort.isEmpty)
                    Button("Disconnect") { radio.disconnect() }
                }
                Text(radio.transportStatus).font(.caption).foregroundStyle(.secondary)
                Text("Use Radio for frequency, mode and raw KX3 CAT commands.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Software") { LabeledContent("Shared core", value: radio.coreVersion) }
            Section("DX cluster") {
                TextField("Operator callsign", text: $features.operatorCallsign).textInputAutocapitalization(.characters).autocorrectionDisabled()
                TextField("Host", text: $features.clusterHost).textInputAutocapitalization(.never).autocorrectionDisabled()
                TextField("Port", text: $features.clusterPort).keyboardType(.numberPad)
                TextField("Watchlist callsigns", text: $features.watchlist, axis: .vertical).textInputAutocapitalization(.characters).autocorrectionDisabled()
                HStack { Button("Connect") { features.connectCluster() }; Button("Disconnect") { features.disconnectCluster() } }
                Text(features.clusterStatus).font(.caption).foregroundStyle(.secondary)
            }
            Section("WSJT-X") {
                TextField("UDP port", text: $features.wsjtxPort).keyboardType(.numberPad)
                HStack { Button("Listen") { features.startWSJTX() }; Button("Stop") { features.stopWSJTX() } }
                Text(features.wsjtxStatus).font(.caption).foregroundStyle(.secondary)
            }
            Section("Wavelog sync") {
                TextField("Wavelog base URL", text: Binding(get: { features.wavelog.baseURL }, set: { features.wavelog.baseURL = $0 })).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("API key", text: Binding(get: { features.wavelog.apiKey }, set: { features.wavelog.apiKey = $0 }))
                if features.wavelog.stations.isEmpty {
                    TextField("Station profile ID", text: Binding(get: { features.wavelog.stationProfile }, set: { features.wavelog.stationProfile = $0 })).textInputAutocapitalization(.never).autocorrectionDisabled()
                } else {
                    Picker("Station", selection: Binding(get: { features.wavelog.stationProfile }, set: features.wavelog.selectStation)) {
                        ForEach(features.wavelog.stations) { Text($0.label).tag($0.id) }
                    }
                }
                HStack {
                    Button("Load stations") { Task { await features.wavelog.loadStations() } }
                    Button("Sync queue") { Task { await features.wavelog.syncNow() } }
                    Button("Full log") { Task { await features.wavelog.fullSync() } }
                }
                LabeledContent("Pending", value: "\(features.wavelog.pendingCount)")
                LabeledContent("Cached remote QSOs", value: "\(features.wavelog.contacts.count)")
                Text(features.wavelog.status).font(.caption).foregroundStyle(.secondary)
            }
            Section("Callbook") {
                Picker("Provider", selection: Binding(get: { features.callbook.provider }, set: { features.callbook.provider = $0 })) {
                    Text("QRZ").tag("QRZ"); Text("HamQTH").tag("HamQTH")
                }
                TextField("Username", text: Binding(get: { features.callbook.username }, set: { features.callbook.username = $0 })).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("Password", text: Binding(get: { features.callbook.password }, set: { features.callbook.password = $0 }))
                Text("Credentials remain in the device Keychain and are sent only to the selected provider.").font(.caption).foregroundStyle(.secondary)
            }
        }.navigationTitle("Settings")
    }
}

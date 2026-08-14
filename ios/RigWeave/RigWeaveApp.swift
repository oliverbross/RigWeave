import SwiftUI

@main
struct RigWeaveApp: App {
    @StateObject private var radio = RadioModel()
    @StateObject private var logbook = QSOStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(radio)
                .environmentObject(logbook)
                .preferredColorScheme(.dark)
        }
    }
}

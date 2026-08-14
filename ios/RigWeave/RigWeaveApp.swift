import SwiftUI

@main
struct RigWeaveApp: App {
    @StateObject private var radio = RadioModel()
    @StateObject private var logbook = QSOStore()
    @StateObject private var features = FeatureModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(radio)
                .environmentObject(logbook)
                .environmentObject(features)
                .preferredColorScheme(.dark)
        }
    }
}

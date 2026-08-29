import SwiftUI

@main
struct NFCProbeApp: App {
    @StateObject private var controller = NFCProbeController()

    var body: some Scene {
        WindowGroup {
            ContentView(controller: controller)
        }
    }
}


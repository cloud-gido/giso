import SwiftUI
import GISOTracker

@main
struct GisoVideoDemoApp: App {
    init() {
        let endpoint = ProcessInfo.processInfo.environment["GISO_ENDPOINT"]
            ?? "http://localhost:8123/v1/track"
        var cfg = TrackerConfig(
            appId: "video-ios",
            appVersion: "1.0.0",
            endpoint: URL(string: endpoint)!
        )
        cfg.debug = true
        cfg.env = "test"
        Tracker.initialize(config: cfg)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("GISO iOS Demo")
            Button("模拟 page_enter") {
                Tracker.shared.enterPage("home", params: ["tab_name": "rec"])
            }
        }
        .padding()
    }
}

import SwiftUI
import SwiftData

@main
struct LastLoggedApp: App {
    let modelContainer: ModelContainer

    init() {
        let schema = Schema([
            TrackerItem.self,
            CompletionLog.self,
            TrackerCategory.self,
        ])

        let appGroupURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.com.pearsonmedia.lastlogged"
        )

        let configuration: ModelConfiguration
        if let appGroupURL {
            let storeURL = appGroupURL.appending(path: "LastLogged.sqlite")
            configuration = ModelConfiguration(url: storeURL)
        } else {
            configuration = ModelConfiguration()
        }

        do {
            modelContainer = try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            fatalError("Failed to initialize ModelContainer: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .modelContainer(modelContainer)
    }
}

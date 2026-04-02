import SwiftUI
import SwiftData
import RevenueCat

@main
struct LastLoggedApp: App {
    @Environment(\.scenePhase) private var scenePhase
    let modelContainer: ModelContainer

    init() {
        AppSecrets.validate()

        // Migrate sensitive values from UserDefaults to Keychain (one-time)
        KeychainService.migrateDateFromUserDefaults(key: "com.pearsonmedia.lastlogged.lastSyncTimestamp")
        KeychainService.migrateBoolFromUserDefaults(key: "isPremium")

        AnalyticsService.shared.configure()
        RevenueCatService.shared.configure()
        let schema = Schema(versionedSchema: SchemaV1.self)

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
            modelContainer = try ModelContainer(
                for: schema,
                migrationPlan: LastLoggedMigrationPlan.self,
                configurations: [configuration]
            )
        } catch {
            fatalError("Failed to initialize ModelContainer: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    DataSeeder.seedDefaultCategoriesIfNeeded(
                        modelContext: modelContainer.mainContext
                    )
                }
                .task {
                    await SupabaseService.shared.restoreSession()
                }
        }
        .modelContainer(modelContainer)
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                SupabaseService.shared.syncOnForeground(
                    modelContext: modelContainer.mainContext
                )
            }
        }
    }
}

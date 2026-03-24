import AppIntents
import SwiftData
import TelemetryDeck
import WidgetKit

struct LogCompletionIntent: AppIntent {
    static var title: LocalizedStringResource = "Log Completion"
    static var description = IntentDescription("Log a completion for a tracked item")

    @Parameter(title: "Tracker Item ID")
    var trackerItemIdString: String

    init() {}

    init(trackerItemId: UUID) {
        self.trackerItemIdString = trackerItemId.uuidString
    }

    func perform() async throws -> some IntentResult {
        guard let trackerItemId = UUID(uuidString: trackerItemIdString) else {
            return .result()
        }

        guard let container = WidgetDataProvider.shared.makeSharedModelContainer() else {
            return .result()
        }

        let context = ModelContext(container)
        let descriptor = FetchDescriptor<TrackerItem>(
            predicate: #Predicate { $0.id == trackerItemId }
        )

        guard let item = try? context.fetch(descriptor).first else {
            return .result()
        }

        let log = CompletionLog(trackerItemId: item.id)
        context.insert(log)
        item.lastCompletedAt = log.completedAt
        try? context.save()

        WidgetCenter.shared.reloadAllTimelines()

        TelemetryDeck.signal("widget_tapped")

        return .result()
    }
}

import WidgetKit
import SwiftUI

struct LastLoggedWidgetEntry: TimelineEntry {
    let date: Date
    let items: [OverdueItem]
}

struct LastLoggedWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> LastLoggedWidgetEntry {
        LastLoggedWidgetEntry(date: Date(), items: [])
    }

    func getSnapshot(in context: Context, completion: @escaping (LastLoggedWidgetEntry) -> Void) {
        let items = WidgetDataProvider.shared.getTopOverdueItems(count: 5)
        completion(LastLoggedWidgetEntry(date: Date(), items: items))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LastLoggedWidgetEntry>) -> Void) {
        let items = WidgetDataProvider.shared.getTopOverdueItems(count: 5)
        let entry = LastLoggedWidgetEntry(date: Date(), items: items)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date()
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }
}

struct LastLoggedWidget: Widget {
    let kind: String = "LastLoggedWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LastLoggedWidgetProvider()) { entry in
            LastLoggedWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Last Logged")
        .description("See your most overdue tracked items.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct LastLoggedWidgetEntryView: View {
    var entry: LastLoggedWidgetEntry

    var body: some View {
        if entry.items.isEmpty {
            Text("Open Last Logged to start tracking")
                .font(.caption)
                .multilineTextAlignment(.center)
                .padding()
        } else {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(entry.items.prefix(3), id: \.id) { item in
                    HStack {
                        Image(systemName: item.iconName)
                            .foregroundStyle(urgencyColor(for: item))
                        Text(item.name)
                            .font(.caption)
                            .lineLimit(1)
                        Spacer()
                        Text(item.elapsedTimeDescription)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding()
        }
    }

    private func urgencyColor(for item: OverdueItem) -> Color {
        switch item.urgencyLevel {
        case .onSchedule: return .green
        case .approaching: return .yellow
        case .overdue: return .red
        case .neutral: return .secondary
        }
    }
}

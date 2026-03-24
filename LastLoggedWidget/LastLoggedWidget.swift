import WidgetKit
import SwiftUI
import AppIntents

struct LastLoggedWidgetEntry: TimelineEntry {
    let date: Date
    let items: [OverdueItem]
}

struct LastLoggedWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> LastLoggedWidgetEntry {
        LastLoggedWidgetEntry(date: Date(), items: OverdueItem.sampleItems)
    }

    func getSnapshot(in context: Context, completion: @escaping (LastLoggedWidgetEntry) -> Void) {
        if context.isPreview {
            completion(LastLoggedWidgetEntry(date: Date(), items: OverdueItem.sampleItems))
        } else {
            let items = WidgetDataProvider.shared.getTopOverdueItems(count: 5)
            completion(LastLoggedWidgetEntry(date: Date(), items: items))
        }
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
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Last Logged")
        .description("See your most overdue tracked items.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - Entry View

struct LastLoggedWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    var entry: LastLoggedWidgetEntry

    var body: some View {
        if entry.items.isEmpty {
            emptyStateView
        } else {
            switch family {
            case .systemSmall:
                SmallWidgetView(item: entry.items[0])
                    .widgetURL(URL(string: "lastlogged://tracker/\(entry.items[0].id.uuidString)"))
            case .systemMedium:
                MediumWidgetView(items: Array(entry.items.prefix(3)))
            case .systemLarge:
                LargeWidgetView(items: Array(entry.items.prefix(5)))
            default:
                MediumWidgetView(items: Array(entry.items.prefix(3)))
            }
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .font(.largeTitle)
                .foregroundStyle(.green.opacity(0.6))
            Text("Open Last Logged to start tracking")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

// MARK: - Small Widget (2x2)

struct SmallWidgetView: View {
    let item: OverdueItem

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: item.iconName)
                    .font(.title3)
                    .foregroundStyle(urgencyColor(for: item))
                Spacer()
                urgencyIndicator(for: item)
            }

            Spacer()

            Text(item.name)
                .font(.headline)
                .lineLimit(2)
                .minimumScaleFactor(0.8)

            Text(item.elapsedTimeDescription)
                .font(.caption)
                .foregroundStyle(urgencyColor(for: item))
                .lineLimit(1)
        }
        .padding(2)
    }
}

// MARK: - Medium Widget (4x2)

struct MediumWidgetView: View {
    let items: [OverdueItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Last Logged")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(.bottom, 6)

            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                InteractiveWidgetRowView(item: item)
                if index < items.count - 1 {
                    Divider()
                        .padding(.vertical, 2)
                }
            }

            if items.count < 3 {
                Spacer()
            }
        }
        .padding(2)
    }
}

// MARK: - Large Widget (4x4)

struct LargeWidgetView: View {
    let items: [OverdueItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Last Logged")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(.bottom, 8)

            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                LargeWidgetRowView(item: item)
                if index < items.count - 1 {
                    Divider()
                        .padding(.vertical, 4)
                }
            }

            Spacer()
        }
        .padding(4)
    }
}

struct LargeWidgetRowView: View {
    let item: OverdueItem

    var body: some View {
        HStack(spacing: 10) {
            urgencyIndicator(for: item)

            Image(systemName: item.iconName)
                .font(.body)
                .foregroundStyle(urgencyColor(for: item))
                .frame(width: 22)

            VStack(alignment: .leading, spacing: 2) {
                Text(item.name)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)

                Text(item.categoryName)
                    .font(.caption2)
                    .foregroundStyle(categoryColor(for: item))
                    .lineLimit(1)
            }

            Spacer()

            Text(item.elapsedTimeDescription)
                .font(.caption)
                .foregroundStyle(urgencyColor(for: item))
                .lineLimit(1)

            Button(intent: LogCompletionIntent(trackerItemId: item.id)) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.green)
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Interactive Row (Medium + Large shared pattern)

struct InteractiveWidgetRowView: View {
    let item: OverdueItem

    var body: some View {
        HStack(spacing: 8) {
            urgencyIndicator(for: item)

            Image(systemName: item.iconName)
                .font(.callout)
                .foregroundStyle(urgencyColor(for: item))
                .frame(width: 20)

            Text(item.name)
                .font(.subheadline)
                .lineLimit(1)

            Spacer()

            Text(item.elapsedTimeDescription)
                .font(.caption)
                .foregroundStyle(urgencyColor(for: item))
                .lineLimit(1)

            Button(intent: LogCompletionIntent(trackerItemId: item.id)) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.callout)
                    .foregroundStyle(.green)
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Urgency Helpers

private func urgencyColor(for item: OverdueItem) -> Color {
    switch item.urgencyLevel {
    case .onSchedule: return .green
    case .approaching: return .yellow
    case .overdue: return .red
    case .neutral: return .secondary
    }
}

private func urgencyIndicator(for item: OverdueItem) -> some View {
    Circle()
        .fill(urgencyColor(for: item))
        .frame(width: 8, height: 8)
}

private func categoryColor(for item: OverdueItem) -> Color {
    Color(hex: item.categoryColorHex) ?? .secondary
}

// MARK: - Color Hex Extension (duplicated from main app for widget target)

extension Color {
    init?(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        guard hexSanitized.count == 6 else { return nil }

        var rgb: UInt64 = 0
        guard Scanner(string: hexSanitized).scanHexInt64(&rgb) else { return nil }

        self.init(
            red: Double((rgb & 0xFF0000) >> 16) / 255.0,
            green: Double((rgb & 0x00FF00) >> 8) / 255.0,
            blue: Double(rgb & 0x0000FF) / 255.0
        )
    }
}

// MARK: - Sample Data

extension OverdueItem {
    static let sampleItems: [OverdueItem] = [
        OverdueItem(
            id: UUID(),
            name: "Change HVAC Filter",
            iconName: "fan.fill",
            categoryName: "Home Maintenance",
            categoryColorHex: "#4A90D9",
            lastCompletedAt: Calendar.current.date(byAdding: .day, value: -95, to: Date()),
            reminderIntervalDays: 90,
            overdueScore: 1.06
        ),
        OverdueItem(
            id: UUID(),
            name: "Oil Change",
            iconName: "car.fill",
            categoryName: "Car Care",
            categoryColorHex: "#F39C12",
            lastCompletedAt: Calendar.current.date(byAdding: .day, value: -80, to: Date()),
            reminderIntervalDays: 90,
            overdueScore: 0.89
        ),
        OverdueItem(
            id: UUID(),
            name: "Call Parents",
            iconName: "phone.fill",
            categoryName: "Social & Family",
            categoryColorHex: "#2ECC71",
            lastCompletedAt: Calendar.current.date(byAdding: .day, value: -10, to: Date()),
            reminderIntervalDays: 14,
            overdueScore: 0.71
        ),
        OverdueItem(
            id: UUID(),
            name: "Dental Cleaning",
            iconName: "heart.fill",
            categoryName: "Health & Wellness",
            categoryColorHex: "#E74C3C",
            lastCompletedAt: Calendar.current.date(byAdding: .day, value: -200, to: Date()),
            reminderIntervalDays: 180,
            overdueScore: 1.11
        ),
        OverdueItem(
            id: UUID(),
            name: "Flea Treatment",
            iconName: "pawprint.fill",
            categoryName: "Pet Care",
            categoryColorHex: "#1ABC9C",
            lastCompletedAt: Calendar.current.date(byAdding: .day, value: -35, to: Date()),
            reminderIntervalDays: 30,
            overdueScore: 1.17
        ),
    ]
}

import SwiftUI
#if canImport(ActivityKit)
import ActivityKit
import WidgetKit

/// Live Activity / Dynamic Island presentation for "just logged" events.
///
/// Surfaces the most recently logged tracker on the lock screen and in the
/// Dynamic Island with compact, expanded, and minimal states for iOS 16.1+.
@available(iOS 16.1, *)
struct LastLoggedLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LastLoggedActivityAttributes.self) { context in
            // Lock-screen / banner presentation
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(Color.green.opacity(0.18))
                        .frame(width: 44, height: 44)
                    Image(systemName: "checkmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(Color.green)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.trackerName)
                        .font(.headline)
                        .lineLimit(1)
                    Text("Logged \(context.state.relativeLabel)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: context.attributes.iconName)
                    .font(.title3)
                    .foregroundStyle(.tint)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .activityBackgroundTint(Color.black.opacity(0.25))
            .activitySystemActionForegroundColor(Color.white)
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded presentation (tap-and-hold)
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: context.attributes.iconName)
                        .font(.title2)
                        .foregroundStyle(.tint)
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.green)
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text(context.attributes.trackerName)
                            .font(.headline)
                            .lineLimit(1)
                        Text("Logged \(context.state.relativeLabel)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    EmptyView()
                }
            } compactLeading: {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            } compactTrailing: {
                Text(context.state.relativeLabel)
                    .font(.caption2)
                    .lineLimit(1)
            } minimal: {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            }
        }
    }
}
#endif

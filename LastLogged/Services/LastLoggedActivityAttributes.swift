import Foundation
#if canImport(ActivityKit)
import ActivityKit

/// ActivityAttributes shared between the main app (which starts/updates/ends
/// the Live Activity) and the widget extension (which renders the
/// compact/expanded/minimal presentations). File is a target member of both
/// LastLogged and LastLoggedWidgetExtension.
@available(iOS 16.1, *)
struct LastLoggedActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// When the log happened — used to render "Logged X ago".
        public var loggedAt: Date
        /// Short human label, e.g. "Just now" or "2 min ago" for the
        /// minimal/compact presentations that have very little space.
        public var relativeLabel: String

        public init(loggedAt: Date, relativeLabel: String) {
            self.loggedAt = loggedAt
            self.relativeLabel = relativeLabel
        }
    }

    /// The tracker name (static per activity).
    public var trackerName: String
    /// SF Symbol name for the tracker icon.
    public var iconName: String

    public init(trackerName: String, iconName: String) {
        self.trackerName = trackerName
        self.iconName = iconName
    }
}
#endif

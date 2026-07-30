import Foundation
import Observation

/// Parses the URLs that can open the app and holds the pending destination
/// until a view is ready to navigate.
///
/// Mirrors `util/DeepLinks.kt` on Android — keep the accepted paths in sync with
/// `APP_LINK_PATHS` in `website/src/config/app-association.ts`, which is what
/// the served `apple-app-site-association` advertises.
@Observable
final class DeepLinkRouter {
    static let shared = DeepLinkRouter()

    enum Target: Equatable {
        case trackerDetail(UUID)
        case home
    }

    /// Set when a link arrives; cleared by whichever view consumes it.
    var pendingTarget: Target?

    private init() {}

    static let webHost = "lastlogged.com"
    static let customScheme = "lastlogged"

    func handle(_ url: URL) {
        guard let target = Self.parse(url) else { return }
        pendingTarget = target
    }

    func consume() -> Target? {
        defer { pendingTarget = nil }
        return pendingTarget
    }

    /// Returns nil for anything unrecognised so a malformed or hostile link
    /// leaves the user on Home instead of routing somewhere unexpected.
    static func parse(_ url: URL) -> Target? {
        let scheme = url.scheme?.lowercased()

        if scheme == customScheme {
            // lastlogged://tracker/<uuid> — the host carries the first component.
            switch url.host?.lowercased() {
            case "tracker":
                return url.pathComponents
                    .first { $0 != "/" }
                    .flatMap(trackerTarget)
            case "open":
                return .home
            default:
                return nil
            }
        }

        guard scheme == "https", url.host?.lowercased() == webHost else { return nil }

        // https://lastlogged.com/tracker/<uuid>
        let components = url.pathComponents.filter { $0 != "/" }
        guard let first = components.first?.lowercased() else { return nil }

        switch first {
        case "tracker", "share":
            return components.dropFirst().first.flatMap(trackerTarget)
        case "open":
            return .home
        default:
            return nil
        }
    }

    private static func trackerTarget(_ rawId: String) -> Target? {
        guard let uuid = UUID(uuidString: rawId.trimmingCharacters(in: .whitespaces)) else {
            return nil
        }
        return .trackerDetail(uuid)
    }
}

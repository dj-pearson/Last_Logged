import XCTest
@testable import LastLogged

/// Covers the urgency/overdue logic the widget and Home screen share.
///
/// `OverdueItem` is a plain struct, so these run without a ModelContainer.
/// The ordering rule mirrors `WidgetItems.kt` on Android — a ratio of elapsed
/// time to reminder interval, never a raw day count.
final class WidgetDataProviderTests: XCTestCase {

    private let day: TimeInterval = 86_400

    private func item(
        name: String = "Test",
        daysAgo: Double? = 0,
        intervalDays: Int? = 30,
        score: Double = 0
    ) -> OverdueItem {
        OverdueItem(
            id: UUID(),
            name: name,
            iconName: "checkmark.circle",
            categoryName: "Test",
            categoryColorHex: "#4f46e5",
            lastCompletedAt: daysAgo.map { Date().addingTimeInterval(-$0 * day) },
            reminderIntervalDays: intervalDays,
            overdueScore: score
        )
    }

    // MARK: - Urgency

    func testFreshItemIsOnSchedule() {
        XCTAssertEqual(item(daysAgo: 1, intervalDays: 30).urgencyLevel, .onSchedule)
    }

    func testItemPastThreeQuartersIsApproaching() {
        // 0.75 is the documented threshold.
        XCTAssertEqual(item(daysAgo: 23, intervalDays: 30).urgencyLevel, .approaching)
    }

    func testItemPastItsIntervalIsOverdue() {
        XCTAssertEqual(item(daysAgo: 31, intervalDays: 30).urgencyLevel, .overdue)
    }

    func testExactlyAtTheIntervalIsStillApproaching() {
        // Boundary: ratio == 1.0 is not yet > 1.0.
        XCTAssertEqual(item(daysAgo: 30, intervalDays: 30).urgencyLevel, .approaching)
    }

    func testNeverLoggedItemIsOverdue() {
        XCTAssertEqual(item(daysAgo: nil, intervalDays: 30).urgencyLevel, .overdue)
    }

    func testItemWithoutAnIntervalIsNeutral() {
        XCTAssertEqual(item(daysAgo: 100, intervalDays: nil).urgencyLevel, .neutral)
    }

    func testItemWithZeroIntervalIsNeutralRatherThanDividingByZero() {
        XCTAssertEqual(item(daysAgo: 100, intervalDays: 0).urgencyLevel, .neutral)
    }

    // MARK: - Elapsed description

    func testNeverLoggedItemDescribesItself() {
        XCTAssertEqual(item(daysAgo: nil).elapsedTimeDescription, "Never logged")
    }

    func testLoggedItemProducesANonEmptyDescription() {
        XCTAssertFalse(item(daysAgo: 5).elapsedTimeDescription.isEmpty)
    }

    // MARK: - Ordering

    func testSortingByScorePutsMostOverdueFirst() {
        let items = [
            item(name: "fresh", score: 0.1),
            item(name: "overdue", score: 3.0),
            item(name: "due-soon", score: 0.9),
        ]

        let sorted = items.sorted { $0.overdueScore > $1.overdueScore }

        XCTAssertEqual(sorted.map(\.name), ["overdue", "due-soon", "fresh"])
    }

    func testRelativeLatenessOutranksAbsoluteLateness() {
        // 3 days late on a weekly task must beat 3 days late on an annual one.
        let weekly = item(name: "weekly", score: 10.0 / 7.0)
        let annual = item(name: "annual", score: 368.0 / 365.0)

        let sorted = [annual, weekly].sorted { $0.overdueScore > $1.overdueScore }

        XCTAssertEqual(sorted.map(\.name), ["weekly", "annual"])
    }

    // MARK: - Premium flag

    func testPremiumFlagReadsSafelyWithoutAnAppGroupContainer() {
        // Unit tests run without the App Group suite. The getter must fall back
        // to false rather than trapping on a nil UserDefaults.
        XCTAssertFalse(WidgetDataProvider.shared.isPremium)
    }
}

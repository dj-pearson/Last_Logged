import XCTest
@testable import LastLogged

final class InputSanitizerTests: XCTestCase {

    // MARK: - Whitespace

    func testTrimsLeadingAndTrailingWhitespace() {
        XCTAssertEqual(InputSanitizer.sanitize("  Oil Change  ", maxLength: 100), "Oil Change")
    }

    func testCollapsesRepeatedInternalSpaces() {
        XCTAssertEqual(
            InputSanitizer.sanitize("Oil     Change", maxLength: 100),
            "Oil Change"
        )
    }

    func testWhitespaceOnlyInputBecomesEmpty() {
        XCTAssertEqual(InputSanitizer.sanitize("     ", maxLength: 100), "")
        XCTAssertEqual(InputSanitizer.sanitize("\n\n\t", maxLength: 100), "")
    }

    // MARK: - Invisible characters

    func testStripsZeroWidthCharacters() {
        // A name padded with zero-width joiners would otherwise pass a
        // non-empty check while rendering as nothing.
        let sneaky = "A\u{200B}B\u{200C}C\u{200D}D\u{FEFF}"
        XCTAssertEqual(InputSanitizer.sanitize(sneaky, maxLength: 100), "ABCD")
    }

    func testStripsControlCharacters() {
        let withControls = "Oil\u{0000}Cha\u{0007}nge"
        XCTAssertEqual(InputSanitizer.sanitize(withControls, maxLength: 100), "OilChange")
    }

    // MARK: - Length

    func testTruncatesToMaxLength() {
        let long = String(repeating: "a", count: 250)
        XCTAssertEqual(InputSanitizer.sanitize(long, maxLength: 100).count, 100)
    }

    func testDoesNotPadShortInput() {
        XCTAssertEqual(InputSanitizer.sanitize("ab", maxLength: 100), "ab")
    }

    // MARK: - Field-specific limits

    func testTrackerNameUsesItsOwnLimit() {
        let long = String(repeating: "x", count: 500)
        XCTAssertEqual(
            InputSanitizer.sanitizeTrackerName(long).count,
            InputSanitizer.maxTrackerNameLength
        )
    }

    func testCategoryNameUsesItsOwnLimit() {
        let long = String(repeating: "x", count: 500)
        XCTAssertEqual(
            InputSanitizer.sanitizeCategoryName(long).count,
            InputSanitizer.maxCategoryNameLength
        )
    }

    func testNotesUseTheLongestLimit() {
        let long = String(repeating: "x", count: 2000)
        XCTAssertEqual(
            InputSanitizer.sanitizeNotes(long).count,
            InputSanitizer.maxNotesLength
        )
        XCTAssertGreaterThan(
            InputSanitizer.maxNotesLength,
            InputSanitizer.maxTrackerNameLength
        )
    }

    // MARK: - Remaining characters

    func testRemainingCharacters() {
        XCTAssertEqual(InputSanitizer.remainingCharacters(text: "abc", maxLength: 10), 7)
        XCTAssertEqual(InputSanitizer.remainingCharacters(text: "", maxLength: 10), 10)
    }

    func testRemainingCharactersNeverGoesNegative() {
        let over = String(repeating: "x", count: 20)
        XCTAssertEqual(InputSanitizer.remainingCharacters(text: over, maxLength: 10), 0)
    }

    // MARK: - Preservation

    func testPreservesUnicodeContent() {
        XCTAssertEqual(InputSanitizer.sanitize("Café ☕️ 日本語", maxLength: 100), "Café ☕️ 日本語")
    }
}

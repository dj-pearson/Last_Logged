import XCTest
@testable import LastLogged

final class CategoryTemplatesTests: XCTestCase {

    func testEveryCategoryHasAtLeastOneTemplate() {
        XCTAssertFalse(CategoryTemplates.templates.isEmpty)

        for (category, items) in CategoryTemplates.templates {
            XCTAssertFalse(
                items.isEmpty,
                "Category '\(category)' is offered in the picker but has no templates"
            )
        }
    }

    func testTemplatesForKnownCategory() {
        let items = CategoryTemplates.templates(for: "Home Maintenance")

        XCTAssertFalse(items.isEmpty)
        XCTAssertTrue(items.contains { $0.name == "HVAC Filter" })
    }

    func testTemplatesForUnknownCategoryReturnsEmptyRatherThanCrashing() {
        XCTAssertTrue(CategoryTemplates.templates(for: "Nonexistent").isEmpty)
        XCTAssertTrue(CategoryTemplates.templates(for: "").isEmpty)
    }

    func testTemplateLookupIsCaseSensitiveByDesign() {
        // Callers pass the exact category name from the seeded data, so a
        // lowercase miss is expected — this documents it rather than asserting
        // a behaviour change.
        XCTAssertTrue(CategoryTemplates.templates(for: "home maintenance").isEmpty)
    }

    func testAllTemplatesHaveUsableReminderIntervals() {
        for (category, items) in CategoryTemplates.templates {
            for item in items {
                XCTAssertGreaterThan(
                    item.reminderIntervalDays, 0,
                    "\(category)/\(item.name) has a non-positive reminder interval"
                )
                XCTAssertLessThanOrEqual(
                    item.reminderIntervalDays, 3650,
                    "\(category)/\(item.name) has an implausible reminder interval"
                )
            }
        }
    }

    func testAllTemplatesHaveNamesAndIcons() {
        for (category, items) in CategoryTemplates.templates {
            for item in items {
                XCTAssertFalse(item.name.isEmpty, "Empty template name in \(category)")
                XCTAssertFalse(item.iconName.isEmpty, "Empty icon for \(category)/\(item.name)")
            }
        }
    }

    func testTemplateNamesAreUniqueWithinACategory() {
        for (category, items) in CategoryTemplates.templates {
            let names = items.map(\.name)
            XCTAssertEqual(
                names.count, Set(names).count,
                "Duplicate template names in \(category)"
            )
        }
    }

    func testTemplateNamesSurviveSanitization() {
        // A template name that the sanitizer would alter would be saved
        // differently from what the picker displayed.
        for (_, items) in CategoryTemplates.templates {
            for item in items {
                XCTAssertEqual(InputSanitizer.sanitizeTrackerName(item.name), item.name)
            }
        }
    }
}

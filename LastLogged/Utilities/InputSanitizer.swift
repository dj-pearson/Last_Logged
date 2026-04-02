import Foundation

enum InputSanitizer {
    /// Maximum lengths for each field type
    static let maxTrackerNameLength = 100
    static let maxCategoryNameLength = 50
    static let maxNotesLength = 500

    /// Sanitize user input text by stripping control characters,
    /// zero-width characters, normalizing whitespace, and enforcing length limits.
    static func sanitize(_ text: String, maxLength: Int) -> String {
        var result = text

        // Strip control characters (except newline/tab for notes)
        result = result.unicodeScalars.filter { scalar in
            !scalar.properties.isDefaultIgnorableCodePoint
                && (CharacterSet.controlCharacters.inverted.contains(scalar)
                    || scalar == "\n" || scalar == "\t")
        }.map { Character($0) }.reduce(into: "") { $0.append($1) }

        // Strip zero-width characters
        let zeroWidthChars: [Character] = [
            "\u{200B}", // zero-width space
            "\u{200C}", // zero-width non-joiner
            "\u{200D}", // zero-width joiner
            "\u{FEFF}", // BOM / zero-width no-break space
        ]
        result = String(result.filter { !zeroWidthChars.contains($0) })

        // Normalize internal whitespace (collapse multiple spaces to one)
        while result.contains("  ") {
            result = result.replacingOccurrences(of: "  ", with: " ")
        }

        // Trim leading/trailing whitespace
        result = result.trimmingCharacters(in: .whitespacesAndNewlines)

        // Enforce max length
        if result.count > maxLength {
            result = String(result.prefix(maxLength))
        }

        return result
    }

    /// Sanitize a tracker name
    static func sanitizeTrackerName(_ text: String) -> String {
        sanitize(text, maxLength: maxTrackerNameLength)
    }

    /// Sanitize a category name
    static func sanitizeCategoryName(_ text: String) -> String {
        sanitize(text, maxLength: maxCategoryNameLength)
    }

    /// Sanitize notes text
    static func sanitizeNotes(_ text: String) -> String {
        sanitize(text, maxLength: maxNotesLength)
    }

    /// Returns remaining characters for display
    static func remainingCharacters(text: String, maxLength: Int) -> Int {
        max(0, maxLength - text.count)
    }
}

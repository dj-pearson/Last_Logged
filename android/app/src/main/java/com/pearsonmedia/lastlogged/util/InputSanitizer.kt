package com.pearsonmedia.lastlogged.util

object InputSanitizer {

    const val MAX_TRACKER_NAME = 100
    const val MAX_CATEGORY_NAME = 50
    const val MAX_NOTES = 500

    fun sanitize(text: String, maxLength: Int): String {
        return text
            .replace(Regex("[\\p{Cc}&&[^\\n\\r\\t]]"), "") // Strip control chars (keep newline, return, tab)
            .replace(Regex("[\\u200B-\\u200F\\u2028-\\u202F\\uFEFF]"), "") // Strip zero-width chars
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
            .take(maxLength)
    }

    fun sanitizeTrackerName(text: String): String = sanitize(text, MAX_TRACKER_NAME)

    fun sanitizeCategoryName(text: String): String = sanitize(text, MAX_CATEGORY_NAME)

    fun sanitizeNotes(text: String): String = sanitize(text, MAX_NOTES)

    fun remainingCharacters(text: String, maxLength: Int): Int {
        return maxLength - text.length
    }
}

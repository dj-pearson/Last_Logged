package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.util.InputSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSanitizerTest {

    @Test
    fun `sanitize trims whitespace`() {
        assertEquals("hello", InputSanitizer.sanitize("  hello  ", 100))
    }

    @Test
    fun `sanitize normalizes internal whitespace`() {
        assertEquals("hello world", InputSanitizer.sanitize("hello   world", 100))
    }

    @Test
    fun `sanitize strips control characters`() {
        assertEquals("hello", InputSanitizer.sanitize("hel\u0000lo", 100))
    }

    @Test
    fun `sanitize strips zero-width characters`() {
        assertEquals("hello", InputSanitizer.sanitize("hel\u200Blo", 100))
        assertEquals("hello", InputSanitizer.sanitize("hel\uFEFFlo", 100))
    }

    @Test
    fun `sanitize enforces max length`() {
        val longText = "a".repeat(200)
        val result = InputSanitizer.sanitize(longText, 100)
        assertEquals(100, result.length)
    }

    @Test
    fun `sanitizeTrackerName enforces 100 char limit`() {
        val longName = "a".repeat(150)
        val result = InputSanitizer.sanitizeTrackerName(longName)
        assertEquals(100, result.length)
    }

    @Test
    fun `sanitizeCategoryName enforces 50 char limit`() {
        val longName = "a".repeat(80)
        val result = InputSanitizer.sanitizeCategoryName(longName)
        assertEquals(50, result.length)
    }

    @Test
    fun `sanitizeNotes enforces 500 char limit`() {
        val longNotes = "a".repeat(600)
        val result = InputSanitizer.sanitizeNotes(longNotes)
        assertEquals(500, result.length)
    }

    @Test
    fun `remainingCharacters returns correct count`() {
        assertEquals(95, InputSanitizer.remainingCharacters("hello", 100))
        assertEquals(0, InputSanitizer.remainingCharacters("a".repeat(100), 100))
    }

    @Test
    fun `sanitize handles empty string`() {
        assertEquals("", InputSanitizer.sanitize("", 100))
    }

    @Test
    fun `sanitize handles whitespace-only string`() {
        assertEquals("", InputSanitizer.sanitize("   ", 100))
    }

    @Test
    fun `sanitize preserves normal text`() {
        assertEquals("My HVAC Filter", InputSanitizer.sanitize("My HVAC Filter", 100))
    }

    @Test
    fun `sanitize handles mixed content`() {
        val input = "  Hello\u0000 \u200B World  "
        val result = InputSanitizer.sanitize(input, 100)
        assertEquals("Hello World", result)
    }
}

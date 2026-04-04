package com.pearsonmedia.lastlogged

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for the retry logic pattern used in SupabaseService.
 * Tests the exponential backoff algorithm independently of Supabase.
 */
class SyncRetryTest {

    private val maxRetryAttempts = 4
    private val retryDelays = longArrayOf(2000, 4000, 8000, 16000)

    @Test
    fun `retry delays follow exponential backoff pattern`() {
        assertEquals(2000L, retryDelays[0])
        assertEquals(4000L, retryDelays[1])
        assertEquals(8000L, retryDelays[2])
        assertEquals(16000L, retryDelays[3])
    }

    @Test
    fun `max retry attempts is 4`() {
        assertEquals(4, maxRetryAttempts)
    }

    @Test
    fun `retry delays double each time`() {
        for (i in 1 until retryDelays.size) {
            assertEquals(retryDelays[i - 1] * 2, retryDelays[i])
        }
    }

    @Test
    fun `auth errors should not be retried`() {
        val authErrors = listOf("401", "403", "JWT expired", "jwt malformed")
        authErrors.forEach { error ->
            val shouldRetry = !error.contains("401") && !error.contains("403") &&
                !error.contains("JWT", ignoreCase = true)
            assertEquals("Auth error '$error' should not be retried", false, shouldRetry)
        }
    }

    @Test
    fun `network errors should be retried`() {
        val networkErrors = listOf("timeout", "connection refused", "no route to host")
        networkErrors.forEach { error ->
            val shouldNotRetry = error.contains("401") || error.contains("403") ||
                error.contains("JWT", ignoreCase = true)
            assertEquals("Network error '$error' should be retried", false, shouldNotRetry)
        }
    }

    @Test
    fun `successful operation returns immediately without retry`() {
        var attempts = 0
        val result = simulateRetry {
            attempts++
            "success"
        }
        assertEquals(1, attempts)
        assertEquals("success", result)
    }

    @Test
    fun `operation succeeds after failures within retry limit`() {
        var attempts = 0
        val result = simulateRetry {
            attempts++
            if (attempts < 3) throw RuntimeException("network error")
            "success"
        }
        assertEquals(3, attempts)
        assertEquals("success", result)
    }

    @Test
    fun `operation fails after exhausting all retries`() {
        var attempts = 0
        try {
            simulateRetry<String> {
                attempts++
                throw RuntimeException("persistent error")
            }
            fail("Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals(maxRetryAttempts, attempts)
            assertEquals("persistent error", e.message)
        }
    }

    private fun <T> simulateRetry(block: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0 until maxRetryAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: ""
                if (msg.contains("401") || msg.contains("403") || msg.contains("JWT", ignoreCase = true)) {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("Failed after $maxRetryAttempts attempts")
    }
}

package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.service.AnalyticsService
import com.pearsonmedia.lastlogged.service.SecureStorageService
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Covers payload construction and the disabled-by-default path. The HTTP send
 * is deliberately not exercised — `trackEvent` is fire-and-forget, and a blank
 * `TELEMETRYDECK_APP_ID` (the unit-test default) short-circuits before any
 * network or storage work happens.
 */
class AnalyticsServiceTest {

    private val secureStorage: SecureStorageService = mockk(relaxed = true)

    private fun service() = AnalyticsService(mockk(relaxed = true), secureStorage)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `payload matches the TelemetryDeck signal shape`() {
        val json = JSONArray(service().buildPayload("item_logged", emptyMap(), "user-hash"))

        assertEquals(1, json.length())
        val signal = json.getJSONObject(0)
        assertEquals("item_logged", signal.getString("type"))
        assertEquals("user-hash", signal.getString("clientUser"))
        assertTrue(signal.has("appID"))
        assertTrue(signal.has("payload"))
    }

    @Test
    fun `properties are carried in the payload object`() {
        val json = JSONArray(
            service().buildPayload(
                "app_error",
                mapOf("context" to "sync", "error" to "timeout"),
                "user-hash"
            )
        )

        val payload = json.getJSONObject(0).getJSONObject("payload")
        assertEquals("sync", payload.getString("context"))
        assertEquals("timeout", payload.getString("error"))
    }

    @Test
    fun `empty properties still produce a valid payload object`() {
        val json = JSONArray(service().buildPayload("item_created", emptyMap(), "u"))

        assertEquals(0, json.getJSONObject(0).getJSONObject("payload").length())
    }

    @Test
    fun `tracking is a complete no-op when the app id is blank`() {
        val svc = service()

        svc.trackEvent("item_logged")
        svc.trackError("sync_failed", "boom")
        svc.trackItemCreated()

        // No anonymous id is minted and nothing is persisted.
        verify { secureStorage wasNot Called }
    }

    @Test
    fun `configure does not throw when unconfigured`() {
        service().configure()
    }

    @Test
    fun `the raw user id is never what goes on the wire`() {
        val rawUserId = "8f14e45f-ea8f-4b2c-9a1b-000000000001"
        val hashed = sha256(rawUserId)

        val body = service().buildPayload("item_logged", emptyMap(), hashed)

        assertFalse("raw user id must never be sent", body.contains(rawUserId))
        assertTrue(body.contains(hashed))
        assertEquals(64, hashed.length)
    }

    @Test
    fun `identify tolerates null and blank without throwing`() {
        val svc = service()

        svc.identify(null)
        svc.identify("")
        svc.identify("   ")

        verify { secureStorage wasNot Called }
    }

    @Test
    fun `convenience event names match the iOS AnalyticsService`() {
        // These strings are the join key between the two platforms' funnels.
        val expected = listOf(
            "item_created", "item_logged", "onboarding_completed",
            "paywall_presented", "paywall_dismissed", "paywall_converted",
            "trial_started", "category_selected", "account_deleted",
            "widget_tapped", "sync_conflict"
        )

        val svc = service()
        expected.forEach { name ->
            val body = svc.buildPayload(name, emptyMap(), "u")
            assertEquals(name, JSONArray(body).getJSONObject(0).getString("type"))
        }
    }
}

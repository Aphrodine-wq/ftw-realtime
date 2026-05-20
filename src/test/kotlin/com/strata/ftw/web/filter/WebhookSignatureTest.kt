package com.strata.ftw.web.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks the wire format of the ftw-scraper -> ftw-realtime ingest signature.
 *
 * The reference HMAC was computed with:
 *   printf '{"hello":"world"}' | openssl dgst -sha256 -hmac "test-secret"
 *
 * Both the Node side (ftw-scraper/scripts/post-to-ftw.js) and the Kotlin
 * side (this module) must reproduce that exact hex. If either drifts, the
 * live ingest endpoint silently 401s on every request.
 */
class WebhookSignatureTest {

    private val body = """{"hello":"world"}"""
    private val secret = "test-secret"
    private val expectedHex = "84cc33df716ed0b0598f07437c94069ace3730358778a592bd6bbd1423d111f3"

    @Test
    fun `computes interop hex matching openssl reference`() {
        assertEquals(expectedHex, WebhookSignature.computeHex(body, secret))
    }

    @Test
    fun `verifies a correctly-signed body`() {
        assertTrue(WebhookSignature.verify(body, "sha256=$expectedHex", secret))
    }

    @Test
    fun `verifies without sha256 prefix`() {
        assertTrue(WebhookSignature.verify(body, expectedHex, secret))
    }

    @Test
    fun `rejects a tampered body`() {
        val tamperedBody = """{"hello":"world!"}"""
        assertFalse(WebhookSignature.verify(tamperedBody, "sha256=$expectedHex", secret))
    }

    @Test
    fun `rejects a tampered signature`() {
        val tampered = "sha256=" + expectedHex.dropLast(1) + "0"
        assertFalse(WebhookSignature.verify(body, tampered, secret))
    }

    @Test
    fun `rejects a wrong secret`() {
        assertFalse(WebhookSignature.verify(body, "sha256=$expectedHex", "wrong-secret"))
    }

    @Test
    fun `rejects a length mismatch without crashing`() {
        assertFalse(WebhookSignature.verify(body, "sha256=deadbeef", secret))
    }
}

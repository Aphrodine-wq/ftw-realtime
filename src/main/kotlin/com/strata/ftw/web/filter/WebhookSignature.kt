package com.strata.ftw.web.filter

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 helpers for signed-webhook style endpoints.
 *
 * Used by PricesIngestController; ftw-scraper signs outgoing requests with
 * the same algorithm in scripts/post-to-ftw.js. The interop is locked by
 * the test in WebhookSignatureTest — if that test breaks, the live ingest
 * will silently 401.
 */
object WebhookSignature {

    fun computeHex(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }

    /**
     * Constant-time comparison of a header (with optional `sha256=` prefix)
     * to the expected HMAC.
     */
    fun verify(body: String, signatureHeader: String, secret: String): Boolean {
        val provided = signatureHeader.removePrefix("sha256=").lowercase()
        val expected = computeHex(body, secret).lowercase()
        if (provided.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (provided[i].code xor expected[i].code)
        return diff == 0
    }
}

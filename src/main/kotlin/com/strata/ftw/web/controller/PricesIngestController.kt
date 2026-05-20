package com.strata.ftw.web.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.strata.ftw.service.MaterialPricingService
import com.strata.ftw.web.dto.IngestPayloadDto
import com.strata.ftw.web.dto.IngestResultDto
import com.strata.ftw.web.filter.WebhookSignature
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Daily material price ingest from ftw-scraper.
 *
 * Auth: HMAC-SHA256 of the raw body using FTW_INGEST_SECRET. The header is
 * `X-FTW-Signature: sha256=<hex>` (mirrors GitHub's X-Hub-Signature-256
 * shape so a GitHub Action can also call this endpoint directly).
 *
 * The scraper repo POSTs after every successful run; idempotency is keyed on
 * scrape_run.run_id so retries are safe.
 */
@RestController
@RequestMapping("/api/v1/prices")
class PricesIngestController(
    private val pricingService: MaterialPricingService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.ingest.secret:}") private val ingestSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/ingest")
    fun ingest(
        @RequestBody body: String,
        @RequestHeader(name = "X-FTW-Signature", required = false) signature: String?
    ): ResponseEntity<Any> {
        if (ingestSecret.isBlank()) {
            log.error("ingest rejected: app.ingest.secret not configured on this instance")
            return ResponseEntity.status(503).body(mapOf("error" to "ingest_not_configured"))
        }
        if (signature.isNullOrBlank()) {
            return ResponseEntity.status(401).body(mapOf("error" to "missing_signature"))
        }
        if (!WebhookSignature.verify(body, signature, ingestSecret)) {
            log.warn("ingest rejected: bad signature")
            return ResponseEntity.status(401).body(mapOf("error" to "bad_signature"))
        }

        val payload = try {
            objectMapper.readValue(body, IngestPayloadDto::class.java)
        } catch (e: Exception) {
            log.warn("ingest rejected: malformed body — ${e.message}")
            return ResponseEntity.badRequest().body(mapOf("error" to "malformed_body", "detail" to e.message))
        }

        val result: IngestResultDto = try {
            pricingService.ingest(payload)
        } catch (e: IllegalArgumentException) {
            log.warn("ingest rejected: ${e.message}")
            return ResponseEntity.badRequest().body(mapOf("error" to "validation_failed", "detail" to e.message))
        } catch (e: Exception) {
            log.error("ingest crashed for run ${payload.scrapeRun.runId}", e)
            return ResponseEntity.status(500).body(mapOf("error" to "internal_error"))
        }

        return ResponseEntity.ok(result)
    }
}

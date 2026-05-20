package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.WebhookSubscription
import com.strata.ftw.domain.repository.WebhookSubscriptionRepository
import com.strata.ftw.web.dto.WebhookCreateDto
import com.strata.ftw.web.dto.WebhookSummaryDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Outbound webhook subscription management.
 *
 * Buyers register a URL + shared secret here; on every price.changed event
 * we POST a signed payload (X-PriceGrid-Signature: sha256=<hex>) to the URL.
 * Multi-tenant productization will scope this by tenant_id (from API key).
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@CrossOrigin(origins = ["*"], allowCredentials = "false")
@Tag(name = "Webhooks", description = "Subscribe to price.changed events.")
class WebhooksController(
    private val repo: WebhookSubscriptionRepository
) {

    @Operation(
        summary = "List webhook subscriptions",
        description = "Returns all subscriptions for the calling tenant. (Today: all subscriptions, since auth is single-tenant.)"
    )
    @GetMapping
    fun list(): ResponseEntity<Any> {
        val subs = repo.findAll().map { toDto(it) }
        return ResponseEntity.ok(mapOf("count" to subs.size, "webhooks" to subs))
    }

    @Operation(
        summary = "Register a webhook subscription",
        description = "POST a JSON body with name, url, secret, and optional event_types array. Returns the created subscription. The secret is stored as-is — keep it private to verify incoming signatures."
    )
    @PostMapping
    fun create(@RequestBody body: WebhookCreateDto): ResponseEntity<Any> {
        if (body.url.isBlank() || !body.url.startsWith("http")) {
            return ResponseEntity.badRequest().body(mapOf("error" to "invalid_url"))
        }
        if (body.secret.length < 16) {
            return ResponseEntity.badRequest().body(mapOf("error" to "secret_too_short", "min_length" to 16))
        }

        val eventTypes = (body.eventTypes ?: listOf("price.changed")).joinToString(",")
        val sub = WebhookSubscription(
            name = body.name.ifBlank { "unnamed" },
            url = body.url,
            secret = body.secret,
            eventTypes = eventTypes,
            active = true
        )
        val saved = repo.save(sub)
        return ResponseEntity.status(201).body(toDto(saved))
    }

    @Operation(summary = "Delete a webhook subscription")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Any> {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "invalid_id"))
        if (!repo.existsById(uuid)) {
            return ResponseEntity.status(404).body(mapOf("error" to "not_found"))
        }
        repo.deleteById(uuid)
        return ResponseEntity.noContent().build()
    }

    private fun toDto(s: WebhookSubscription): WebhookSummaryDto = WebhookSummaryDto(
        id = s.id!!.toString(),
        name = s.name,
        url = s.url,
        eventTypes = s.eventTypes.split(",").map { it.trim() },
        active = s.active,
        lastDeliveryAt = s.lastDeliveryAt?.toString(),
        lastDeliveryStatus = s.lastDeliveryStatus,
        consecutiveFailures = s.consecutiveFailures,
        insertedAt = s.insertedAt?.toString()
    )
}

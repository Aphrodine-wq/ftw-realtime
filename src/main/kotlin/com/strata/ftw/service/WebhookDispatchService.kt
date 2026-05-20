package com.strata.ftw.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.strata.ftw.domain.entity.PriceAlert
import com.strata.ftw.domain.entity.WebhookSubscription
import com.strata.ftw.domain.repository.MaterialRepository
import com.strata.ftw.domain.repository.WebhookSubscriptionRepository
import com.strata.ftw.web.filter.WebhookSignature
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Dispatches outbound webhook events to subscribed buyer endpoints.
 *
 * Same wire format as our inbound HMAC pattern (X-FTW-Signature → renamed
 * X-PriceGrid-Signature for outbound). Buyers verify with the secret they
 * registered. Same shape as GitHub's X-Hub-Signature-256 — devs already know
 * how to verify this.
 *
 * Delivery is fire-and-forget over @Async. Failures bump the subscription's
 * `consecutive_failures` counter; 5 consecutive failures auto-deactivates
 * the subscription (the buyer's URL is dead).
 */
@Service
class WebhookDispatchService(
    private val subscriptionRepo: WebhookSubscriptionRepository,
    private val materialRepo: MaterialRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val deliveryTimeout = Duration.ofSeconds(10)

    /**
     * Fan out a batch of price alerts to every active subscriber whose
     * event_types includes "price.changed". Idempotent on the subscriber
     * side via the `delivery_id` field (UUID) in the body.
     */
    @Async
    fun dispatchPriceAlerts(alerts: List<PriceAlert>) {
        if (alerts.isEmpty()) return
        val subs = subscriptionRepo.findByActiveTrueOrderByInsertedAtAsc()
            .filter { it.eventTypes.split(",").any { e -> e.trim() == "price.changed" } }
        if (subs.isEmpty()) {
            log.debug("dispatchPriceAlerts: no active subscribers, skipping {} alerts", alerts.size)
            return
        }

        // Resolve material keys once so the payload is buyer-friendly.
        val materialIds = alerts.mapNotNull { it.materialId }.distinct()
        val materials = materialRepo.findAllById(materialIds).associateBy { it.id!! }

        val events = alerts.mapNotNull { a ->
            val mat = materials[a.materialId] ?: return@mapNotNull null
            mapOf(
                "delivery_id" to java.util.UUID.randomUUID().toString(),
                "event" to "price.changed",
                "occurred_at" to Instant.now().toString(),
                "data" to mapOf(
                    "material_key" to mat.materialKey,
                    "material_name" to mat.name,
                    "category" to mat.category.name,
                    "source" to a.source,
                    "previous_price_cents" to a.previousPriceCents,
                    "current_price_cents" to a.currentPriceCents,
                    "change_pct" to a.changePct,
                    "direction" to a.direction.name,
                    "scrape_run_id" to a.scrapeRunId
                )
            )
        }

        for (sub in subs) {
            for (event in events) {
                deliverOne(sub, event)
            }
        }
    }

    @Transactional
    fun deliverOne(sub: WebhookSubscription, event: Map<String, Any?>) {
        val body = objectMapper.writeValueAsString(event)
        val signature = "sha256=" + WebhookSignature.computeHex(body, sub.secret)

        val req = HttpRequest.newBuilder(URI.create(sub.url))
            .timeout(deliveryTimeout)
            .header("Content-Type", "application/json")
            .header("X-PriceGrid-Signature", signature)
            .header("X-PriceGrid-Event", event["event"]?.toString() ?: "unknown")
            .header("X-PriceGrid-Delivery", event["delivery_id"]?.toString() ?: "")
            .header("User-Agent", "PriceGrid-Webhooks/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        try {
            val res = http.send(req, HttpResponse.BodyHandlers.discarding())
            sub.lastDeliveryAt = Instant.now()
            sub.lastDeliveryStatus = res.statusCode()
            if (res.statusCode() in 200..299) {
                sub.consecutiveFailures = 0
            } else {
                sub.consecutiveFailures += 1
                log.warn("webhook {} → {} returned {}", sub.id, sub.url, res.statusCode())
                maybeDeactivate(sub)
            }
        } catch (e: Exception) {
            sub.lastDeliveryAt = Instant.now()
            sub.lastDeliveryStatus = -1
            sub.consecutiveFailures += 1
            log.warn("webhook {} → {} failed: {}", sub.id, sub.url, e.message)
            maybeDeactivate(sub)
        }
        subscriptionRepo.save(sub)
    }

    private fun maybeDeactivate(sub: WebhookSubscription) {
        if (sub.consecutiveFailures >= 5) {
            log.error("webhook {} deactivated after {} consecutive failures", sub.id, sub.consecutiveFailures)
            sub.active = false
        }
    }
}

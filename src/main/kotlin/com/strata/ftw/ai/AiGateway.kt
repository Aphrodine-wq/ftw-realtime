package com.strata.ftw.ai

import com.strata.ftw.domain.repository.FairPriceEntryRepository
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import jakarta.annotation.PostConstruct

@Service
class AiGateway(
    private val fairPriceEntryRepository: FairPriceEntryRepository,
    @Value("\${app.ai.runpod-url:}") private val runpodUrl: String
) {
    private val restTemplate = RestTemplate()

    // FairPrice: in-memory cache loaded from DB
    private val fairPriceCache = ConcurrentHashMap<String, Map<String, Any>>()

    // FairScope: Caffeine cache with 7-day TTL
    private val fairScopeCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays(7))
        .maximumSize(10_000)
        .build<String, Map<String, Any>>()

    // Cost tracking
    private val costCounters = ConcurrentHashMap<String, AtomicLong>()

    val categories = listOf(
        "General Contracting", "Plumbing", "Electrical", "HVAC", "Roofing",
        "Painting", "Flooring", "Landscaping", "Remodeling", "Concrete", "Fencing", "Drywall"
    )
    val sizes = listOf("small", "medium", "large", "major")

    @PostConstruct
    fun loadFairPrices() {
        val entries = fairPriceEntryRepository.findAll()
        entries.forEach { entry ->
            val key = "${entry.category}|${entry.zipPrefix}|${entry.size}"
            fairPriceCache[key] = mapOf(
                "low" to entry.low,
                "high" to entry.high,
                "materials_pct" to (entry.materialsPct ?: 0.0),
                "labor_pct" to (entry.laborPct ?: 0.0),
                "confidence" to (entry.confidence ?: "medium"),
                "computed_at" to (entry.updatedAt?.toString() ?: "")
            )
        }
    }

    fun fairPrice(category: String, zip: String, size: String): Map<String, Any>? {
        val zipPrefix = zip.take(3)
        val key = "$category|$zipPrefix|$size"
        recordCost("fair_price", "cache_hit")
        return fairPriceCache[key]
    }

    fun fairScope(category: String, title: String, areas: List<String>, materials: String?): Map<String, Any> {
        val cacheKey = "$category|${areas.sorted().hashCode()}|${materials ?: "standard"}"
        val cached = fairScopeCache.getIfPresent(cacheKey)
        if (cached != null) {
            recordCost("fair_scope", "cache_hit")
            return cached + ("cached" to true)
        }

        recordCost("fair_scope", "inference")

        if (runpodUrl.isBlank()) {
            return mapOf("scope" to "RunPod URL not configured", "cached" to false)
        }

        return try {
            val result = restTemplate.postForObject(
                "$runpodUrl/scope",
                mapOf("category" to category, "title" to title, "areas" to areas, "materials" to materials),
                Map::class.java
            ) as? Map<String, Any> ?: mapOf("scope" to "No response", "cached" to false)

            fairScopeCache.put(cacheKey, result)
            result + ("cached" to false)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Scope generation failed"), "cached" to false)
        }
    }

    fun estimate(description: String): Map<String, Any> {
        recordCost("estimate_agent", "inference")

        if (runpodUrl.isBlank()) {
            return mapOf("error" to "RunPod URL not configured")
        }

        return try {
            val result = restTemplate.postForObject(
                "$runpodUrl/estimate",
                mapOf("description" to description),
                Map::class.java
            ) as? Map<String, Any> ?: mapOf("error" to "No response")
            result
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Estimation failed"))
        }
    }

    fun dailyStats(): Map<String, Any> {
        val today = LocalDate.now().toString()
        val features = listOf("fair_price", "fair_scope", "estimate_agent")
        return features.associateWith { feature ->
            val hits = costCounters["$feature|$today|cache_hit"]?.get() ?: 0
            val inferences = costCounters["$feature|$today|inference"]?.get() ?: 0
            mapOf(
                "cache_hits" to hits,
                "inferences" to inferences,
                "total_requests" to (hits + inferences),
                "estimated_cost_cents" to inferences * 0.2
            )
        }
    }

    fun refreshFairPrices() {
        fairPriceCache.clear()
        loadFairPrices()
    }

    fun cleanupFairScope(): Int {
        fairScopeCache.cleanUp()
        return fairScopeCache.estimatedSize().toInt()
    }

    private fun recordCost(feature: String, type: String) {
        val today = LocalDate.now().toString()
        val key = "$feature|$today|$type"
        costCounters.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }
}

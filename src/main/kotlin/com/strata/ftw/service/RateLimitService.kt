package com.strata.ftw.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-API-key token-bucket rate limiter.
 *
 * In-memory only — production at scale would back this with Redis (Bucket4j
 * has a Redis driver). For a single-instance deployment this is correct and
 * fast. The buyer can swap the storage in 30 lines without touching call
 * sites.
 *
 * Bucket capacity = the configured per-hour limit. Refill is the same
 * capacity over 1 hour, intervally — meaning a smooth refill rather than
 * a single big restock at the top of the hour.
 */
@Service
class RateLimitService {
    private val buckets = ConcurrentHashMap<UUID, BucketEntry>()

    fun tryConsume(apiKeyId: UUID, capacityPerHour: Int): RateLimitOutcome {
        val entry = buckets.compute(apiKeyId) { _, existing ->
            if (existing != null && existing.capacity == capacityPerHour) existing
            else BucketEntry(capacityPerHour, newBucket(capacityPerHour))
        }!!

        val probe = entry.bucket.tryConsumeAndReturnRemaining(1)
        return RateLimitOutcome(
            allowed = probe.isConsumed,
            remaining = probe.remainingTokens,
            limit = capacityPerHour.toLong(),
            retryAfterSeconds = if (probe.isConsumed) 0 else (probe.nanosToWaitForRefill / 1_000_000_000L) + 1
        )
    }

    private fun newBucket(capacityPerHour: Int): Bucket {
        val limit = Bandwidth.builder()
            .capacity(capacityPerHour.toLong())
            .refillIntervally(capacityPerHour.toLong(), Duration.ofHours(1))
            .build()
        return Bucket.builder().addLimit(limit).build()
    }

    private data class BucketEntry(val capacity: Int, val bucket: Bucket)
}

data class RateLimitOutcome(
    val allowed: Boolean,
    val remaining: Long,
    val limit: Long,
    val retryAfterSeconds: Long
)

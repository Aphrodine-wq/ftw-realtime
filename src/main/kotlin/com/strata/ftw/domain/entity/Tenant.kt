package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Per-customer tenant in the productized PriceGrid API.
 *
 * The default tenant ID `00000000-0000-0000-0000-000000000001` is reserved
 * for the legacy/single-tenant FTW deployment. New buyers get fresh UUIDs.
 */
@Entity
@Table(name = "tenants")
class Tenant(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false, length = 120)
    var name: String = "",

    @Column(nullable = false, length = 60, unique = true)
    var slug: String = "",

    /** Tier governs rate limits, history retention, webhook count. */
    @Column(nullable = false, length = 40)
    var tier: String = "starter",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var config: String = "{}",

    @Column(nullable = false)
    var active: Boolean = true,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
) {
    companion object {
        val DEFAULT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

        /** Per-tier rate limits in requests/hour. Override on api_key.rate_limit_per_hour. */
        val TIER_RATE_LIMITS = mapOf(
            "free"       to 100,
            "starter"    to 1_000,
            "pro"        to 10_000,
            "enterprise" to 100_000
        )

        fun rateLimitForTier(tier: String): Int =
            TIER_RATE_LIMITS[tier.lowercase()] ?: TIER_RATE_LIMITS["starter"]!!
    }
}

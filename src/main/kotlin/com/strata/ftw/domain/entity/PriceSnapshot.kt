package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "price_snapshots")
class PriceSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    var material: Material? = null,

    @Column(name = "material_id", insertable = false, updatable = false)
    var materialId: UUID? = null,

    // Source is a free-form String at the DB level (enum can't represent
    // "84_lumber" — Kotlin enum constants can't start with a digit). The API
    // layer validates against PriceSource.VALID at ingest time.
    @Column(nullable = false, length = 32)
    var source: String = "",

    @Column(name = "source_url", nullable = false, length = 1024)
    var sourceUrl: String = "",

    var sku: String? = null,

    // Stored in cents. Long is safer than Int for forward compatibility, even
    // though no real construction material costs >$21M.
    @Column(name = "price_cents", nullable = false)
    var priceCents: Long = 0,

    @Column(nullable = false, length = 3)
    var currency: String = "USD",

    @Column(name = "in_stock")
    var inStock: Boolean? = null,

    @Column(name = "store_location")
    var storeLocation: String? = null,

    @Column(name = "scraped_at", nullable = false)
    var scrapedAt: Instant = Instant.now(),

    @Column(name = "price_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var priceType: PriceType = PriceType.regular,

    @Column(name = "scrape_run_id")
    var scrapeRunId: String? = null,

    @Column(name = "tenant_id", columnDefinition = "uuid")
    var tenantId: UUID? = Tenant.DEFAULT_ID,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null
)

object PriceSource {
    const val HOME_DEPOT = "home_depot"
    const val LOWES = "lowes"
    const val MENARDS = "menards"
    const val LUMBER_84 = "84_lumber"
    const val LOCAL_DIST = "local_dist"

    /**
     * Known retailers we display first-class in the UI. Snapshots from
     * other merchants (Amazon, Walmart, regional yards Google attributes)
     * are still accepted — see `isValid()` for the actual ingest gate.
     */
    val FEATURED = setOf(HOME_DEPOT, LOWES, MENARDS, LUMBER_84, LOCAL_DIST)

    private val SLUG = Regex("^[a-z0-9_]{1,32}$")

    fun isValid(source: String): Boolean = SLUG.matches(source)
}

enum class PriceType {
    regular, sale, bulk, pro, clearance
}

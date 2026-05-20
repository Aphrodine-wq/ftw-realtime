package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "price_alerts")
class PriceAlert(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    var material: Material? = null,

    @Column(name = "material_id", insertable = false, updatable = false)
    var materialId: UUID? = null,

    @Column(nullable = false, length = 32)
    var source: String = "",

    @Column(name = "previous_price_cents", nullable = false)
    var previousPriceCents: Long = 0,

    @Column(name = "current_price_cents", nullable = false)
    var currentPriceCents: Long = 0,

    @Column(name = "change_cents", nullable = false)
    var changeCents: Long = 0,

    @Column(name = "change_pct", nullable = false)
    var changePct: Double = 0.0,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var direction: PriceDirection = PriceDirection.up,

    @Column(name = "threshold_pct", nullable = false)
    var thresholdPct: Double = 5.0,

    @Column(name = "scrape_run_id")
    var scrapeRunId: String? = null,

    @Column(nullable = false)
    var acknowledged: Boolean = false,

    @Column(name = "acknowledged_by", columnDefinition = "uuid")
    var acknowledgedBy: UUID? = null,

    @Column(name = "acknowledged_at")
    var acknowledgedAt: Instant? = null,

    @Column(name = "tenant_id", columnDefinition = "uuid")
    var tenantId: UUID? = Tenant.DEFAULT_ID,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null
)

enum class PriceDirection { up, down }

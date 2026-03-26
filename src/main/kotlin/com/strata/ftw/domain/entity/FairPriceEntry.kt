package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "fair_prices",
    uniqueConstraints = [UniqueConstraint(columnNames = ["category", "zip_prefix", "size"])]
)
class FairPriceEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false)
    var category: String = "",

    @Column(name = "zip_prefix", nullable = false)
    var zipPrefix: String = "",

    @Column(nullable = false)
    var size: String = "",

    @Column(nullable = false)
    var low: Int = 0,

    @Column(nullable = false)
    var high: Int = 0,

    @Column(name = "materials_pct")
    var materialsPct: Double? = null,

    @Column(name = "labor_pct")
    var laborPct: Double? = null,

    var confidence: String? = null,

    @Column(name = "raw_response")
    var rawResponse: String? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

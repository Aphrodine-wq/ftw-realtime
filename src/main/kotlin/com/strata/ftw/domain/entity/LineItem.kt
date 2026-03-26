package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "line_items")
class LineItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    var description: String? = null,
    var quantity: Double = 1.0,
    var unit: String? = null,

    @Column(name = "unit_price")
    var unitPrice: Int = 0,

    var total: Int = 0,
    var category: String? = null,

    @Column(name = "sort_order")
    var sortOrder: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id")
    var estimate: Estimate? = null,

    @Column(name = "estimate_id", insertable = false, updatable = false)
    var estimateId: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

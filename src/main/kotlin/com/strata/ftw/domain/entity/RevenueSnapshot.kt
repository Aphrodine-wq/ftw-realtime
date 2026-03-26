package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "revenue_snapshots")
class RevenueSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(unique = true, nullable = false)
    var date: LocalDate? = null,

    @Column(name = "total_revenue")
    var totalRevenue: Int = 0,

    @Column(name = "commission_revenue")
    var commissionRevenue: Int = 0,

    @Column(name = "jobs_completed")
    var jobsCompleted: Int = 0,

    @Column(name = "bids_placed")
    var bidsPlaced: Int = 0,

    @Column(name = "users_signed_up")
    var usersSignedUp: Int = 0,

    @Column(name = "disputes_opened")
    var disputesOpened: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var breakdown: Map<String, Any>? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

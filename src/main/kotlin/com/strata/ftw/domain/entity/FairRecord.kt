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
@Table(name = "fair_records")
class FairRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "public_id", unique = true)
    var publicId: String? = null,

    var category: String? = null,

    @Column(name = "location_city")
    var locationCity: String? = null,

    @Column(name = "scope_summary")
    var scopeSummary: String? = null,

    @Column(name = "estimated_budget")
    var estimatedBudget: Int = 0,

    @Column(name = "final_cost")
    var finalCost: Int = 0,

    @Column(name = "budget_accuracy_pct")
    var budgetAccuracyPct: Double? = null,

    @Column(name = "on_budget")
    var onBudget: Boolean = false,

    @Column(name = "estimated_end_date")
    var estimatedEndDate: LocalDate? = null,

    @Column(name = "actual_completion_date")
    var actualCompletionDate: LocalDate? = null,

    @Column(name = "on_time")
    var onTime: Boolean = false,

    @Column(name = "quality_score_at_completion")
    var qualityScoreAtCompletion: Int = 0,

    @Column(name = "avg_rating")
    var avgRating: Double = 0.0,

    @Column(name = "review_count")
    var reviewCount: Int = 0,

    @Column(name = "dispute_count")
    var disputeCount: Int = 0,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var photos: List<String> = emptyList(),

    @Column(name = "homeowner_confirmed")
    var homeownerConfirmed: Boolean = false,

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,

    @Column(name = "signature_hash")
    var signatureHash: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    @Column(name = "project_id", insertable = false, updatable = false)
    var projectId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id")
    var contractor: User? = null,

    @Column(name = "contractor_id", insertable = false, updatable = false)
    var contractorId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homeowner_id")
    var homeowner: User? = null,

    @Column(name = "homeowner_id", insertable = false, updatable = false)
    var homeownerId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    var job: Job? = null,

    @Column(name = "job_id", insertable = false, updatable = false)
    var jobId: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "jobs")
class Job(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false)
    var title: String = "",

    var description: String? = null,

    var category: String? = null,

    @Column(name = "budget_min")
    var budgetMin: Int? = null,

    @Column(name = "budget_max")
    var budgetMax: Int? = null,

    var location: String? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: JobStatus = JobStatus.open,

    @Column(name = "bid_count")
    var bidCount: Int = 0,

    var latitude: Double? = null,
    var longitude: Double? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homeowner_id")
    var homeowner: User? = null,

    @Column(name = "homeowner_id", insertable = false, updatable = false)
    var homeownerId: UUID? = null,

    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY)
    var bids: MutableList<Bid> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class JobStatus {
    open, bidding, awarded, in_progress, completed, disputed, cancelled
}

package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "bids",
    uniqueConstraints = [UniqueConstraint(columnNames = ["job_id", "contractor_id"])]
)
class Bid(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false)
    var amount: Int = 0,

    var message: String? = null,
    var timeline: String? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: BidStatus = BidStatus.pending,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    var job: Job? = null,

    @Column(name = "job_id", insertable = false, updatable = false)
    var jobId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id")
    var contractor: User? = null,

    @Column(name = "contractor_id", insertable = false, updatable = false)
    var contractorId: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class BidStatus {
    pending, accepted, rejected, withdrawn
}

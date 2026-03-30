package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "sub_bids",
    uniqueConstraints = [UniqueConstraint(columnNames = ["sub_job_id", "sub_contractor_id"])]
)
class SubBid(
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
    @JoinColumn(name = "sub_job_id")
    var subJob: SubJob? = null,

    @Column(name = "sub_job_id", insertable = false, updatable = false)
    var subJobId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_contractor_id")
    var subContractor: User? = null,

    @Column(name = "sub_contractor_id", insertable = false, updatable = false)
    var subContractorId: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

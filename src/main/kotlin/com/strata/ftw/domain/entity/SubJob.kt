package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sub_jobs")
class SubJob(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "contractor_id", nullable = false)
    var contractorId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id", insertable = false, updatable = false)
    var contractor: User? = null,

    @Column(name = "project_id", nullable = false)
    var projectId: String = "",

    @Column(name = "milestone_label", nullable = false)
    var milestoneLabel: String = "",

    @Column(name = "milestone_index")
    var milestoneIndex: Int = 0,

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    var category: String? = null,
    var skills: String? = null,  // Comma-separated
    var location: String? = null,

    @Column(name = "budget_min")
    var budgetMin: Int? = null,

    @Column(name = "budget_max")
    var budgetMax: Int? = null,

    @Column(name = "payment_path", nullable = false)
    @Enumerated(EnumType.STRING)
    var paymentPath: SubPaymentPath = SubPaymentPath.contractor_escrow,

    @Column(name = "disclosed_to_owner")
    var disclosedToOwner: Boolean = false,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: SubJobStatus = SubJobStatus.open,

    var deadline: Instant? = null,

    @Column(name = "bid_count")
    var bidCount: Int = 0,

    @OneToMany(mappedBy = "subJob", fetch = FetchType.LAZY)
    var subBids: MutableList<SubBid> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class SubJobStatus {
    open, in_progress, completed, cancelled
}

enum class SubPaymentPath {
    contractor_escrow, passthrough_escrow
}

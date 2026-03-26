package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "estimates")
class Estimate(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false)
    var title: String = "",

    var description: String? = null,
    var total: Int = 0,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: EstimateStatus = EstimateStatus.draft,

    @Column(name = "valid_until")
    var validUntil: Instant? = null,

    var notes: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id")
    var contractor: User? = null,

    @Column(name = "contractor_id", insertable = false, updatable = false)
    var contractorId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    var client: Client? = null,

    @Column(name = "client_id", insertable = false, updatable = false)
    var clientId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    var job: Job? = null,

    @Column(name = "job_id", insertable = false, updatable = false)
    var jobId: UUID? = null,

    @OneToMany(mappedBy = "estimate", cascade = [CascadeType.ALL], orphanRemoval = true)
    var lineItems: MutableList<LineItem> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class EstimateStatus {
    draft, sent, viewed, accepted, declined, expired
}

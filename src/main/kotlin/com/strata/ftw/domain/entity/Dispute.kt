package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "disputes")
class Dispute(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false)
    var reason: String = "",

    @Column(nullable = false)
    var status: String = "open",

    var description: String? = null,

    @Column(name = "resolution_notes")
    var resolutionNotes: String? = null,

    @Column(name = "opened_at")
    var openedAt: Instant? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    var job: Job? = null,

    @Column(name = "job_id", insertable = false, updatable = false)
    var jobId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by")
    var opener: User? = null,

    @Column(name = "opened_by", insertable = false, updatable = false)
    var openedBy: UUID? = null,

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
    @JoinColumn(name = "resolved_by")
    var resolver: User? = null,

    @Column(name = "resolved_by", insertable = false, updatable = false)
    var resolvedBy: UUID? = null,

    @OneToMany(mappedBy = "dispute", fetch = FetchType.LAZY)
    var evidence: MutableList<DisputeEvidence> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

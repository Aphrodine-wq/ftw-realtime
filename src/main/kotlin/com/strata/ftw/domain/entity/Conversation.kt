package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "conversations",
    uniqueConstraints = [UniqueConstraint(columnNames = ["job_id", "homeowner_id", "contractor_id"])]
)
class Conversation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    var job: Job? = null,

    @Column(name = "job_id", insertable = false, updatable = false)
    var jobId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homeowner_id")
    var homeowner: User? = null,

    @Column(name = "homeowner_id", insertable = false, updatable = false)
    var homeownerId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id")
    var contractor: User? = null,

    @Column(name = "contractor_id", insertable = false, updatable = false)
    var contractorId: UUID? = null,

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    var messages: MutableList<Message> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

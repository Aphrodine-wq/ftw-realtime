package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_flags")
class ContentFlag(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "entity_type", nullable = false)
    var entityType: String = "",

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    var entityId: UUID? = null,

    @Column(nullable = false)
    var reason: String = "",

    @Column(nullable = false)
    var status: String = "open",

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    var notes: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flagged_by")
    var flagger: User? = null,

    @Column(name = "flagged_by", insertable = false, updatable = false)
    var flaggedBy: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    var resolver: User? = null,

    @Column(name = "resolved_by", insertable = false, updatable = false)
    var resolvedBy: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

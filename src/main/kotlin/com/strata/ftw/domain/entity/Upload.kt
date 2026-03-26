package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "uploads")
class Upload(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false)
    var filename: String = "",

    @Column(name = "content_type", nullable = false)
    var contentType: String = "",

    var size: Int? = null,

    @Column(nullable = false)
    var path: String = "",

    @Column(name = "entity_type", nullable = false)
    var entityType: String = "",

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    var entityId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    var uploader: User? = null,

    @Column(name = "uploader_id", insertable = false, updatable = false)
    var uploaderId: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

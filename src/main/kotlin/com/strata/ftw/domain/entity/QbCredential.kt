package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "qb_credentials")
class QbCredential(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null,

    @Column(name = "realm_id", nullable = false)
    var realmId: String = "",

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    var accessToken: String = "",

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    var refreshToken: String = "",

    @Column(name = "token_expires_at", nullable = false)
    var tokenExpiresAt: Instant = Instant.now(),

    @Column(name = "company_name")
    var companyName: String? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

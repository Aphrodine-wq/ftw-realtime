package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_settings", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])])
class UserSetting(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "notifications_email")
    var notificationsEmail: Boolean = true,

    @Column(name = "notifications_push")
    var notificationsPush: Boolean = true,

    @Column(name = "notifications_sms")
    var notificationsSms: Boolean = false,

    @Column(name = "appearance_theme")
    var appearanceTheme: String = "light",

    var language: String = "en",

    var timezone: String = "America/Chicago",

    @Column(name = "privacy_profile_visible")
    var privacyProfileVisible: Boolean = true,

    @Column(name = "privacy_show_rating")
    var privacyShowRating: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @Column(name = "user_id", insertable = false, updatable = false)
    var userId: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

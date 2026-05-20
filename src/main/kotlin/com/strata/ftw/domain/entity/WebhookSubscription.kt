package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "webhook_subscriptions")
class WebhookSubscription(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    // Reserved for the multi-tenant productized version. NULL = the single
    // shared tenant (FTW itself, today).
    @Column(name = "tenant_id", columnDefinition = "uuid")
    var tenantId: UUID? = null,

    @Column(nullable = false, length = 120)
    var name: String = "",

    @Column(nullable = false, length = 2048)
    var url: String = "",

    @Column(nullable = false, length = 128)
    var secret: String = "",

    /**
     * Comma-separated event types this subscription cares about.
     * Currently only `price.changed` exists. Future: `material.added`,
     * `scrape.failed`, `alert.acknowledged`.
     */
    @Column(name = "event_types", nullable = false, length = 255)
    var eventTypes: String = "price.changed",

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "last_delivery_at")
    var lastDeliveryAt: Instant? = null,

    @Column(name = "last_delivery_status")
    var lastDeliveryStatus: Int? = null,

    @Column(name = "consecutive_failures", nullable = false)
    var consecutiveFailures: Int = 0,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

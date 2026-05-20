package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Audit log entry — every catalog/webhook/api-key change writes one row.
 *
 * Table is the SOC-2 evidence trail buyers ask about during due diligence.
 * We never delete from this table; aged rows can be archived to cold
 * storage but the live table is append-only.
 */
@Entity
@Table(name = "audit_log")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "tenant_id", columnDefinition = "uuid")
    var tenantId: UUID? = null,

    /** "api_key:<prefix>" or "system" or "user:<email>". */
    @Column(length = 120)
    var actor: String? = null,

    @Column(nullable = false, length = 60)
    var entity: String = "",

    @Column(name = "entity_id", columnDefinition = "uuid")
    var entityId: UUID? = null,

    @Column(nullable = false, length = 40)
    var action: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    var beforeJson: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    var afterJson: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null
)

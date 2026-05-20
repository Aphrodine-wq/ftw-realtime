package com.strata.ftw.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.strata.ftw.domain.entity.AuditLog
import com.strata.ftw.domain.repository.AuditLogRepository
import com.strata.ftw.web.filter.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Tiny helper for writing audit_log rows. Every catalog/webhook/api-key
 * mutation should pass through this. The buyer's compliance team uses
 * this table for SOC 2 evidence during their audit.
 *
 * Failures inserting audit rows are *swallowed* — we never want a missing
 * audit record to cascade into a failed business operation. The log line
 * surfaces them.
 */
@Service
class AuditLogService(
    private val repo: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun record(
        entity: String,
        action: String,
        entityId: UUID? = null,
        before: Any? = null,
        after: Any? = null,
        metadata: Map<String, Any?>? = null
    ) {
        try {
            val ctx = TenantContext.current()
            val actor = when {
                ctx.apiKeyPrefix != null -> "api_key:${ctx.apiKeyPrefix}"
                else -> "system"
            }
            repo.save(
                AuditLog(
                    tenantId = ctx.tenantId,
                    actor = actor,
                    entity = entity,
                    entityId = entityId,
                    action = action,
                    beforeJson = before?.let { objectMapper.writeValueAsString(it) },
                    afterJson = after?.let { objectMapper.writeValueAsString(it) },
                    metadata = metadata?.let { objectMapper.writeValueAsString(it) }
                )
            )
        } catch (e: Exception) {
            log.warn("audit_log write skipped (entity={}, action={}): {}", entity, action, e.message)
        }
    }
}

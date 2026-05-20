package com.strata.ftw.web.filter

import com.strata.ftw.domain.entity.Tenant
import java.util.UUID

/**
 * Per-request tenant context. Set by ApiKeyAuthFilter, read by services
 * and controllers that need tenant-scoped queries.
 *
 * Stored in a ThreadLocal because Spring's request-scoped beans don't play
 * well with @Async dispatch. Manual cleanup in the filter's finally block.
 */
object TenantContext {
    private val tl = ThreadLocal<TenantInfo>()

    fun set(info: TenantInfo) = tl.set(info)
    fun current(): TenantInfo = tl.get() ?: DEFAULT
    fun currentTenantId(): UUID = current().tenantId
    fun clear() = tl.remove()

    val DEFAULT = TenantInfo(
        tenantId = Tenant.DEFAULT_ID,
        tier = "enterprise",
        apiKeyId = null,
        apiKeyPrefix = null
    )
}

data class TenantInfo(
    val tenantId: UUID,
    val tier: String,
    val apiKeyId: UUID?,
    val apiKeyPrefix: String?
)

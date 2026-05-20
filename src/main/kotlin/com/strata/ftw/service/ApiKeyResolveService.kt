package com.strata.ftw.service

import com.strata.ftw.domain.entity.ApiKey
import com.strata.ftw.domain.entity.Tenant
import com.strata.ftw.domain.repository.ApiKeyRepository
import com.strata.ftw.domain.repository.TenantRepository
import com.strata.ftw.web.filter.TenantInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Extracted from ApiKeyAuthFilter so the @Transactional boundary lives on
 * a Spring service, not on the OncePerRequestFilter (which CGLib can't
 * proxy without breaking init).
 */
@Service
class ApiKeyResolveService(
    private val apiKeyRepo: ApiKeyRepository,
    private val tenantRepo: TenantRepository,
    private val rateLimit: RateLimitService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun resolve(rawKey: String, remoteIp: String?): Resolved? {
        val hash = ApiKey.sha256Hex(rawKey)
        val key = apiKeyRepo.findByKeyHash(hash) ?: return null
        if (!key.active) return null
        if (key.expiresAt != null && key.expiresAt!!.isBefore(Instant.now())) return null

        val tenant = tenantRepo.findById(key.tenantId).orElse(null) ?: return null
        if (!tenant.active) return null

        val limit = key.rateLimitPerHour ?: Tenant.rateLimitForTier(tenant.tier)
        val outcome = rateLimit.tryConsume(key.id!!, limit)

        try {
            key.lastUsedAt = Instant.now()
            key.lastUsedIp = remoteIp?.take(64)
            apiKeyRepo.save(key)
        } catch (e: Exception) {
            log.debug("api_key last_used update skipped: {}", e.message)
        }

        return Resolved(
            tenantInfo = TenantInfo(
                tenantId = tenant.id!!,
                tier = tenant.tier,
                apiKeyId = key.id,
                apiKeyPrefix = key.keyPrefix
            ),
            rateLimitOutcome = outcome
        )
    }

    data class Resolved(
        val tenantInfo: TenantInfo,
        val rateLimitOutcome: RateLimitOutcome
    )
}

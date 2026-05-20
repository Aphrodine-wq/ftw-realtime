package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.ApiKey
import com.strata.ftw.domain.entity.Tenant
import com.strata.ftw.domain.repository.ApiKeyRepository
import com.strata.ftw.domain.repository.AuditLogRepository
import com.strata.ftw.domain.repository.TenantRepository
import com.strata.ftw.service.AuditLogService
import com.strata.ftw.web.dto.ApiKeyCreateDto
import com.strata.ftw.web.dto.ApiKeyCreateResponseDto
import com.strata.ftw.web.dto.ApiKeySummaryDto
import com.strata.ftw.web.dto.TenantCreateDto
import com.strata.ftw.web.dto.TenantSummaryDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Admin API for managing tenants and API keys in the productized PriceGrid
 * deployment.
 *
 * Today these endpoints are ungated (callable from anywhere). The buyer's
 * dashboard fronts them with their own auth (Auth0/Clerk/etc.) and a
 * reverse proxy. Documented behavior — admin endpoints expect to be called
 * from a trusted backend, not the public internet.
 */
@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = ["*"], allowCredentials = "false")
@Tag(name = "Admin — Tenants & API Keys", description = "Tenant + API key management for the buyer's dashboard.")
class PriceGridAdminController(
    private val tenantRepo: TenantRepository,
    private val apiKeyRepo: ApiKeyRepository,
    private val auditLogRepo: AuditLogRepository,
    private val auditLog: AuditLogService
) {

    // ── Tenants ──────────────────────────────────────────────────────

    @Operation(summary = "List all tenants")
    @GetMapping("/tenants")
    fun listTenants(): ResponseEntity<Any> {
        val rows = tenantRepo.findAll().map(::toTenantDto)
        return ResponseEntity.ok(mapOf("count" to rows.size, "tenants" to rows))
    }

    @Operation(summary = "Create a tenant")
    @PostMapping("/tenants")
    fun createTenant(@RequestBody body: TenantCreateDto): ResponseEntity<Any> {
        if (body.name.isBlank() || body.slug.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "name_and_slug_required"))
        }
        if (tenantRepo.findBySlug(body.slug) != null) {
            return ResponseEntity.status(409).body(mapOf("error" to "slug_taken"))
        }
        val tier = body.tier ?: "starter"
        if (tier !in Tenant.TIER_RATE_LIMITS) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "invalid_tier", "valid" to Tenant.TIER_RATE_LIMITS.keys.toList())
            )
        }
        val saved = tenantRepo.save(Tenant(name = body.name, slug = body.slug, tier = tier))
        auditLog.record(
            entity = "tenant",
            action = "created",
            entityId = saved.id,
            after = mapOf("name" to saved.name, "slug" to saved.slug, "tier" to saved.tier)
        )
        return ResponseEntity.status(201).body(toTenantDto(saved))
    }

    // ── API keys ─────────────────────────────────────────────────────

    @Operation(summary = "List API keys (across all tenants)")
    @GetMapping("/api-keys")
    fun listKeys(): ResponseEntity<Any> {
        val rows = apiKeyRepo.findAll().map(::toApiKeyDto)
        return ResponseEntity.ok(mapOf("count" to rows.size, "api_keys" to rows))
    }

    @Operation(
        summary = "Mint a new API key",
        description = "Returns the raw `pgk_live_xxx` key ONCE. Store it client-side — only the sha256 hash is kept on the server."
    )
    @PostMapping("/api-keys")
    fun createKey(@RequestBody body: ApiKeyCreateDto): ResponseEntity<Any> {
        val tenantId = body.tenantId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: Tenant.DEFAULT_ID
        val tenant = tenantRepo.findById(tenantId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "tenant_not_found"))

        val (raw, hash, prefix) = ApiKey.mint()
        val expires = body.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val saved = apiKeyRepo.save(
            ApiKey(
                tenantId = tenant.id!!,
                name = body.name.ifBlank { "unnamed" },
                keyPrefix = prefix,
                keyHash = hash,
                scope = body.scope ?: "read",
                rateLimitPerHour = body.rateLimitPerHour,
                expiresAt = expires
            )
        )

        auditLog.record(
            entity = "api_key",
            action = "created",
            entityId = saved.id,
            after = mapOf(
                "name" to saved.name,
                "tenant_id" to saved.tenantId.toString(),
                "scope" to saved.scope,
                "key_prefix" to saved.keyPrefix
            )
        )

        return ResponseEntity.status(201).body(
            ApiKeyCreateResponseDto(
                id = saved.id!!.toString(),
                name = saved.name,
                tenantId = saved.tenantId.toString(),
                scope = saved.scope,
                rateLimitPerHour = saved.rateLimitPerHour ?: Tenant.rateLimitForTier(tenant.tier),
                apiKey = raw,
                keyPrefix = saved.keyPrefix
            )
        )
    }

    /**
     * Audit log feed. Compliance/SOC-2 evidence — every catalog/webhook/
     * API-key change shows up here, append-only.
     */
    @Operation(summary = "Tenant audit log feed (last 200)")
    @GetMapping("/audit-log")
    fun auditLogFeed(): ResponseEntity<Any> {
        // Cheap implementation — uses the default tenant scope today. With
        // tenant-aware admin auth (productized version), this filters by
        // the calling tenant's ID.
        val all = auditLogRepo.findAll()
        val rows = all.sortedByDescending { it.insertedAt }.take(200).map {
            mapOf(
                "id" to it.id.toString(),
                "tenant_id" to it.tenantId?.toString(),
                "actor" to it.actor,
                "entity" to it.entity,
                "entity_id" to it.entityId?.toString(),
                "action" to it.action,
                "before" to it.beforeJson,
                "after" to it.afterJson,
                "metadata" to it.metadata,
                "inserted_at" to it.insertedAt?.toString()
            )
        }
        return ResponseEntity.ok(mapOf("count" to rows.size, "entries" to rows))
    }

    @Operation(summary = "Revoke an API key")
    @DeleteMapping("/api-keys/{id}")
    fun revokeKey(@PathVariable id: String): ResponseEntity<Any> {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "invalid_id"))
        val key = apiKeyRepo.findById(uuid).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "not_found"))
        key.active = false
        apiKeyRepo.save(key)
        auditLog.record(
            entity = "api_key",
            action = "revoked",
            entityId = key.id,
            after = mapOf("key_prefix" to key.keyPrefix, "active" to false)
        )
        return ResponseEntity.noContent().build()
    }

    // ── Mappers ──────────────────────────────────────────────────────

    private fun toTenantDto(t: Tenant): TenantSummaryDto = TenantSummaryDto(
        id = t.id!!.toString(),
        name = t.name,
        slug = t.slug,
        tier = t.tier,
        active = t.active,
        apiKeyCount = apiKeyRepo.findByTenantIdOrderByInsertedAtDesc(t.id!!).count { it.active },
        insertedAt = t.insertedAt?.toString()
    )

    private fun toApiKeyDto(k: ApiKey): ApiKeySummaryDto = ApiKeySummaryDto(
        id = k.id!!.toString(),
        name = k.name,
        tenantId = k.tenantId.toString(),
        keyPrefix = k.keyPrefix,
        scope = k.scope,
        rateLimitPerHour = k.rateLimitPerHour,
        active = k.active,
        lastUsedAt = k.lastUsedAt?.toString(),
        insertedAt = k.insertedAt?.toString()
    )
}

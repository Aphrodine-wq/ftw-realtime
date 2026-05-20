package com.strata.ftw.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

// ── Tenant management ────────────────────────────────────────────────

data class TenantCreateDto(
    val name: String,
    val slug: String,
    val tier: String? = null
)

data class TenantSummaryDto(
    val id: String,
    val name: String,
    val slug: String,
    val tier: String,
    val active: Boolean,
    @JsonProperty("api_key_count") val apiKeyCount: Int,
    @JsonProperty("inserted_at") val insertedAt: String?
)

// ── API key management ──────────────────────────────────────────────

data class ApiKeyCreateDto(
    val name: String,
    @JsonProperty("tenant_id") val tenantId: String? = null,  // optional — defaults to default tenant
    val scope: String? = null,
    @JsonProperty("rate_limit_per_hour") val rateLimitPerHour: Int? = null,
    @JsonProperty("expires_at") val expiresAt: String? = null
)

/** Returned ONCE on creation. Contains the raw key — never shown again. */
data class ApiKeyCreateResponseDto(
    val id: String,
    val name: String,
    @JsonProperty("tenant_id") val tenantId: String,
    val scope: String,
    @JsonProperty("rate_limit_per_hour") val rateLimitPerHour: Int,
    @JsonProperty("api_key") val apiKey: String,
    @JsonProperty("key_prefix") val keyPrefix: String,
    @JsonProperty("warning") val warning: String = "Store this key — it will never be shown again."
)

data class ApiKeySummaryDto(
    val id: String,
    val name: String,
    @JsonProperty("tenant_id") val tenantId: String,
    @JsonProperty("key_prefix") val keyPrefix: String,
    val scope: String,
    @JsonProperty("rate_limit_per_hour") val rateLimitPerHour: Int?,
    val active: Boolean,
    @JsonProperty("last_used_at") val lastUsedAt: String?,
    @JsonProperty("inserted_at") val insertedAt: String?
)

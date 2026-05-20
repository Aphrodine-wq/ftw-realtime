package com.strata.ftw.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class PriceHealthDto(
    val status: String, // "fresh" | "stale" | "empty"
    @JsonProperty("last_run_at") val lastRunAt: String?,
    @JsonProperty("freshness_minutes") val freshnessMinutes: Long?,
    @JsonProperty("materials_count") val materialsCount: Long,
    @JsonProperty("snapshots_count") val snapshotsCount: Long,
    @JsonProperty("merchant_count") val merchantCount: Long,
    @JsonProperty("alerts_unacknowledged") val alertsUnacknowledged: Long
)

data class RecentAlertDto(
    @JsonProperty("alert_id") val alertId: String,
    @JsonProperty("material_key") val materialKey: String,
    @JsonProperty("material_name") val materialName: String,
    val source: String,
    @JsonProperty("previous_price_cents") val previousPriceCents: Long,
    @JsonProperty("current_price_cents") val currentPriceCents: Long,
    @JsonProperty("change_pct") val changePct: Double,
    val direction: String,
    val acknowledged: Boolean,
    @JsonProperty("inserted_at") val insertedAt: String
)

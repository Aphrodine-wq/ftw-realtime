package com.strata.ftw.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class TrendResponseDto(
    @JsonProperty("material_key") val materialKey: String,
    val name: String,
    @JsonProperty("period_days") val periodDays: Int,
    val interval: String,
    val points: List<TrendPointDto>,
    val sources: Map<String, List<TrendPointDto>>
)

data class TrendPointDto(
    val date: String,            // YYYY-MM-DD
    @JsonProperty("median_price_cents") val medianPriceCents: Long,
    @JsonProperty("min_price_cents") val minPriceCents: Long,
    @JsonProperty("max_price_cents") val maxPriceCents: Long,
    @JsonProperty("merchant_count") val merchantCount: Int
)

data class ForecastResponseDto(
    @JsonProperty("material_key") val materialKey: String,
    val name: String,
    @JsonProperty("horizon_days") val horizonDays: Int,
    @JsonProperty("training_window_days") val trainingWindowDays: Int,
    @JsonProperty("training_points") val trainingPoints: Int,
    @JsonProperty("current_price_cents") val currentPriceCents: Long,
    @JsonProperty("projected_price_cents") val projectedPriceCents: Long,
    @JsonProperty("daily_change_cents") val dailyChangeCents: Double,
    @JsonProperty("daily_change_pct") val dailyChangePct: Double,
    @JsonProperty("r_squared") val rSquared: Double,
    val confidence: String,    // "high" | "medium" | "low" — derived from R²
    @JsonProperty("confidence_band_cents") val confidenceBandCents: Long,
    @JsonProperty("trend_direction") val trendDirection: String  // "up" | "down" | "flat"
)

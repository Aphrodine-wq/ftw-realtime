package com.strata.ftw.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class CurrentPriceDto(
    @JsonProperty("material_key") val materialKey: String,
    val name: String,
    val category: String,
    val subcategory: String?,
    val unit: String,
    val dimensions: String?,
    val brand: String?,
    @JsonProperty("merchant_count") val merchantCount: Int,
    @JsonProperty("median_price_cents") val medianPriceCents: Long,
    @JsonProperty("min_price_cents") val minPriceCents: Long,
    @JsonProperty("max_price_cents") val maxPriceCents: Long,
    val prices: List<MerchantPriceDto>
)

data class MerchantPriceDto(
    val source: String,
    val featured: Boolean,
    @JsonProperty("price_cents") val priceCents: Long,
    val currency: String,
    @JsonProperty("in_stock") val inStock: Boolean?,
    @JsonProperty("source_url") val sourceUrl: String,
    @JsonProperty("scraped_at") val scrapedAt: String
)

data class MaterialDetailDto(
    val current: CurrentPriceDto,
    val history: List<HistoryPointDto>
)

data class HistoryPointDto(
    @JsonProperty("scraped_at") val scrapedAt: String,
    val source: String,
    @JsonProperty("price_cents") val priceCents: Long
)

data class CurrentPricePageDto(
    val count: Int,
    @JsonProperty("next_cursor") val nextCursor: String?,
    val materials: List<CurrentPriceDto>
)

data class BulkLookupRequestDto(
    @JsonProperty("material_keys") val materialKeys: List<String>,
    @JsonProperty("featured_only") val featuredOnly: Boolean = false
)

data class BulkLookupResponseDto(
    val count: Int,
    val materials: List<CurrentPriceDto>,
    @JsonProperty("not_found") val notFound: List<String>
)

data class BestPriceDto(
    @JsonProperty("material_key") val materialKey: String,
    val name: String,
    val unit: String,
    @JsonProperty("price_cents") val priceCents: Long,
    val source: String,
    @JsonProperty("source_url") val sourceUrl: String,
    @JsonProperty("scraped_at") val scrapedAt: String,
    @JsonProperty("merchants_compared") val merchantsCompared: Int,
    @JsonProperty("median_price_cents") val medianPriceCents: Long,
    @JsonProperty("savings_vs_median_pct") val savingsVsMedianPct: Double
)

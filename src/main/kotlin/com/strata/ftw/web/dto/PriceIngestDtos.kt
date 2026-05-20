package com.strata.ftw.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTOs that mirror the JSON schema produced by ftw-scraper at
 * data/prices/YYYY-MM-DD.json. Field names use snake_case to match the
 * scraper output; the global Jackson config (SNAKE_CASE) auto-translates
 * Kotlin property names. Where Kotlin idioms diverge (camelCase vars), we
 * pin field names with @JsonProperty.
 */

data class IngestPayloadDto(
    @JsonProperty("scrape_run") val scrapeRun: ScrapeRunDto,
    val materials: List<IngestMaterialDto> = emptyList(),
    @JsonProperty("price_changes") val priceChanges: List<IngestPriceChangeDto> = emptyList()
)

data class ScrapeRunDto(
    @JsonProperty("run_id") val runId: String,
    @JsonProperty("started_at") val startedAt: String,
    @JsonProperty("completed_at") val completedAt: String,
    @JsonProperty("sources_scraped") val sourcesScraped: List<String> = emptyList(),
    @JsonProperty("store_location") val storeLocation: String? = null,
    @JsonProperty("total_materials") val totalMaterials: Int = 0,
    val errors: List<Map<String, Any?>> = emptyList()
)

data class IngestMaterialDto(
    @JsonProperty("material_id") val materialId: String,
    val name: String,
    val category: String,
    val subcategory: String? = null,
    val unit: String,
    val dimensions: String? = null,
    val brand: String? = null,
    val sku: String? = null,
    val prices: List<IngestPriceSnapshotDto> = emptyList()
)

data class IngestPriceSnapshotDto(
    @JsonProperty("snapshot_id") val snapshotId: String? = null,
    val source: String,
    @JsonProperty("source_url") val sourceUrl: String,
    val price: Long,
    val currency: String = "USD",
    @JsonProperty("in_stock") val inStock: Boolean? = null,
    @JsonProperty("store_location") val storeLocation: String? = null,
    @JsonProperty("scraped_at") val scrapedAt: String,
    @JsonProperty("price_type") val priceType: String = "regular"
)

data class IngestPriceChangeDto(
    @JsonProperty("material_id") val materialId: String,
    val name: String? = null,
    val category: String? = null,
    val source: String,
    @JsonProperty("previous_price") val previousPrice: Long,
    @JsonProperty("current_price") val currentPrice: Long,
    @JsonProperty("change_pct") val changePct: Double,
    val direction: String,
    @JsonProperty("threshold_pct") val thresholdPct: Double? = null
)

data class IngestResultDto(
    @JsonProperty("run_id") val runId: String,
    @JsonProperty("materials_upserted") val materialsUpserted: Int,
    @JsonProperty("snapshots_inserted") val snapshotsInserted: Int,
    @JsonProperty("alerts_created") val alertsCreated: Int,
    val skipped: Boolean = false,
    @JsonProperty("skip_reason") val skipReason: String? = null
)

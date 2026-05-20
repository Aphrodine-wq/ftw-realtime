package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.MaterialCategory
import com.strata.ftw.service.MaterialPricingReadService
import com.strata.ftw.service.PriceForecastService
import com.strata.ftw.web.dto.BulkLookupRequestDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Public read endpoints for the PriceGrid material price API.
 *
 * CORS is wide-open (any origin, no credentials) so buyers can embed live
 * prices in their dashboards or marketing sites without proxying through
 * their backend. The endpoints return no PII and no tenant-scoped data.
 */
@RestController
@RequestMapping("/api/v1/prices")
@CrossOrigin(origins = ["*"], allowCredentials = "false")
@Tag(name = "Prices — Public Reads", description = "Live multi-merchant building material prices.")
class PricesReadController(
    private val readService: MaterialPricingReadService,
    private val forecastService: PriceForecastService
) {

    /**
     * Current prices across all tracked materials. One row per material with
     * latest price from every merchant we know about.
     *
     * GET /api/v1/prices/current
     * GET /api/v1/prices/current?category=lumber
     * GET /api/v1/prices/current?featured_only=true
     */
    @Operation(
        summary = "List current prices",
        description = "Returns the latest snapshot per (material, merchant). Defaults to all merchants. Pass featured_only=true to limit to Home Depot, Lowe's, Menards, 84 Lumber, and local distributors. Pagination is opt-in: pass limit and/or cursor and the response shape changes to a paged document."
    )
    @GetMapping("/current")
    fun current(
        @Parameter(description = "Filter by category. Valid values: lumber, concrete, steel, drywall, insulation, roofing.")
        @RequestParam(required = false) category: String?,
        @Parameter(description = "Limit to featured retailers only.")
        @RequestParam(name = "featured_only", required = false, defaultValue = "false") featuredOnly: Boolean,
        @Parameter(description = "Page size (1-500). Triggers paginated response shape.")
        @RequestParam(required = false) limit: Int?,
        @Parameter(description = "Pagination cursor from previous response's next_cursor.")
        @RequestParam(required = false) cursor: String?
    ): ResponseEntity<Any> {
        val cat = category?.let {
            runCatching { MaterialCategory.valueOf(it) }.getOrElse {
                return ResponseEntity.badRequest().body(mapOf("error" to "unknown_category", "valid" to MaterialCategory.entries.map { it.name }))
            }
        }
        // Paginated path when caller asks for it explicitly.
        if (limit != null || cursor != null) {
            val page = readService.currentPricesPage(cat, featuredOnly, limit ?: 100, cursor)
            return ResponseEntity.ok(page)
        }
        val rows = readService.currentPrices(cat, featuredOnly)
        return ResponseEntity.ok(mapOf("count" to rows.size, "materials" to rows))
    }

    /**
     * Look up many materials at once. POST body: { material_keys: [...],
     * featured_only: bool }. Capped at 500 keys per request.
     */
    @PostMapping("/lookup")
    fun lookup(@RequestBody req: BulkLookupRequestDto): ResponseEntity<Any> {
        if (req.materialKeys.size > 500) {
            return ResponseEntity.badRequest().body(mapOf("error" to "too_many_keys", "max" to 500, "received" to req.materialKeys.size))
        }
        return ResponseEntity.ok(readService.bulkLookup(req.materialKeys, req.featuredOnly))
    }

    /**
     * Cheapest current price across featured retailers for one material.
     * Designed for estimate auto-fill — one round trip per line item.
     *
     * GET /api/v1/prices/best/mat_2x4x8_spf
     * GET /api/v1/prices/best/mat_2x4x8_spf?featured_only=false
     */
    @GetMapping("/best/{materialKey}")
    fun best(
        @PathVariable materialKey: String,
        @RequestParam(name = "featured_only", required = false, defaultValue = "true") featuredOnly: Boolean
    ): ResponseEntity<Any> {
        val best = readService.bestPrice(materialKey, featuredOnly)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "no_prices_for_material", "material_key" to materialKey))
        return ResponseEntity.ok(best)
    }

    /**
     * Detail for one tracked material: current prices per merchant + 14d
     * history. Used by the estimate UI when a contractor adds a line item.
     *
     * GET /api/v1/prices/material/mat_2x4x8_spf
     * GET /api/v1/prices/material/mat_2x4x8_spf?history_days=30
     */
    @GetMapping("/material/{materialKey}")
    fun material(
        @PathVariable materialKey: String,
        @RequestParam(name = "history_days", required = false, defaultValue = "14") historyDays: Int
    ): ResponseEntity<Any> {
        val days = historyDays.coerceIn(1, 365)
        val detail = readService.materialDetail(materialKey, days)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "material_not_found", "material_key" to materialKey))
        return ResponseEntity.ok(detail)
    }

    /**
     * Pipeline health — fresh/stale/empty plus row counts. Use this in the
     * admin dashboard, in monitoring, and as a Cloud Routine post-run check.
     *
     * GET /api/v1/prices/health
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Any> {
        return ResponseEntity.ok(readService.health())
    }

    /**
     * Historical trend — daily medians over a configurable period. Returns
     * a combined series and a per-source breakdown. Use this for charts.
     *
     * GET /api/v1/prices/material/mat_2x4x8_spf/trend?period_days=30
     */
    @Operation(
        summary = "Historical price trend",
        description = "Daily-aggregated price points for the last N days. Includes a combined series (median across all merchants) and a per-source breakdown for chart overlays. Defaults to 30 days, max 365."
    )
    @GetMapping("/material/{materialKey}/trend")
    fun trend(
        @PathVariable materialKey: String,
        @RequestParam(name = "period_days", required = false, defaultValue = "30") periodDays: Int,
        @RequestParam(name = "featured_only", required = false, defaultValue = "true") featuredOnly: Boolean
    ): ResponseEntity<Any> {
        val trend = forecastService.trend(materialKey, periodDays, featuredOnly)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "material_not_found"))
        return ResponseEntity.ok(trend)
    }

    /**
     * Linear-regression price forecast — projects N days forward from the
     * trailing 30-day trend. Returns slope, projected price, R²-derived
     * confidence, and trend direction.
     *
     * Honest about its simplicity: this is OLS regression on daily medians,
     * not Prophet/ARIMA. Hook to plug in real ML at PriceForecastService.regression().
     *
     * GET /api/v1/prices/material/mat_2x4x8_spf/forecast?horizon_days=7
     */
    @Operation(
        summary = "Price forecast",
        description = "Linear-regression projection N days forward from the trailing 30-day trend. Surfaces slope (%/day), projected price, R²-derived confidence (high/medium/low), and direction (up/down/flat)."
    )
    @GetMapping("/material/{materialKey}/forecast")
    fun forecast(
        @PathVariable materialKey: String,
        @RequestParam(name = "horizon_days", required = false, defaultValue = "7") horizonDays: Int,
        @RequestParam(name = "training_window_days", required = false, defaultValue = "30") trainingWindowDays: Int,
        @RequestParam(name = "featured_only", required = false, defaultValue = "true") featuredOnly: Boolean
    ): ResponseEntity<Any> {
        val horizon = horizonDays.coerceIn(1, 90)
        val window = trainingWindowDays.coerceIn(7, 365)
        val f = forecastService.forecast(materialKey, horizon, window, featuredOnly)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "material_not_found"))
        return ResponseEntity.ok(f)
    }

    /**
     * CSV export of current prices — one row per (material, merchant) pair.
     * For non-developer buyers (insurance adjusters, GCs, analysts) who want
     * to load prices into Excel or a BI tool. Same query as /current but
     * flattened.
     *
     * GET /api/v1/prices/current.csv
     * GET /api/v1/prices/current.csv?category=lumber&featured_only=true
     */
    @Operation(
        summary = "Export current prices as CSV",
        description = "Same data as /current, flattened to one row per (material, merchant). Streams as text/csv with a Content-Disposition attachment header so browsers prompt to download."
    )
    @GetMapping("/current.csv", produces = ["text/csv"])
    fun currentCsv(
        @RequestParam(required = false) category: String?,
        @RequestParam(name = "featured_only", required = false, defaultValue = "false") featuredOnly: Boolean
    ): ResponseEntity<String> {
        val cat = category?.let { runCatching { MaterialCategory.valueOf(it) }.getOrNull() }
        val rows = readService.currentPrices(cat, featuredOnly)

        val sb = StringBuilder()
        sb.appendLine("material_key,name,category,subcategory,unit,dimensions,brand,source,price_cents,price_dollars,currency,in_stock,scraped_at,source_url")
        for (m in rows) {
            for (p in m.prices) {
                sb.append(csv(m.materialKey)).append(',')
                sb.append(csv(m.name)).append(',')
                sb.append(csv(m.category)).append(',')
                sb.append(csv(m.subcategory ?: "")).append(',')
                sb.append(csv(m.unit)).append(',')
                sb.append(csv(m.dimensions ?: "")).append(',')
                sb.append(csv(m.brand ?: "")).append(',')
                sb.append(csv(p.source)).append(',')
                sb.append(p.priceCents).append(',')
                sb.append("%.2f".format(p.priceCents / 100.0)).append(',')
                sb.append(csv(p.currency)).append(',')
                sb.append(p.inStock?.toString() ?: "").append(',')
                sb.append(csv(p.scrapedAt)).append(',')
                sb.append(csv(p.sourceUrl))
                sb.append('\n')
            }
        }

        return ResponseEntity.ok()
            .header("Content-Type", "text/csv; charset=utf-8")
            .header("Content-Disposition", "attachment; filename=\"pricegrid-${java.time.LocalDate.now()}.csv\"")
            .body(sb.toString())
    }

    private fun csv(s: String): String {
        // Escape per RFC 4180: wrap in quotes if comma/quote/newline, double internal quotes.
        val needsQuoting = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        if (!needsQuoting) return s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }

    /**
     * Recent price alerts (day-over-day moves above per-category threshold).
     * Defaults to unacknowledged only — pass include_acknowledged=true for
     * the full audit log.
     *
     * GET /api/v1/prices/alerts/recent
     * GET /api/v1/prices/alerts/recent?limit=20&include_acknowledged=true
     */
    @GetMapping("/alerts/recent")
    fun recentAlerts(
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(name = "include_acknowledged", required = false, defaultValue = "false") includeAck: Boolean
    ): ResponseEntity<Any> {
        val alerts = readService.recentAlerts(limit, includeAck)
        return ResponseEntity.ok(mapOf("count" to alerts.size, "alerts" to alerts))
    }
}

package com.strata.ftw.service

import com.strata.ftw.domain.entity.Material
import com.strata.ftw.domain.entity.MaterialCategory
import com.strata.ftw.domain.entity.PriceSnapshot
import com.strata.ftw.domain.entity.PriceSource
import com.strata.ftw.domain.repository.MaterialRepository
import com.strata.ftw.domain.repository.PriceAlertRepository
import com.strata.ftw.domain.repository.PriceSnapshotRepository
import com.strata.ftw.web.dto.BestPriceDto
import com.strata.ftw.web.dto.BulkLookupResponseDto
import com.strata.ftw.web.dto.CurrentPriceDto
import com.strata.ftw.web.dto.CurrentPricePageDto
import com.strata.ftw.web.dto.HistoryPointDto
import com.strata.ftw.web.dto.MaterialDetailDto
import com.strata.ftw.web.dto.MerchantPriceDto
import com.strata.ftw.web.dto.PriceHealthDto
import com.strata.ftw.web.dto.RecentAlertDto
import java.util.Base64
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

// Snapshots older than this are considered stale — surfaces missed cron runs.
private const val FRESH_THRESHOLD_HOURS = 36L

@Service
class MaterialPricingReadService(
    private val materialRepository: MaterialRepository,
    private val priceSnapshotRepository: PriceSnapshotRepository,
    private val priceAlertRepository: PriceAlertRepository
) {

    @Transactional(readOnly = true)
    fun currentPrices(category: MaterialCategory? = null, featuredOnly: Boolean = false): List<CurrentPriceDto> {
        val allLatest = priceSnapshotRepository.findLatestPerMaterialAndSource()
        if (allLatest.isEmpty()) return emptyList()

        val materials = materialRepository.findAllById(allLatest.mapNotNull { it.materialId }.distinct())
            .associateBy { it.id!! }

        val grouped = allLatest.groupBy { it.materialId!! }

        return grouped.mapNotNull { (materialId, snaps) ->
            val mat = materials[materialId] ?: return@mapNotNull null
            if (category != null && mat.category != category) return@mapNotNull null

            val filtered = if (featuredOnly) snaps.filter { it.source in PriceSource.FEATURED } else snaps
            if (filtered.isEmpty()) return@mapNotNull null

            buildCurrentPriceDto(mat, filtered)
        }.sortedBy { it.name }
    }

    @Transactional(readOnly = true)
    fun materialDetail(materialKey: String, historyDays: Int = 14): MaterialDetailDto? {
        val mat = materialRepository.findByMaterialKey(materialKey) ?: return null
        val materialId = mat.id ?: return null

        val current = priceSnapshotRepository.findLatestPerMaterialAndSource()
            .filter { it.materialId == materialId }
        if (current.isEmpty()) return null

        val since = Instant.now().minus(historyDays.toLong(), ChronoUnit.DAYS)
        val historySnaps = priceSnapshotRepository.findByMaterialIdSince(materialId, since)
        val history = historySnaps.map {
            HistoryPointDto(
                scrapedAt = it.scrapedAt.toString(),
                source = it.source,
                priceCents = it.priceCents
            )
        }

        return MaterialDetailDto(
            current = buildCurrentPriceDto(mat, current),
            history = history
        )
    }

    /**
     * Paginated cursor-based variant of currentPrices(). The cursor is the
     * material_key of the last item in the previous page, base64-encoded.
     * Sort order is stable on name asc — the cursor strategy assumes that.
     */
    @Transactional(readOnly = true)
    fun currentPricesPage(
        category: MaterialCategory? = null,
        featuredOnly: Boolean = false,
        limit: Int = 100,
        cursor: String? = null
    ): CurrentPricePageDto {
        val all = currentPrices(category, featuredOnly)
        val capped = limit.coerceIn(1, 500)

        val startIdx = cursor?.let { c ->
            val key = decodeCursor(c) ?: return@let 0
            // Cursor points to last-seen-key; we resume *after* it.
            val idx = all.indexOfFirst { it.materialKey == key }
            if (idx < 0) 0 else idx + 1
        } ?: 0

        val slice = all.drop(startIdx).take(capped)
        val nextCursor = if (startIdx + slice.size < all.size && slice.isNotEmpty()) {
            encodeCursor(slice.last().materialKey)
        } else null

        return CurrentPricePageDto(
            count = slice.size,
            nextCursor = nextCursor,
            materials = slice
        )
    }

    /**
     * Look up many materials in one round trip. Buyers integrating an
     * estimating tool will pre-load all line-item materials when an estimate
     * loads — this is the endpoint that supports that workflow.
     */
    @Transactional(readOnly = true)
    fun bulkLookup(materialKeys: List<String>, featuredOnly: Boolean = false): BulkLookupResponseDto {
        if (materialKeys.isEmpty()) return BulkLookupResponseDto(0, emptyList(), emptyList())
        val materials = materialRepository.findByMaterialKeyIn(materialKeys.distinct())
        val byKey = materials.associateBy { it.materialKey }
        val notFound = materialKeys.filter { it !in byKey }

        val allLatest = priceSnapshotRepository.findLatestPerMaterialAndSource()
        val grouped = allLatest.groupBy { it.materialId!! }

        val results = materials.mapNotNull { mat ->
            val snaps = grouped[mat.id!!] ?: return@mapNotNull null
            val filtered = if (featuredOnly) snaps.filter { it.source in PriceSource.FEATURED } else snaps
            if (filtered.isEmpty()) null else buildCurrentPriceDto(mat, filtered)
        }

        return BulkLookupResponseDto(
            count = results.size,
            materials = results,
            notFound = notFound
        )
    }

    /**
     * The 80/20 endpoint for estimate auto-fill. Returns the cheapest current
     * price across featured retailers (configurable). Includes savings-vs-
     * median so the buyer can show "Best price: $3.98 — saves 12% vs.
     * average."
     */
    @Transactional(readOnly = true)
    fun bestPrice(materialKey: String, featuredOnly: Boolean = true): BestPriceDto? {
        val mat = materialRepository.findByMaterialKey(materialKey) ?: return null
        val materialId = mat.id ?: return null

        val all = priceSnapshotRepository.findLatestPerMaterialAndSource().filter { it.materialId == materialId }
        val pool = if (featuredOnly) all.filter { it.source in PriceSource.FEATURED } else all
        if (pool.isEmpty()) return null

        val sorted = pool.sortedBy { it.priceCents }
        val cheapest = sorted.first()
        val median = sorted[sorted.size / 2].priceCents
        val savingsPct = if (median > 0) {
            ((median - cheapest.priceCents).toDouble() / median.toDouble()) * 100.0
        } else 0.0

        return BestPriceDto(
            materialKey = mat.materialKey,
            name = mat.name,
            unit = mat.unit.name,
            priceCents = cheapest.priceCents,
            source = cheapest.source,
            sourceUrl = cheapest.sourceUrl,
            scrapedAt = cheapest.scrapedAt.toString(),
            merchantsCompared = pool.size,
            medianPriceCents = median,
            savingsVsMedianPct = (savingsPct * 10).toInt() / 10.0
        )
    }

    private fun encodeCursor(key: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray(Charsets.UTF_8))

    private fun decodeCursor(cursor: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
    }.getOrNull()

    @Transactional(readOnly = true)
    fun health(): PriceHealthDto {
        val materialsCount = materialRepository.count()
        val snapshotsCount = priceSnapshotRepository.count()
        val merchantCount = priceSnapshotRepository.countDistinctSources()
        val alertsUnack = priceAlertRepository.countUnacknowledged()
        val lastScrape = priceSnapshotRepository.findMaxScrapedAt()

        val (status, freshnessMinutes) = when {
            lastScrape == null -> "empty" to null
            else -> {
                val mins = ChronoUnit.MINUTES.between(lastScrape, Instant.now())
                val s = if (mins <= FRESH_THRESHOLD_HOURS * 60) "fresh" else "stale"
                s to mins
            }
        }

        return PriceHealthDto(
            status = status,
            lastRunAt = lastScrape?.toString(),
            freshnessMinutes = freshnessMinutes,
            materialsCount = materialsCount,
            snapshotsCount = snapshotsCount,
            merchantCount = merchantCount,
            alertsUnacknowledged = alertsUnack
        )
    }

    @Transactional(readOnly = true)
    fun recentAlerts(limit: Int = 50, includeAcknowledged: Boolean = false): List<RecentAlertDto> {
        val pageable = PageRequest.of(0, limit.coerceIn(1, 200))
        val alerts = if (includeAcknowledged) {
            priceAlertRepository.findAllByOrderByInsertedAtDesc(pageable)
        } else {
            priceAlertRepository.findByAcknowledgedFalseOrderByInsertedAtDesc(pageable)
        }
        if (alerts.isEmpty()) return emptyList()

        val materialIds = alerts.mapNotNull { it.materialId }.distinct()
        val materials = materialRepository.findAllById(materialIds).associateBy { it.id!! }

        return alerts.mapNotNull { a ->
            val mat = materials[a.materialId] ?: return@mapNotNull null
            RecentAlertDto(
                alertId = a.id!!.toString(),
                materialKey = mat.materialKey,
                materialName = mat.name,
                source = a.source,
                previousPriceCents = a.previousPriceCents,
                currentPriceCents = a.currentPriceCents,
                changePct = a.changePct,
                direction = a.direction.name,
                acknowledged = a.acknowledged,
                insertedAt = a.insertedAt.toString()
            )
        }
    }

    private fun buildCurrentPriceDto(mat: Material, snaps: List<PriceSnapshot>): CurrentPriceDto {
        val sortedByPrice = snaps.sortedBy { it.priceCents }
        val priceVals = sortedByPrice.map { it.priceCents }

        val merchantPrices = snaps.map {
            MerchantPriceDto(
                source = it.source,
                featured = it.source in PriceSource.FEATURED,
                priceCents = it.priceCents,
                currency = it.currency,
                inStock = it.inStock,
                sourceUrl = it.sourceUrl,
                scrapedAt = it.scrapedAt.toString()
            )
        }.sortedBy { it.priceCents }

        return CurrentPriceDto(
            materialKey = mat.materialKey,
            name = mat.name,
            category = mat.category.name,
            subcategory = mat.subcategory,
            unit = mat.unit.name,
            dimensions = mat.dimensions,
            brand = mat.brand,
            merchantCount = snaps.size,
            medianPriceCents = priceVals[priceVals.size / 2],
            minPriceCents = priceVals.first(),
            maxPriceCents = priceVals.last(),
            prices = merchantPrices
        )
    }
}

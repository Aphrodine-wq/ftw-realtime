package com.strata.ftw.service

import com.strata.ftw.domain.entity.Material
import com.strata.ftw.domain.entity.MaterialCategory
import com.strata.ftw.domain.entity.MaterialUnit
import com.strata.ftw.domain.entity.PriceAlert
import com.strata.ftw.domain.entity.PriceDirection
import com.strata.ftw.domain.entity.PriceSnapshot
import com.strata.ftw.domain.entity.PriceSource
import com.strata.ftw.domain.entity.PriceType
import com.strata.ftw.domain.repository.MaterialRepository
import com.strata.ftw.domain.repository.PriceAlertRepository
import com.strata.ftw.domain.repository.PriceSnapshotRepository
import com.strata.ftw.web.dto.IngestMaterialDto
import com.strata.ftw.web.dto.IngestPayloadDto
import com.strata.ftw.web.dto.IngestPriceChangeDto
import com.strata.ftw.web.dto.IngestPriceSnapshotDto
import com.strata.ftw.web.dto.IngestResultDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class MaterialPricingService(
    private val materialRepository: MaterialRepository,
    private val priceSnapshotRepository: PriceSnapshotRepository,
    private val priceAlertRepository: PriceAlertRepository,
    private val webhookDispatch: WebhookDispatchService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun ingest(payload: IngestPayloadDto): IngestResultDto {
        val runId = payload.scrapeRun.runId
        log.info("ingest run={} materials={} changes={}", runId, payload.materials.size, payload.priceChanges.size)

        // Idempotency: if we've already ingested this run_id, do nothing.
        val existing = priceSnapshotRepository.findByScrapeRunId(runId)
        if (existing.isNotEmpty()) {
            log.warn("ingest run={} already imported ({} snapshots) — skipping", runId, existing.size)
            return IngestResultDto(
                runId = runId,
                materialsUpserted = 0,
                snapshotsInserted = 0,
                alertsCreated = 0,
                skipped = true,
                skipReason = "run_already_ingested"
            )
        }

        val keysSeen = payload.materials.map { it.materialId }
        val byKey = materialRepository.findByMaterialKeyIn(keysSeen).associateBy { it.materialKey }.toMutableMap()

        var upserted = 0
        var snapshotsInserted = 0

        for (mat in payload.materials) {
            val material = byKey[mat.materialId]?.also {
                applyMaterialUpdates(it, mat)
                materialRepository.save(it)
            } ?: run {
                val created = materialRepository.save(toMaterial(mat))
                byKey[mat.materialId] = created
                upserted += 1
                created
            }

            for (snap in mat.prices) {
                if (!PriceSource.isValid(snap.source)) {
                    log.warn("ingest run={} material={} bad source slug={} — skipping snapshot", runId, mat.materialId, snap.source)
                    continue
                }
                priceSnapshotRepository.save(toSnapshot(material, snap, runId))
                snapshotsInserted += 1
            }
        }

        var alertsCreated = 0
        val savedAlerts = mutableListOf<PriceAlert>()
        for (change in payload.priceChanges) {
            val material = byKey[change.materialId] ?: continue
            val alert = priceAlertRepository.save(toAlert(material, change, runId))
            savedAlerts.add(alert)
            alertsCreated += 1
        }

        // Fan out to subscribed webhooks. Async — won't block the ingest
        // response. Subscribers verify via X-PriceGrid-Signature HMAC.
        if (savedAlerts.isNotEmpty()) {
            webhookDispatch.dispatchPriceAlerts(savedAlerts)
        }

        log.info(
            "ingest run={} done. upserted={} snapshots={} alerts={}",
            runId, upserted, snapshotsInserted, alertsCreated
        )

        return IngestResultDto(
            runId = runId,
            materialsUpserted = upserted,
            snapshotsInserted = snapshotsInserted,
            alertsCreated = alertsCreated,
            skipped = false,
            skipReason = null
        )
    }

    private fun applyMaterialUpdates(target: Material, src: IngestMaterialDto) {
        target.name = src.name
        target.category = parseCategory(src.category)
        target.subcategory = src.subcategory
        target.unit = parseUnit(src.unit)
        target.dimensions = src.dimensions
        target.brand = src.brand
        target.sku = src.sku ?: target.sku
    }

    private fun toMaterial(src: IngestMaterialDto): Material = Material(
        materialKey = src.materialId,
        name = src.name,
        category = parseCategory(src.category),
        subcategory = src.subcategory,
        unit = parseUnit(src.unit),
        dimensions = src.dimensions,
        brand = src.brand,
        sku = src.sku
    )

    private fun toSnapshot(material: Material, src: IngestPriceSnapshotDto, runId: String): PriceSnapshot = PriceSnapshot(
        material = material,
        source = src.source,
        sourceUrl = src.sourceUrl,
        sku = null,
        priceCents = src.price,
        currency = src.currency,
        inStock = src.inStock,
        storeLocation = src.storeLocation,
        scrapedAt = Instant.parse(src.scrapedAt),
        priceType = runCatching { PriceType.valueOf(src.priceType) }.getOrDefault(PriceType.regular),
        scrapeRunId = runId
    )

    private fun toAlert(material: Material, src: IngestPriceChangeDto, runId: String): PriceAlert = PriceAlert(
        material = material,
        source = src.source,
        previousPriceCents = src.previousPrice,
        currentPriceCents = src.currentPrice,
        changeCents = src.currentPrice - src.previousPrice,
        changePct = src.changePct,
        direction = if (src.direction == "up") PriceDirection.up else PriceDirection.down,
        thresholdPct = src.thresholdPct ?: 5.0,
        scrapeRunId = runId,
        acknowledged = false
    )

    private fun parseCategory(s: String): MaterialCategory =
        runCatching { MaterialCategory.valueOf(s) }.getOrElse {
            throw IllegalArgumentException("unknown material category: $s")
        }

    private fun parseUnit(s: String): MaterialUnit =
        runCatching { MaterialUnit.valueOf(s) }.getOrElse {
            throw IllegalArgumentException("unknown material unit: $s")
        }
}

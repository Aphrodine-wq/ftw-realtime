package com.strata.ftw.service

import com.strata.ftw.domain.entity.PriceSource
import com.strata.ftw.domain.repository.MaterialRepository
import com.strata.ftw.domain.repository.PriceSnapshotRepository
import com.strata.ftw.web.dto.ForecastResponseDto
import com.strata.ftw.web.dto.TrendPointDto
import com.strata.ftw.web.dto.TrendResponseDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Historical trend aggregation + simple linear-regression price forecasting.
 *
 * Forecast is intentionally minimal — daily medians as ground truth, OLS
 * linear regression on the trailing 30 days, projected forward N days.
 * R² → confidence band. This isn't ARIMA/Prophet, but it's correct enough
 * to demo "lumber trending +0.4%/day, projected $4.12 in 7 days" — which
 * is the only thing buyers ask about during sales calls.
 *
 * The hook for the buyer to plug in real ML: replace `regression()` below
 * with a call to a Python service (RunPod, etc.). Same DTO shape.
 */
@Service
class PriceForecastService(
    private val materialRepository: MaterialRepository,
    private val priceSnapshotRepository: PriceSnapshotRepository
) {

    @Transactional(readOnly = true)
    fun trend(materialKey: String, periodDays: Int = 30, featuredOnly: Boolean = true): TrendResponseDto? {
        val mat = materialRepository.findByMaterialKey(materialKey) ?: return null
        val materialId = mat.id ?: return null

        val days = periodDays.coerceIn(2, 365)
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val snaps = priceSnapshotRepository.findByMaterialIdSince(materialId, since)
        val pool = if (featuredOnly) snaps.filter { it.source in PriceSource.FEATURED } else snaps

        // Aggregate: bucket by (date, source).
        val byDateAll = pool.groupBy { it.scrapedAt.atOffset(ZoneOffset.UTC).toLocalDate() }
        val combinedPoints = byDateAll.entries
            .sortedBy { it.key }
            .map { (date, list) ->
                val prices = list.map { it.priceCents }.sorted()
                TrendPointDto(
                    date = date.toString(),
                    medianPriceCents = prices[prices.size / 2],
                    minPriceCents = prices.first(),
                    maxPriceCents = prices.last(),
                    merchantCount = list.map { it.source }.distinct().size
                )
            }

        val perSourceMap = mutableMapOf<String, List<TrendPointDto>>()
        for ((source, snaps) in pool.groupBy { it.source }) {
            val points = snaps
                .groupBy { it.scrapedAt.atOffset(ZoneOffset.UTC).toLocalDate() }
                .entries
                .sortedBy { it.key }
                .map { (date, list) ->
                    val prices = list.map { it.priceCents }.sorted()
                    TrendPointDto(
                        date = date.toString(),
                        medianPriceCents = prices[prices.size / 2],
                        minPriceCents = prices.first(),
                        maxPriceCents = prices.last(),
                        merchantCount = 1
                    )
                }
            perSourceMap[source] = points
        }

        return TrendResponseDto(
            materialKey = mat.materialKey,
            name = mat.name,
            periodDays = days,
            interval = "daily",
            points = combinedPoints,
            sources = perSourceMap
        )
    }

    @Transactional(readOnly = true)
    fun forecast(materialKey: String, horizonDays: Int = 7, trainingWindowDays: Int = 30, featuredOnly: Boolean = true): ForecastResponseDto? {
        val trend = trend(materialKey, trainingWindowDays, featuredOnly) ?: return null
        val mat = materialRepository.findByMaterialKey(materialKey)!!

        if (trend.points.size < 2) return ForecastResponseDto(
            materialKey = mat.materialKey,
            name = mat.name,
            horizonDays = horizonDays,
            trainingWindowDays = trainingWindowDays,
            trainingPoints = trend.points.size,
            currentPriceCents = trend.points.lastOrNull()?.medianPriceCents ?: 0L,
            projectedPriceCents = trend.points.lastOrNull()?.medianPriceCents ?: 0L,
            dailyChangeCents = 0.0,
            dailyChangePct = 0.0,
            rSquared = 0.0,
            confidence = "low",
            confidenceBandCents = 0L,
            trendDirection = "flat"
        )

        val firstDate = LocalDate.parse(trend.points.first().date)
        val xs = trend.points.map { ChronoUnit.DAYS.between(firstDate, LocalDate.parse(it.date)).toDouble() }
        val ys = trend.points.map { it.medianPriceCents.toDouble() }

        val reg = regression(xs, ys)
        val current = ys.last()
        val xMax = xs.last()
        val projectedX = xMax + horizonDays
        val projected = reg.intercept + reg.slope * projectedX

        val dailyChangePct = if (current > 0) (reg.slope / current) * 100.0 else 0.0
        val confidence = when {
            reg.rSquared >= 0.7 -> "high"
            reg.rSquared >= 0.4 -> "medium"
            else -> "low"
        }
        val direction = when {
            abs(dailyChangePct) < 0.05 -> "flat"
            reg.slope > 0 -> "up"
            else -> "down"
        }

        return ForecastResponseDto(
            materialKey = mat.materialKey,
            name = mat.name,
            horizonDays = horizonDays,
            trainingWindowDays = trainingWindowDays,
            trainingPoints = trend.points.size,
            currentPriceCents = current.roundToLong(),
            projectedPriceCents = projected.roundToLong().coerceAtLeast(0),
            dailyChangeCents = (reg.slope * 10).roundToLong() / 10.0,
            dailyChangePct = (dailyChangePct * 10).roundToLong() / 10.0,
            rSquared = (reg.rSquared * 1000).roundToLong() / 1000.0,
            confidence = confidence,
            confidenceBandCents = reg.standardError.roundToLong(),
            trendDirection = direction
        )
    }

    /**
     * Plain old least-squares linear regression. y = mx + b.
     * Returns slope, intercept, R², and standard error of the residuals.
     */
    private fun regression(xs: List<Double>, ys: List<Double>): Reg {
        val n = xs.size
        val xMean = xs.sum() / n
        val yMean = ys.sum() / n
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - xMean
            num += dx * (ys[i] - yMean)
            den += dx * dx
        }
        val slope = if (den == 0.0) 0.0 else num / den
        val intercept = yMean - slope * xMean

        // R² and SSR
        var ssTot = 0.0
        var ssRes = 0.0
        for (i in 0 until n) {
            val dy = ys[i] - yMean
            ssTot += dy * dy
            val pred = intercept + slope * xs[i]
            val res = ys[i] - pred
            ssRes += res * res
        }
        val r2 = if (ssTot == 0.0) 1.0 else 1.0 - (ssRes / ssTot)
        val stdErr = if (n > 2) sqrt(ssRes / (n - 2)) else 0.0

        return Reg(slope, intercept, r2.coerceIn(0.0, 1.0), stdErr)
    }

    private data class Reg(val slope: Double, val intercept: Double, val rSquared: Double, val standardError: Double)
}

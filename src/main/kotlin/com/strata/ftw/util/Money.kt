package com.strata.ftw.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All money in this codebase is stored as integer cents. These helpers convert
 * to/from BigDecimal for external APIs (QuickBooks expects dollars-with-cents)
 * and compute percentage-based fees without float precision drift.
 *
 * Why BigDecimal, not Double:
 *   Double can't exactly represent values like 0.10 — over thousands of
 *   payments those cent-fractions accumulate and the platform fee total
 *   drifts from the per-payout sum. BigDecimal with scale=2 HALF_EVEN
 *   (banker's rounding) matches what QuickBooks does internally.
 */
object Money {
    private const val SCALE = 2
    private val ROUNDING = RoundingMode.HALF_EVEN
    private val HUNDRED = BigDecimal(100)

    /** Cents (Int) -> BigDecimal dollars, exactly two decimal places. */
    fun centsToDollars(cents: Int): BigDecimal =
        BigDecimal(cents).divide(HUNDRED, SCALE, ROUNDING)

    /** Same, for Long-cents call sites. */
    fun centsToDollars(cents: Long): BigDecimal =
        BigDecimal(cents).divide(HUNDRED, SCALE, ROUNDING)

    /**
     * Percent of a cent amount, returned in cents.
     *
     * Uses banker's rounding so that a 5% fee on 1_005 cents resolves
     * deterministically and a fleet of payouts won't systematically drift
     * the platform up or down.
     */
    fun feeCents(amountCents: Int, percent: Double): Int =
        BigDecimal(amountCents)
            .multiply(BigDecimal.valueOf(percent))
            .divide(HUNDRED, 0, ROUNDING)
            .toInt()
}

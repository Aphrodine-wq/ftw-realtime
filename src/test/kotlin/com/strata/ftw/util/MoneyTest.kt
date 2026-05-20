package com.strata.ftw.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `centsToDollars produces exact 2-decimal scale`() {
        assertEquals(BigDecimal("0.00"), Money.centsToDollars(0))
        assertEquals(BigDecimal("1.00"), Money.centsToDollars(100))
        assertEquals(BigDecimal("1.23"), Money.centsToDollars(123))
        assertEquals(BigDecimal("123456.78"), Money.centsToDollars(12_345_678))
    }

    @Test
    fun `feeCents at 5 percent of common amounts`() {
        // 5% of $10.00 = $0.50 = 50 cents
        assertEquals(50, Money.feeCents(1_000, 5.0))
        // 5% of $100.00 = $5.00 = 500 cents
        assertEquals(500, Money.feeCents(10_000, 5.0))
        // 5% of $1234.56 = $61.728 -> banker's rounds to $61.73 = 6173 cents
        assertEquals(6_173, Money.feeCents(123_456, 5.0))
    }

    @Test
    fun `feeCents 3 percent homeowner fee on the half-cent boundary`() {
        // 3% of $0.17 = 0.51 cents -> banker's rounds to 1 cent
        assertEquals(1, Money.feeCents(17, 3.0))
        // 3% of $0.16 = 0.48 cents -> rounds to 0
        assertEquals(0, Money.feeCents(16, 3.0))
    }

    @Test
    fun `feeCents handles zero amount`() {
        assertEquals(0, Money.feeCents(0, 5.0))
        assertEquals(0, Money.feeCents(0, 100.0))
    }

    @Test
    fun `feeCents does not systematically truncate versus the old toInt approach`() {
        // The old code was `(amount * percent / 100.0).toInt()` which truncates
        // toward zero. Banker's rounding should match-or-beat that on cases
        // where the fractional part is at least 0.5, and tie behavior should
        // round to even rather than always-down.
        // 5% of 199c = 9.95c -> banker's rounds to 10 (truncation gave 9).
        assertEquals(10, Money.feeCents(199, 5.0))
        // 5% of 1010c = 50.5c -> banker's rounds to 50 (toward even);
        // 5% of 1030c = 51.5c -> rounds to 52 (toward even).
        assertEquals(50, Money.feeCents(1_010, 5.0))
        assertEquals(52, Money.feeCents(1_030, 5.0))
    }
}

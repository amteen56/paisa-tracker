package com.amteen.paisa.core.money

import com.amteen.paisa.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class CurrencyConverterTest {

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    private val usd = Currency("USD", "US Dollar", "$", 2, 280.0)
    private val jpy = Currency("JPY", "Japanese Yen", "¥", 0, 1.85)
    private val gbp = Currency("GBP", "Pound Sterling", "£", 2, 355.0)

    @Test
    fun `converts to the base currency`() {
        // $10.00 at 1 USD = 280 PKR -> Rs. 2,800.00
        val result = CurrencyConverter.convert(Money(1000, "USD"), usd, pkr)
        assertEquals(Money(280_000, "PKR"), result)
    }

    @Test
    fun `converts from the base currency`() {
        val result = CurrencyConverter.convert(Money(280_000, "PKR"), pkr, usd)
        assertEquals(Money(1000, "USD"), result)
    }

    @Test
    fun `cross-converts through the base without a pairwise rate`() {
        // £1 = 355 PKR, $1 = 280 PKR, so £100 = 35,500 PKR = $126.7857... -> $126.79
        val result = CurrencyConverter.convert(Money(10_000, "GBP"), gbp, usd)
        assertEquals(Money(12679, "USD"), result)
    }

    @Test
    fun `respects the target currency's decimal digits`() {
        // Rs. 100.00 at 1 JPY = 1.85 PKR -> 54.05 yen -> 54 (JPY has no minor units)
        val result = CurrencyConverter.convert(Money(10_000, "PKR"), pkr, jpy)
        assertEquals(Money(54, "JPY"), result)
    }

    @Test
    fun `rounds half up at the target precision`() {
        // A rate chosen so the result lands exactly on a half minor unit.
        val half = Currency("HAL", "Half", "H", 2, 2.0)
        // 5 minor units of HAL = 0.05 HAL = 0.10 PKR -> 10 minor. Then a value that
        // produces x.xx5 in PKR must round away from zero.
        val odd = Currency("ODD", "Odd", "O", 2, 1.005)
        val result = CurrencyConverter.convert(Money(100, "ODD"), odd, pkr)
        // 1.00 ODD * 1.005 = 1.005 PKR -> 100.5 minor -> 101
        assertEquals(Money(101, "PKR"), result)
        assertEquals(Money(10, "PKR"), CurrencyConverter.convert(Money(5, "HAL"), half, pkr))
    }

    @Test
    fun `same currency is returned untouched`() {
        val money = Money(12345, "PKR")
        assertEquals(money, CurrencyConverter.convert(money, pkr, pkr))
    }

    @Test
    fun `rejects a zero or negative rate`() {
        val broken = Currency("BAD", "Bad", "B", 2, 0.0)
        assertFailsWith<IllegalArgumentException> {
            CurrencyConverter.convert(Money(100, "BAD"), broken, pkr)
        }

        val negative = Currency("NEG", "Negative", "N", 2, -5.0)
        assertFailsWith<IllegalArgumentException> {
            CurrencyConverter.convert(Money(100, "NEG"), negative, pkr)
        }
    }

    @Test
    fun `rejects money that is not in the source currency`() {
        assertFailsWith<IllegalArgumentException> {
            CurrencyConverter.convert(Money(100, "PKR"), usd, pkr)
        }
    }

    // -- Rebasing -----------------------------------------------------------

    @Test
    fun `rebasing makes the new base exactly one and preserves cross-rates`() {
        val rebased = CurrencyConverter.rebase(listOf(pkr, usd, gbp), "USD")

        val newUsd = rebased.first { it.code == "USD" }
        val newPkr = rebased.first { it.code == "PKR" }
        val newGbp = rebased.first { it.code == "GBP" }

        assertEquals(1.0, newUsd.rateToBase, 0.0)
        // 1 PKR is now 1/280 USD.
        assertEquals(1.0 / 280.0, newPkr.rateToBase, 1e-12)
        // £100 must still be worth the same in dollars as before the rebase.
        assertEquals(
            CurrencyConverter.convert(Money(10_000, "GBP"), gbp, usd),
            CurrencyConverter.convert(Money(10_000, "GBP"), newGbp, newUsd),
        )
    }

    @Test
    fun `rebasing to an unknown currency is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CurrencyConverter.rebase(listOf(pkr, usd), "XXX")
        }
    }
}

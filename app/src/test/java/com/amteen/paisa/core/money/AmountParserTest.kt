package com.amteen.paisa.core.money

import com.amteen.paisa.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountParserTest {

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    private val jpy = Currency("JPY", "Japanese Yen", "¥", 0, 1.85)

    private fun minor(input: String, currency: Currency = pkr): Long? =
        (AmountParser.parse(input, currency) as? AmountParser.Result.Valid)?.amountMinor

    private fun reason(input: String, currency: Currency = pkr): AmountParser.Reason? =
        (AmountParser.parse(input, currency) as? AmountParser.Result.Invalid)?.reason

    @Test
    fun `parses plain and decimal input`() {
        assertEquals(80000L, minor("800"))
        assertEquals(35050L, minor("350.50"))
        assertEquals(35005L, minor("350.05"))
        assertEquals(35000L, minor("350.")) // trailing dot is a mid-typing state
    }

    @Test
    fun `accepts grouping separators and stray spaces`() {
        assertEquals(123456789L, minor("1,234,567.89"))
        assertEquals(100000L, minor(" 1 000 "))
    }

    @Test
    fun `accepts a leading decimal point`() {
        assertEquals(50L, minor(".50"))
    }

    @Test
    fun `ignores leading zeros`() {
        assertEquals(80000L, minor("000800"))
    }

    @Test
    fun `accepts non-Latin digits from a localised keyboard`() {
        // Arabic-Indic ٨٠٠ and Devanagari १२३ are digits a real keyboard produces.
        assertEquals(80000L, minor("٨٠٠"))
        assertEquals(12300L, minor("१२३"))
    }

    @Test
    fun `rejects zero and blank by default`() {
        assertEquals(AmountParser.Reason.NOT_POSITIVE, reason("0"))
        assertEquals(AmountParser.Reason.NOT_POSITIVE, reason("0.00"))
        assertEquals(AmountParser.Reason.EMPTY, reason(""))
        assertEquals(AmountParser.Reason.EMPTY, reason("   "))
    }

    @Test
    fun `allows zero when the caller opts in`() {
        val result = AmountParser.parse("0", pkr, allowZero = true)
        assertEquals(AmountParser.Result.Valid(0L), result)
    }

    @Test
    fun `rejects malformed input`() {
        assertEquals(AmountParser.Reason.TOO_MANY_DECIMAL_POINTS, reason("1.2.3"))
        assertEquals(AmountParser.Reason.NOT_A_NUMBER, reason("12a"))
        assertEquals(AmountParser.Reason.NOT_A_NUMBER, reason("-50"))
    }

    @Test
    fun `rejects more decimal digits than the currency has`() {
        assertEquals(AmountParser.Reason.TOO_MANY_DECIMAL_DIGITS, reason("1.234"))
        // JPY has no minor units at all, so any decimal is too many.
        assertEquals(AmountParser.Reason.TOO_MANY_DECIMAL_DIGITS, reason("1.5", jpy))
        assertEquals(1200L, minor("1200", jpy))
    }

    @Test
    fun `rejects a value that would overflow Long`() {
        assertEquals(AmountParser.Reason.TOO_LARGE, reason("99999999999999999999"))
        assertEquals(AmountParser.Reason.TOO_LARGE, reason("92233720368547758.08"))
    }
}

package com.amteen.paisa.core.money

import com.amteen.paisa.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class MoneyTest {

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    private val usd = Currency("USD", "US Dollar", "$", 2, 280.0)
    private val jpy = Currency("JPY", "Japanese Yen", "¥", 0, 1.85)

    @Test
    fun `adds and subtracts within one currency`() {
        val a = Money(35050, "PKR")   // 350.50
        val b = Money(14950, "PKR")   // 149.50

        assertEquals(Money(50000, "PKR"), a + b)
        assertEquals(Money(20100, "PKR"), a - b)
    }

    @Test
    fun `mixing currencies throws rather than coercing`() {
        val rupees = Money(1000, "PKR")
        val dollars = Money(1000, "USD")

        assertFailsWith<IllegalArgumentException> { rupees + dollars }
        assertFailsWith<IllegalArgumentException> { rupees - dollars }
        assertFailsWith<IllegalArgumentException> { rupees.compareTo(dollars) }
    }

    @Test
    fun `overflow is reported, not wrapped`() {
        // Silently wrapping to a huge negative balance would be far worse than
        // failing loudly here.
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, "PKR") + Money(1, "PKR") }
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, "PKR") * 2 }
    }

    @Test
    fun `summing an empty list yields a correctly denominated zero`() {
        assertEquals(Money(0, "PKR"), emptyList<Money>().sum("PKR"))
    }

    @Test
    fun `no drift across many small additions`() {
        // The whole reason money is Long: 0.1 + 0.2 in Double is not 0.3.
        val total = List(1000) { Money(10, "PKR") }.sum("PKR")
        assertEquals(Money(10_000, "PKR"), total)
    }

    // -- Formatting ---------------------------------------------------------

    @Test
    fun `formats with symbol, grouping and fixed decimals`() {
        assertEquals("Rs. 350.50", MoneyFormatter.format(Money(35050, "PKR"), pkr))
        assertEquals("Rs. 1,234,567.89", MoneyFormatter.format(Money(123456789, "PKR"), pkr))
        assertEquals("$0.05", MoneyFormatter.format(Money(5, "USD"), usd))
    }

    @Test
    fun `zero-decimal currency renders no decimal point`() {
        assertEquals("¥1,200", MoneyFormatter.format(Money(1200, "JPY"), jpy))
    }

    @Test
    fun `negative amounts keep the sign outside the symbol`() {
        assertEquals("-Rs. 99.00", MoneyFormatter.format(Money(-9900, "PKR"), pkr))
    }

    @Test
    fun `grouping boundaries are correct`() {
        assertEquals("Rs. 999.00", MoneyFormatter.format(Money(99900, "PKR"), pkr))
        assertEquals("Rs. 1,000.00", MoneyFormatter.format(Money(100000, "PKR"), pkr))
        assertEquals("Rs. 10,000.00", MoneyFormatter.format(Money(1000000, "PKR"), pkr))
    }

    @Test
    fun `formats the extreme value without crashing`() {
        // Math.abs(Long.MIN_VALUE) is negative; the formatter must not rely on it.
        val rendered = MoneyFormatter.format(Money(Long.MIN_VALUE, "PKR"), pkr)
        assertTrue(rendered.startsWith("-"))
    }

    @Test
    fun `compact form is used only above a thousand`() {
        assertEquals("Rs. 999.00", MoneyFormatter.formatCompact(Money(99900, "PKR"), pkr))
        assertEquals("Rs. 1.2K", MoneyFormatter.formatCompact(Money(125000, "PKR"), pkr))
        assertEquals("Rs. 2.4M", MoneyFormatter.formatCompact(Money(240000000, "PKR"), pkr))
    }
}

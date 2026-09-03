package com.amteen.paisa.core.money

/**
 * An amount of money, held as **minor units** of a currency.
 *
 * ```
 * Rs. 350.50  ->  Money(amountMinor = 35050, currencyCode = "PKR")
 * ¥ 1200      ->  Money(amountMinor = 1200,  currencyCode = "JPY")   // 0 decimal digits
 * ```
 *
 * There is no floating point anywhere in this class, and there must never be one.
 * A `Double` cannot represent 0.1 exactly, so summing a few hundred expenses in
 * `Double` drifts by fractions of a rupee — which is exactly the kind of bug a
 * finance app cannot afford. See CLAUDE.md.
 *
 * Arithmetic across two different currencies **throws** rather than silently
 * coercing. Converting is an explicit, lossy, rounding operation and belongs to
 * [CurrencyConverter].
 */
data class Money(
    val amountMinor: Long,
    val currencyCode: String,
) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.addExact(amountMinor, other.amountMinor))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.subtractExact(amountMinor, other.amountMinor))
    }

    operator fun times(factor: Long): Money =
        copy(amountMinor = Math.multiplyExact(amountMinor, factor))

    operator fun times(factor: Int): Money = times(factor.toLong())

    operator fun unaryMinus(): Money = copy(amountMinor = Math.negateExact(amountMinor))

    val isZero: Boolean get() = amountMinor == 0L
    val isPositive: Boolean get() = amountMinor > 0L
    val isNegative: Boolean get() = amountMinor < 0L

    fun abs(): Money =
        if (amountMinor < 0) copy(amountMinor = Math.negateExact(amountMinor)) else this

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Cannot combine $currencyCode with ${other.currencyCode}. " +
                "Convert explicitly through CurrencyConverter first."
        }
    }

    companion object {
        fun zero(currencyCode: String) = Money(0L, currencyCode)
    }
}

/**
 * Sums money that is already known to share a currency.
 *
 * [currencyCode] is required so that an empty list still produces a correctly
 * denominated zero rather than throwing or guessing.
 */
fun Iterable<Money>.sum(currencyCode: String): Money =
    fold(Money.zero(currencyCode)) { acc, money -> acc + money }

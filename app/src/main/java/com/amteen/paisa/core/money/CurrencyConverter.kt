package com.amteen.paisa.core.money

import com.amteen.paisa.domain.model.Currency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The **only** place in the app where currency conversion and monetary rounding
 * happen. See CLAUDE.md.
 *
 * Conversion goes through the base currency rather than a pairwise table:
 *
 * ```
 * major(from)  = minor / 10^digits(from)
 * major(base)  = major(from) * rateToBase(from)
 * major(to)    = major(base) / rateToBase(to)
 * minor(to)    = round_half_up( major(to) * 10^digits(to) )
 * ```
 *
 * Arithmetic runs in [BigDecimal] so the only floating-point value involved is the
 * user's rate itself; the result is rounded HALF_UP exactly once, at the target
 * currency's precision.
 */
object CurrencyConverter {

    private val MATH_CONTEXT = MathContext(24, RoundingMode.HALF_UP)

    /**
     * Converts [money] from [from] to [to].
     *
     * Returns [money] unchanged when the currencies match, so a single-currency
     * user never pays a rounding cost.
     *
     * @throws IllegalArgumentException if [money] is not denominated in [from], or
     *   if either rate is zero, negative, or not finite. A non-positive rate is
     *   always user error and would otherwise produce a nonsensical amount.
     */
    fun convert(money: Money, from: Currency, to: Currency): Money {
        require(money.currencyCode == from.code) {
            "Money is in ${money.currencyCode} but was asked to convert from ${from.code}."
        }
        if (from.code == to.code) return money

        requireUsableRate(from)
        requireUsableRate(to)

        val minor = BigDecimal.valueOf(money.amountMinor)
        val fromScale = BigDecimal.valueOf(from.minorUnitsPerMajor)
        val toScale = BigDecimal.valueOf(to.minorUnitsPerMajor)

        val inBase = minor.divide(fromScale, MATH_CONTEXT)
            .multiply(BigDecimal.valueOf(from.rateToBase), MATH_CONTEXT)

        val inTarget = inBase.divide(BigDecimal.valueOf(to.rateToBase), MATH_CONTEXT)
            .multiply(toScale, MATH_CONTEXT)

        return Money(
            amountMinor = inTarget.setScale(0, RoundingMode.HALF_UP).longValueExact(),
            currencyCode = to.code,
        )
    }

    /**
     * Rebases the whole rate table when the user picks a new base currency.
     *
     * Every rate is divided by the new base's old rate, so the new base lands on
     * exactly `1.0` and all cross-rates are preserved. Transaction amounts are
     * never touched — see CLAUDE.md rule 5.
     */
    fun rebase(currencies: List<Currency>, newBaseCode: String): List<Currency> {
        val newBase = currencies.firstOrNull { it.code == newBaseCode }
            ?: throw IllegalArgumentException("Unknown currency $newBaseCode")
        requireUsableRate(newBase)

        val divisor = BigDecimal.valueOf(newBase.rateToBase)
        return currencies.map { currency ->
            when {
                currency.code == newBaseCode -> currency.copy(rateToBase = 1.0)
                !currency.rateToBase.isFinite() || currency.rateToBase <= 0.0 -> currency
                else -> currency.copy(
                    rateToBase = BigDecimal.valueOf(currency.rateToBase)
                        .divide(divisor, MATH_CONTEXT)
                        .toDouble(),
                )
            }
        }
    }

    private fun requireUsableRate(currency: Currency) {
        require(currency.rateToBase.isFinite() && currency.rateToBase > 0.0) {
            "${currency.code} has an unusable exchange rate (${currency.rateToBase}). " +
                "Rates must be finite and greater than zero."
        }
    }
}

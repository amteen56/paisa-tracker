package com.amteen.paisa.domain.model

import com.amteen.paisa.core.money.CurrencyConverter
import com.amteen.paisa.core.money.Money

/**
 * The user's currencies plus their chosen base, wrapped so callers can convert and
 * format without each one re-deriving the lookup.
 *
 * An unknown code resolves to a [fallback] rather than throwing: a transaction whose
 * currency was deleted, or one arriving from a hand-edited import, must still render
 * and still be summable. Showing the amount with its raw code beats an empty screen.
 */
class CurrencyTable(
    val currencies: List<Currency>,
    baseCode: String,
) {
    private val byCode: Map<String, Currency> = currencies.associateBy { it.code }

    val base: Currency = byCode[baseCode]
        ?: currencies.firstOrNull { it.isBase }
        ?: fallback(baseCode)

    /** Currencies offered in pickers; archived ones stay out of new entries. */
    val active: List<Currency> = currencies.filterNot { it.archived }

    fun currency(code: String): Currency = byCode[code] ?: fallback(code)

    fun convert(money: Money, toCode: String): Money =
        CurrencyConverter.convert(money, currency(money.currencyCode), currency(toCode))

    fun toBase(money: Money): Money = convert(money, base.code)


    companion object {
        fun fallback(code: String) = Currency(
            code = code,
            name = code,
            symbol = code,
            decimalDigits = 2,
            rateToBase = 1.0,
        )

        val Empty = CurrencyTable(emptyList(), AppSettings.DEFAULT_BASE_CURRENCY)
    }
}


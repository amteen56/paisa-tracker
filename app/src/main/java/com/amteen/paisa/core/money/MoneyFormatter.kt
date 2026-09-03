package com.amteen.paisa.core.money

import com.amteen.paisa.domain.model.Currency

/**
 * The **only** place that turns a [Money] into a display string. Composables must
 * never assemble an amount themselves — see CLAUDE.md.
 *
 * Grouping is done by hand rather than via `DecimalFormat` because every
 * `DecimalFormat` path runs through `double`, which is precisely what the `Long`
 * minor-unit model exists to avoid.
 */
object MoneyFormatter {

    /**
     * @param withSymbol prefix the currency symbol, e.g. `Rs. 1,250.00`.
     * @param withGrouping insert thousands separators.
     * @param signed always show `+` or `-`. Off by default: a transaction row
     *   already conveys direction through colour and an explicit sign, so a bare
     *   figure is the common case.
     */
    fun format(
        money: Money,
        currency: Currency,
        withSymbol: Boolean = true,
        withGrouping: Boolean = true,
        signed: Boolean = false,
    ): String {
        require(money.currencyCode == currency.code) {
            "Money is in ${money.currencyCode} but was given ${currency.code} to format with."
        }

        val negative = money.amountMinor < 0
        // Math.abs(Long.MIN_VALUE) is itself negative, so that one value has to go
        // the long way round. Everything else takes the cheap path.
        val digits = if (money.amountMinor == Long.MIN_VALUE) {
            java.math.BigInteger.valueOf(money.amountMinor).abs().toString()
        } else {
            Math.abs(money.amountMinor).toString()
        }

        val padded = digits.padStart(currency.decimalDigits + 1, '0')
        val splitAt = padded.length - currency.decimalDigits
        val whole = padded.substring(0, splitAt)
        val fraction = padded.substring(splitAt)

        val sb = StringBuilder()
        when {
            negative -> sb.append('-')
            signed -> sb.append('+')
        }
        if (withSymbol) sb.appendSymbol(currency)
        sb.append(if (withGrouping) group(whole) else whole)
        if (currency.decimalDigits > 0) {
            sb.append('.').append(fraction)
        }
        return sb.toString()
    }

    /**
     * A compact form for chart labels and calendar cells, where a full figure will
     * not fit: `1250` -> `1.3K`, `2400000` -> `2.4M`.
     *
     * Deliberately drops precision, so it is for labels only — never for a figure
     * the user is expected to reconcile against.
     */
    fun formatCompact(money: Money, currency: Currency, withSymbol: Boolean = true): String {
        val major = money.amountMinor / currency.minorUnitsPerMajor
        val negative = major < 0
        val magnitude = if (negative) -major else major

        val (scaled, suffix) = when {
            magnitude >= 1_000_000_000 -> magnitude / 100_000_000 to "B"
            magnitude >= 1_000_000 -> magnitude / 100_000 to "M"
            magnitude >= 1_000 -> magnitude / 100 to "K"
            else -> return format(money, currency, withSymbol = withSymbol)
        }

        val sb = StringBuilder()
        if (negative) sb.append('-')
        if (withSymbol) sb.appendSymbol(currency)
        sb.append(scaled / 10)
        val remainder = scaled % 10
        if (remainder != 0L) sb.append('.').append(remainder)
        sb.append(suffix)
        return sb.toString()
    }

    /** Renders the input side of the amount keypad: raw digits, no symbol. */
    fun formatForInput(amountMinor: Long, currency: Currency): String =
        format(
            money = Money(amountMinor, currency.code),
            currency = currency,
            withSymbol = false,
            withGrouping = true,
        )

    /**
     * Appends the symbol, with a space only where one is needed.
     *
     * `$1,250` reads correctly but `Rs.1,250` does not — a symbol ending in a
     * letter or a full stop needs separating from the digits. Both entry points
     * share this so they cannot drift apart.
     */
    private fun StringBuilder.appendSymbol(currency: Currency) {
        if (currency.symbol.isEmpty()) return
        append(currency.symbol)
        val last = currency.symbol.last()
        if (last.isLetterOrDigit() || last == '.') append(' ')
    }

    private fun group(whole: String): String {
        if (whole.length <= 3) return whole
        val sb = StringBuilder(whole.length + whole.length / 3)
        val firstGroup = whole.length % 3
        if (firstGroup != 0) {
            sb.append(whole, 0, firstGroup)
        }
        var i = firstGroup
        while (i < whole.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(whole, i, i + 3)
            i += 3
        }
        return sb.toString()
    }
}

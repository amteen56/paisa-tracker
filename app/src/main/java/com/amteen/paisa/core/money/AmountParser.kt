package com.amteen.paisa.core.money

import com.amteen.paisa.domain.model.Currency

/**
 * Turns what the user typed into `Long` minor units.
 *
 * The amount field is the single most-used input in the app, so it accepts the
 * sloppy things people actually type — grouping commas, spaces, a leading dot,
 * a trailing dot, non-Latin digits — and rejects everything else with a reason
 * rather than silently producing a wrong number.
 */
object AmountParser {

    sealed interface Result {
        data class Valid(val amountMinor: Long) : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        EMPTY,
        NOT_A_NUMBER,
        TOO_MANY_DECIMAL_POINTS,
        TOO_MANY_DECIMAL_DIGITS,
        NOT_POSITIVE,
        TOO_LARGE,
    }

    /**
     * @param allowZero budgets and rates may legitimately be zero while a
     *   transaction amount may not, so the caller decides.
     */
    fun parse(input: String, currency: Currency, allowZero: Boolean = false): Result {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return Result.Invalid(Reason.EMPTY)

        val parts = normalized.split('.')
        if (parts.size > 2) return Result.Invalid(Reason.TOO_MANY_DECIMAL_POINTS)

        val wholeText = parts[0].ifEmpty { "0" }
        val fractionText = parts.getOrNull(1).orEmpty()

        if (!wholeText.all { it.isDigit() } || !fractionText.all { it.isDigit() }) {
            return Result.Invalid(Reason.NOT_A_NUMBER)
        }
        if (fractionText.length > currency.decimalDigits) {
            return Result.Invalid(Reason.TOO_MANY_DECIMAL_DIGITS)
        }

        return try {
            val whole = if (wholeText.isEmpty()) 0L else wholeText.toLong()
            val fraction = fractionText
                .padEnd(currency.decimalDigits, '0')
                .ifEmpty { "0" }
                .toLong()

            val minor = Math.addExact(
                Math.multiplyExact(whole, currency.minorUnitsPerMajor),
                fraction,
            )
            when {
                minor < 0L -> Result.Invalid(Reason.NOT_POSITIVE)
                minor == 0L && !allowZero -> Result.Invalid(Reason.NOT_POSITIVE)
                else -> Result.Valid(minor)
            }
        } catch (e: ArithmeticException) {
            Result.Invalid(Reason.TOO_LARGE)
        } catch (e: NumberFormatException) {
            Result.Invalid(Reason.TOO_LARGE)
        }
    }

    /**
     * Strips grouping and maps any Unicode decimal digit to its Latin equivalent,
     * so Urdu/Arabic/Devanagari numerals entered from a localised keyboard parse
     * correctly instead of being rejected as "not a number".
     */
    private fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input.trim()) {
            when {
                ch.isDigit() -> sb.append('0' + Character.digit(ch, 10))
                ch == '.' || ch == '٫' -> sb.append('.')
                ch == ',' || ch == ' ' || ch == ' ' || ch == '٬' -> Unit
                else -> sb.append(ch) // kept so it fails validation loudly
            }
        }
        return sb.toString()
    }
}

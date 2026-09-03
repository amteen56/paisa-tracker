package com.amteen.paisa.domain.model

/**
 * A currency the user has configured, with a **manually entered** exchange rate.
 *
 * Rates are never fetched from the network — the app has no `INTERNET` permission.
 * The user maintains them by hand in Settings.
 *
 * @param decimalDigits minor units per major unit, as a power of ten. PKR/USD = 2,
 *   JPY = 0. This is what makes `35050` mean `350.50` for one currency and
 *   `35050` for another.
 * @param rateToBase how many **base-currency** units one unit of this currency is
 *   worth. With PKR as base: `USD.rateToBase = 280.0` reads as "1 USD = 280 PKR",
 *   and the base currency itself is always exactly `1.0`.
 *
 *   This is the single `Double` in the entire money path, and it is a user-entered
 *   rate rather than an amount. Every conversion derives from these anchors, which
 *   is why there is no pairwise rate table to drift out of sync.
 */
data class Currency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimalDigits: Int,
    val rateToBase: Double,
    val archived: Boolean = false,
) {
    /** 10^[decimalDigits] — the number of minor units in one major unit. */
    val minorUnitsPerMajor: Long
        get() = POWERS_OF_TEN[decimalDigits]

    val isBase: Boolean get() = rateToBase == 1.0

    companion object {
        /** Guard rail for user input; beyond this the UI should reject the value. */
        const val MAX_DECIMAL_DIGITS = 4

        private val POWERS_OF_TEN = longArrayOf(1, 10, 100, 1_000, 10_000)
    }
}

package com.amteen.paisa.data.file

import java.time.YearMonth

/**
 * Every path the app writes, in one place.
 *
 * All paths are relative to the store root (`<filesDir>/app-data`), so the store
 * can be pointed at a temp directory in tests without touching Android.
 */
object FilePaths {

    /** Root directory name under the app's private files dir. */
    const val ROOT = "app-data"

    const val CATEGORIES = "categories.json"
    const val BUDGETS = "budgets.json"
    const val CURRENCIES = "currencies.json"
    const val PAYMENT_METHODS = "paymentmethods.json"
    const val SETTINGS = "settings.json"

    const val TRANSACTIONS_DIR = "transactions"
    const val BACKUP_DIR = "backup"

    /** One shard per month: `transactions/2026-09.json`. */
    fun transactionShard(month: YearMonth): String =
        "$TRANSACTIONS_DIR/${shardName(month)}.json"

    /** `YearMonth.toString()` is already `uuuu-MM`, which sorts lexicographically. */
    fun shardName(month: YearMonth): String = month.toString()

    /** Reverse of [transactionShard]; returns null for anything unrecognised. */
    fun monthFromShardFileName(fileName: String): YearMonth? {
        if (!fileName.endsWith(".json")) return null
        return try {
            YearMonth.parse(fileName.removeSuffix(".json"))
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }
    }
}

package com.amteen.paisa.data.seed

import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.random.Random

/**
 * Plausible fake history, for trying the app out and for screenshots.
 *
 * **Deterministic.** The generator is seeded, so the same `months` always produces the
 * same ledger. Random sample data makes a screenshot unrepeatable and a bug report
 * impossible to follow up — "the dashboard shows the wrong average" is only actionable
 * if the data can be regenerated exactly.
 *
 * Shaped to look like real spending rather than uniform noise, because uniform noise
 * makes every chart in the app look identical and hides the very bugs a chart has:
 *
 * - salary lands once a month, on the 1st
 * - groceries cluster at weekends
 * - rent and bills are monthly and large
 * - most days have one or two small expenses, some have none
 * - a handful of outliers, so the reports' "biggest expenses" has something to show
 */
object SampleData {

    /** Ids are prefixed so a seeded ledger can be told apart from real entries. */
    const val ID_PREFIX = "sample-"

    /**
     * @param months how many months back to generate, including [endMonth].
     * @param endMonth the last month generated; defaults to the current one.
     */
    fun transactions(
        months: Int = 6,
        endMonth: YearMonth = YearMonth.now(),
        today: LocalDate = LocalDate.now(),
    ): List<Transaction> {
        val random = Random(SEED)
        val result = ArrayList<Transaction>()
        var counter = 0

        fun id(): String = "$ID_PREFIX${counter++}"

        fun add(
            date: LocalDate,
            amountMinor: Long,
            categoryId: String,
            subcategoryId: String?,
            description: String,
            type: TransactionType = TransactionType.EXPENSE,
            hour: Int = random.nextInt(8, 21),
            method: String = METHODS.random(random),
        ) {
            // Never generate the future: a forward-dated record skews the dashboard's
            // rolling average and looks like a bug rather than sample data.
            if (date.isAfter(today)) return
            result += Transaction(
                id = id(),
                type = type,
                amountMinor = amountMinor,
                currencyCode = DefaultData.BASE_CURRENCY_CODE,
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                description = description,
                date = date,
                time = LocalTime.of(hour, listOf(0, 15, 30, 45).random(random)),
                paymentMethodId = method,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        }

        for (back in (months - 1) downTo 0) {
            val month = endMonth.minusMonths(back.toLong())
            val lastDay = month.lengthOfMonth()

            // Salary, on the 1st, with a little variation so the trend line is not
            // perfectly flat.
            add(
                date = month.atDay(1),
                amountMinor = 45_000_00L + random.nextLong(-2_000_00L, 3_000_00L),
                categoryId = "cat-salary",
                subcategoryId = null,
                description = "Monthly salary",
                type = TransactionType.INCOME,
                hour = 10,
                method = "pm-bank",
            )

            // Rent, early, large, same every month.
            add(month.atDay(3), 18_000_00L, "cat-housing", null, "Rent", hour = 11, method = "pm-bank")

            // Bills, mid-month, varying.
            add(month.atDay(8), 4_200_00L + random.nextLong(0, 1_800_00L), "cat-bills", null, "Electricity", method = "pm-bank")
            add(month.atDay(9), 2_500_00L, "cat-bills", null, "Internet", method = "pm-bank")
            add(month.atDay(10), 1_200_00L + random.nextLong(0, 400_00L), "cat-bills", null, "Mobile top-up", method = "pm-wallet")

            for (day in 1..lastDay) {
                val date = month.atDay(day)
                val weekend = date.dayOfWeek.value >= 6

                // The big weekend shop.
                if (weekend && random.nextInt(100) < 70) {
                    add(date, 2_800_00L + random.nextLong(0, 2_200_00L), "cat-food", null, "Groceries")
                }

                // Everyday small spending — not every day, so the calendar has gaps.
                val everyday = random.nextInt(100)
                when {
                    everyday < 22 -> Unit
                    everyday < 60 -> add(date, 250_00L + random.nextLong(0, 500_00L), "cat-food", null, EATING_OUT.random(random))
                    everyday < 80 -> add(date, 300_00L + random.nextLong(0, 700_00L), "cat-transport", null, TRANSPORT.random(random))
                    everyday < 90 -> add(date, 600_00L + random.nextLong(0, 1_500_00L), "cat-shopping", null, SHOPPING.random(random))
                    else -> add(date, 400_00L + random.nextLong(0, 900_00L), "cat-entertainment", null, LEISURE.random(random))
                }
            }

            // One outlier a month, so "biggest expenses" and the busiest-day marker
            // have something real to point at.
            if (random.nextInt(100) < 70) {
                val day = random.nextInt(5, lastDay - 2)
                val (category, label) = OUTLIERS.random(random)
                add(month.atDay(day), 9_000_00L + random.nextLong(0, 22_000_00L), category, null, label)
            }
        }

        return result
    }

    /**
     * A couple of budgets, deliberately in different states — one comfortable, one
     * close, one over — so the status thresholds and their colours are all visible
     * without having to construct spending by hand.
     */
    fun budgets(): List<Budget> = listOf(
        Budget(
            id = "${ID_PREFIX}budget-food",
            categoryId = "cat-food",
            limitMinor = 25_000_00L,
            currencyCode = DefaultData.BASE_CURRENCY_CODE,
            period = null,
        ),
        Budget(
            id = "${ID_PREFIX}budget-transport",
            categoryId = "cat-transport",
            limitMinor = 6_000_00L,
            currencyCode = DefaultData.BASE_CURRENCY_CODE,
            period = null,
        ),
        Budget(
            id = "${ID_PREFIX}budget-bills",
            categoryId = "cat-bills",
            limitMinor = 7_000_00L,
            currencyCode = DefaultData.BASE_CURRENCY_CODE,
            period = null,
        ),
    )

    /** Fixed, so the sample ledger is reproducible across runs and devices. */
    private const val SEED = 20260904L

    private val METHODS = listOf("pm-cash", "pm-debit", "pm-credit", "pm-wallet")

    private val EATING_OUT = listOf("Chai", "Lunch", "Coffee", "Samosas", "Biryani", "Ice cream")
    private val TRANSPORT = listOf("Fuel", "Careem", "Bus fare", "Parking", "Rickshaw")
    private val SHOPPING = listOf("Shampoo & soap", "T-shirt", "Phone case", "Detergent", "Notebook")
    private val LEISURE = listOf("Cinema", "Netflix", "Cricket match", "Book", "Game")

    private val OUTLIERS = listOf(
        "cat-health" to "Dentist",
        "cat-shopping" to "Headphones",
        "cat-travel" to "Flight to Karachi",
        "cat-family" to "School fees",
        "cat-transport" to "Car service",
    )

    private fun <T> List<T>.random(random: Random): T = this[random.nextInt(size)]
}

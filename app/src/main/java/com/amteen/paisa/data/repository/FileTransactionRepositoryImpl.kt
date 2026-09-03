package com.amteen.paisa.data.repository

import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.data.dto.TransactionsFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

/**
 * Transactions, sharded one file per month.
 *
 * A single `transactions.json` would mean rewriting every record on every add —
 * roughly 3 MB of I/O at 10,000 transactions, on the app's most-used action. A
 * shard holds one month (a few hundred records at most), so adding an expense
 * writes only `2026-09.json`.
 *
 * Shards load lazily and stay cached in [shards], which is the single source of
 * truth the UI observes. [idIndex] maps a transaction id to the shard holding it,
 * which is what makes a cross-month edit cheap to detect.
 */
class FileTransactionRepositoryImpl(
    private val store: JsonFileStore,
) : TransactionRepository {

    private val shards = MutableStateFlow<Map<YearMonth, List<Transaction>>>(emptyMap())
    private val idIndex = HashMap<String, YearMonth>()
    private val loadedMonths = HashSet<YearMonth>()
    private val mutex = Mutex()

    @Volatile
    private var allLoaded = false

    // -- Observation --------------------------------------------------------

    override fun observeMonth(month: YearMonth): Flow<List<Transaction>> = flow {
        ensureLoaded(month)
        emitAll(shards.map { it[month].orEmpty() }.distinctUntilChanged())
    }

    override fun observeRange(range: DateRange): Flow<List<Transaction>> = flow {
        val months = range.months()
        months.forEach { ensureLoaded(it) }
        emitAll(
            shards
                .map { cache -> months.flatMap { cache[it].orEmpty() }.filter { it.date in range } }
                .map { it.sortedWith(DISPLAY_ORDER) }
                .distinctUntilChanged(),
        )
    }

    override fun observeAll(): Flow<List<Transaction>> = flow {
        ensureAllLoaded()
        emitAll(
            shards
                .map { cache -> cache.values.flatten().sortedWith(DISPLAY_ORDER) }
                .distinctUntilChanged(),
        )
    }

    // -- Reads --------------------------------------------------------------

    override suspend fun getMonth(month: YearMonth): List<Transaction> {
        ensureLoaded(month)
        return shards.value[month].orEmpty()
    }

    override suspend fun getRange(range: DateRange): List<Transaction> {
        val months = range.months()
        months.forEach { ensureLoaded(it) }
        val cache = shards.value
        return months.flatMap { cache[it].orEmpty() }
            .filter { it.date in range }
            .sortedWith(DISPLAY_ORDER)
    }

    override suspend fun getAll(): List<Transaction> {
        ensureAllLoaded()
        return shards.value.values.flatten().sortedWith(DISPLAY_ORDER)
    }

    override suspend fun getById(id: String): Transaction? {
        shards.value[idIndex[id]]?.firstOrNull { it.id == id }?.let { return it }
        // Not in a loaded shard. Load everything once so the id index is complete;
        // this is what lets `save` detect a cross-month move without a disk scan.
        ensureAllLoaded()
        return shards.value[idIndex[id]]?.firstOrNull { it.id == id }
    }

    override suspend fun availableMonths(): List<YearMonth> =
        store.listFiles(FilePaths.TRANSACTIONS_DIR)
            .mapNotNull { FilePaths.monthFromShardFileName(it) }
            .sortedDescending()

    override suspend fun countByCategory(categoryId: String): Int =
        getAll().count { it.categoryId == categoryId }

    override suspend fun countBySubcategory(subcategoryId: String): Int =
        getAll().count { it.subcategoryId == subcategoryId }

    override suspend fun countByPaymentMethod(paymentMethodId: String): Int =
        getAll().count { it.paymentMethodId == paymentMethodId }

    override suspend fun countByCurrency(currencyCode: String): Int =
        getAll().count { it.currencyCode == currencyCode }

    // -- Writes -------------------------------------------------------------

    /**
     * Inserts or updates.
     *
     * The important case is an edit that moves the date across a month boundary:
     * the record must leave the old shard and join the new one, and **both** files
     * must be written. Handling only the new shard leaves a duplicate behind and
     * silently inflates the old month's totals. See CLAUDE.md rule 3.
     */
    override suspend fun save(transaction: Transaction) {
        mutex.withLock {
            val newMonth = transaction.month
            loadLocked(newMonth)

            val oldMonth = idIndex[transaction.id]
            if (oldMonth != null && oldMonth != newMonth) {
                loadLocked(oldMonth)
                val trimmed = shards.value[oldMonth].orEmpty()
                    .filterNot { it.id == transaction.id }
                writeShardLocked(oldMonth, trimmed)
            }

            val merged = shards.value[newMonth].orEmpty()
                .filterNot { it.id == transaction.id }
                .plus(transaction)
            writeShardLocked(newMonth, merged)
        }
    }

    override suspend fun saveAll(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        mutex.withLock {
            val incomingIds = transactions.map { it.id }.toSet()

            // Every shard that has to change: the ones the new records land in,
            // plus any shard currently holding a record that is moving out.
            val targetMonths = transactions.map { it.month }.toSet()
            val sourceMonths = incomingIds.mapNotNull { idIndex[it] }.toSet()
            (targetMonths + sourceMonths).forEach { loadLocked(it) }

            val byMonth = transactions.groupBy { it.month }
            for (month in targetMonths + sourceMonths) {
                val kept = shards.value[month].orEmpty().filterNot { it.id in incomingIds }
                val next = kept + byMonth[month].orEmpty()
                writeShardLocked(month, next)
            }
        }
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            val month = idIndex[id] ?: return@withLock
            loadLocked(month)
            val next = shards.value[month].orEmpty().filterNot { it.id == id }
            writeShardLocked(month, next)
        }
    }

    override suspend fun replaceAll(transactions: List<Transaction>) {
        mutex.withLock {
            // Remove shards that the new data does not cover, or a Replace import
            // would leave months from the old data behind.
            val existing = store.listFiles(FilePaths.TRANSACTIONS_DIR)
                .mapNotNull { FilePaths.monthFromShardFileName(it) }
            val incoming = transactions.groupBy { it.month }

            for (month in existing - incoming.keys) {
                store.delete(FilePaths.transactionShard(month))
            }

            shards.value = emptyMap()
            idIndex.clear()
            loadedMonths.clear()

            for ((month, records) in incoming) {
                loadedMonths += month
                writeShardLocked(month, records)
            }
            allLoaded = true
        }
    }

    // -- Loading ------------------------------------------------------------

    private suspend fun ensureLoaded(month: YearMonth) {
        if (month in loadedMonths) return
        mutex.withLock { loadLocked(month) }
    }

    private suspend fun ensureAllLoaded() {
        if (allLoaded) return
        val months = availableMonths()
        mutex.withLock {
            months.forEach { loadLocked(it) }
            allLoaded = true
        }
    }

    /** Caller must hold [mutex]. */
    private suspend fun loadLocked(month: YearMonth) {
        if (month in loadedMonths) return

        val read = store.readFile(
            relativePath = FilePaths.transactionShard(month),
            serializer = TransactionsFile.serializer(),
            default = { TransactionsFile(month = FilePaths.shardName(month)) },
        )

        val records = read.value.transactions.mapNotNull { it.toDomain() }
        val (belong, misplaced) = records.partition { it.month == month }

        loadedMonths += month
        putShard(month, belong)

        // Self-heal a record filed under the wrong month — possible after a
        // hand-edited file or a faulty import. Re-home it rather than dropping it
        // or leaving it invisible to date-range queries.
        if (misplaced.isNotEmpty()) {
            writeShardLocked(month, belong)
            for ((target, records2) in misplaced.groupBy { it.month }) {
                loadLocked(target)
                val existing = shards.value[target].orEmpty()
                val ids = records2.map { it.id }.toSet()
                writeShardLocked(target, existing.filterNot { it.id in ids } + records2)
            }
        }
    }

    /** Writes one shard and syncs the cache. Caller must hold [mutex]. */
    private suspend fun writeShardLocked(month: YearMonth, records: List<Transaction>) {
        val sorted = records.sortedWith(DISPLAY_ORDER)
        if (sorted.isEmpty()) {
            // Deleting the last transaction in a month removes the file entirely
            // rather than leaving an empty shard for every month ever touched.
            store.delete(FilePaths.transactionShard(month))
        } else {
            store.writeFile(
                relativePath = FilePaths.transactionShard(month),
                serializer = TransactionsFile.serializer(),
                value = TransactionsFile(
                    month = FilePaths.shardName(month),
                    transactions = sorted.map { it.toDto() },
                ),
            )
        }
        loadedMonths += month
        putShard(month, sorted)
    }

    private fun putShard(month: YearMonth, records: List<Transaction>) {
        idIndex.entries.removeAll { it.value == month }
        records.forEach { idIndex[it.id] = month }
        shards.value = shards.value + (month to records)
    }

    companion object {
        /**
         * Newest first, with the creation instant breaking ties so two entries made
         * on the same day at the same clock minute keep a stable order between
         * recompositions.
         */
        val DISPLAY_ORDER: Comparator<Transaction> =
            compareByDescending<Transaction> { it.date }
                .thenByDescending { it.time }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
    }
}

package com.amteen.paisa.data.repository

import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.file.Recovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

/**
 * Shared machinery for the five single-file collections (categories, budgets,
 * currencies, payment methods).
 *
 * Each of those repositories is the same three things — load once, hold the list in
 * a `StateFlow` as the single source of truth for the UI, and write the whole file
 * on every mutation — so they share one implementation rather than four copies of
 * the same load/persist/lock dance.
 *
 * Whole-file writes are fine here and only here: these files hold tens of records,
 * not thousands. Transactions get sharded instead; see [FileTransactionRepositoryImpl].
 *
 * @param F the file-root DTO, e.g. `CategoriesFile`.
 * @param D the domain type, e.g. `Category`.
 */
internal class FileBackedCollection<D, F>(
    private val store: JsonFileStore,
    private val path: String,
    private val serializer: KSerializer<F>,
    private val extract: (F) -> List<D>,
    private val wrap: (List<D>) -> F,
    private val seed: () -> List<D>,
    private val sort: (List<D>) -> List<D> = { it },
) {

    private val _items = MutableStateFlow<List<D>>(emptyList())
    val items: StateFlow<List<D>> = _items.asStateFlow()

    private val _recovery = MutableStateFlow(Recovery.OK)

    /** Surfaces a degraded read so the UI can warn that data was recovered or lost. */
    val recovery: StateFlow<Recovery> = _recovery.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    suspend fun load() {
        mutex.withLock { loadLocked() }
    }

    private suspend fun loadLocked() {
        if (loaded) return

        val read = store.readFile(path, serializer) { wrap(seed()) }
        _items.value = sort(extract(read.value))
        _recovery.value = read.recovery
        loaded = true

        // A missing file means first run (or the user cleared data): materialise
        // the seed so the next launch reads a real file rather than re-seeding.
        // A corrupt file is *not* re-seeded here — the quarantined original may
        // still be recoverable by hand, and silently replacing it with defaults
        // would look to the user like their data vanished.
        if (read.recovery == Recovery.MISSING) {
            persist(_items.value)
        }
    }

    suspend fun ensureLoaded() {
        if (!loaded) load()
    }

    /**
     * Applies [transform] and persists the result.
     *
     * The file is written **before** the in-memory state updates, so a failed write
     * cannot leave the UI showing data that never reached disk.
     */
    suspend fun mutate(transform: (List<D>) -> List<D>) {
        ensureLoaded()
        mutex.withLock {
            val next = sort(transform(_items.value))
            persist(next)
            _items.value = next
        }
    }

    /** Wholesale replacement, for import. Skips the load so it cannot re-seed. */
    suspend fun replaceAll(items: List<D>) {
        mutex.withLock {
            val next = sort(items)
            persist(next)
            _items.value = next
            loaded = true
        }
    }

    fun current(): List<D> = _items.value

    private suspend fun persist(items: List<D>) {
        store.writeFile(path, serializer, wrap(items))
    }
}

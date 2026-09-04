package com.amteen.paisa.data.repository

import com.amteen.paisa.data.csv.Csv
import com.amteen.paisa.data.dto.BackupFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.AppSnapshot
import com.amteen.paisa.domain.model.CsvRow
import com.amteen.paisa.domain.repository.BackupRepository
import java.time.Instant

/**
 * The wire format for whole-app documents, and the rolling local snapshots.
 *
 * Everything DTO-shaped, JSON-shaped or RFC-4180-shaped lives here so the import and
 * export use cases can be written and tested against domain types alone.
 */
class FileBackupRepositoryImpl(
    private val store: JsonFileStore,
    private val now: () -> Instant = { Instant.now() },
) : BackupRepository {

    override val schemaVersion: Int = JsonFileStore.SCHEMA_VERSION

    override suspend fun encodeJson(snapshot: AppSnapshot): String {
        val file = BackupFile(
            exportedAt = (snapshot.exportedAt ?: now()).toString(),
            app = APP_TAG,
            settings = (snapshot.settings ?: AppSettings()).toDto(),
            categories = snapshot.categories.map { it.toDto() },
            paymentMethods = snapshot.paymentMethods.map { it.toDto() },
            budgets = snapshot.budgets.map { it.toDto() },
            // Written so the document is self-describing: a future reader should not
            // have to guess what the amounts are denominated in.
            currencies = listOf(DefaultData.currency.toDto()),
            transactions = snapshot.transactions.map { it.toDto() },
        )
        return store.json.encodeToString(BackupFile.serializer(), file)
    }

    override suspend fun decodeJson(text: String): AppSnapshot {
        val parsed = try {
            store.json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a Paisa backup.", e)
        }

        val categories = parsed.categories.mapNotNull { it.toDomain() }
        val transactions = parsed.transactions.mapNotNull { it.toDomain() }

        return AppSnapshot(
            schemaVersion = parsed.schemaVersion,
            exportedAt = runCatching { Instant.parse(parsed.exportedAt) }.getOrNull(),
            settings = parsed.settings.toDomain(),
            categories = categories,
            paymentMethods = parsed.paymentMethods.mapNotNull { it.toDomain() },
            budgets = parsed.budgets.mapNotNull { it.toDomain() },
            transactions = transactions,
            // Reported rather than swallowed: "40 of 43" is useful, a silent 40 is a
            // loss the user never learns about.
            unreadableTransactions = parsed.transactions.size - transactions.size,
            unreadableCategories = parsed.categories.size - categories.size,
        )
    }

    override suspend fun encodeCsv(header: List<String>, rows: List<List<String>>): String =
        Csv.writeAll(header, rows)

    override suspend fun decodeCsv(text: String): List<CsvRow> {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return emptyList()

        // Column order is whatever the spreadsheet wrote, so rows are keyed by the
        // file's own header rather than by position.
        val header = rows.first().map { it.trim().lowercase() }
        return rows.drop(1).map { row ->
            CsvRow(
                header.withIndex().associate { (index, name) ->
                    name to (row.getOrNull(index) ?: "")
                },
            )
        }
    }

    override suspend fun writeLocalBackup(content: String, reason: String, keep: Int): String {
        store.ensureDirectories()
        val name = fileName(reason)
        store.writeRaw("${FilePaths.BACKUP_DIR}/$name", content)
        prune(keep)
        return name
    }

    override suspend fun listLocalBackups(): List<String> =
        store.listFiles(FilePaths.BACKUP_DIR)
            .filter { it.startsWith(PREFIX) && it.endsWith(".json") }
            // Names sort lexicographically by time, which is what makes pruning a
            // sort and a drop rather than a date parse per file.
            .sortedDescending()

    override suspend fun readLocalBackup(name: String): String? =
        store.readRaw("${FilePaths.BACKUP_DIR}/$name")

    private suspend fun prune(keep: Int) {
        if (keep <= 0) return
        listLocalBackups().drop(keep).forEach {
            store.delete("${FilePaths.BACKUP_DIR}/$it")
        }
    }

    /** `backup-2026-09-04T12-30-00Z-before-import.json`. */
    private fun fileName(reason: String): String {
        val stamp = now().toString().replace(':', '-').replace('.', '-')
        val safe = reason.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "manual" }
        return "$PREFIX$stamp-$safe.json"
    }

    companion object {
        const val PREFIX = "backup-"
        private const val APP_TAG = "Paisa"
    }
}

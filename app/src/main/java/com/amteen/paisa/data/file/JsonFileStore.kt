package com.amteen.paisa.data.file

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The one and only path from this app to the filesystem.
 *
 * No other class may open a stream over an app-data file — see CLAUDE.md rule 1.
 * Everything here is deliberately Android-free (it takes a plain [File] root), so
 * the whole storage layer is unit-testable on the JVM with no emulator.
 *
 * **Writing** never truncates a live file:
 * ```
 * serialize -> X.json.tmp -> flush + fd.sync -> rename X.json to X.json.bak -> rename tmp over X.json
 * ```
 * A crash at any point leaves either the previous file or its `.bak` intact.
 *
 * **Reading** recovers instead of crashing — see [readFile] for the ladder.
 */
class JsonFileStore(
    private val rootDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val json: Json = Json {
        // Pretty-printed because these files are the user's own data and a stated
        // feature is that they can open and read them. The size cost is irrelevant
        // next to being able to inspect a backup in any text editor.
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true      // forward compatibility within a schema version
        encodeDefaults = true
        explicitNulls = false          // omit nulls; absent reads back as the default
    }

    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Paths whose on-disk file was written by a newer schema. Writing to one is
     * refused outright — overwriting would discard fields this build cannot
     * represent, which is unrecoverable data loss.
     */
    private val lockedPaths = ConcurrentHashMap.newKeySet<String>()

    private fun lockFor(path: String): Mutex = locks.computeIfAbsent(path) { Mutex() }

    fun resolve(relativePath: String): File = File(rootDir, relativePath)

    fun isLocked(relativePath: String): Boolean = lockedPaths.contains(relativePath)

    // -- Reading ------------------------------------------------------------

    /**
     * Reads [relativePath], falling back through the recovery ladder:
     *
     * | On disk | Outcome |
     * |---|---|
     * | Missing, and no `.bak` | [Recovery.MISSING] with [default] |
     * | Missing, but `.bak` present (crash mid-rename) | [Recovery.RESTORED_FROM_BACKUP] |
     * | Empty (0 bytes) | [Recovery.MISSING] with [default] |
     * | Unparseable / truncated | quarantined to `.corrupt-<ts>`, then `.bak`, else [default] |
     * | `schemaVersion` newer than this build | [Recovery.SCHEMA_TOO_NEW]; path locked for writes |
     *
     * Never throws for bad content. Only a genuine I/O fault propagates.
     */
    suspend fun <T> readFile(
        relativePath: String,
        serializer: KSerializer<T>,
        default: () -> T,
    ): StoreRead<T> = withContext(ioDispatcher) {
        lockFor(relativePath).withLock {
            val file = resolve(relativePath)

            if (!file.exists() || file.length() == 0L) {
                val fromBackup = tryReadBackup(relativePath, serializer)
                return@withLock fromBackup
                    ?: StoreRead(default(), Recovery.MISSING)
            }

            when (val parsed = decode(file, serializer)) {
                is Decoded.Success -> StoreRead(parsed.value, Recovery.OK)

                is Decoded.SchemaTooNew -> {
                    lockedPaths.add(relativePath)
                    StoreRead(
                        value = default(),
                        recovery = Recovery.SCHEMA_TOO_NEW,
                        fileSchemaVersion = parsed.fileVersion,
                    )
                }

                is Decoded.Corrupt -> {
                    quarantine(file)
                    tryReadBackup(relativePath, serializer)
                        ?: StoreRead(default(), Recovery.CORRUPT_DEFAULTED, parsed.cause)
                }
            }
        }
    }

    private fun <T> tryReadBackup(relativePath: String, serializer: KSerializer<T>): StoreRead<T>? {
        val backup = resolve("$relativePath$SUFFIX_BAK")
        if (!backup.exists() || backup.length() == 0L) return null
        return when (val parsed = decode(backup, serializer)) {
            is Decoded.Success -> StoreRead(parsed.value, Recovery.RESTORED_FROM_BACKUP)
            else -> null
        }
    }

    private sealed interface Decoded<out T> {
        data class Success<T>(val value: T) : Decoded<T>
        data class SchemaTooNew(val fileVersion: Int) : Decoded<Nothing>
        data class Corrupt(val cause: Throwable) : Decoded<Nothing>
    }

    private fun <T> decode(file: File, serializer: KSerializer<T>): Decoded<T> = try {
        val element = json.parseToJsonElement(file.readText(Charsets.UTF_8))
        val version = (element as? JsonObject)
            ?.get(KEY_SCHEMA_VERSION)
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()

        if (version != null && version > SCHEMA_VERSION) {
            Decoded.SchemaTooNew(version)
        } else {
            Decoded.Success(json.decodeFromJsonElement(serializer, element))
        }
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        // Malformed JSON, truncated file, wrong shape, bad enum value — all the
        // same to the caller: this file cannot be trusted.
        Decoded.Corrupt(e)
    }

    // -- Writing ------------------------------------------------------------

    /**
     * Serializes [value] and replaces [relativePath] atomically.
     *
     * @throws SchemaTooNewException if the existing file came from a newer build.
     */
    suspend fun <T> writeFile(
        relativePath: String,
        serializer: KSerializer<T>,
        value: T,
    ): Unit = withContext(ioDispatcher) {
        if (lockedPaths.contains(relativePath)) {
            throw SchemaTooNewException(relativePath)
        }
        lockFor(relativePath).withLock {
            val target = resolve(relativePath)
            target.parentFile?.mkdirs()

            val bytes = json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
            val tmp = File(target.parentFile, "${target.name}$SUFFIX_TMP")

            // 1. Fully materialise the new content on disk, durably. Without the
            //    fsync, a power loss can leave a renamed-but-empty file: the
            //    rename is journalled while the data is still only in page cache.
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }

            // 2. Keep the previous good copy as .bak so read() has something to
            //    recover from if the new file is later found corrupt.
            if (target.exists()) {
                move(target, File(target.parentFile, "${target.name}$SUFFIX_BAK"), atomic = false)
            }

            // 3. Swap the new content in.
            move(tmp, target, atomic = true)
        }
    }

    suspend fun delete(relativePath: String): Boolean = withContext(ioDispatcher) {
        lockFor(relativePath).withLock {
            val target = resolve(relativePath)
            File(target.parentFile, "${target.name}$SUFFIX_BAK").delete()
            target.delete()
        }
    }

    suspend fun exists(relativePath: String): Boolean = withContext(ioDispatcher) {
        resolve(relativePath).exists()
    }

    /** Names of the files directly inside [relativeDir]; empty if it does not exist. */
    suspend fun listFiles(relativeDir: String): List<String> = withContext(ioDispatcher) {
        resolve(relativeDir).listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    suspend fun ensureDirectories() = withContext(ioDispatcher) {
        rootDir.mkdirs()
        resolve(FilePaths.TRANSACTIONS_DIR).mkdirs()
        resolve(FilePaths.BACKUP_DIR).mkdirs()
        Unit
    }

    // -- Internals ----------------------------------------------------------

    private fun move(from: File, to: File, atomic: Boolean) {
        try {
            if (atomic) {
                Files.move(
                    from.toPath(),
                    to.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } else {
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: AtomicMoveNotSupportedException) {
            // Some filesystems (and some Windows conditions) refuse an atomic
            // replace. A plain replace still beats truncating the original.
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Moves an unreadable file aside rather than deleting it. The user's data may
     * be partially salvageable by hand, and silently destroying it would be worse
     * than any amount of disk use.
     */
    private fun quarantine(file: File) {
        val target = File(file.parentFile, "${file.name}$SUFFIX_CORRUPT${System.currentTimeMillis()}")
        try {
            move(file, target, atomic = false)
        } catch (e: IOException) {
            // If even the rename fails there is nothing more to try; the caller
            // still gets defaults and the app stays usable.
        }
    }

    companion object {
        /** Bump only alongside a migration in `SchemaMigrations`. See CLAUDE.md. */
        const val SCHEMA_VERSION = 1

        const val KEY_SCHEMA_VERSION = "schemaVersion"

        const val SUFFIX_TMP = ".tmp"
        const val SUFFIX_BAK = ".bak"
        const val SUFFIX_CORRUPT = ".corrupt-"
    }
}

/** How a read went. [Recovery.OK] is the normal case; the rest are worth surfacing. */
enum class Recovery {
    OK,
    MISSING,
    RESTORED_FROM_BACKUP,
    CORRUPT_DEFAULTED,
    SCHEMA_TOO_NEW;

    val isDegraded: Boolean get() = this == RESTORED_FROM_BACKUP ||
        this == CORRUPT_DEFAULTED ||
        this == SCHEMA_TOO_NEW
}

data class StoreRead<T>(
    val value: T,
    val recovery: Recovery,
    val cause: Throwable? = null,
    val fileSchemaVersion: Int? = null,
)

class SchemaTooNewException(path: String) : IllegalStateException(
    "$path was written by a newer version of Paisa and will not be overwritten.",
)

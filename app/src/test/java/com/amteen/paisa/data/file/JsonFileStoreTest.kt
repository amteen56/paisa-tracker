package com.amteen.paisa.data.file

import com.amteen.paisa.data.dto.CategoriesFile
import com.amteen.paisa.data.dto.CategoryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFailsWith

/**
 * The storage safety net.
 *
 * These run on the JVM against a real temp directory — no emulator, no mocking of
 * the filesystem. A fake would not catch the things that actually go wrong here
 * (partial writes, rename semantics, unparseable bytes).
 */
class JsonFileStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: JsonFileStore

    private val serializer = CategoriesFile.serializer()
    private fun emptyFile() = CategoriesFile()

    private fun sample(vararg names: String) = CategoriesFile(
        categories = names.map { CategoryDto(id = it, name = it) },
    )

    @Before
    fun setUp() {
        root = temp.newFolder("app-data")
        store = JsonFileStore(root)
    }

    @Test
    fun `round-trips a file`() = runTest {
        store.writeFile(PATH, serializer, sample("food", "transport"))

        val read = store.readFile(PATH, serializer, ::emptyFile)

        assertEquals(Recovery.OK, read.recovery)
        assertEquals(listOf("food", "transport"), read.value.categories.map { it.id })
    }

    @Test
    fun `a missing file yields defaults instead of throwing`() = runTest {
        val read = store.readFile(PATH, serializer) { sample("seeded") }

        assertEquals(Recovery.MISSING, read.recovery)
        assertEquals(listOf("seeded"), read.value.categories.map { it.id })
    }

    @Test
    fun `an empty file is treated as missing`() = runTest {
        File(root, PATH).writeText("")

        val read = store.readFile(PATH, serializer) { sample("seeded") }

        assertEquals(Recovery.MISSING, read.recovery)
    }

    @Test
    fun `no temp file survives a completed write`() = runTest {
        store.writeFile(PATH, serializer, sample("food"))

        assertFalse(File(root, PATH + JsonFileStore.SUFFIX_TMP).exists())
        assertTrue(File(root, PATH).exists())
    }

    @Test
    fun `the previous version is kept as a sidecar backup`() = runTest {
        store.writeFile(PATH, serializer, sample("first"))
        store.writeFile(PATH, serializer, sample("second"))

        val backup = File(root, PATH + JsonFileStore.SUFFIX_BAK)
        assertTrue(backup.exists())
        assertTrue(backup.readText().contains("first"))
    }

    @Test
    fun `a truncated file is quarantined and recovered from the backup`() = runTest {
        store.writeFile(PATH, serializer, sample("good"))
        store.writeFile(PATH, serializer, sample("newer"))

        // Simulate an interrupted write landing half a document on disk.
        val target = File(root, PATH)
        target.writeText(target.readText().take(20))

        val read = store.readFile(PATH, serializer, ::emptyFile)

        assertEquals(Recovery.RESTORED_FROM_BACKUP, read.recovery)
        assertEquals(listOf("good"), read.value.categories.map { it.id })

        // The unreadable bytes are moved aside, never deleted — they may still be
        // salvageable by hand.
        val quarantined = root.listFiles()!!.filter { it.name.contains(JsonFileStore.SUFFIX_CORRUPT) }
        assertEquals(1, quarantined.size)
    }

    @Test
    fun `an unparseable file with no backup falls back to defaults`() = runTest {
        File(root, PATH).writeText("{ this is not json ")

        val read = store.readFile(PATH, serializer) { sample("seeded") }

        assertEquals(Recovery.CORRUPT_DEFAULTED, read.recovery)
        assertEquals(listOf("seeded"), read.value.categories.map { it.id })
        assertTrue(root.listFiles()!!.any { it.name.contains(JsonFileStore.SUFFIX_CORRUPT) })
    }

    @Test
    fun `a file from a newer schema is neither read nor overwritten`() = runTest {
        File(root, PATH).writeText(
            """{ "schemaVersion": ${JsonFileStore.SCHEMA_VERSION + 5}, "categories": [] }""",
        )

        val read = store.readFile(PATH, serializer) { sample("seeded") }

        assertEquals(Recovery.SCHEMA_TOO_NEW, read.recovery)
        assertEquals(JsonFileStore.SCHEMA_VERSION + 5, read.fileSchemaVersion)
        assertTrue(store.isLocked(PATH))

        // Overwriting would destroy fields this build cannot represent.
        assertFailsWith<SchemaTooNewException> {
            store.writeFile(PATH, serializer, sample("clobber"))
        }
        assertTrue(File(root, PATH).readText().contains("\"schemaVersion\""))
    }

    @Test
    fun `unknown keys are ignored so a newer minor format still loads`() = runTest {
        File(root, PATH).writeText(
            """
            {
              "schemaVersion": ${JsonFileStore.SCHEMA_VERSION},
              "categories": [ { "id": "food", "name": "Food", "somethingNew": 42 } ],
              "alsoNew": true
            }
            """.trimIndent(),
        )

        val read = store.readFile(PATH, serializer, ::emptyFile)

        assertEquals(Recovery.OK, read.recovery)
        assertEquals(listOf("food"), read.value.categories.map { it.id })
    }

    @Test
    fun `nested directories are created on write`() = runTest {
        val nested = "transactions/2026-09.json"
        store.writeFile(nested, serializer, sample("x"))

        assertTrue(File(root, nested).exists())
    }

    @Test
    fun `concurrent writes to one file do not interleave`() = runTest {
        // Each write must land whole; the last one wins and the file stays valid.
        // Real threads, not the test scheduler — the per-file Mutex is what is
        // under test and virtual time would never exercise it.
        withContext(Dispatchers.Default) {
            (1..20)
                .map { index -> async { store.writeFile(PATH, serializer, sample("run-$index")) } }
                .awaitAll()
        }

        val read = store.readFile(PATH, serializer, ::emptyFile)
        assertEquals(Recovery.OK, read.recovery)
        assertEquals(1, read.value.categories.size)
    }

    private companion object {
        const val PATH = "categories.json"
    }
}

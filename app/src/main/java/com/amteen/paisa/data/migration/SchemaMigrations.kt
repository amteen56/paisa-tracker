package com.amteen.paisa.data.migration

import com.amteen.paisa.data.file.JsonFileStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Schema evolution, as a chain of pure `n -> n+1` functions.
 *
 * The rules, from CLAUDE.md:
 *
 * - **Never edit an existing migration.** A file written two versions ago must still
 *   pass through the exact same transformations it would have then. Editing one
 *   silently changes how already-shipped data is interpreted.
 * - **Always append.** Adding a field with a default is not a schema bump at all —
 *   the DTO default handles it. A bump is for changes an old file cannot survive.
 * - Every bump gets a round-trip test against a real fixture of the old format.
 *
 * Nothing to migrate yet: version 1 is the first released format. This exists now
 * so that when the first bump comes, the shape it belongs in already exists and
 * the temptation to hand-patch DTOs never arises.
 */
object SchemaMigrations {

    const val CURRENT = JsonFileStore.SCHEMA_VERSION

    /** Ordered `from` -> transformation. Append; never reorder or edit. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    val oldestSupported: Int = 1

    fun canMigrate(fromVersion: Int): Boolean =
        fromVersion in oldestSupported..CURRENT

    /**
     * Steps [root] forward to [CURRENT].
     *
     * @throws IllegalArgumentException for a version this build cannot reach —
     *   either older than [oldestSupported] or newer than [CURRENT]. A newer file
     *   is never rewritten; see [JsonFileStore].
     */
    fun migrate(root: JsonObject, fromVersion: Int): JsonObject {
        require(fromVersion <= CURRENT) {
            "Data is from schema $fromVersion; this build understands $CURRENT."
        }
        require(fromVersion >= oldestSupported) {
            "Schema $fromVersion is too old to migrate (oldest supported is $oldestSupported)."
        }

        var current = root
        for (version in fromVersion until CURRENT) {
            val step = migrations[version]
                ?: error("No migration registered for schema $version -> ${version + 1}.")
            current = step(current)
        }
        return withVersion(current, CURRENT)
    }

    private fun withVersion(root: JsonObject, version: Int): JsonObject = buildJsonObject {
        root.forEach { (key, value) -> if (key != JsonFileStore.KEY_SCHEMA_VERSION) put(key, value) }
        put(JsonFileStore.KEY_SCHEMA_VERSION, version)
    }
}

package com.amteen.paisa.data.repository

import com.amteen.paisa.data.dto.SettingsFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.file.Recovery
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `settings.json`.
 *
 * A single object rather than a bag of keys, so any settings change is one atomic
 * write and can never be observed half-applied.
 */
class FileSettingsRepositoryImpl(
    private val store: JsonFileStore,
) : SettingsRepository {

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    override suspend fun load() {
        mutex.withLock { loadLocked() }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        val read = store.readFile(FilePaths.SETTINGS, SettingsFile.serializer()) { SettingsFile() }
        _settings.value = read.value.settings.toDomain()
        loaded = true
        if (read.recovery == Recovery.MISSING) {
            persist(_settings.value)
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        if (!loaded) load()
        mutex.withLock {
            val next = transform(_settings.value)
            if (next == _settings.value) return@withLock
            persist(next)
            _settings.value = next
        }
    }

    private suspend fun persist(settings: AppSettings) {
        store.writeFile(FilePaths.SETTINGS, SettingsFile.serializer(), SettingsFile(settings = settings.toDto()))
    }
}

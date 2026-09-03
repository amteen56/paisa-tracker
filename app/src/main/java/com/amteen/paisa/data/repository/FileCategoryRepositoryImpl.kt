package com.amteen.paisa.data.repository

import com.amteen.paisa.data.dto.CategoriesFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.StateFlow

class FileCategoryRepositoryImpl(store: JsonFileStore) : CategoryRepository {

    private val backing = FileBackedCollection(
        store = store,
        path = FilePaths.CATEGORIES,
        serializer = CategoriesFile.serializer(),
        extract = { file -> file.categories.mapNotNull { it.toDomain() } },
        wrap = { list -> CategoriesFile(categories = list.map { it.toDto() }) },
        seed = { DefaultData.categories },
        sort = { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.name })) },
    )

    override val categories: StateFlow<List<Category>> = backing.items

    override suspend fun load() = backing.load()

    override suspend fun getById(id: String): Category? {
        backing.ensureLoaded()
        return backing.current().firstOrNull { it.id == id }
    }

    override suspend fun upsert(category: Category) = backing.mutate { current ->
        if (current.any { it.id == category.id }) {
            current.map { if (it.id == category.id) category else it }
        } else {
            current + category.copy(
                sortOrder = if (category.sortOrder == 0) {
                    (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
                } else {
                    category.sortOrder
                },
            )
        }
    }

    /**
     * Hides the category from pickers while leaving it resolvable, so existing
     * transactions and past reports keep rendering correctly. See CLAUDE.md rule 4.
     */
    override suspend fun archive(id: String, archived: Boolean) = backing.mutate { current ->
        current.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    /**
     * Only safe at reference count zero. The caller checks
     * `TransactionRepository.countByCategory` first and offers Archive otherwise —
     * a transaction must never end up pointing at an id that no longer resolves.
     */
    override suspend fun hardDelete(id: String) = backing.mutate { current ->
        current.filterNot { it.id == id }
    }

    override suspend fun reorder(orderedIds: List<String>) = backing.mutate { current ->
        val position = orderedIds.withIndex().associate { (index, id) -> id to index }
        current.map { it.copy(sortOrder = position[it.id] ?: it.sortOrder) }
    }

    override suspend fun replaceAll(categories: List<Category>) = backing.replaceAll(categories)
}

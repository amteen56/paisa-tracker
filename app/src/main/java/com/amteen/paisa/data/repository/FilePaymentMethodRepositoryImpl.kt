package com.amteen.paisa.data.repository

import com.amteen.paisa.data.dto.PaymentMethodsFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import kotlinx.coroutines.flow.StateFlow

class FilePaymentMethodRepositoryImpl(store: JsonFileStore) : PaymentMethodRepository {

    private val backing = FileBackedCollection(
        store = store,
        path = FilePaths.PAYMENT_METHODS,
        serializer = PaymentMethodsFile.serializer(),
        extract = { file -> file.paymentMethods.mapNotNull { it.toDomain() } },
        wrap = { list -> PaymentMethodsFile(paymentMethods = list.map { it.toDto() }) },
        seed = { DefaultData.paymentMethods },
        sort = { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.name })) },
    )

    override val paymentMethods: StateFlow<List<PaymentMethod>> = backing.items

    override suspend fun load() = backing.load()

    override suspend fun getById(id: String): PaymentMethod? {
        backing.ensureLoaded()
        return backing.current().firstOrNull { it.id == id }
    }

    override suspend fun upsert(paymentMethod: PaymentMethod) = backing.mutate { current ->
        if (current.any { it.id == paymentMethod.id }) {
            current.map { if (it.id == paymentMethod.id) paymentMethod else it }
        } else {
            current + paymentMethod.copy(
                sortOrder = if (paymentMethod.sortOrder == 0) {
                    (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
                } else {
                    paymentMethod.sortOrder
                },
            )
        }
    }

    override suspend fun archive(id: String, archived: Boolean) = backing.mutate { current ->
        current.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    /** Only at reference count zero — see [FileCategoryRepositoryImpl.hardDelete]. */
    override suspend fun hardDelete(id: String) = backing.mutate { current ->
        current.filterNot { it.id == id }
    }

    override suspend fun reorder(orderedIds: List<String>) = backing.mutate { current ->
        val position = orderedIds.withIndex().associate { (index, id) -> id to index }
        current.map { it.copy(sortOrder = position[it.id] ?: it.sortOrder) }
    }

    override suspend fun replaceAll(paymentMethods: List<PaymentMethod>) =
        backing.replaceAll(paymentMethods)
}

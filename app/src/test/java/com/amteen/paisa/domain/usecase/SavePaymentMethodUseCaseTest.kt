package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.testing.FakePaymentMethodRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SavePaymentMethodUseCaseTest {

    private lateinit var repository: FakePaymentMethodRepository
    private lateinit var save: SavePaymentMethodUseCase

    @Before
    fun setUp() {
        repository = FakePaymentMethodRepository()
        var counter = 0
        save = SavePaymentMethodUseCase(repository) { "generated-${counter++}" }
    }

    @Test
    fun `a blank name is rejected`() = runTest {
        val error = (save(PaymentMethodInput(name = "  ", iconKey = "cash")) as AppResult.Err).error

        assertTrue(error is AppError.Validation)
        assertEquals(SavePaymentMethodUseCase.FIELD_NAME, (error as AppError.Validation).field)
    }

    @Test
    fun `a duplicate name is rejected regardless of case`() = runTest {
        repository.upsert(PaymentMethod("pm-cash", "Cash", "cash", 0))

        val result = save(PaymentMethodInput(name = "cash", iconKey = "cash"))

        assertTrue(result is AppResult.Err)
        assertEquals(1, repository.paymentMethods.value.size)
    }

    @Test
    fun `renaming does not clash with itself`() = runTest {
        repository.upsert(PaymentMethod("pm-cash", "Cash", "cash", 0))

        val result = save(PaymentMethodInput(id = "pm-cash", name = "Cash", iconKey = "wallet"))

        assertTrue(result is AppResult.Ok)
        assertEquals("wallet", repository.getById("pm-cash")?.iconKey)
    }

    @Test
    fun `an edit preserves sort order and the archived flag`() = runTest {
        repository.upsert(PaymentMethod("pm-cash", "Cash", "cash", 4, archived = true))

        save(PaymentMethodInput(id = "pm-cash", name = "Petty Cash", iconKey = "cash"))

        val saved = repository.getById("pm-cash")!!
        assertEquals(4, saved.sortOrder)
        assertTrue(saved.archived)
        assertEquals("Petty Cash", saved.name)
    }

    @Test
    fun `a name is trimmed and a blank icon falls back to the default`() = runTest {
        save(PaymentMethodInput(name = "  Mobile Wallet  ", iconKey = ""))

        val saved = repository.paymentMethods.value.single()
        assertEquals("Mobile Wallet", saved.name)
        assertEquals(SavePaymentMethodUseCase.DEFAULT_ICON, saved.iconKey)
        assertEquals("generated-0", saved.id)
    }
}

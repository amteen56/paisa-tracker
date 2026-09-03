package com.amteen.paisa.domain.model

/**
 * How a transaction was paid — Cash, a card, a mobile wallet.
 *
 * Archived rather than deleted once referenced, for the same reason categories are.
 */
data class PaymentMethod(
    val id: String,
    val name: String,
    val iconKey: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

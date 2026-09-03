package com.amteen.paisa.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a stored `iconKey` to a drawable icon.
 *
 * The key is a stable string rather than a resource id or a vector reference
 * because it is written into the user's JSON files and has to keep resolving across
 * app versions, icon-set changes and a restore onto a different device. An unknown
 * key falls back to a generic icon rather than crashing — a category imported from
 * a newer build must still render.
 */
object CategoryIcons {

    private val byKey: Map<String, ImageVector> = mapOf(
        "restaurant" to Icons.Filled.Restaurant,
        "car" to Icons.Filled.DirectionsCar,
        "home" to Icons.Filled.Home,
        "receipt" to Icons.Filled.Receipt,
        "shopping-bag" to Icons.Filled.ShoppingBag,
        "health" to Icons.Filled.LocalHospital,
        "school" to Icons.Filled.School,
        "movie" to Icons.Filled.Movie,
        "family" to Icons.Filled.FamilyRestroom,
        "flight" to Icons.Filled.Flight,
        "gift" to Icons.Filled.CardGiftcard,
        "bank" to Icons.Filled.AccountBalance,
        "category" to Icons.Filled.Category,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "store" to Icons.Filled.Store,
        "laptop" to Icons.Filled.Laptop,
        "trending-up" to Icons.AutoMirrored.Filled.TrendingUp,
        "cash" to Icons.Filled.Payments,
        "card" to Icons.Filled.CreditCard,
        "credit-card" to Icons.Filled.CreditCard,
    )

    /** Every key offered by the category icon picker, in a sensible display order. */
    val pickerKeys: List<String> = byKey.keys.toList()

    operator fun get(key: String?): ImageVector =
        byKey[key] ?: Icons.Filled.Category
}

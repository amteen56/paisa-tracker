package com.amteen.paisa.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.ui.graphics.vector.ImageVector

/** One option in the icon picker. [label] is what TalkBack reads out. */
data class IconChoice(val key: String, val label: String, val image: ImageVector)

/**
 * Maps a stored `iconKey` to a drawable icon.
 *
 * The key is a stable string rather than a resource id or a vector reference
 * because it is written into the user's JSON files and has to keep resolving across
 * app versions, icon-set changes and a restore onto a different device. An unknown
 * key falls back to a generic icon rather than crashing — a category imported from
 * a newer build must still render.
 *
 * **Never rename or repurpose a key.** Add new ones to the end of [choices].
 */
object CategoryIcons {

    /** Every icon offered, in picker order. */
    val choices: List<IconChoice> = listOf(
        IconChoice("category", "General", Icons.Filled.Category),
        IconChoice("restaurant", "Restaurant", Icons.Filled.Restaurant),
        IconChoice("cafe", "Coffee", Icons.Filled.LocalCafe),
        IconChoice("car", "Car", Icons.Filled.DirectionsCar),
        IconChoice("fuel", "Fuel", Icons.Filled.LocalGasStation),
        IconChoice("home", "Home", Icons.Filled.Home),
        IconChoice("receipt", "Bills", Icons.Filled.Receipt),
        IconChoice("phone", "Phone", Icons.Filled.Phone),
        IconChoice("shopping-bag", "Shopping", Icons.Filled.ShoppingBag),
        IconChoice("clothing", "Clothing", Icons.Filled.Checkroom),
        IconChoice("health", "Health", Icons.Filled.LocalHospital),
        IconChoice("fitness", "Fitness", Icons.Filled.FitnessCenter),
        IconChoice("school", "Education", Icons.Filled.School),
        IconChoice("movie", "Entertainment", Icons.Filled.Movie),
        IconChoice("games", "Games", Icons.Filled.SportsEsports),
        IconChoice("family", "Family", Icons.Filled.FamilyRestroom),
        IconChoice("pets", "Pets", Icons.Filled.Pets),
        IconChoice("flight", "Travel", Icons.Filled.Flight),
        IconChoice("gift", "Gifts", Icons.Filled.CardGiftcard),
        IconChoice("insurance", "Insurance", Icons.Filled.Umbrella),
        IconChoice("bank", "Bank", Icons.Filled.AccountBalance),
        IconChoice("wallet", "Wallet", Icons.Filled.AccountBalanceWallet),
        IconChoice("cash", "Cash", Icons.Filled.Payments),
        IconChoice("card", "Card", Icons.Filled.CreditCard),
        IconChoice("credit-card", "Credit card", Icons.Filled.CreditCard),
        IconChoice("qr", "QR payment", Icons.Filled.QrCode),
        IconChoice("store", "Store", Icons.Filled.Store),
        IconChoice("laptop", "Laptop", Icons.Filled.Laptop),
        IconChoice("trending-up", "Investments", Icons.AutoMirrored.Filled.TrendingUp),
    )

    private val byKey: Map<String, IconChoice> = choices.associateBy { it.key }

    /** Every key offered by the category icon picker, in display order. */
    val pickerKeys: List<String> = choices.map { it.key }

    /**
     * The subset offered for payment methods. A payment method is a *way to pay*,
     * so the full category list would mostly be noise here.
     */
    val paymentMethodChoices: List<IconChoice> =
        listOf("cash", "card", "credit-card", "bank", "wallet", "qr", "phone", "receipt", "category")
            .mapNotNull { byKey[it] }

    operator fun get(key: String?): ImageVector =
        byKey[key]?.image ?: Icons.Filled.Category

    fun labelFor(key: String?): String = byKey[key]?.label ?: "General"
}

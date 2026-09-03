package com.amteen.paisa.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * The app's notification channels.
 *
 * There is exactly one, and it is local. Nothing here reaches the network — these
 * are notifications the app generates from the user's own data, on their own device.
 */
object NotificationChannels {

    const val BUDGET_ALERTS = "budget_alerts"

    /**
     * Idempotent: creating an existing channel updates its name and description but
     * never resets the importance or the user's own overrides of it.
     *
     * `minSdk` is 26, so channels always exist and there is no version guard.
     */
    fun ensure(context: Context) {
        val channel = NotificationChannel(
            BUDGET_ALERTS,
            context.getString(com.amteen.paisa.R.string.notification_channel_budgets),
            // DEFAULT rather than HIGH: a budget crossing 75% is worth a glance, not
            // a heads-up card that interrupts whatever the user is doing.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(com.amteen.paisa.R.string.notification_channel_budgets_desc)
            setShowBadge(true)
        }

        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}

package com.amteen.paisa.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amteen.paisa.MainActivity
import com.amteen.paisa.R
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.domain.model.BudgetAlertThresholds
import com.amteen.paisa.domain.usecase.BudgetAlertEvent
import com.amteen.paisa.domain.usecase.EvaluateBudgetAlertsUseCase
import kotlin.math.roundToInt

/**
 * Shows the budget alerts that [EvaluateBudgetAlertsUseCase] decided on.
 *
 * This is the Android half and nothing else: it holds no thresholds, sums nothing,
 * and makes no decision about whether an alert is due. Keeping the judgement in the
 * use case is what lets "once per threshold per period" be unit-tested without an
 * emulator.
 *
 * Every notification here is generated locally from the user's own file. The app has
 * no `INTERNET` permission — see CLAUDE.md.
 */
class BudgetAlertNotifier(
    private val context: Context,
    private val evaluate: EvaluateBudgetAlertsUseCase,
) {

    /**
     * Evaluates and shows. Safe to call often — the use case is what stops it being
     * noisy, so callers do not have to debounce.
     *
     * Records an alert as shown **only** once it has actually been posted. If the
     * user has denied the permission, the alert stays pending and fires whenever
     * they grant it, rather than being silently consumed while the shade was closed.
     */
    suspend fun check() {
        val events = evaluate()
        if (events.isEmpty()) return

        if (!canPost()) return

        val manager = NotificationManagerCompat.from(context)
        val shown = events.filter { event ->
            try {
                manager.notify(event.notificationId(), build(event))
                true
            } catch (e: SecurityException) {
                // The permission can be revoked between the check above and here.
                // Losing the notification is acceptable; losing the record of it is
                // not, so this alert stays pending.
                false
            }
        }

        evaluate.markShown(shown)
    }

    private fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun build(event: BudgetAlertEvent): android.app.Notification {
        val progress = event.summary.progress
        val currency = event.summary.currency
        val percent = progress.percent.roundToInt()

        val title = when (event.newlyCrossed) {
            BudgetAlertThresholds.EXCEEDED ->
                context.getString(R.string.notification_budget_exceeded, event.summary.label)
            else ->
                context.getString(
                    R.string.notification_budget_threshold,
                    event.summary.label,
                    percent,
                )
        }

        val body = if (progress.remainingMinor < 0) {
            context.getString(
                R.string.notification_budget_body_over,
                MoneyFormatter.format(progress.remaining.abs(), currency),
                MoneyFormatter.format(progress.budget.limit, currency),
            )
        } else {
            context.getString(
                R.string.notification_budget_body_left,
                MoneyFormatter.format(progress.remaining, currency),
                MoneyFormatter.format(progress.budget.limit, currency),
            )
        }

        return NotificationCompat.Builder(context, NotificationChannels.BUDGET_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * One notification slot per budget, so a budget that crosses 75% and later 100%
     * replaces its own earlier alert instead of stacking two contradictory ones.
     */
    private fun BudgetAlertEvent.notificationId(): Int =
        NOTIFICATION_ID_BASE + summary.id.hashCode()

    private companion object {
        const val NOTIFICATION_ID_BASE = 4200
    }
}

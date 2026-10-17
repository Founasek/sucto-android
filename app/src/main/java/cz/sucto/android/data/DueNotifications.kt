package cz.sucto.android.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.sucto.android.MainActivity
import cz.sucto.android.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Denní upozornění na splatnosti (v 9:00). Úloha na pozadí si sama načte nezaplacené faktury naposledy otevřené firmy.
 * Upozornění se může zobrazit na zamčené obrazovce, proto je ve výchozím stavu neutrální (bez firmy a počtů) a podrobnosti
 * si uživatel zapíná vědomě; i s nimi se na zamčeném telefonu ukáže jen neutrální veřejná verze. Částky ani jména v něm nejsou nikdy.
 */
object DueNotifications {
    private const val WORK = "due-notifications"
    private const val CHANNEL = "due"
    private const val NOTIFICATION_ID = 1001
    private const val HOUR = 9

    fun needsPermission(context: Context) = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Naplánuje denní úlohu na nejbližších 9:00; zapnuté upozornění se po startu aplikace jen obnoví. */
    fun schedule(context: Context) {
        if (!AppSettings.dueNotificationsEnabled(context)) { cancel(context); return }
        val now = LocalDateTime.now()
        var first = now.with(LocalTime.of(HOUR, 0))
        if (!first.isAfter(now)) first = first.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DueWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK) }

    internal fun show(context: Context, today: Int, overdue: Int, companyName: String?) {
        if (needsPermission(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Splatnosti", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val neutral = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Splatnosti dnes")
            .setContentText("Máte faktury ke splatnosti. Klepnutím je zobrazíte.").build()
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(neutral)
        if (AppSettings.dueNotificationDetails(context)) {
            builder.setContentTitle("Splatnosti dnes" + (companyName?.let { " · $it" } ?: ""))
            builder.setContentText(listOfNotNull("Dnes splatné: $today".takeIf { today > 0 }, "Po splatnosti dosud: $overdue".takeIf { overdue > 0 }).joinToString("\n"))
        } else {
            builder.setContentTitle("Splatnosti dnes").setContentText("Máte faktury ke splatnosti. Klepnutím je zobrazíte.")
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    internal class DueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val store = SessionStore(applicationContext)
            val token = store.loadToken() ?: return Result.success()
            val companyId = store.lastCompanyId ?: return Result.success()
            return try {
                val items = SuctoApi().dueItems(token, companyId, LocalDate.now())
                val today = LocalDate.now()
                val dueToday = items.count { ChronoUnit.DAYS.between(today, it.due) == 0L }
                val overdue = items.count { it.due.isBefore(today) }
                if (dueToday > 0 || overdue > 0) show(applicationContext, dueToday, overdue, AppSettings.lastCompanyName(applicationContext))
                Result.success()
            } catch (error: ApiException) {
                if (error.unauthorized) Result.success() else Result.retry()
            } catch (error: Exception) {
                Result.retry()
            }
        }
    }
}

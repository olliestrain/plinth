package app.cadence.gym.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.cadence.gym.MainActivity
import app.cadence.gym.R
import app.cadence.gym.CadenceApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Daily check: if the last export is older than the user's threshold, nudge them to back up. */
class BackupReminderWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = ctx.applicationContext as CadenceApp
        val prefs = app.settings.flow.first()
        if (!prefs.onboarded || prefs.reminderDays <= 0) return Result.success()
        if (app.repo.doneSessions.value.isEmpty()) return Result.success()
        val ageDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - prefs.lastExport)
        if (prefs.lastExport != 0L && ageDays < prefs.reminderDays) return Result.success()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()

        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CadenceApp.CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(ctx.getString(R.string.notif_backup_title))
            .setContentText(ctx.getString(R.string.notif_backup_text))
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(42, n)
        return Result.success()
    }
}

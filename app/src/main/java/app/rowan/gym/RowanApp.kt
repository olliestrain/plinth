package app.rowan.gym

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.rowan.gym.data.Repository
import app.rowan.gym.data.Settings
import app.rowan.gym.data.RowanDb
import app.rowan.gym.net.HubClient
import app.rowan.gym.net.HubManager
import app.rowan.gym.work.BackupReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class RowanApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: Settings; private set
    lateinit var repo: Repository; private set
    val hub by lazy { HubClient(this) }
    val hubManager by lazy { HubManager(this) }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repo = Repository(this, RowanDb.build(this), settings, scope)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_BACKUP, getString(R.string.notif_channel_backup), NotificationManager.IMPORTANCE_DEFAULT))
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "backup-reminder", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    companion object { const val CHANNEL_BACKUP = "backup" }
}

val LocalApp = staticCompositionLocalOf<RowanApp> { error("RowanApp not provided") }

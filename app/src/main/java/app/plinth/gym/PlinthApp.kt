package app.plinth.gym

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.plinth.gym.data.Repository
import app.plinth.gym.data.Settings
import app.plinth.gym.data.PlinthDb
import app.plinth.gym.net.UpdateState
import app.plinth.gym.work.BackupReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class PlinthApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: Settings; private set
    lateinit var repo: Repository; private set
    val updates = UpdateState()

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repo = Repository(this, PlinthDb.build(this), settings, scope)
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

val LocalApp = staticCompositionLocalOf<PlinthApp> { error("PlinthApp not provided") }

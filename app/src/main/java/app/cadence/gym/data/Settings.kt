package app.cadence.gym.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore("cadence_settings")

data class Prefs(
    val onboarded: Boolean = false,
    val name: String = "",
    val goal: String = "consistency",
    val experience: Int = 1,
    val weeklyGoal: Int = 3,
    val trainingDays: Set<Int> = setOf(1, 3, 5), // ISO: 1 = Monday
    val useLb: Boolean = false,
    val lastExport: Long = 0,
    val reminderDays: Int = 7,
    val skippedUpdate: String = "",
    val restSeconds: Int = 90,
) {
}

class Settings(private val ctx: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val goal = stringPreferencesKey("goal")
        val experience = intPreferencesKey("experience")
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val trainingDays = stringPreferencesKey("training_days")
        val useLb = booleanPreferencesKey("use_lb")
        val lastExport = longPreferencesKey("last_export")
        val reminderDays = intPreferencesKey("reminder_days")
        val skippedUpdate = stringPreferencesKey("skipped_update")
        val restSeconds = intPreferencesKey("rest_seconds")
    }

    val flow: Flow<Prefs> = ctx.store.data.map { p ->
        Prefs(
            onboarded = p[K.onboarded] ?: false,
            name = p[K.name] ?: "",
            goal = p[K.goal] ?: "consistency",
            experience = p[K.experience] ?: 1,
            weeklyGoal = p[K.weeklyGoal] ?: 3,
            trainingDays = (p[K.trainingDays] ?: "1,3,5").split(",").mapNotNull { it.toIntOrNull() }.toSet(),
            useLb = p[K.useLb] ?: false,
            lastExport = p[K.lastExport] ?: 0,
            reminderDays = p[K.reminderDays] ?: 7,
            skippedUpdate = p[K.skippedUpdate] ?: "",
            restSeconds = p[K.restSeconds] ?: 90,
        )
    }

    suspend fun update(block: Editor.() -> Unit) {
        ctx.store.edit { Editor(it).block() }
    }

    class Editor(private val p: androidx.datastore.preferences.core.MutablePreferences) {
        var onboarded: Boolean get() = p[K.onboarded] ?: false; set(v) { p[K.onboarded] = v }
        var name: String get() = p[K.name] ?: ""; set(v) { p[K.name] = v }
        var goal: String get() = p[K.goal] ?: ""; set(v) { p[K.goal] = v }
        var experience: Int get() = p[K.experience] ?: 1; set(v) { p[K.experience] = v }
        var weeklyGoal: Int get() = p[K.weeklyGoal] ?: 3; set(v) { p[K.weeklyGoal] = v }
        var trainingDays: Set<Int>
            get() = (p[K.trainingDays] ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
            set(v) { p[K.trainingDays] = v.sorted().joinToString(",") }
        var useLb: Boolean get() = p[K.useLb] ?: false; set(v) { p[K.useLb] = v }
        var lastExport: Long get() = p[K.lastExport] ?: 0; set(v) { p[K.lastExport] = v }
        var reminderDays: Int get() = p[K.reminderDays] ?: 7; set(v) { p[K.reminderDays] = v }
        var skippedUpdate: String get() = p[K.skippedUpdate] ?: ""; set(v) { p[K.skippedUpdate] = v }
        var restSeconds: Int get() = p[K.restSeconds] ?: 90; set(v) { p[K.restSeconds] = v }
    }
}

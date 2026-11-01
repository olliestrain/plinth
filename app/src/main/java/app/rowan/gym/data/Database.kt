package app.rowan.gym.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Exercise::class, WorkoutTemplate::class, TemplateExercise::class, DayPlan::class,
        Session::class, SessionExercise::class, SetEntry::class, Note::class, BodyWeight::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RowanDb : RoomDatabase() {
    abstract fun dao(): RowanDao

    companion object {
        fun build(ctx: Context): RowanDb =
            Room.databaseBuilder(ctx, RowanDb::class.java, "rowan.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

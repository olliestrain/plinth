package app.cadence.gym.data

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
abstract class CadenceDb : RoomDatabase() {
    abstract fun dao(): CadenceDao

    companion object {
        fun build(ctx: Context): CadenceDb =
            Room.databaseBuilder(ctx, CadenceDb::class.java, "cadence.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

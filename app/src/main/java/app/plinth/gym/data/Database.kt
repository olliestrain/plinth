package app.plinth.gym.data

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
abstract class PlinthDb : RoomDatabase() {
    abstract fun dao(): PlinthDao

    companion object {
        fun build(ctx: Context): PlinthDb =
            Room.databaseBuilder(ctx, PlinthDb::class.java, "plinth.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

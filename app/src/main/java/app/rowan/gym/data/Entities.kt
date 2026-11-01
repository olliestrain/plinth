package app.rowan.gym.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Measure { WEIGHT_REPS, REPS, TIME }
enum class DayType { NONE, WORKOUT, REST }
enum class SessionStatus { DRAFT, DONE }

@Entity
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val measure: Measure = Measure.WEIGHT_REPS,
    val archived: Boolean = false,
)

@Entity
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIdx: Int = 0,
    val position: Int = 0,
)

@Entity(
    foreignKeys = [ForeignKey(WorkoutTemplate::class, ["id"], ["templateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("templateId")],
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val sets: Int = 3,
    val repsMin: Int = 8,
    val repsMax: Int = 12,
    val weight: Double = 0.0,
    val note: String = "",
)

data class TemplateExerciseFull(
    @Embedded val te: TemplateExercise,
    val exName: String,
    val exMeasure: Measure,
)

/** One independent calendar day. Changing a Monday never touches other Mondays. */
@Entity
data class DayPlan(
    @PrimaryKey val date: String, // ISO yyyy-MM-dd
    val type: DayType = DayType.NONE,
    val templateId: Long? = null,
    val note: String = "",
)

@Entity(indices = [Index("date")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val templateId: Long? = null,
    val name: String,
    val status: SessionStatus = SessionStatus.DRAFT,
    val startedAt: Long = System.currentTimeMillis(),
    val durationSec: Int = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val name: String,
    val measure: Measure,
    val position: Int,
    val note: String = "",
    /** Shown prominently next to the exercise the next time it is performed. */
    val nextTimeNote: String = "",
    val substitutedFrom: String? = null,
)

@Entity(
    foreignKeys = [ForeignKey(SessionExercise::class, ["id"], ["sessionExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionExerciseId")],
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val weight: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val done: Boolean = false,
)

@Entity
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val source: String = "",
)

@Entity(indices = [Index("date")])
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val kg: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

// ---- in-memory aggregates (never stored) ----
data class ExerciseFull(val ex: SessionExercise, val sets: List<SetEntry>)
data class SessionFull(val session: Session, val exercises: List<ExerciseFull>) {
    val doneSets get() = exercises.sumOf { e -> e.sets.count { it.done } }
}
data class TemplateFull(val template: WorkoutTemplate, val exercises: List<TemplateExerciseFull>)

package app.rowan.gym.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RowanDao {
    // exercises
    @Query("SELECT * FROM Exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun exercises(): Flow<List<Exercise>>
    @Query("SELECT * FROM Exercise ORDER BY id") suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM Exercise WHERE id = :id") suspend fun exercise(id: Long): Exercise?
    @Query("SELECT * FROM Exercise WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun exerciseByName(name: String): Exercise?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: Exercise): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExercises(e: List<Exercise>)

    // templates
    @Query("SELECT * FROM WorkoutTemplate ORDER BY position, id") fun templates(): Flow<List<WorkoutTemplate>>
    @Query("SELECT * FROM WorkoutTemplate ORDER BY id") suspend fun allTemplates(): List<WorkoutTemplate>
    @Query("SELECT * FROM WorkoutTemplate WHERE id = :id") suspend fun template(id: Long): WorkoutTemplate?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: WorkoutTemplate): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTemplates(t: List<WorkoutTemplate>)
    @Update suspend fun update(t: WorkoutTemplate)
    @Query("DELETE FROM WorkoutTemplate WHERE id = :id") suspend fun deleteTemplate(id: Long)

    @Query(
        """SELECT te.*, e.name AS exName, e.measure AS exMeasure FROM TemplateExercise te
           JOIN Exercise e ON e.id = te.exerciseId ORDER BY te.templateId, te.position"""
    )
    fun templateExercises(): Flow<List<TemplateExerciseFull>>
    @Query(
        """SELECT te.*, e.name AS exName, e.measure AS exMeasure FROM TemplateExercise te
           JOIN Exercise e ON e.id = te.exerciseId WHERE te.templateId = :id ORDER BY te.position"""
    )
    suspend fun templateExercises(id: Long): List<TemplateExerciseFull>
    @Query("SELECT * FROM TemplateExercise ORDER BY id") suspend fun allTemplateExercises(): List<TemplateExercise>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTemplateExercises(l: List<TemplateExercise>)
    @Query("DELETE FROM TemplateExercise WHERE templateId = :id") suspend fun clearTemplateExercises(id: Long)

    // calendar
    @Query("SELECT * FROM DayPlan WHERE type != 'NONE' OR note != ''") fun dayPlans(): Flow<List<DayPlan>>
    @Query("SELECT * FROM DayPlan") suspend fun allDayPlans(): List<DayPlan>
    @Query("SELECT * FROM DayPlan WHERE date = :date") suspend fun dayPlan(date: String): DayPlan?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: DayPlan)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDayPlans(d: List<DayPlan>)
    @Query("DELETE FROM DayPlan WHERE date = :date") suspend fun deleteDayPlan(date: String)

    // sessions
    @Query("SELECT * FROM Session ORDER BY date DESC, createdAt DESC") fun sessions(): Flow<List<Session>>
    @Query("SELECT * FROM Session ORDER BY id") suspend fun allSessions(): List<Session>
    @Query("SELECT * FROM Session WHERE id = :id") suspend fun session(id: Long): Session?
    @Query("SELECT * FROM SessionExercise ORDER BY sessionId, position") fun sessionExercises(): Flow<List<SessionExercise>>
    @Query("SELECT * FROM SessionExercise WHERE sessionId = :id ORDER BY position") suspend fun sessionExercises(id: Long): List<SessionExercise>
    @Query("SELECT * FROM SessionExercise ORDER BY id") suspend fun allSessionExercises(): List<SessionExercise>
    @Query("SELECT * FROM SetEntry ORDER BY sessionExerciseId, position") fun sets(): Flow<List<SetEntry>>
    @Query("SELECT * FROM SetEntry ORDER BY id") suspend fun allSets(): List<SetEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: Session): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSessions(s: List<Session>)
    @Update suspend fun update(s: Session)
    @Insert suspend fun insert(e: SessionExercise): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSessionExercises(e: List<SessionExercise>)
    @Insert suspend fun insertSets(s: List<SetEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSets(s: List<SetEntry>)
    @Query("DELETE FROM SessionExercise WHERE sessionId = :id") suspend fun clearSessionExercises(id: Long)
    @Query("DELETE FROM Session WHERE id = :id") suspend fun deleteSession(id: Long)

    @Query(
        """SELECT se.* FROM SessionExercise se JOIN Session s ON s.id = se.sessionId
           WHERE se.exerciseId = :exerciseId AND s.status = 'DONE' AND s.id != :excludeSession
           ORDER BY s.date DESC, s.createdAt DESC LIMIT 1"""
    )
    suspend fun lastExercise(exerciseId: Long, excludeSession: Long): SessionExercise?
    @Query("SELECT * FROM SetEntry WHERE sessionExerciseId = :id ORDER BY position")
    suspend fun setsOf(id: Long): List<SetEntry>

    // notes
    @Query("SELECT * FROM Note ORDER BY createdAt DESC") fun notes(): Flow<List<Note>>
    @Query("SELECT * FROM Note ORDER BY id") suspend fun allNotes(): List<Note>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(n: Note): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNotes(n: List<Note>)
    @Delete suspend fun delete(n: Note)

    // body weight
    @Query("SELECT * FROM BodyWeight ORDER BY date DESC, createdAt DESC") fun bodyWeights(): Flow<List<BodyWeight>>
    @Query("SELECT * FROM BodyWeight ORDER BY id") suspend fun allBodyWeights(): List<BodyWeight>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(b: BodyWeight): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBodyWeights(b: List<BodyWeight>)
    @Delete suspend fun delete(b: BodyWeight)

    // wipe (import)
    @Query("DELETE FROM Exercise") suspend fun wipeExercises()
    @Query("DELETE FROM WorkoutTemplate") suspend fun wipeTemplates()
    @Query("DELETE FROM DayPlan") suspend fun wipeDayPlans()
    @Query("DELETE FROM Session") suspend fun wipeSessions()
    @Query("DELETE FROM Note") suspend fun wipeNotes()
    @Query("DELETE FROM BodyWeight") suspend fun wipeBodyWeights()
}

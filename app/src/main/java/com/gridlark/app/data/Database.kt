package com.gridlark.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One row per puzzle the player has opened.
 * id = "d:<game>:<epochDay>" for dailies, "p:<game>:<seed>" for practice.
 */
@Entity(tableName = "plays")
data class PlayEntity(
    @PrimaryKey val id: String,
    val game: String,
    val mode: String,
    /** Daily: the puzzle's date. Practice: the day it was started. */
    val epochDay: Long,
    val seed: Long,
    val difficulty: String,
    /** Serialized [SavedBoard]; null until the first move. */
    val stateJson: String?,
    val elapsedMs: Long,
    val hintsUsed: Int,
    val mistakesFixed: Int,
    val solved: Boolean,
    val solvedAtMillis: Long?,
    /** Local day the puzzle was solved (for streaks). */
    val solvedOnEpochDay: Long?,
    val updatedAt: Long,
) {
    companion object {
        fun dailyId(game: GameType, epochDay: Long) = "d:${game.key}:$epochDay"
        fun practiceId(game: GameType, seed: Long) = "p:${game.key}:$seed"
    }
}

@Dao
interface PlayDao {
    @Query("SELECT * FROM plays WHERE id = :id")
    suspend fun get(id: String): PlayEntity?

    @Upsert
    suspend fun upsert(entity: PlayEntity)

    @Query("SELECT * FROM plays WHERE mode = 'DAILY' AND epochDay BETWEEN :from AND :to")
    fun observeDailies(from: Long, to: Long): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE mode = 'DAILY' AND epochDay BETWEEN :from AND :to")
    suspend fun dailies(from: Long, to: Long): List<PlayEntity>

    @Query("SELECT * FROM plays WHERE solved = 1")
    fun observeSolved(): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE mode = 'PRACTICE' AND solved = 0 ORDER BY updatedAt DESC")
    fun observeOpenPractice(): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE solved = 1 AND game = :game")
    suspend fun solvedForGame(game: String): List<PlayEntity>

    @Query("SELECT COUNT(*) FROM plays WHERE solved = 1")
    suspend fun solvedCount(): Int

    @Query("DELETE FROM plays WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [PlayEntity::class], version = 1, exportSchema = true)
abstract class GridlarkDatabase : RoomDatabase() {
    abstract fun plays(): PlayDao

    companion object {
        fun build(context: Context): GridlarkDatabase =
            Room.databaseBuilder(context, GridlarkDatabase::class.java, "gridlark.db").build()
    }
}

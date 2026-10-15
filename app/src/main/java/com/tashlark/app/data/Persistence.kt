package com.tashlark.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tashlark.engine.Action
import com.tashlark.engine.Card
import com.tashlark.engine.Suit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Compact text form of an action, used for saved matches (replayed on resume). */
object ActionCodec {
    fun encode(a: Action): String = when (a) {
        is Action.Play -> "P${a.card.code}"
        is Action.Bid -> "B${a.value}"
        Action.Pass -> "X"
        is Action.SetTrump -> "T${a.card?.code ?: -1}:${a.suit.ordinal}"
        Action.CallReveal -> "R"
        Action.Double -> "D"
        Action.Redouble -> "RD"
        Action.NoDouble -> "ND"
    }

    fun decode(s: String): Action = when {
        s == "X" -> Action.Pass
        s == "R" -> Action.CallReveal
        s == "D" -> Action.Double
        s == "RD" -> Action.Redouble
        s == "ND" -> Action.NoDouble
        s.startsWith("P") -> Action.Play(Card(s.drop(1).toInt()))
        s.startsWith("B") -> Action.Bid(s.drop(1).toInt())
        s.startsWith("T") -> {
            val (c, suit) = s.drop(1).split(':').map { it.toInt() }
            Action.SetTrump(if (c < 0) null else Card(c), Suit.entries[suit])
        }
        else -> throw IllegalArgumentException("unknown action '$s'")
    }
}

/**
 * Everything needed to rebuild a match exactly: the game, the house rules it started with,
 * the seed, and every action taken. Deals are deterministic from the seed.
 */
@Serializable
data class SavedMatch(
    val version: Int = 1,
    val gameId: String,
    val seed: Long,
    val skill: String,
    val rules: SavedRules,
    /** Actions of each finished hand. */
    val hands: List<List<String>>,
    /** Actions of the hand in progress. */
    val current: List<String>,
    val assisted: Boolean = false,
    val updatedAt: Long = 0L,
)

@Serializable
data class SavedRules(
    val cbMustBeat: Boolean, val cbMaxBid: Int, val cbRounds: Int,
    val tnPair: Boolean, val tnDoubling: Boolean, val tnLastTrickPoint: Boolean,
    val cpTarget: Int,
    val mkCutHukum: Boolean, val mkWeighted: Boolean, val mkTarget: Int,
) {
    fun toHouseRules() = HouseRules(cbMustBeat, cbMaxBid, cbRounds, tnPair, tnDoubling, tnLastTrickPoint, cpTarget, mkCutHukum, mkWeighted, mkTarget)

    companion object {
        fun of(r: HouseRules) = SavedRules(
            r.cbMustBeat, r.cbMaxBid, r.cbRounds, r.tnPair, r.tnDoubling, r.tnLastTrickPoint,
            r.cpTarget, r.mkCutHukum, r.mkWeighted, r.mkTarget,
        )
    }
}

/** One saved match per game, written atomically (temp file + rename) so a kill never corrupts it. */
class MatchStore(context: Context) {
    private val dir = File(context.filesDir, "matches").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private fun file(gameId: String) = File(dir, "$gameId.json")

    suspend fun load(gameId: String): SavedMatch? = withContext(Dispatchers.IO) {
        lock.withLock {
            val f = file(gameId)
            if (!f.exists()) return@withLock null
            runCatching { json.decodeFromString(SavedMatch.serializer(), f.readText()) }.getOrNull()
        }
    }

    suspend fun save(m: SavedMatch) = withContext(Dispatchers.IO) {
        lock.withLock {
            val tmp = File(dir, "${m.gameId}.json.tmp")
            tmp.writeText(json.encodeToString(SavedMatch.serializer(), m))
            if (!tmp.renameTo(file(m.gameId))) {
                file(m.gameId).delete()
                tmp.renameTo(file(m.gameId))
            }
        }
    }

    suspend fun clear(gameId: String) = withContext(Dispatchers.IO) {
        lock.withLock { file(gameId).delete() }
    }

    suspend fun all(): List<SavedMatch> = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull {
                runCatching { json.decodeFromString(SavedMatch.serializer(), it.readText()) }.getOrNull()
            }
        }
    }
}

// --------------------------------------------------------------------------- match history

@Entity(tableName = "matches")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val endedAt: Long,
    val won: Boolean,
    /** e.g. "12.4" or "6 – 3". */
    val yourScore: String,
    val hands: Int,
    val skill: String,
    val assisted: Boolean,
)

@Dao
interface MatchDao {
    @Insert
    suspend fun insert(r: MatchRecord)

    @Query("SELECT * FROM matches ORDER BY endedAt DESC")
    fun observeAll(): Flow<List<MatchRecord>>

    @Query("DELETE FROM matches")
    suspend fun clear()
}

@Database(entities = [MatchRecord::class], version = 1, exportSchema = true)
abstract class TashDatabase : RoomDatabase() {
    abstract fun matches(): MatchDao

    companion object {
        fun build(context: Context): TashDatabase =
            Room.databaseBuilder(context, TashDatabase::class.java, "tashlark.db").build()
    }
}

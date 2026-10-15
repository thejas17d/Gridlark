package com.tashlark.app.data

import com.tashlark.engine.CardGame
import com.tashlark.engine.HandState
import com.tashlark.engine.callbreak.CallBreakConfig
import com.tashlark.engine.callbreak.CallBreakGame
import com.tashlark.engine.courtpiece.CourtPieceConfig
import com.tashlark.engine.courtpiece.CourtPieceGame
import com.tashlark.engine.mendikot.MendikotConfig
import com.tashlark.engine.mendikot.MendikotGame
import com.tashlark.engine.mendikot.MendikotScoring
import com.tashlark.engine.mendikot.MendikotTrumpMode
import com.tashlark.engine.twentynine.TwentyNineConfig
import com.tashlark.engine.twentynine.TwentyNineGame

/** Home-screen facts about each game. [id] is persisted; never change it. */
data class GameInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val players: String,
    val length: String,
)

object GameCatalog {

    val all: List<GameInfo> = listOf(
        GameInfo("callbreak", "Call Break", "Bid your tricks, spades always trump", "4 players, each for themselves", "5 rounds · about 12 min"),
        GameInfo("twentynine", "29", "Bid for points, hide the trump, find the pair", "2 teams of 2", "First to 6 · about 15 min"),
        GameInfo("courtpiece", "Court Piece", "Call trump from five cards, go for a court", "2 teams of 2", "First to 10 · about 15 min"),
        GameInfo("mendikot", "Mendikot", "Capture the tens with your partner", "2 teams of 2", "First to 5 · about 15 min"),
    )

    fun info(id: String): GameInfo = all.first { it.id == id }

    /** Builds the engine game for [id] with the given house rules. */
    fun create(id: String, r: HouseRules): CardGame<HandState, Any> = when (id) {
        "callbreak" -> CallBreakGame(
            CallBreakConfig(mustBeatWhenFollowing = r.cbMustBeat, maxBid = r.cbMaxBid, rounds = r.cbRounds),
        ).erased()
        "twentynine" -> TwentyNineGame(
            TwentyNineConfig(lastTrickPoint = r.tnLastTrickPoint, doubling = r.tnDoubling, pair = r.tnPair),
        ).erased()
        "courtpiece" -> CourtPieceGame(CourtPieceConfig(target = r.cpTarget)).erased()
        "mendikot" -> MendikotGame(
            MendikotConfig(
                trumpMode = if (r.mkCutHukum) MendikotTrumpMode.CUT_HUKUM else MendikotTrumpMode.BAND_HUKUM,
                scoring = if (r.mkWeighted) MendikotScoring.WEIGHTED else MendikotScoring.SIMPLE,
                target = r.mkTarget,
            ),
        ).erased()
        else -> throw IllegalArgumentException("unknown game $id")
    }
}

/** Lets the app drive any game through one non-generic type. Safe: each game only ever sees its own states. */
@Suppress("UNCHECKED_CAST")
fun <S : HandState, M : Any> CardGame<S, M>.erased(): CardGame<HandState, Any> = this as CardGame<HandState, Any>

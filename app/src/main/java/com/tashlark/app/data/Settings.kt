package com.tashlark.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tashlark.app.ui.theme.ThemeMode
import com.tashlark.engine.Skill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

enum class CardSize { NORMAL, LARGE }
enum class Speed(val factor: Double, val label: String) {
    RELAXED(1.4, "Relaxed"), NORMAL(1.0, "Normal"), FAST(0.5, "Fast")
}
/** Visual seating only: the traditional South Asian direction is anticlockwise. */
enum class Direction { ANTICLOCKWISE, CLOCKWISE }

/** House rules, stored as plain values and turned into engine configs by [GameCatalog]. */
data class HouseRules(
    val cbMustBeat: Boolean = true,
    val cbMaxBid: Int = 13,
    val cbRounds: Int = 5,
    val tnPair: Boolean = true,
    val tnDoubling: Boolean = true,
    val tnLastTrickPoint: Boolean = false,
    val cpTarget: Int = 10,
    val mkCutHukum: Boolean = false,
    val mkWeighted: Boolean = true,
    val mkTarget: Int = 5,
)

data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fourColourDeck: Boolean = false,
    val cardSize: CardSize = CardSize.NORMAL,
    val speed: Speed = Speed.NORMAL,
    val direction: Direction = Direction.ANTICLOCKWISE,
    val skill: Skill = Skill.MEDIUM,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val oneTapPlay: Boolean = false,
    val welcomeDone: Boolean = false,
    val adsRemoved: Boolean = false,
    val adFreeUntil: Long = 0L,
    val reviewAsked: Boolean = false,
    val rules: HouseRules = HouseRules(),
)

data class AdCounters(
    val firstOpenEpochDay: Long,
    val matchesFinished: Int,
    val lastInterstitialAt: Long,
)

class SettingsRepository(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val fourColour = booleanPreferencesKey("four_colour")
        val cardSize = stringPreferencesKey("card_size")
        val speed = stringPreferencesKey("speed")
        val direction = stringPreferencesKey("direction")
        val skill = stringPreferencesKey("skill")
        val haptics = booleanPreferencesKey("haptics")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val oneTap = booleanPreferencesKey("one_tap")
        val welcomeDone = booleanPreferencesKey("welcome_done")
        val adsRemoved = booleanPreferencesKey("ads_removed")
        val adFreeUntil = longPreferencesKey("ad_free_until")
        val reviewAsked = booleanPreferencesKey("review_asked")
        val firstOpen = longPreferencesKey("first_open_day")
        val matchesFinished = intPreferencesKey("matches_finished")
        val lastInterstitial = longPreferencesKey("last_interstitial_at")
        val cbMustBeat = booleanPreferencesKey("cb_must_beat")
        val cbMaxBid = intPreferencesKey("cb_max_bid")
        val cbRounds = intPreferencesKey("cb_rounds")
        val tnPair = booleanPreferencesKey("tn_pair")
        val tnDoubling = booleanPreferencesKey("tn_doubling")
        val tnLast = booleanPreferencesKey("tn_last_trick")
        val cpTarget = intPreferencesKey("cp_target")
        val mkCut = booleanPreferencesKey("mk_cut")
        val mkWeighted = booleanPreferencesKey("mk_weighted")
        val mkTarget = intPreferencesKey("mk_target")
    }

    private val data: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    val settings: Flow<UserSettings> = data.map { p ->
        UserSettings(
            theme = p.enum(K.theme, ThemeMode.SYSTEM),
            fourColourDeck = p[K.fourColour] ?: false,
            cardSize = p.enum(K.cardSize, CardSize.NORMAL),
            speed = p.enum(K.speed, Speed.NORMAL),
            direction = p.enum(K.direction, Direction.ANTICLOCKWISE),
            skill = p.enum(K.skill, Skill.MEDIUM),
            haptics = p[K.haptics] ?: true,
            reduceMotion = p[K.reduceMotion] ?: false,
            oneTapPlay = p[K.oneTap] ?: false,
            welcomeDone = p[K.welcomeDone] ?: false,
            adsRemoved = p[K.adsRemoved] ?: false,
            adFreeUntil = p[K.adFreeUntil] ?: 0L,
            reviewAsked = p[K.reviewAsked] ?: false,
            rules = HouseRules(
                cbMustBeat = p[K.cbMustBeat] ?: true,
                cbMaxBid = p[K.cbMaxBid] ?: 13,
                cbRounds = p[K.cbRounds] ?: 5,
                tnPair = p[K.tnPair] ?: true,
                tnDoubling = p[K.tnDoubling] ?: true,
                tnLastTrickPoint = p[K.tnLast] ?: false,
                cpTarget = p[K.cpTarget] ?: 10,
                mkCutHukum = p[K.mkCut] ?: false,
                mkWeighted = p[K.mkWeighted] ?: true,
                mkTarget = p[K.mkTarget] ?: 5,
            ),
        )
    }

    suspend fun current(): UserSettings = settings.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setTheme(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setFourColour(v: Boolean) = edit { it[K.fourColour] = v }
    suspend fun setCardSize(v: CardSize) = edit { it[K.cardSize] = v.name }
    suspend fun setSpeed(v: Speed) = edit { it[K.speed] = v.name }
    suspend fun setDirection(v: Direction) = edit { it[K.direction] = v.name }
    suspend fun setSkill(v: Skill) = edit { it[K.skill] = v.name }
    suspend fun setHaptics(v: Boolean) = edit { it[K.haptics] = v }
    suspend fun setReduceMotion(v: Boolean) = edit { it[K.reduceMotion] = v }
    suspend fun setOneTapPlay(v: Boolean) = edit { it[K.oneTap] = v }
    suspend fun setWelcomeDone() = edit { it[K.welcomeDone] = true }
    suspend fun setAdsRemoved(v: Boolean) = edit { it[K.adsRemoved] = v }
    suspend fun setAdFreeUntil(v: Long) = edit { it[K.adFreeUntil] = v }
    suspend fun setReviewAsked() = edit { it[K.reviewAsked] = true }

    suspend fun setRules(r: HouseRules) = edit {
        it[K.cbMustBeat] = r.cbMustBeat
        it[K.cbMaxBid] = r.cbMaxBid
        it[K.cbRounds] = r.cbRounds
        it[K.tnPair] = r.tnPair
        it[K.tnDoubling] = r.tnDoubling
        it[K.tnLast] = r.tnLastTrickPoint
        it[K.cpTarget] = r.cpTarget
        it[K.mkCut] = r.mkCutHukum
        it[K.mkWeighted] = r.mkWeighted
        it[K.mkTarget] = r.mkTarget
    }

    suspend fun counters(today: Long): AdCounters {
        val p = data.first()
        val first = p[K.firstOpen] ?: today.also { d -> edit { it[K.firstOpen] = d } }
        return AdCounters(first, p[K.matchesFinished] ?: 0, p[K.lastInterstitial] ?: 0L)
    }

    suspend fun recordMatchFinished() = edit { it[K.matchesFinished] = (it[K.matchesFinished] ?: 0) + 1 }
    suspend fun recordInterstitial(now: Long) = edit { it[K.lastInterstitial] = now }
}

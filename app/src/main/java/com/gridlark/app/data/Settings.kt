package com.gridlark.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gridlark.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val showTimer: Boolean = true,
    val highlightConflicts: Boolean = true,
    val patternAssist: Boolean = false,
    val reduceMotion: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 8 * 60,
    val reminderPromptDone: Boolean = false,
    val welcomeDone: Boolean = false,
    val rulesSeen: Set<String> = emptySet(),
    val adsRemoved: Boolean = false,
    val reviewAsked: Boolean = false,
)

/** Bookkeeping that doesn't belong in the settings screen. */
data class AdCounters(
    val firstOpenEpochDay: Long,
    val solvesSinceInterstitial: Int,
    val lastInterstitialAt: Long,
    val freeHintDay: Long,
    val freeHintsUsed: Int,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics")
        val showTimer = booleanPreferencesKey("show_timer")
        val highlightConflicts = booleanPreferencesKey("highlight_conflicts")
        val patternAssist = booleanPreferencesKey("pattern_assist")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderMinutes = intPreferencesKey("reminder_minutes")
        val reminderPromptDone = booleanPreferencesKey("reminder_prompt_done")
        val welcomeDone = booleanPreferencesKey("welcome_done")
        val rulesSeen = stringPreferencesKey("rules_seen")
        val adsRemoved = booleanPreferencesKey("ads_removed")
        val reviewAsked = booleanPreferencesKey("review_asked")
        val firstOpenDay = longPreferencesKey("first_open_day")
        val solvesSinceInterstitial = intPreferencesKey("solves_since_interstitial")
        val lastInterstitialAt = longPreferencesKey("last_interstitial_at")
        val freeHintDay = longPreferencesKey("free_hint_day")
        val freeHintsUsed = intPreferencesKey("free_hints_used")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            haptics = p[Keys.haptics] ?: true,
            showTimer = p[Keys.showTimer] ?: true,
            highlightConflicts = p[Keys.highlightConflicts] ?: true,
            patternAssist = p[Keys.patternAssist] ?: false,
            reduceMotion = p[Keys.reduceMotion] ?: false,
            reminderEnabled = p[Keys.reminderEnabled] ?: false,
            reminderMinutes = p[Keys.reminderMinutes] ?: (8 * 60),
            reminderPromptDone = p[Keys.reminderPromptDone] ?: false,
            welcomeDone = p[Keys.welcomeDone] ?: false,
            rulesSeen = p[Keys.rulesSeen]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            adsRemoved = p[Keys.adsRemoved] ?: false,
            reviewAsked = p[Keys.reviewAsked] ?: false,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setHaptics(on: Boolean) = context.dataStore.edit { it[Keys.haptics] = on }
    suspend fun setShowTimer(on: Boolean) = context.dataStore.edit { it[Keys.showTimer] = on }
    suspend fun setHighlightConflicts(on: Boolean) = context.dataStore.edit { it[Keys.highlightConflicts] = on }
    suspend fun setPatternAssist(on: Boolean) = context.dataStore.edit { it[Keys.patternAssist] = on }
    suspend fun setReduceMotion(on: Boolean) = context.dataStore.edit { it[Keys.reduceMotion] = on }
    suspend fun setReminder(enabled: Boolean, minutes: Int) = context.dataStore.edit {
        it[Keys.reminderEnabled] = enabled
        it[Keys.reminderMinutes] = minutes.coerceIn(0, 24 * 60 - 1)
        it[Keys.reminderPromptDone] = true
    }
    suspend fun setReminderPromptDone() = context.dataStore.edit { it[Keys.reminderPromptDone] = true }
    suspend fun setWelcomeDone() = context.dataStore.edit { it[Keys.welcomeDone] = true }
    suspend fun markRulesSeen(game: GameType) = context.dataStore.edit {
        val seen = it[Keys.rulesSeen]?.split(',')?.filter { s -> s.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        seen.add(game.key)
        it[Keys.rulesSeen] = seen.joinToString(",")
    }
    suspend fun setAdsRemoved(removed: Boolean) = context.dataStore.edit { it[Keys.adsRemoved] = removed }
    suspend fun setReviewAsked() = context.dataStore.edit { it[Keys.reviewAsked] = true }

    // --- ad and hint bookkeeping ---

    suspend fun counters(today: Long): AdCounters {
        val p = context.dataStore.data.first()
        val first = p[Keys.firstOpenDay] ?: today.also { d -> context.dataStore.edit { it[Keys.firstOpenDay] = d } }
        return AdCounters(
            firstOpenEpochDay = first,
            solvesSinceInterstitial = p[Keys.solvesSinceInterstitial] ?: 0,
            lastInterstitialAt = p[Keys.lastInterstitialAt] ?: 0L,
            freeHintDay = p[Keys.freeHintDay] ?: -1L,
            freeHintsUsed = p[Keys.freeHintsUsed] ?: 0,
        )
    }

    suspend fun recordSolve() = context.dataStore.edit {
        it[Keys.solvesSinceInterstitial] = (it[Keys.solvesSinceInterstitial] ?: 0) + 1
    }

    suspend fun recordInterstitial(now: Long) = context.dataStore.edit {
        it[Keys.solvesSinceInterstitial] = 0
        it[Keys.lastInterstitialAt] = now
    }

    /** Uses today's free hint if one is left. Returns true when a free hint was granted. */
    suspend fun tryUseFreeHint(today: Long, perDay: Int): Boolean {
        var granted = false
        context.dataStore.edit {
            val day = it[Keys.freeHintDay] ?: -1L
            val used = if (day == today) it[Keys.freeHintsUsed] ?: 0 else 0
            if (used < perDay) {
                it[Keys.freeHintDay] = today
                it[Keys.freeHintsUsed] = used + 1
                granted = true
            }
        }
        return granted
    }

    suspend fun freeHintsLeft(today: Long, perDay: Int): Int {
        val p = context.dataStore.data.first()
        val day = p[Keys.freeHintDay] ?: -1L
        val used = if (day == today) p[Keys.freeHintsUsed] ?: 0 else 0
        return (perDay - used).coerceAtLeast(0)
    }
}

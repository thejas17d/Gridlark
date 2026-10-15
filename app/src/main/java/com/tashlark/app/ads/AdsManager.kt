package com.tashlark.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.tashlark.app.BuildConfig
import com.tashlark.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns consent, SDK start-up and the full-screen ad formats.
 *
 * Rules, from the research brief (card players' top complaint is ad spam):
 *  - Never an ad on the card table, during a deal, or on app open/resume/back.
 *  - Interstitial only when the player taps on from a result: after a match ([Break.MATCH]),
 *    or at a round break ([Break.ROUND]) if [ROUND_GAP_MS] has passed. Never on the install
 *    day, never in the first [FREE_MATCHES] matches, never while an ad-free hour is active.
 *  - Rewarded ads are opt-in only and buy an hour without ads.
 */
class AdsManager(
    private val appContext: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val started = AtomicBoolean(false)
    private val consentRequested = AtomicBoolean(false)

    private val _canShowAds = MutableStateFlow(false)
    /** True once consent allows ad requests and the SDK has started. */
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    @Volatile private var adsRemoved = false
    @Volatile private var adFreeUntil = 0L
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
        set(value) { field = value; _rewardedReady.value = value != null && _canShowAds.value }
    private val _rewardedReady = MutableStateFlow(false)
    /** Observable: true while a rewarded ad is loaded and may be offered. */
    val rewardedReadyFlow: StateFlow<Boolean> = _rewardedReady.asStateFlow()
    private var rewardedLoading = false

    init {
        scope.launch {
            settings.settings.collect { s ->
                adsRemoved = s.adsRemoved
                adFreeUntil = s.adFreeUntil
                if (s.adsRemoved) {
                    interstitial = null
                    rewarded = null
                }
            }
        }
    }

    /** Call from the activity on every start. Shows the consent form only where the law requires it. */
    fun gatherConsent(activity: Activity) {
        // A previous session may already allow requests; don't wait for the network.
        startIfAllowed()
        // Once per process: returning from an ad or another app must not re-run the network check.
        if (!consentRequested.compareAndSet(false, true)) return
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (activity.isFinishing || activity.isDestroyed) {
                    // The activity went away meanwhile; try again on the next start.
                    consentRequested.set(false)
                    startIfAllowed()
                    return@requestConsentInfoUpdate
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    _privacyOptionsRequired.value =
                        consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    startIfAllowed()
                }
            },
            { error ->
                // Offline or misconfigured: fall back to whatever consent was stored previously.
                Log.w(TAG, "Consent update failed: ${error.message}")
                consentRequested.set(false)
                startIfAllowed()
            },
        )
    }

    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { onDone() }
    }

    private fun startIfAllowed() {
        if (!consent.canRequestAds()) return
        if (!started.compareAndSet(false, true)) {
            _canShowAds.value = true
            return
        }
        // A 3+ card game: keep ads to the general-audience (PG) rating.
        val config = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
            .build()
        MobileAds.setRequestConfiguration(config)
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) {
                scope.launch {
                    // Ads never blast sound over the game.
                    MobileAds.setAppMuted(true)
                    _canShowAds.value = true
                    preload()
                }
            }
        }
    }

    fun preload() {
        // Players who removed ads never need any ad loaded.
        if (!_canShowAds.value || adsRemoved) return
        loadInterstitial()
        loadRewarded()
    }

    private fun loadInterstitial() {
        if (interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(appContext, BuildConfig.AD_INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; interstitialLoading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null; interstitialLoading = false }
            })
    }

    private fun loadRewarded() {
        if (rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(appContext, BuildConfig.AD_REWARDED, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedLoading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { rewarded = null; rewardedLoading = false }
            })
    }

    enum class Break { ROUND, MATCH }

    /**
     * Called when the player taps on from a round or match result. Shows an interstitial if
     * the rules allow, then runs [then] (always exactly once).
     */
    fun atBreak(activity: Activity, kind: Break, then: () -> Unit) {
        scope.launch {
            val ad = interstitial
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            val c = settings.counters(today)
            val gap = if (kind == Break.MATCH) MATCH_GAP_MS else ROUND_GAP_MS
            val eligible = !adsRemoved && now >= adFreeUntil && _canShowAds.value && ad != null &&
                c.firstOpenEpochDay != today &&
                c.matchesFinished >= FREE_MATCHES &&
                now - c.lastInterstitialAt >= gap
            if (!eligible || ad == null || activity.isFinishing || activity.isDestroyed) {
                then()
                if (interstitial == null) preload()
                return@launch
            }
            interstitial = null
            val finished = AtomicBoolean(false)
            fun finish() { if (finished.compareAndSet(false, true)) { then(); preload() } }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() = finish()
                override fun onAdFailedToShowFullScreenContent(error: AdError) = finish()
            }
            settings.recordInterstitial(now)
            ad.show(activity)
        }
    }

    /** Whether a rewarded ad is ready right now. */
    fun rewardedReady(): Boolean = _canShowAds.value && rewarded != null

    /**
     * Shows a rewarded ad the player explicitly asked for. [onReward] runs only if they
     * earned it; the reward (an ad-free hour) means nothing offline, so no ad means no reward.
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onSkipped: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            onSkipped()
            preload()
            return
        }
        rewarded = null
        var earned = false
        val finished = AtomicBoolean(false)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (finished.compareAndSet(false, true)) { if (earned) onReward() else onSkipped(); preload() }
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                if (finished.compareAndSet(false, true)) { onSkipped(); preload() }
            }
        }
        ad.show(activity) { earned = true }
    }

    companion object {
        private const val TAG = "AdsManager"
        const val FREE_MATCHES = 2
        const val MATCH_GAP_MS = 3 * 60 * 1000L
        const val ROUND_GAP_MS = 8 * 60 * 1000L
        const val AD_FREE_HOUR_MS = 60 * 60 * 1000L
    }
}

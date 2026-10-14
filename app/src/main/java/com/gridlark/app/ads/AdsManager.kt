package com.gridlark.app.ads

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
import com.gridlark.app.BuildConfig
import com.gridlark.app.data.SettingsRepository
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
 * Rules, from the research brief (players hate ad-heavy puzzle apps):
 *  - Never an ad on the puzzle board, never before the result is shown.
 *  - Interstitial only after the player taps Next/Done on a result, at most once per
 *    [SOLVES_PER_INTERSTITIAL] solves and [MIN_GAP_MS] apart, never on the install day.
 *  - Rewarded ads are opt-in (extra hints). If none is ready (e.g. offline) the hint is free.
 */
class AdsManager(
    private val appContext: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val started = AtomicBoolean(false)

    private val _canShowAds = MutableStateFlow(false)
    /** True once consent allows ad requests and the SDK has started. */
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    @Volatile private var adsRemoved = false
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    init {
        scope.launch {
            settings.settings.collect { s ->
                adsRemoved = s.adsRemoved
                if (s.adsRemoved) {
                    interstitial = null
                }
            }
        }
    }

    /** Call from the activity on every start. Shows the consent form only where the law requires it. */
    fun gatherConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
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
                startIfAllowed()
            },
        )
        // A previous session may already allow requests; don't wait for the network.
        startIfAllowed()
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
        // Puzzles are for a general audience (13+); ads are rated for teens and up.
        val config = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
            .build()
        MobileAds.setRequestConfiguration(config)
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) {
                scope.launch {
                    _canShowAds.value = true
                    preload()
                }
            }
        }
    }

    fun preload() {
        if (!_canShowAds.value) return
        if (!adsRemoved) loadInterstitial()
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

    /**
     * Called when the player leaves a result screen. Shows an interstitial if the frequency
     * rules allow, then runs [then] (always exactly once).
     */
    fun afterResult(activity: Activity, then: () -> Unit) {
        scope.launch {
            val ad = interstitial
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            val c = settings.counters(today)
            val eligible = !adsRemoved && _canShowAds.value && ad != null &&
                c.firstOpenEpochDay != today &&
                c.solvesSinceInterstitial >= SOLVES_PER_INTERSTITIAL &&
                now - c.lastInterstitialAt >= MIN_GAP_MS
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
     * Shows a rewarded ad. [onReward] runs if the player earned the reward, or straight away
     * if no ad could be shown (offline players are never punished).
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onSkipped: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            onReward()
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
                if (finished.compareAndSet(false, true)) { onReward(); preload() }
            }
        }
        ad.show(activity) { earned = true }
    }

    companion object {
        private const val TAG = "AdsManager"
        const val SOLVES_PER_INTERSTITIAL = 2
        const val MIN_GAP_MS = 4 * 60 * 1000L
        const val FREE_HINTS_PER_DAY = 1
    }
}

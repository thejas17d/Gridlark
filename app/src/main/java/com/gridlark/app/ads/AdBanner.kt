package com.gridlark.app.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.gridlark.app.BuildConfig

/**
 * Anchored adaptive banner. Its height is reserved up front so content never jumps
 * when the ad arrives (or doesn't, offline). Hidden on screens narrower than a banner.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        if (widthDp < 320) return@BoxWithConstraints
        val context = LocalContext.current
        key(widthDp) {
            val size = remember { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
            val heightDp = with(LocalDensity.current) { size.getHeightInPixels(context).toDp() }
            val adView = remember {
                AdView(context).apply {
                    setAdSize(size)
                    adUnitId = BuildConfig.AD_BANNER
                    loadAd(AdRequest.Builder().build())
                }
            }
            LifecycleResumeEffect(adView) {
                adView.resume()
                onPauseOrDispose { adView.pause() }
            }
            DisposableEffect(adView) { onDispose { adView.destroy() } }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heightDp.coerceAtLeast(50.dp))
                    .semantics { contentDescription = "Advertisement" },
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(factory = { adView })
            }
        }
    }
}

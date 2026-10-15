package com.tashlark.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tashlark.app.AppContainer
import com.tashlark.app.ads.AdBanner
import com.tashlark.app.data.UserSettings
import com.tashlark.app.ui.components.Hairline
import com.tashlark.app.ui.home.HomeScreen
import com.tashlark.app.ui.settings.DocumentScreen
import com.tashlark.app.ui.settings.SettingsScreen
import com.tashlark.app.ui.stats.StatsScreen
import com.tashlark.app.ui.table.TableScreen
import com.tashlark.app.ui.table.TableViewModel
import com.tashlark.app.ui.theme.Tash
import kotlinx.serialization.Serializable

@Serializable data object WelcomeRoute
@Serializable data object HomeRoute
@Serializable data object SettingsRoute
@Serializable data object StatsRoute
@Serializable data object PrivacyRoute
@Serializable data object LicensesRoute
@Serializable data class TableRoute(val gameId: String, val resume: Boolean, val nonce: Long)

/** Banner below the content on calm screens only (never on the card table). */
@Composable
private fun WithBanner(container: AppContainer, content: @Composable (Modifier) -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings(adsRemoved = true))
    val canShowAds by container.ads.canShowAds.collectAsStateWithLifecycle()
    val adFree = settings.adsRemoved || System.currentTimeMillis() < settings.adFreeUntil
    Column(Modifier.fillMaxSize().background(Tash.colors.table)) {
        content(Modifier.weight(1f).statusBarsPadding())
        if (canShowAds && !adFree) {
            Hairline()
            AdBanner(Modifier.padding(top = 4.dp, bottom = 4.dp))
        }
        Column(Modifier.navigationBarsPadding()) {}
    }
}

@Composable
fun TashlarkNav(container: AppContainer, startWithWelcome: Boolean, reduceMotion: Boolean) {
    val nav = rememberNavController()
    val d = if (reduceMotion) 120 else 250
    NavHost(
        navController = nav,
        startDestination = if (startWithWelcome) WelcomeRoute else HomeRoute,
        modifier = Modifier.fillMaxSize().background(Tash.colors.table),
        enterTransition = { fadeIn(tween(d)) },
        exitTransition = { fadeOut(tween(d / 2)) },
        popEnterTransition = { fadeIn(tween(d)) },
        popExitTransition = { fadeOut(tween(d / 2)) },
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(container, dropUnlessResumed {
                nav.navigate(HomeRoute) { popUpTo(WelcomeRoute) { inclusive = true } }
            })
        }
        composable<HomeRoute> { entry ->
            WithBanner(container) { m ->
                HomeScreen(
                    container = container,
                    onPlay = { id, resume ->
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            nav.navigate(TableRoute(id, resume, System.nanoTime()))
                        }
                    },
                    onSettings = { if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.navigate(SettingsRoute) },
                    onStats = { if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.navigate(StatsRoute) },
                    modifier = m,
                )
            }
        }
        composable<TableRoute> { entry ->
            val r = entry.toRoute<TableRoute>()
            val vm: TableViewModel = viewModel(
                key = "${r.gameId}-${r.nonce}",
                factory = viewModelFactory { initializer { TableViewModel(container, r.gameId, r.resume, createSavedStateHandle()) } },
            )
            TableScreen(
                container = container,
                vm = vm,
                // Leaving after a result can follow an ad, when this entry isn't RESUMED yet,
                // so these are guarded inside the screen (once only) rather than here.
                onBack = { nav.popBackStack(HomeRoute, inclusive = false) },
                onPlayAgain = {
                    nav.navigate(TableRoute(r.gameId, false, System.nanoTime())) {
                        popUpTo(HomeRoute) { inclusive = false }
                    }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                container = container,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenPrivacy = dropUnlessResumed { nav.navigate(PrivacyRoute) },
                onOpenLicenses = dropUnlessResumed { nav.navigate(LicensesRoute) },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
            )
        }
        composable<StatsRoute> {
            WithBanner(container) { m ->
                Column(m) {
                    com.tashlark.app.ui.components.BareIconButton(
                        com.tashlark.app.ui.components.GridIcons.Back, "Back", dropUnlessResumed { nav.popBackStack() },
                    )
                    StatsScreen(container, Modifier.weight(1f))
                }
            }
        }
        composable<PrivacyRoute> {
            DocumentScreen("Privacy policy", "privacy.txt", dropUnlessResumed { nav.popBackStack() }, Modifier.statusBarsPadding().navigationBarsPadding())
        }
        composable<LicensesRoute> {
            DocumentScreen("Open-source licences", "licenses.txt", dropUnlessResumed { nav.popBackStack() }, Modifier.statusBarsPadding().navigationBarsPadding())
        }
    }
}

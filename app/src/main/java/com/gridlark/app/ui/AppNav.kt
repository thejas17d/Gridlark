package com.gridlark.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gridlark.app.AppContainer
import com.gridlark.app.ads.AdBanner
import com.gridlark.app.data.Daily
import com.gridlark.app.data.GameType
import com.gridlark.app.data.PlayMode
import com.gridlark.app.data.UserSettings
import com.gridlark.app.ui.archive.ArchiveScreen
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.home.HomeScreen
import com.gridlark.app.ui.puzzle.NextTarget
import com.gridlark.app.ui.puzzle.PuzzleArgs
import com.gridlark.app.ui.puzzle.PuzzleScreen
import com.gridlark.app.ui.puzzle.PuzzleViewModel
import com.gridlark.app.ui.settings.DocumentScreen
import com.gridlark.app.ui.settings.SettingsScreen
import com.gridlark.app.ui.stats.StatsScreen
import com.gridlark.app.ui.theme.Grid
import com.gridlark.engine.Difficulty
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.random.Random

@Serializable data object WelcomeRoute
@Serializable data object MainRoute
@Serializable data object SettingsRoute
@Serializable data object PrivacyRoute
@Serializable data object LicensesRoute
@Serializable data class PuzzleRoute(
    val game: String,
    val mode: String,
    val epochDay: Long,
    val seed: Long,
    val difficulty: String,
)

private enum class Tab(val label: String) { TODAY("Today"), ARCHIVE("Archive"), STATS("Stats") }

fun dailyRoute(game: GameType, epochDay: Long): PuzzleRoute {
    val date = LocalDate.ofEpochDay(epochDay)
    return PuzzleRoute(game.key, PlayMode.DAILY.name, epochDay, Daily.seedFor(game, epochDay), Daily.difficultyFor(date).name)
}

fun practiceRoute(game: GameType, difficulty: Difficulty, seed: Long?): PuzzleRoute =
    PuzzleRoute(
        game.key, PlayMode.PRACTICE.name, LocalDate.now().toEpochDay(),
        seed ?: Random.nextLong(), difficulty.name,
    )

@Composable
fun GridlarkNav(container: AppContainer, startWithWelcome: Boolean, reduceMotion: Boolean) {
    val nav = rememberNavController()
    val duration = if (reduceMotion) 120 else 300
    NavHost(
        navController = nav,
        startDestination = if (startWithWelcome) WelcomeRoute else MainRoute,
        modifier = Modifier.fillMaxSize().background(Grid.colors.paper),
        enterTransition = {
            if (reduceMotion) fadeIn(tween(duration)) else slideInHorizontally(tween(duration)) { it / 6 } + fadeIn(tween(duration))
        },
        exitTransition = { fadeOut(tween(duration / 2)) },
        popEnterTransition = { fadeIn(tween(duration)) },
        popExitTransition = {
            if (reduceMotion) fadeOut(tween(duration)) else slideOutHorizontally(tween(duration)) { it / 6 } + fadeOut(tween(duration))
        },
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(container) {
                nav.navigate(MainRoute) { popUpTo(WelcomeRoute) { inclusive = true } }
            }
        }
        composable<MainRoute> {
            MainTabs(container, nav)
        }
        composable<PuzzleRoute> { entry ->
            val r = entry.toRoute<PuzzleRoute>()
            val args = PuzzleArgs(
                game = GameType.fromKey(r.game),
                mode = PlayMode.valueOf(r.mode),
                epochDay = r.epochDay,
                seed = r.seed,
                difficulty = Difficulty.valueOf(r.difficulty),
            )
            val vm: PuzzleViewModel = viewModel(
                key = args.id,
                factory = viewModelFactory { initializer { PuzzleViewModel(container, args) } },
            )
            PuzzleScreen(
                container = container,
                vm = vm,
                onBack = { nav.popBackStack() },
                onNext = { target ->
                    val next = when (target) {
                        is NextTarget.Daily -> dailyRoute(target.game, target.epochDay)
                        is NextTarget.Practice -> practiceRoute(target.game, target.difficulty, null)
                        NextTarget.Home -> null
                    }
                    if (next == null) nav.popBackStack()
                    else nav.navigate(next) { popUpTo<PuzzleRoute> { inclusive = true } }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenPrivacy = { nav.navigate(PrivacyRoute) },
                onOpenLicenses = { nav.navigate(LicensesRoute) },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
            )
        }
        composable<PrivacyRoute> {
            DocumentScreen("Privacy policy", "privacy.txt", { nav.popBackStack() }, Modifier.statusBarsPadding().navigationBarsPadding())
        }
        composable<LicensesRoute> {
            DocumentScreen("Open-source licences", "licenses.txt", { nav.popBackStack() }, Modifier.statusBarsPadding().navigationBarsPadding())
        }
    }
}

@Composable
private fun MainTabs(container: AppContainer, nav: NavHostController) {
    val c = Grid.colors
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var today by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    // Roll over to the new day's puzzles if the app was left open past midnight.
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now().toEpochDay()
        onPauseOrDispose { }
    }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings(adsRemoved = true))
    val canShowAds by container.ads.canShowAds.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(c.paper)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "tabs",
            ) { t ->
                when (t) {
                    Tab.TODAY -> HomeScreen(
                        container = container,
                        today = LocalDate.ofEpochDay(today),
                        onOpenDaily = { g, d -> nav.navigate(dailyRoute(g, d)) },
                        onOpenPractice = { g, d, seed -> nav.navigate(practiceRoute(g, d, seed)) },
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                    )
                    Tab.ARCHIVE -> ArchiveScreen(container, onOpen = { g, d -> nav.navigate(dailyRoute(g, d)) })
                    Tab.STATS -> StatsScreen(container)
                }
            }
        }
        if (canShowAds && !settings.adsRemoved) {
            Hairline()
            AdBanner()
        }
        Column(Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding()) {
            Hairline(color = c.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Tab) { tab = t }
                            .semantics { this.selected = selected }
                            .padding(top = 10.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) c.ink else c.inkMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(3.dp)
                                .background(if (selected) c.accent else c.paper),
                        )
                    }
                }
            }
        }
    }
}

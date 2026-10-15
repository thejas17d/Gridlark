package com.gridlark.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gridlark.app.data.UserSettings
import com.gridlark.app.ui.GridlarkNav
import com.gridlark.app.ui.theme.GridlarkTheme
import com.gridlark.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val container get() = (application as GridlarkApplication).container
    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }
        enableEdgeToEdge()

        setContent {
            // Settings come from disk; wait for the first value so the right screen and theme show at once.
            var initial by remember { mutableStateOf<UserSettings?>(null) }
            LaunchedEffect(Unit) {
                initial = container.settings.settings.first()
                ready = true
            }
            val start = initial ?: return@setContent
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = start)
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                // darkScrim is used where the system can't draw dark icons (API 26): keep them visible.
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B))
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GridlarkTheme(settings.theme) {
                GridlarkNav(container, startWithWelcome = !start.welcomeDone, reduceMotion = settings.reduceMotion)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.ads.gatherConsent(this)
        container.billing.connect()
    }
}

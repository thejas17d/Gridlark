package com.tashlark.app

import android.app.Application
import com.tashlark.app.ads.AdsManager
import com.tashlark.app.billing.BillingManager
import com.tashlark.app.data.MatchStore
import com.tashlark.app.data.SettingsRepository
import com.tashlark.app.data.TashDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Manual dependency container: a small app needs no DI framework. */
class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val db: TashDatabase = TashDatabase.build(app)
    val matches = MatchStore(app)
    val ads = AdsManager(app, settings)
    val billing = BillingManager(app, settings)
    /** For work that must finish even if a screen closes (saves, stats). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class TashlarkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Records the install day on first launch (no interstitials that day).
        container.appScope.launch { container.settings.counters(LocalDate.now().toEpochDay()) }
    }
}

package com.gridlark.app

import android.app.Application
import androidx.work.ExistingWorkPolicy
import com.gridlark.app.ads.AdsManager
import com.gridlark.app.billing.BillingManager
import com.gridlark.app.data.GridlarkDatabase
import com.gridlark.app.data.SettingsRepository
import com.gridlark.app.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container: small app, no DI framework needed. */
class AppContainer(app: Application) {
    val db: GridlarkDatabase = GridlarkDatabase.build(app)
    val settings = SettingsRepository(app)
    val ads = AdsManager(app, settings)
    val billing = BillingManager(app, settings)
    /** Survives screens; used for work that must finish even if the user navigates away (saves). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class GridlarkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.ensureChannel(this)
        container.appScope.launch {
            // Records the install day on first launch (no interstitials that day).
            container.settings.counters(java.time.LocalDate.now().toEpochDay())
            val s = container.settings.current()
            Reminders.schedule(this@GridlarkApplication, s.reminderEnabled, s.reminderMinutes, ExistingWorkPolicy.KEEP)
        }
    }
}

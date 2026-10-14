package com.gridlark.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gridlark.app.AppContainer
import com.gridlark.app.BuildConfig
import com.gridlark.app.billing.BillingManager
import com.gridlark.app.data.UserSettings
import com.gridlark.app.reminders.Reminders
import com.gridlark.app.ui.components.BareIconButton
import com.gridlark.app.ui.components.GridIcons
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.components.SectionLabel
import com.gridlark.app.ui.components.SwitchRow
import com.gridlark.app.ui.components.ValueRow
import com.gridlark.app.ui.theme.Grid
import com.gridlark.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Grid.colors
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val repo = container.settings

    val s by repo.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val billingStatus by container.billing.status.collectAsStateWithLifecycle()
    val privacyOptionsRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { container.billing.connect() }

    // --- reminder plumbing ---
    var systemAllowsNotifications by remember { mutableStateOf(notificationsEnabled(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }

    // The player may change notification access in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        systemAllowsNotifications = notificationsEnabled(context)
        if (systemAllowsNotifications && Reminders.hasPermission(context)) permissionDenied = false
    }

    fun applyReminder(enabled: Boolean, minutes: Int) {
        val app = context.applicationContext
        // App scope: the write and the reschedule must finish even if the player leaves the screen.
        container.appScope.launch {
            repo.setReminder(enabled, minutes)
            Reminders.schedule(app, enabled, minutes)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            systemAllowsNotifications = notificationsEnabled(context)
            applyReminder(true, s.reminderMinutes)
        } else {
            permissionDenied = true
            applyReminder(false, s.reminderMinutes)
        }
    }

    if (showTimeDialog) {
        ReminderTimeDialog(
            initialMinutes = s.reminderMinutes,
            is24Hour = is24Hour,
            onDismiss = { showTimeDialog = false },
            onConfirm = { minutes ->
                showTimeDialog = false
                applyReminder(s.reminderEnabled, minutes)
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(GridIcons.Back, "Back", onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Hairline()

        Section("Appearance") {
            Text(
                "Theme",
                style = MaterialTheme.typography.bodyLarge,
                color = c.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
            )
            ThemeSelector(current = s.theme, onSelect = { mode -> scope.launch { repo.setTheme(mode) } })
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Reduce motion",
                subtitle = "Keeps animations to a minimum",
                checked = s.reduceMotion,
                onChange = { on -> scope.launch { repo.setReduceMotion(on) } },
            )
            SwitchRow(
                title = "Pattern assist",
                subtitle = "Adds textures to Crowns regions so colours aren't needed",
                checked = s.patternAssist,
                onChange = { on -> scope.launch { repo.setPatternAssist(on) } },
            )
        }

        Section("Play") {
            SwitchRow(
                title = "Highlight rule breaks",
                subtitle = "Marks cells that clash with a rule as you place them",
                checked = s.highlightConflicts,
                onChange = { on -> scope.launch { repo.setHighlightConflicts(on) } },
            )
            SwitchRow(
                title = "Show timer",
                subtitle = "Times are still kept for your stats when hidden",
                checked = s.showTimer,
                onChange = { on -> scope.launch { repo.setShowTimer(on) } },
            )
            SwitchRow(
                title = "Haptics",
                subtitle = "Light taps when you place and complete",
                checked = s.haptics,
                onChange = { on -> scope.launch { repo.setHaptics(on) } },
            )
        }

        Section("Daily reminder") {
            SwitchRow(
                title = "Remind me",
                subtitle = "One quiet notification when today's puzzles are waiting",
                checked = s.reminderEnabled,
                onChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.hasPermission(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        if (on) permissionDenied = false
                        applyReminder(on, s.reminderMinutes)
                    }
                },
            )
            if (s.reminderEnabled) {
                ValueRow(
                    title = "Time",
                    value = formatMinutes(s.reminderMinutes, is24Hour),
                    onClick = { showTimeDialog = true },
                )
            }
            if (permissionDenied || (s.reminderEnabled && !systemAllowsNotifications)) {
                Text(
                    "Notifications are off for Gridlark in system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkMuted,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                )
                ValueRow(title = "Open notification settings", value = null, onClick = { openNotificationSettings(context) })
            }
        }

        Section("Support Gridlark") {
            val status = billingStatus
            val owned = status is BillingManager.Status.Owned || s.adsRemoved
            when {
                owned -> InfoRow(title = "Ads removed. Thank you.", subtitle = null)
                status is BillingManager.Status.Pending -> InfoRow(
                    title = "Purchase pending",
                    subtitle = "Ads go away as soon as Google Play confirms it",
                )
                status is BillingManager.Status.Ready -> ValueRow(
                    title = "Remove ads",
                    value = status.price,
                    subtitle = "One purchase. Every puzzle stays free either way.",
                    onClick = { activity?.let { container.billing.buy(it) } },
                )
                else -> InfoRow(
                    title = "Remove ads",
                    subtitle = "Available when Google Play is reachable",
                    muted = true,
                )
            }
            if (!owned) {
                ValueRow(title = "Restore purchase", value = null, onClick = { container.billing.restore() })
            }
        }

        Section("Privacy") {
            ValueRow(title = "Privacy policy", value = null, onClick = onOpenPrivacy)
            if (privacyOptionsRequired) {
                ValueRow(
                    title = "Ad privacy choices",
                    value = null,
                    subtitle = "Review how ads may use your data",
                    onClick = { activity?.let { container.ads.showPrivacyOptions(it) {} } },
                )
            }
            ValueRow(title = "Open-source licences", value = null, onClick = onOpenLicenses)
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            Text("Gridlark ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            Text("Made for calm minds.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(
            title,
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp)
                .semantics { heading() },
        )
        content()
        Spacer(Modifier.height(8.dp))
        Hairline()
    }
}

/** A non-interactive row in the same rhythm as [ValueRow]. */
@Composable
private fun InfoRow(title: String, subtitle: String?, muted: Boolean = false) {
    val c = Grid.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (muted) c.inkFaint else c.ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
}

/** System / Light / Dark as one segmented ink control. */
@Composable
private fun ThemeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = Grid.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.5.dp, c.ink, shape)
            .selectableGroup(),
    ) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            val selected = mode == current
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(c.ink),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp)
                    .background(if (selected) c.ink else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    themeLabel(mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) c.paper else c.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialMinutes: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val c = Grid.colors
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = is24Hour,
    )
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = c.ink)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(state.hour * 60 + state.minute) },
                colors = buttonColors,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Set", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = buttonColors,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        },
        title = { Text("Reminder time", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.sunken,
                        clockDialSelectedContentColor = c.paper,
                        clockDialUnselectedContentColor = c.ink,
                        selectorColor = c.ink,
                        containerColor = c.surface,
                        periodSelectorBorderColor = c.rule,
                        periodSelectorSelectedContainerColor = c.accent,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = c.onAccent,
                        periodSelectorUnselectedContentColor = c.inkMuted,
                        timeSelectorSelectedContainerColor = c.accent,
                        timeSelectorUnselectedContainerColor = c.sunken,
                        timeSelectorSelectedContentColor = c.onAccent,
                        timeSelectorUnselectedContentColor = c.ink,
                    ),
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = c.surface,
        titleContentColor = c.ink,
        textContentColor = c.ink,
        tonalElevation = 0.dp,
    )
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun formatMinutes(minutes: Int, is24Hour: Boolean): String {
    val time = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

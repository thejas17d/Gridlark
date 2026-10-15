package com.gridlark.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gridlark.app.GridlarkApplication
import com.gridlark.app.MainActivity
import com.gridlark.app.R
import com.gridlark.app.data.GameType
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * A gentle daily reminder. Uses WorkManager (no exact-alarm permission, survives reboots).
 * Each run schedules the next one, so the time stays close to what the player picked.
 */
object Reminders {
    private const val WORK = "daily_reminder"
    const val CHANNEL = "daily"

    /**
     * @param policy REPLACE when the player changes the setting; KEEP at app start (so a
     * reminder that is about to run is never cancelled); APPEND_OR_REPLACE from the worker itself.
     */
    fun schedule(
        context: Context,
        enabled: Boolean,
        minutesOfDay: Int,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        // Zoned times so daylight-saving changes and travel keep the chosen wall-clock time.
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        var next = ZonedDateTime.of(now.toLocalDate(), time, zone)
        if (!next.isAfter(now)) next = ZonedDateTime.of(now.toLocalDate().plusDays(1), time, zone)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK, policy, request)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = context.getString(R.string.reminder_channel_desc)
            nm.createNotificationChannel(ch)
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GridlarkApplication
        val s = app.container.settings.current()
        if (!s.reminderEnabled) return Result.success()
        try {
            val today = LocalDate.now().toEpochDay()
            val solvedToday = app.container.db.plays().dailies(today, today).count { it.solved }
            if (solvedToday < GameType.entries.size) notify(solvedToday)
        } finally {
            // Queue tomorrow's run behind this one instead of cancelling the running worker.
            Reminders.schedule(applicationContext, s.reminderEnabled, s.reminderMinutes, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    private fun notify(solvedToday: Int) {
        val ctx = applicationContext
        if (!Reminders.hasPermission(ctx)) return
        Reminders.ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (solvedToday == 0) ctx.getString(R.string.reminder_text_fresh)
        else ctx.getString(R.string.reminder_text_partial, GameType.entries.size - solvedToday)
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.reminder_title))
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }
}

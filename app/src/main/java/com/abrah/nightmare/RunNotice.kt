package com.abrah.nightmare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * ⭐⭐ The END of a render, in the shade (upstream issue #79: long renders
 * outlive the screen, and the user had to keep checking the phone).
 *
 * ⚠⚠ NOT a progress readout -- [DownloadNotice] already owns the bar. This
 * speaks only at the end: one dismissible row, and TWO channels -- one for a
 * finished run, one for a failed one -- so the user can give each its own
 * sound in the system's channel settings. That is Android's own ringtone
 * picker, so nothing is shipped in the APK and nothing here to maintain.
 *
 * ⚠⚠ Deliberately silent in two cases, both by design:
 * 1. The run was CANCELLED -- the user pressed the button, they know.
 * 2. The app was in the FOREGROUND when the run ended -- they watched it
 *    finish ([MainActivity] keeps [foreground]; a banner over the very
 *    picture that just finished would only get in the way).
 *
 * ⚠ Gated on [Prefs.notifRun] (Settings), and Android 13+ still needs
 * POST_NOTIFICATIONS: ungranted, `notify` is a silent no-op -- the same
 * promise as [DownloadNotice]. Nothing here is load-bearing; the canvas's own
 * run bar remains the source of truth.
 */
object RunNotice {
    // ⚠⚠ v2 suffix: 1.6.281 created the unsuffixed IDs at IMPORTANCE_DEFAULT on
    // devices that had already run it, and a channel's importance is FIXED at
    // creation -- re-creating the same ID is a no-op. The banner (the user:
    // "like an incoming message") needs HIGH, so the IDs are bumped and the
    // legacy pair deleted in [channels] to keep the settings list clean.
    private const val CHANNEL_OK = "run_done_v2"
    private const val CHANNEL_BAD = "run_failed_v2"
    private const val LEGACY_OK = "run_done"
    private const val LEGACY_BAD = "run_failed"
    private const val ID = 4202

    /** ⭐⚠ True while MainActivity is on screen (set in its onStart/onStop). A run that ends in front of the user needs no shade row. */
    @Volatile
    var foreground: Boolean = false

    /**
     * ⚠⚠ HIGH importance, not DEFAULT/LOW: the whole point is the HEADS-UP
     * banner (the user: "like an incoming message"), and LOW/DEFAULT never
     * banner -- LOW additionally mutes (the lesson [DownloadNotice] paid for
     * in the other direction). Idempotent, and safe on pre-O (no channels).
     * ⚠ A channel's importance is FIXED at creation: changing it here does
     * nothing for a channel that already exists -- a reinstall (or clearing
     * data) re-creates them.
     */
    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_OK, ctx.getString(R.string.notif_run_channel_done), NotificationManager.IMPORTANCE_HIGH)
                )
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_BAD, ctx.getString(R.string.notif_run_channel_failed), NotificationManager.IMPORTANCE_HIGH)
                )
                // ⚠ Housekeeping: drop 1.6.281's DEFAULT-importance pair, if present.
                nm.deleteNotificationChannel(LEGACY_OK)
                nm.deleteNotificationChannel(LEGACY_BAD)
            }
        }
    }

    /**
     * ⭐ The end of a run. [ok] picks the channel, and so the sound the user
     * gave it. [detail] is the one-line "which op / what threw"; null falls
     * back to the generic body.
     */
    fun finished(ctx: Context, ok: Boolean, detail: String? = null) {
        if (!Prefs.notifRun) return
        if (foreground) return
        runCatching {
            channels(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(ctx, if (ok) CHANNEL_OK else CHANNEL_BAD)
                .setContentTitle(ctx.getString(if (ok) R.string.notif_run_done else R.string.notif_run_failed))
                .setContentText(detail ?: ctx.getString(if (ok) R.string.notif_run_done else R.string.notif_run_failed))
                // ⚠ System icons, the house rule (DownloadNotice, BackendKeepAliveService):
                // no app-drawn assets to add, and the shapes already mean done/failed.
                .setSmallIcon(
                    if (ok) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_notify_error
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java)?.notify(ID, n)
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // ⭐ THE PROGRESS BAR (upstream 1.6.300, the user 2026-11-14): a long run
    // shows a downloading-style bar in the shade, updated at most once a
    // second ([HarnessViewModel]'s one-second tick calls [progress]). ⚠ Cost:
    // one `notify()` (a binder call, well under a millisecond) per second at
    // most, against seconds of NPU or GPU work a step. A LOW channel: no
    // sound, no pop-up, no buzz on update.
    // ═════════════════════════════════════════════════════════════════════

    private const val CHANNEL_PROGRESS = "run_progress"
    private const val PROGRESS_ID = 4301

    /** ⚠ Quick renders never flash a bar: only a run past this shows one. */
    const val SHOW_AFTER_MS = 10_000L

    @Volatile private var progressShown = false
    private var lastProgressText: String? = null

    /** ⭐ The shade's line for a run: what runs, its step label and the time so far. Pure, for the tests. */
    internal fun textFor(step: String?, elapsedMs: Long): String =
        listOfNotNull(step, com.abrah.nightmare.canvas.formatMs(elapsedMs)).joinToString(" · ")

    /**
     * Show or update the bar. [fraction] 0..1, or null while nothing is counted (loading, a phase with no total): the
     * bar then moves without a number rather than sitting at zero, which reads as stalled.
     */
    fun progress(ctx: Context, title: String, text: String, fraction: Float?) {
        if (progressShown && text == lastProgressText) return
        val nm = progressManager(ctx) ?: return
        val pct = fraction?.let { (it * 100).toInt().coerceIn(0, 100) }
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setProgress(100, pct ?: 0, pct == null)
            .setContentIntent(open)
            .build()
        if (runCatching { nm.notify(PROGRESS_ID, n) }.isSuccess) { progressShown = true; lastProgressText = text }
    }

    /** ⭐ The run ended (or was cancelled): the bar goes. */
    fun clear(ctx: Context) {
        if (!progressShown) return
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(PROGRESS_ID) }
        progressShown = false
        lastProgressText = null
    }

    private fun progressManager(ctx: Context): NotificationManager? = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, ctx.getString(R.string.run_notice_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
        nm
    }.getOrNull()
}

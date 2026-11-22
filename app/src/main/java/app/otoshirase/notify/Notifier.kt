package app.otoshirase.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.otoshirase.R
import app.otoshirase.data.Artwork
import app.otoshirase.data.Release
import app.otoshirase.data.ReleaseNotice
import app.otoshirase.ui.MainActivity
import app.otoshirase.ui.kindLabel
import app.otoshirase.ui.monthDay
import app.otoshirase.ui.releaseIntent
import java.time.LocalDate

object Notifier {
    private const val CHANNEL = "releases"
    private const val GROUP = "app.otoshirase.RELEASES"
    /** 新譜以外のお知らせ。新譜の通知とは別にオフにできるよう分ける */
    const val CHANNEL_TIPS = "tips"

    fun createChannels(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.notif_channel_desc) }
        val tips = NotificationChannel(CHANNEL_TIPS, ctx.getString(R.string.notif_channel_tips), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.notif_channel_tips_desc) }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(ch, tips))
    }

    suspend fun notifyReleases(ctx: Context, releases: List<Release>) {
        if (releases.isEmpty()) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(ctx)
        releases.take(8).forEach { r ->
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_note)
                // 長いタイトルは途中で切れるので、タイトルは誰の作品かだけにして、何がいつ出るかは本文に書く
                .setContentTitle(r.artistName)
                .setContentText(text(ctx, r))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text(ctx, r)))
                .setLargeIcon(r.artwork?.let { Artwork.load(it) })
                .setContentIntent(openIntent(ctx, r))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            nm.notify(r.id.hashCode(), n)
        }
        if (releases.size > 1) {
            val style = NotificationCompat.InboxStyle()
            releases.forEach { style.addLine("${it.artistName} — ${it.title}") }
            val summary = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_note)
                .setContentTitle(ctx.getString(R.string.notif_summary, releases.size))
                .setStyle(style)
                .setGroup(GROUP).setGroupSummary(true).setAutoCancel(true)
                .setContentIntent(appIntent(ctx))
                .build()
            nm.notify(0, summary)
        }
    }

    private fun text(ctx: Context, r: Release): String {
        val kind = kindLabel(ctx, r.kind)
        val date = r.date?.let { monthDay(it) }.orEmpty()
        return when (ReleaseNotice.of(r.date, LocalDate.now())) {
            ReleaseNotice.Upcoming -> ctx.getString(R.string.notif_text_upcoming, kind, r.title, date)
            ReleaseNotice.Today -> ctx.getString(R.string.notif_text_today, kind, r.title)
            ReleaseNotice.JustReleased -> ctx.getString(R.string.notif_text_released, kind, r.title)
            ReleaseNotice.Found -> ctx.getString(R.string.notif_text_found, kind, r.title, date)
        }
    }

    private fun openIntent(ctx: Context, r: Release): PendingIntent {
        val i = releaseIntent(ctx, r) ?: Intent(ctx, MainActivity::class.java)
        return PendingIntent.getActivity(ctx, r.id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun appIntent(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

}

package app.otoshirase.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.otoshirase.R
import app.otoshirase.data.AppleCatalog
import app.otoshirase.data.Release
import app.otoshirase.ui.MainActivity
import app.otoshirase.ui.dateLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object Notifier {
    private const val CHANNEL = "releases"
    private const val GROUP = "app.otoshirase.RELEASES"

    fun createChannels(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "新譜", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "登録したアーティストの新譜" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
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
                .setContentTitle("${r.artistName}「${r.title}」")
                .setContentText("${r.kind} · ${dateLabel(r)}")
                .setLargeIcon(r.artwork?.let { loadBitmap(it) })
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
                .setContentTitle("新譜 ${releases.size}件")
                .setStyle(style)
                .setGroup(GROUP).setGroupSummary(true).setAutoCancel(true)
                .setContentIntent(appIntent(ctx))
                .build()
            nm.notify(0, summary)
        }
    }

    private fun openIntent(ctx: Context, r: Release): PendingIntent {
        val i = r.url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) } ?: Intent(ctx, MainActivity::class.java)
        return PendingIntent.getActivity(ctx, r.id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun appIntent(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            AppleCatalog.http.newCall(Request.Builder().url(url.replace("600x600bb", "300x300bb")).build())
                .execute().use { BitmapFactory.decodeStream(it.body!!.byteStream()) }
        }.getOrNull()
    }
}

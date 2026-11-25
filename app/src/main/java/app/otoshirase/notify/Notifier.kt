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
import app.otoshirase.data.Suggestion
import app.otoshirase.ui.MainActivity
import app.otoshirase.ui.dateLabel
import app.otoshirase.ui.kindLabel

object Notifier {
    private const val CHANNEL = "releases"
    private const val GROUP = "app.otoshirase.RELEASES"
    /** 新譜以外のお知らせ。新譜の通知とは別にオフにできるよう分ける */
    const val CHANNEL_TIPS = "tips"
    /** AI のおすすめ。新譜やお知らせとは別にオフにできるよう分ける */
    const val CHANNEL_SUGGESTIONS = "suggestions"
    // 新譜の通知は collectionId の hashCode と 0（まとめ）を使うので、それと重なりにくい値にする
    private const val SUGGESTIONS_ID = 0x53554747

    fun createChannels(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.notif_channel_desc) }
        val tips = NotificationChannel(CHANNEL_TIPS, ctx.getString(R.string.notif_channel_tips), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.notif_channel_tips_desc) }
        val sg = NotificationChannel(CHANNEL_SUGGESTIONS, ctx.getString(R.string.notif_channel_suggestions), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.notif_channel_suggestions_desc) }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(ch, tips, sg))
    }

    /** おすすめの通知が届く状態か。アプリ全体の許可に加え、チャンネルだけオフにされていることもある */
    fun suggestionsAllowed(ctx: Context): Boolean {
        val nm = NotificationManagerCompat.from(ctx)
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            nm.areNotificationsEnabled() &&
            nm.getNotificationChannel(CHANNEL_SUGGESTIONS)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** 1 回の取り直しで 1 件にまとめる。名前だけを出し、理由は書かない */
    fun notifySuggestions(ctx: Context, fresh: List<Suggestion>) {
        if (fresh.isEmpty() || !suggestionsAllowed(ctx)) return
        // 判定から通知までの間に権限を外されることがあるので、出す直前にも確かめる
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val names = fresh.map { it.name }
        val title = if (names.size == 1) ctx.getString(R.string.notif_suggest_one, names[0])
        else ctx.resources.getQuantityString(R.plurals.notif_suggest_many, names.size, names.size)
        val text = if (names.size == 1) ctx.getString(R.string.notif_suggest_tap)
        else names.joinToString(ctx.getString(R.string.list_separator))
        val big = if (names.size == 1) text else names.joinToString("\n") + "\n\n" + ctx.getString(R.string.notif_suggest_tap)
        val open = PendingIntent.getActivity(
            ctx, SUGGESTIONS_ID, MainActivity.suggestionsIntent(ctx),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_SUGGESTIONS)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // 前の通知が残っていれば置き換える。たまっていくと煩わしいため
        NotificationManagerCompat.from(ctx).notify(SUGGESTIONS_ID, n)
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
                .setContentTitle(ctx.getString(if (r.upcoming) R.string.notif_title_upcoming else R.string.notif_title_released, r.artistName))
                .setContentText(ctx.getString(R.string.notif_text, r.title, kindLabel(ctx, r.kind), dateLabel(ctx, r)))
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

    private fun openIntent(ctx: Context, r: Release): PendingIntent {
        val i = Intent(ctx, OpenReleaseActivity::class.java).putExtra(OpenReleaseActivity.EXTRA_ID, r.id)
        return PendingIntent.getActivity(ctx, r.id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun appIntent(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

}

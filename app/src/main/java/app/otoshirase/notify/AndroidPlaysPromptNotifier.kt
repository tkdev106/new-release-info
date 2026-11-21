package app.otoshirase.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.otoshirase.R
import app.otoshirase.data.PlaysPromptNotifier
import app.otoshirase.ui.MainActivity

class AndroidPlaysPromptNotifier(private val ctx: Context) : PlaysPromptNotifier {

    override fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    override fun showPlaysPrompt(artistName: String) {
        // 判定から通知までの間に権限を外されることがあるので、出す直前にも確かめる
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val text = ctx.getString(R.string.plays_prompt_text)
        // 登録は Most Played の画面から行うので、そこを開く
        val open = PendingIntent.getActivity(
            ctx, REQUEST, MainActivity.playsIntent(ctx),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, Notifier.CHANNEL_TIPS)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(ctx.getString(R.string.plays_prompt_title, artistName))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }

    private companion object {
        // 新譜の通知は collectionId の hashCode と 0（まとめ）を使うので、それと重なりにくい値にする
        const val NOTIFICATION_ID = 0x504c4159
        const val REQUEST = 0x504c4159
    }
}

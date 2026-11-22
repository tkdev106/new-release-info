package app.otoshirase.ui

import android.content.Context
import android.text.format.DateFormat
import app.otoshirase.R
import app.otoshirase.data.Release
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 端末の言語に合わせた月日（ja: 11月10日 / en: Nov 10）。年が違えば年も付ける */
private fun monthDay(d: LocalDate): String {
    val locale = Locale.getDefault()
    val skeleton = if (d.year == LocalDate.now().year) "MMMd" else "yMMMd"
    return d.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

/** 通知・ウィジェット用。発売前か後かも文言に含める */
fun dateLabel(ctx: Context, r: Release): String {
    val d = r.date ?: return ctx.getString(R.string.date_unknown)
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        days > 0 -> ctx.getString(R.string.date_upcoming, monthDay(d))
        days == 0L -> ctx.getString(R.string.date_today)
        else -> ctx.getString(R.string.date_released, monthDay(d))
    }
}

/** アプリ内の一覧用。発売予定かどうかは見出しで分かるので、日付だけにする */
fun shortDate(ctx: Context, r: Release): String {
    val d = r.date ?: return ctx.getString(R.string.date_unknown)
    return if (d == LocalDate.now()) ctx.getString(R.string.today) else monthDay(d)
}

/** 保存データの種別（日本語で持っている）を表示言語に直す */
fun kindLabel(ctx: Context, kind: String): String = when (kind) {
    "シングル" -> ctx.getString(R.string.kind_single)
    "EP" -> ctx.getString(R.string.kind_ep)
    "アルバム" -> ctx.getString(R.string.kind_album)
    else -> kind
}

fun checkedLabel(ctx: Context, t: Long): String =
    if (t == 0L) ctx.getString(R.string.checked_never) else ctx.getString(R.string.checked_ago, agoLabel(ctx, t))

fun agoLabel(ctx: Context, t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> ctx.getString(R.string.ago_now)
        m < 60 -> ctx.getString(R.string.ago_min, m)
        m < 60 * 24 -> ctx.getString(R.string.ago_hour, m / 60)
        else -> ctx.getString(R.string.ago_day, m / 60 / 24)
    }
}

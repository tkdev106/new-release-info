package app.otoshirase.ui

import app.otoshirase.data.Release
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun dateLabel(r: Release): String {
    val d = r.date ?: return "日付不明"
    val today = LocalDate.now()
    val md = if (d.year == today.year) "${d.monthValue}/${d.dayOfMonth}" else "${d.year}/${d.monthValue}/${d.dayOfMonth}"
    val days = ChronoUnit.DAYS.between(today, d)
    return when {
        days > 0 -> "$md 発売予定"
        days == 0L -> "今日リリース"
        else -> "$md リリース"
    }
}

/** 一覧用の短い日付。発売予定かどうかは見出しで分かるので、日付だけにする */
fun shortDate(r: Release): String {
    val d = r.date ?: return "日付不明"
    val today = LocalDate.now()
    return when {
        d == today -> "今日"
        d.year == today.year -> "${d.monthValue}.${d.dayOfMonth}"
        else -> "${d.year}.${d.monthValue}.${d.dayOfMonth}"
    }
}

fun checkedLabel(t: Long): String = if (t == 0L) "未確認" else "最終確認 ${agoLabel(t)}"

fun agoLabel(t: Long): String {
    if (t == 0L) return "未チェック"
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "たった今"
        m < 60 -> "${m}分前"
        m < 60 * 24 -> "${m / 60}時間前"
        else -> "${m / 60 / 24}日前"
    }
}

package app.otoshirase.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 新譜の通知で、作品が発売のどの段階にあるか。段階ごとに文言を変える */
enum class ReleaseNotice {
    Upcoming, Today, JustReleased, Found;

    companion object {
        /**
         * 発売から日が経った作品を「リリースされました」と知らせると、いま出たように見えて紛らわしい。
         * この日数を過ぎたものは「見つかりました」と発売日付きで知らせる
         */
        const val JUST_RELEASED_DAYS = 7L

        fun of(date: LocalDate?, today: LocalDate): ReleaseNotice {
            // 日付が分からない作品は、見つけた時点で出たばかりとみなす
            date ?: return JustReleased
            val days = ChronoUnit.DAYS.between(date, today)
            return when {
                days < 0 -> Upcoming
                days == 0L -> Today
                days <= JUST_RELEASED_DAYS -> JustReleased
                else -> Found
            }
        }
    }
}

package app.otoshirase.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 発売予定として見つけた作品が、発売日を迎えたかの判定 */
object ReleaseDay {
    /**
     * チェックが数日空いても発売日の通知は出したいが、古い作品をまとめて通知しないための猶予。
     * 端末を長く使っていなかったあとに、何週間も前の発売をいまさら知らせても意味がない
     */
    private const val GRACE_DAYS = 3L

    /** @return 発売日を迎えたことを通知すべき作品 */
    fun due(releases: List<Release>, today: LocalDate, zone: ZoneId): List<Release> = releases.filter { r ->
        val date = r.date ?: return@filter false
        val foundOn = Instant.ofEpochMilli(r.foundAt).atZone(zone).toLocalDate()
        !r.releaseDayNotified &&
            foundOn.isBefore(date) && // 見つけた時点で発売済みだったものは、見つけたときに通知している
            !date.isAfter(today) &&
            !date.isBefore(today.minusDays(GRACE_DAYS))
    }
}

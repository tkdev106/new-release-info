package app.otoshirase.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReleaseNoticeTest {

    private val today = LocalDate.of(2026, 11, 22)

    @Test
    fun `発売日より前なら発売予定として知らせる`() {
        assertEquals(ReleaseNotice.Upcoming, ReleaseNotice.of(today.plusDays(1), today))
    }

    @Test
    fun `発売日の当日は本日リリースとして知らせる`() {
        assertEquals(ReleaseNotice.Today, ReleaseNotice.of(today, today))
    }

    @Test
    fun `発売から1週間以内ならリリースされたと知らせる`() {
        assertEquals(ReleaseNotice.JustReleased, ReleaseNotice.of(today.minusDays(7), today))
    }

    @Test
    fun `発売から1週間を過ぎた作品は見つかったと知らせる`() {
        assertEquals(ReleaseNotice.Found, ReleaseNotice.of(today.minusDays(46), today))
    }

    @Test
    fun `発売日が分からない作品は出たばかりとみなす`() {
        assertEquals(ReleaseNotice.JustReleased, ReleaseNotice.of(null, today))
    }
}

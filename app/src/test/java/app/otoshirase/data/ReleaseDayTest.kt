package app.otoshirase.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReleaseDayTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 11, 21)

    private fun release(id: Long, date: String, foundOn: LocalDate, releaseDayNotified: Boolean = false) = Release(
        id = id, artistId = 1, artistName = "羊文学", title = "t$id", kind = "single",
        releaseDate = date, url = null, artwork = null,
        foundAt = foundOn.atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3600_000L,
        releaseDayNotified = releaseDayNotified,
    )

    private fun due(vararg r: Release) = ReleaseDay.due(r.toList(), today, zone).map { it.id }

    @Test
    fun `発売予定で見つけた作品は発売日に通知する`() {
        assertEquals(listOf(1L), due(release(1, "2026-11-21", foundOn = today.minusDays(10))))
    }

    @Test
    fun `発売日の前は通知しない`() {
        assertEquals(emptyList<Long>(), due(release(1, "2026-11-22", foundOn = today.minusDays(10))))
    }

    @Test
    fun `発売日に一度通知したらもう通知しない`() {
        assertEquals(emptyList<Long>(), due(release(1, "2026-11-21", foundOn = today.minusDays(10), releaseDayNotified = true)))
    }

    @Test
    fun `見つけた時点で発売済みだった作品は通知しない`() {
        assertEquals(emptyList<Long>(), due(release(1, "2026-11-21", foundOn = today)))
    }

    @Test
    fun `チェックが空いても数日以内の発売なら通知し、それより古ければ通知しない`() {
        assertEquals(
            listOf(1L),
            due(
                release(1, "2026-11-18", foundOn = today.minusDays(30)),
                release(2, "2026-11-10", foundOn = today.minusDays(30)),
            ),
        )
    }
}

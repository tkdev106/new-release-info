package app.otoshirase.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionScheduleTest {
    private fun sg(id: Long) = Suggestion(id, "A$id")
    private val day = 24L * 60 * 60 * 1000
    private val ready = AppState(artists = listOf(Artist(1, "羊文学")), suggestionFrequency = SuggestionFrequency.Weekly)

    @Test
    fun `これまでに出したことのないおすすめだけを通知の対象にする`() {
        assertEquals(listOf(sg(3)), SuggestionSchedule.fresh(listOf(sg(1), sg(3)), listOf(sg(1), sg(2))))
    }

    @Test
    fun `出したおすすめは新しい順に重ねて覚え上限で切る`() {
        val merged = SuggestionSchedule.mergeHistory(listOf(sg(2), sg(9)), listOf(sg(1), sg(2)))
        assertEquals(listOf(2L, 9L, 1L), merged.map { it.artistId })
        val many = SuggestionSchedule.mergeHistory((1..300L).map(::sg), emptyList())
        assertEquals(SuggestionSchedule.MAX_HISTORY, many.size)
    }

    @Test
    fun `中継に除いてもらう名前は新しいものから50件まで`() {
        val names = SuggestionSchedule.excludeNames((1..80L).map(::sg))
        assertEquals(50, names.size)
        assertEquals("A1", names.first())
    }

    @Test
    fun `頻度がオフ・アーティストなし・通知できないときは裏で問い合わせない`() {
        val now = 100 * day
        assertTrue(SuggestionSchedule.shouldFetchInBackground(ready, canNotify = true, now = now))
        assertFalse(SuggestionSchedule.shouldFetchInBackground(ready.copy(suggestionFrequency = SuggestionFrequency.Off), true, now))
        assertFalse(SuggestionSchedule.shouldFetchInBackground(ready.copy(artists = emptyList()), true, now))
        assertFalse(SuggestionSchedule.shouldFetchInBackground(ready, canNotify = false, now = now))
    }

    @Test
    fun `地域で断られてから7日は裏で問い合わせない`() {
        val blocked = ready.copy(suggestionsRegionBlockedAt = 100 * day)
        assertFalse(SuggestionSchedule.shouldFetchInBackground(blocked, true, 106 * day))
        assertTrue(SuggestionSchedule.shouldFetchInBackground(blocked, true, 107 * day))
    }

    @Test
    fun `通知で知らせたおすすめを先に並べ残りは元の順のまま`() {
        val out = SuggestionSchedule.ordered(listOf(sg(1), sg(2), sg(3), sg(4)), setOf(3L, 4L))
        assertEquals(listOf(3L, 4L, 1L, 2L), out.map { it.artistId })
    }
}

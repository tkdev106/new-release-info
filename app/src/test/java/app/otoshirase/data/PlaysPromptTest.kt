package app.otoshirase.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaysPromptTest {

    private fun play(name: String, lastPlayed: Long) = PlayStat(name, count = 1, lastPlayed = lastPlayed)

    @Test
    fun `2組たまったら直近に再生したアーティストの名前を出す`() {
        val s = AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200)))
        assertEquals("Vaundy", PlaysPrompt.artistToMention(s))
    }

    @Test
    fun `1組だけなら出さない`() {
        assertNull(PlaysPrompt.artistToMention(AppState(plays = listOf(play("羊文学", 100)))))
    }

    @Test
    fun `登録済みと非表示にしたアーティストは数えない`() {
        val s = AppState(
            artists = listOf(Artist(id = 1, name = "羊文学", aliases = listOf("Hitsujibungaku"))),
            plays = listOf(play("Hitsujibungaku", 300), play("Vaundy", 200), play("YOASOBI", 100)),
            ignored = listOf("YOASOBI"),
        )
        assertNull(PlaysPrompt.artistToMention(s))
    }

    @Test
    fun `出したあとは同じ顔ぶれでは出さない`() {
        val s = PlaysPrompt.markPrompted(AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200))))
        assertNull(PlaysPrompt.artistToMention(s))
        assertNull(PlaysPrompt.artistToMention(s.copy(plays = s.plays + play("YOASOBI", 300))))
    }

    @Test
    fun `出したあと新しいアーティストが2組たまったらまた出す`() {
        val s = PlaysPrompt.markPrompted(AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200))))
        val next = s.copy(plays = s.plays + play("YOASOBI", 400) + play("Ado", 300) + play("羊文学", 500).copy(count = 2))
        assertEquals("YOASOBI", PlaysPrompt.artistToMention(next))
    }

    @Test
    fun `通知を出したら出したことを記録する`() = runBlocking {
        val store = FakeStore(AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200))))
        val notifier = FakeNotifier(allowed = true)
        PlaysPrompt.check(store, notifier)
        PlaysPrompt.check(store, notifier)
        assertEquals(listOf("Vaundy"), notifier.shown)
        assertEquals(listOf("羊文学", "vaundy"), store.current().promptedPlays)
    }

    @Test
    fun `通知の権限がなければ出さず、あとで出せるように記録もしない`() = runBlocking {
        val store = FakeStore(AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200))))
        val notifier = FakeNotifier(allowed = false)
        PlaysPrompt.check(store, notifier)
        assertEquals(emptyList<String>(), notifier.shown)
        assertTrue(store.current().promptedPlays.isEmpty())
    }

    private class FakeStore(private var state: AppState) : StateStore {
        override fun current() = state
        override suspend fun update(transform: (AppState) -> AppState) { state = transform(state) }
    }

    private class FakeNotifier(private val allowed: Boolean) : PlaysPromptNotifier {
        val shown = mutableListOf<String>()
        override fun canNotify() = allowed
        override fun showPlaysPrompt(artistName: String) { shown += artistName }
    }
}

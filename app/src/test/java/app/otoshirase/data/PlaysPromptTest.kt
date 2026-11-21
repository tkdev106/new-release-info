package app.otoshirase.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `一度出したら二度と出さない`() {
        val s = AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200)), playsPrompted = true)
        assertNull(PlaysPrompt.artistToMention(s))
    }

    @Test
    fun `通知を出したら出したことを記録する`() = runBlocking {
        val store = FakeStore(AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200))))
        val notifier = FakeNotifier(allowed = true)
        PlaysPrompt.check(store, notifier)
        PlaysPrompt.check(store, notifier)
        assertEquals(listOf("Vaundy"), notifier.shown)
        assertTrue(store.current().playsPrompted)
    }

    @Test
    fun `通知の権限がなければ出さず、あとで出せるように記録もしない`() = runBlocking {
        val store = FakeStore(AppState(plays = listOf(play("羊文学", 100), play("Vaundy", 200))))
        val notifier = FakeNotifier(allowed = false)
        PlaysPrompt.check(store, notifier)
        assertEquals(emptyList<String>(), notifier.shown)
        assertFalse(store.current().playsPrompted)
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

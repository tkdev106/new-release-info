package app.otoshirase.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SuggestionsTest {
    private class FakeSource(private val names: List<String>, private val fail: Boolean = false) : SuggestionSource {
        var sent: List<String>? = null
        override suspend fun suggest(likedArtists: List<String>): List<String> {
            sent = likedArtists
            if (fail) throw IOException("down")
            return names
        }
    }

    private class FakeCatalog(
        private val artists: List<CatalogArtist>,
        private val withReleases: Set<Long> = artists.map { it.id }.toSet(),
        private val broken: Set<String> = emptySet(),
    ) : ArtistCatalog {
        var calls = 0
        override suspend fun searchArtists(term: String): List<CatalogArtist> {
            calls++
            if (term in broken) throw IOException("HTTP 503")
            // iTunes の検索と同じく、部分一致も返す
            return artists.filter { normalizeArtistName(it.name).contains(normalizeArtistName(term)) }
        }
        override suspend fun hasReleases(artistId: Long): Boolean { calls++; return artistId in withReleases }
    }

    private fun artist(id: Long, name: String, aliases: List<String> = emptyList()) = Artist(id, name, aliases = aliases)
    private fun cat(id: Long, name: String) = CatalogArtist(id, name, "J-Pop", null)
    private fun finder(source: SuggestionSource, catalog: ArtistCatalog) = SuggestionFinder(source, catalog, gapMs = 0, pause = {})

    private val liked = listOf(artist(1, "羊文学", aliases = listOf("Hitsujibungaku")))

    @Test
    fun `カタログで名前が完全に一致したアーティストだけをApple上の表記で返す`() = runBlocking {
        val catalog = FakeCatalog(listOf(cat(10, "never young beach"), cat(11, "Kroi")))
        val out = finder(FakeSource(listOf("Never Young Beach", "存在しないバンド")), catalog).find(liked, emptyList(), emptySet())
        assertEquals(listOf(Suggestion(10, "never young beach", "J-Pop", null)), out)
    }

    @Test
    fun `名前が部分一致するだけのアーティストは採用しない`() = runBlocking {
        val catalog = FakeCatalog(listOf(cat(20, "Kroi Band Tribute")))
        assertEquals(emptyList<Suggestion>(), finder(FakeSource(listOf("Kroi")), catalog).find(liked, emptyList(), emptySet()))
    }

    @Test
    fun `登録済みと非表示と興味なしのアーティストを除く`() = runBlocking {
        val catalog = FakeCatalog(listOf(cat(1, "羊文学"), cat(30, "betcover!!"), cat(31, "Kroi"), cat(32, "TENDRE")))
        val source = FakeSource(listOf("hitsujibungaku", "羊文学", "betcover!!", "KROI", "TENDRE"))
        val out = finder(source, catalog).find(liked, ignored = listOf("Kroi"), dismissed = setOf(30L))
        assertEquals(listOf(32L), out.map { it.artistId })
    }

    @Test
    fun `作品が無いアーティストは採用しない`() = runBlocking {
        val catalog = FakeCatalog(listOf(cat(40, "Empty")), withReleases = emptySet())
        assertEquals(emptyList<Suggestion>(), finder(FakeSource(listOf("Empty")), catalog).find(liked, emptyList(), emptySet()))
    }

    @Test
    fun `同じアーティストを二度出さない`() = runBlocking {
        val catalog = FakeCatalog(listOf(cat(50, "TENDRE")))
        val out = finder(FakeSource(listOf("TENDRE", "tendre", "ＴＥＮＤＲＥ")), catalog).find(liked, emptyList(), emptySet())
        assertEquals(listOf(50L), out.map { it.artistId })
    }

    @Test
    fun `カタログの検索に失敗した候補だけを飛ばして続ける`() = runBlocking {
        val catalog = FakeCatalog(listOf(cat(60, "A"), cat(61, "B")), broken = setOf("A"))
        assertEquals(listOf(61L), finder(FakeSource(listOf("A", "B")), catalog).find(liked, emptyList(), emptySet()).map { it.artistId })
    }

    @Test(expected = IOException::class)
    fun `中継が失敗したら例外で知らせて前回の結果を残せるようにする`() {
        runBlocking { finder(FakeSource(emptyList(), fail = true), FakeCatalog(emptyList())).find(liked, emptyList(), emptySet()) }
    }

    @Test
    fun `表示する件数に達したらカタログへの問い合わせをやめる`() = runBlocking {
        val many = (1..20).map { cat(100L + it, "Artist $it") }
        val catalog = FakeCatalog(many)
        val out = finder(FakeSource(many.map { it.name }), catalog).find(liked, emptyList(), emptySet(), limit = 3)
        assertEquals(3, out.size)
        assertEquals(6, catalog.calls)
    }

    @Test
    fun `登録がなければ中継に問い合わせない`() = runBlocking {
        val source = FakeSource(listOf("A"))
        assertEquals(emptyList<Suggestion>(), finder(source, FakeCatalog(emptyList())).find(emptyList(), emptyList(), emptySet()))
        assertEquals(null, source.sent)
    }

    @Test
    fun `中継に送るのは登録名だけで30件まで`() = runBlocking {
        val source = FakeSource(emptyList())
        finder(source, FakeCatalog(emptyList())).find((1..40).map { artist(it.toLong(), "A$it") }, emptyList(), emptySet())
        assertEquals((1..30).map { "A$it" }, source.sent)
    }

    @Test
    fun `7日経つか登録が変わって1日経ったら取り直す`() {
        val day = 24L * 60 * 60 * 1000
        val s = AppState(artists = liked, suggestionsKey = SuggestionPolicy.key(liked), suggestionsAt = 0)
        assertFalse(SuggestionPolicy.needsRefresh(s, 6 * day))
        assertTrue(SuggestionPolicy.needsRefresh(s, 7 * day))
        val changed = s.copy(artists = liked + artist(2, "TENDRE"))
        assertFalse(SuggestionPolicy.needsRefresh(changed, day / 2))
        assertTrue(SuggestionPolicy.needsRefresh(changed, day))
        assertFalse(SuggestionPolicy.needsRefresh(AppState(), 100 * day))
    }
}

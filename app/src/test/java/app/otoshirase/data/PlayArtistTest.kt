package app.otoshirase.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PlayArtistTest {
    private class FakeCatalog(private val artists: List<CatalogArtist>, private val fail: Boolean = false) : ArtistCatalog {
        override suspend fun searchArtists(term: String): List<CatalogArtist> {
            if (fail) throw IOException("HTTP 503")
            return artists.filter { normalizeArtistName(it.name).contains(normalizeArtistName(term)) }
        }
    }

    private fun cat(id: Long, name: String) = CatalogArtist(id, name, "J-Pop", null)

    @Test
    fun `表記ゆれを無視して名前が一致するアーティストを返す`() = runBlocking {
        val r = PlayArtistResolver(FakeCatalog(listOf(cat(1, "suis from Yorushika"), cat(2, "Yorushika")))).resolve(" YORUSHIKA ")
        assertEquals(PlayArtistResolver.Result.Found(cat(2, "Yorushika")), r)
    }

    @Test
    fun `同名が複数いるときは検索結果の先頭を返す`() = runBlocking {
        val r = PlayArtistResolver(FakeCatalog(listOf(cat(5, "Kroi"), cat(6, "KROI")))).resolve("Kroi")
        assertEquals(PlayArtistResolver.Result.Found(cat(5, "Kroi")), r)
    }

    @Test
    fun `部分一致しかないときは見つからなかったことにする`() = runBlocking {
        val r = PlayArtistResolver(FakeCatalog(listOf(cat(1, "suis from Yorushika")))).resolve("suis")
        assertEquals(PlayArtistResolver.Result.NotFound, r)
    }

    @Test
    fun `通信に失敗したら失敗として返す`() = runBlocking {
        assertEquals(PlayArtistResolver.Result.Failed, PlayArtistResolver(FakeCatalog(emptyList(), fail = true)).resolve("Kroi"))
    }
}

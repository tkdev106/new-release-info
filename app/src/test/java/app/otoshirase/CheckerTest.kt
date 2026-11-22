package app.otoshirase

import app.otoshirase.data.AppleCatalog
import app.otoshirase.data.Artist
import app.otoshirase.data.Store
import app.otoshirase.work.Checker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 実際の Apple カタログ API に問い合わせる結合テスト */
class CheckerTest {

    @Test
    fun baselineThenDetectNewRelease() = runBlocking {
        Store.initForTest(File.createTempFile("state", ".json"))

        val hit = AppleCatalog.searchArtists("羊文学").first()
        println("search: ${hit.artistName} ${hit.artistId} ${hit.primaryGenreName}")
        val artist = Artist(id = hit.artistId!!, name = hit.artistName!!, aliases = listOf("羊文学"))
        Store.addArtist(artist)

        // 1回目: 既存作を取り込むだけで、通知対象はゼロ
        val first = Checker.checkArtist(artist)
        assertEquals(0, first.size)
        val s1 = Store.state.value
        println("display name -> ${s1.artists.first().name}, aliases=${s1.artists.first().aliases}")
        println("seen=${s1.seen[artist.id]?.size}, recent in feed=${s1.releases.map { "${it.title}(${it.kind}, ${it.releaseDate})" }}")

        // 最新作を「まだ見ていない」状態に戻して、新着として検知されるか確かめる
        val newest = AppleCatalog.albums(artist.id).maxBy { it.releaseDate ?: "" }
        Store.update { s ->
            s.copy(
                seen = s.seen + (artist.id to s.seen[artist.id]!!.filterNot { it == newest.collectionId }),
                releases = s.releases.filterNot { it.id == newest.collectionId },
                artists = s.artists, // そのまま
            )
        }
        val second = Checker.checkArtist(Store.state.value.artists.first())
        println("newest=${newest.collectionName} ${newest.releaseDate} -> detected=${second.map { it.title }}")
        val date = newest.releaseDate!!.take(10)
        val today = java.time.LocalDate.now()
        // 通知するのは発売前と発売日に見つけたものだけ。発売済みの作品は、一覧に入っても通知しない
        if (date >= today.toString()) assertEquals(listOf(newest.collectionId), second.map { it.id })
        else assertTrue("発売済みの作品はあとから見つけても通知しない", second.isEmpty())
        if (date >= today.minusDays(60).toString()) {
            assertTrue("60 日以内の作品は一覧に入る", Store.state.value.releases.any { it.id == newest.collectionId })
        }

        // 3回目: 変化なし
        assertEquals(0, Checker.checkArtist(Store.state.value.artists.first()).size)
    }

    @Test
    fun playSplitting() = runBlocking {
        Store.initForTest(File.createTempFile("state", ".json"))
        Store.recordPlay("Wednesday feat. MJ Lenderman", "com.apple.android.music")
        Store.recordPlay("Belle & Sebastian", "com.spotify.music")
        Store.recordPlay("wednesday", "com.spotify.music")
        val plays = Store.state.value.plays.associate { it.name to it.count }
        println(plays)
        assertEquals(mapOf("Wednesday" to 2, "MJ Lenderman" to 1, "Belle & Sebastian" to 1), plays)
    }
}

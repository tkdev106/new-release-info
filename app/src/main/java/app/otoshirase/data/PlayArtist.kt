package app.otoshirase.data

import kotlinx.coroutines.CancellationException

/** Most Played の名前から、その場で登録するアーティストを Apple のカタログで探す */
class PlayArtistResolver(private val catalog: ArtistCatalog) {
    sealed interface Result {
        data class Found(val artist: CatalogArtist) : Result
        /** 名前が一致する人がいない。別名義の可能性があるので、検索画面で選んでもらう */
        data object NotFound : Result
        data object Failed : Result
    }

    suspend fun resolve(playName: String): Result {
        val key = normalizeArtistName(playName)
        if (key.isEmpty()) return Result.NotFound
        val hits = try {
            catalog.searchArtists(playName.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.Failed
        }
        // 部分一致は別人を拾うので使わない。同名が複数いるときは検索の並び（人気順）の先頭を採る。
        // 違っていても、その場の「削除」ですぐ戻せる
        return hits.firstOrNull { normalizeArtistName(it.name) == key }?.let { Result.Found(it) } ?: Result.NotFound
    }
}

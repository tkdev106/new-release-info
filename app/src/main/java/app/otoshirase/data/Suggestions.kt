package app.otoshirase.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.Normalizer

/** おすすめの名前を出す側（Gemini の中継）。返す名前は実在の保証がない */
interface SuggestionSource {
    /** 通信や中継の失敗は例外で知らせる。呼び出し側が前回の結果を残せるように */
    suspend fun suggest(likedArtists: List<String>): List<String>
}

/** おすすめが実在するかを確かめる側（Apple のカタログ） */
interface ArtistCatalog {
    suspend fun searchArtists(term: String): List<CatalogArtist>
    suspend fun hasReleases(artistId: Long): Boolean
}

data class CatalogArtist(val id: Long, val name: String, val genre: String?, val url: String?)

/** 表記ゆれ（全角半角・大文字小文字・空白や記号）を無視して比べるための形 */
fun normalizeArtistName(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase().replace(Regex("""[\s\p{P}\p{S}]"""), "")

/**
 * AI の候補を Apple のカタログで確かめ、登録済み・非表示・興味なしを除いたものだけを返す。
 * AI の書いた名前は検索語にしか使わず、表示・保存には Apple 上の ID と表記を使う。
 */
class SuggestionFinder(
    private val source: SuggestionSource,
    private val catalog: ArtistCatalog,
    /** iTunes Search API の目安（20 回/分）を超えないための間隔 */
    private val gapMs: Long = 3_200,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun find(
        liked: List<Artist>,
        ignored: List<String>,
        dismissed: Set<Long>,
        limit: Int = MAX_SHOWN,
    ): List<Suggestion> {
        if (liked.isEmpty()) return emptyList()
        val candidates = source.suggest(liked.map { it.name }.take(MAX_SENT))

        val excludedNames = (liked.flatMap { it.aliases + it.name } + ignored).map(::normalizeArtistName).toSet()
        val excludedIds = liked.map { it.id }.toSet() + dismissed
        val found = mutableListOf<Suggestion>()
        var calls = 0
        suspend fun <T> lookup(block: suspend () -> T): T? {
            if (calls++ > 0) pause(gapMs)
            return try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // その候補だけ諦める
            }
        }

        for (name in candidates) {
            if (found.size >= limit) break
            val key = normalizeArtistName(name)
            if (key.isEmpty() || key in excludedNames) continue
            val hits = lookup { catalog.searchArtists(name) } ?: continue
            // 部分一致や「いちばん上の結果」は別人を拾うので、完全に一致するものだけ
            val match = hits.firstOrNull { normalizeArtistName(it.name) == key } ?: continue
            if (match.id in excludedIds || found.any { it.artistId == match.id }) continue
            if (lookup { catalog.hasReleases(match.id) } != true) continue
            found += Suggestion(match.id, match.name, match.genre, match.url)
        }
        return found
    }

    companion object {
        const val MAX_SENT = 30
        const val MAX_SHOWN = 10
    }
}

/** いつ取り直すか。Gemini とカタログへの問い合わせを減らすため、なるべく前回の結果を使う */
object SuggestionPolicy {
    const val FRESH_MS = 7L * 24 * 60 * 60 * 1000
    /** 登録が変わっても、これより短い間隔では取り直さない（追加のたびに 1 分かかる確認を走らせないため） */
    const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun key(liked: List<Artist>): String = liked.map { it.id }.sorted().joinToString(",")

    fun needsRefresh(state: AppState, now: Long): Boolean {
        if (state.artists.isEmpty()) return false
        val age = now - state.suggestionsAt
        if (age >= FRESH_MS) return true
        return state.suggestionsKey != key(state.artists) && age >= MIN_INTERVAL_MS
    }
}

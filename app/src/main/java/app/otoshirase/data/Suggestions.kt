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
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun find(
        liked: List<Artist>,
        ignored: List<String>,
        dismissed: Set<Long>,
        limit: Int = MAX_SHOWN,
        /** 前に引いた結果。期限内のものは問い合わせずに使う */
        known: Map<String, CatalogLookup> = emptyMap(),
        /** 新しく問い合わせた結果。呼び出し側が保存する */
        onLookup: (String, CatalogLookup) -> Unit = { _, _ -> },
        /** 1 件確かめるたびに、それまでに見つかった分を渡す。全部終わるのを待たずに出すため */
        onFound: (List<Suggestion>) -> Unit = {},
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
            val cached = known[key]?.takeIf { now() - it.checkedAt < LOOKUP_TTL_MS }
            val match = if (cached != null) {
                cached.artistId?.let { CatalogArtist(it, cached.name.orEmpty(), cached.genre, cached.url) }
            } else {
                val hits = lookup { catalog.searchArtists(name) } ?: continue // 失敗は覚えない
                // 部分一致や「いちばん上の結果」は別人を拾うので、完全に一致するものだけ
                hits.firstOrNull { normalizeArtistName(it.name) == key }.also { m ->
                    onLookup(key, CatalogLookup(m?.id, m?.name, m?.genre, m?.url, now()))
                }
            } ?: continue
            if (match.id in excludedIds || found.any { it.artistId == match.id }) continue
            // 作品の有無までは確かめない。検索に出るアーティストはほぼ作品があり、問い合わせが倍になるため
            found += Suggestion(match.id, match.name, match.genre, match.url)
            onFound(found.toList())
        }
        return found
    }

    companion object {
        const val MAX_SENT = 30
        const val MAX_SHOWN = 10
        const val LOOKUP_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** いつ取り直すか。登録が変わったらすぐ、変わらなければ 7 日ごと */
object SuggestionPolicy {
    const val FRESH_MS = 7L * 24 * 60 * 60 * 1000

    fun key(liked: List<Artist>): String = liked.map { it.id }.sorted().joinToString(",")

    fun needsRefresh(state: AppState, now: Long): Boolean {
        if (state.artists.isEmpty()) return false
        if (state.suggestionsKey != key(state.artists)) return true
        return now - state.suggestionsAt >= FRESH_MS
    }
}

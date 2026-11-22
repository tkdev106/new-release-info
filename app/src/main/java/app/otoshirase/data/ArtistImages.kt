package app.otoshirase.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * アーティスト画像を Apple Music の公開ページ（og:image）から取る。
 * iTunes Search API は画像を返さず、Apple Music API は有料の開発者登録が要るため。
 * 公式の API ではないので、取れなければ null を返し、画面は最新作のジャケットで代用する。
 */
object ArtistImages {
    private val ogImage = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""")

    /** ページの <head> にある og:image の URL */
    fun parse(html: String): String? = ogImage.find(html)?.groupValues?.get(1)
        ?.takeIf { it.startsWith("https://") && "mzstatic.com" in it }

    /** og:image は 1200x630 の横長。末尾の寸法指定を差し替えて正方形に切り抜いた画像にする */
    fun square(url: String, px: Int = 300): String = url.replace(Regex("/[^/]+$"), "/${px}x${px}cc.jpg")

    suspend fun fetch(artistId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://music.apple.com/${AppleCatalog.country(java.util.Locale.getDefault()).lowercase()}/artist/$artistId")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
                .build()
            AppleCatalog.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                // ページ全体は 500KB ほどあるが、og:image は先頭の <head> にあるので途中まで読めば足りる
                val head = r.body!!.source().let { src -> src.request(HEAD_BYTES); src.buffer.snapshot().utf8() }
                parse(head)?.let { square(it) }
            }
        }.getOrNull()
    }

    // 検索結果や Most Played は同じアーティストを何度も描くので、アプリを開いている間は結果を覚えておく
    private val byId = ConcurrentHashMap<Long, String>()     // 取れなかったものは空文字
    private val idByName = ConcurrentHashMap<String, Long>() // 見つからなかったものは -1

    /** 覚えている画像。まだ取りに行っていなければ null */
    fun peek(artistId: Long): String? = byId[artistId]?.ifEmpty { null }

    suspend fun cached(artistId: Long): String? {
        byId[artistId]?.let { return it.ifEmpty { null } }
        return fetch(artistId).also { byId[artistId] = it.orEmpty() }
    }

    /** 名前しか分からないアーティスト（再生履歴から集めたもの）の画像。カタログで名前が完全に一致したときだけ使う */
    suspend fun forName(name: String): String? {
        val key = name.trim().lowercase()
        val id = idByName[key] ?: run {
            // 通信に失敗したときは覚えず、次に描くときに取り直す
            val hits = runCatching { AppleCatalog.searchArtists(name.trim()) }.getOrNull() ?: return null
            (hits.firstOrNull { it.artistName?.trim()?.lowercase() == key }?.artistId ?: -1L).also { idByName[key] = it }
        }
        return if (id < 0) null else cached(id)
    }

    private const val HEAD_BYTES = 160_000L
}

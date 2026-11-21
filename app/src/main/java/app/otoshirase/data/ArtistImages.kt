package app.otoshirase.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

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
                .url("https://music.apple.com/jp/artist/$artistId")
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

    private const val HEAD_BYTES = 160_000L
}

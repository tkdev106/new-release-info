package app.otoshirase.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Apple の公開カタログ API (iTunes Search API)。キー不要。
 * 目安のレート制限は 20 リクエスト/分 程度なので、連続呼び出しは間隔を空ける。
 */
object AppleCatalog {
    const val COUNTRY = "JP"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Resp(val resultCount: Int = 0, val results: List<Item> = emptyList())

    @Serializable
    data class Item(
        val wrapperType: String? = null,
        val collectionType: String? = null,
        val artistId: Long? = null,
        val collectionId: Long? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val collectionViewUrl: String? = null,
        val artistLinkUrl: String? = null,
        val artworkUrl100: String? = null,
        val releaseDate: String? = null,
        val trackCount: Int? = null,
        val primaryGenreName: String? = null,
    )

    private suspend fun get(path: String, params: Map<String, String>): Resp = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("country", COUNTRY)
            addQueryParameter("lang", lang(Locale.getDefault()))
        }.build()
        val req = Request.Builder().url(url).header("User-Agent", "Otoshirase/0.1 (Android)").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.decodeFromString(Resp.serializer(), r.body!!.string())
        }
    }

    /**
     * 日本ストアのままでも lang を変えるとジャンル名だけが英語になる（作品名・アーティスト名は変わらないことを確かめた）。
     * 日本語以外の端末で「オルタナティブ」と出ないよう、端末の言語に合わせる
     */
    fun lang(locale: Locale): String = if (locale.language == "ja") "ja_jp" else "en_us"

    suspend fun searchArtists(term: String): List<Item> =
        get("search", mapOf("term" to term, "entity" to "musicArtist", "limit" to "15"))
            .results.filter { it.wrapperType == "artist" && it.artistId != null }

    /** アーティスト自身の情報（ジャンルなど）と、アルバム/EP/シングル一覧（新しい順とは限らない） */
    data class Discography(val artist: Item?, val albums: List<Item>)

    suspend fun discography(artistId: Long): Discography {
        val results = get("lookup", mapOf("id" to artistId.toString(), "entity" to "album", "limit" to "200")).results
        return Discography(
            artist = results.firstOrNull { it.wrapperType == "artist" },
            albums = results.filter { it.wrapperType == "collection" && it.collectionId != null },
        )
    }

    suspend fun albums(artistId: Long): List<Item> = discography(artistId).albums

    /** "2026-09-18T07:00:00Z" → 端末のタイムゾーンでの日付 */
    private fun localDate(s: String): String = runCatching {
        java.time.Instant.parse(s).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }.getOrDefault(s.take(10))

    fun toRelease(artist: Artist, it: Item, notified: Boolean): Release {
        val raw = it.collectionName.orEmpty()
        val (title, kind) = when {
            raw.endsWith(" - Single") -> raw.removeSuffix(" - Single") to "シングル"
            raw.endsWith(" - EP") -> raw.removeSuffix(" - EP") to "EP"
            else -> raw to "アルバム"
        }
        return Release(
            id = it.collectionId!!,
            artistId = artist.id,
            artistName = it.artistName ?: artist.name,
            title = title,
            kind = kind,
            releaseDate = it.releaseDate?.let(::localDate),
            url = it.collectionViewUrl?.substringBefore("?uo="),
            artwork = it.artworkUrl100?.replace("100x100bb", "600x600bb"),
            trackCount = it.trackCount ?: 0,
            notified = notified,
        )
    }
}

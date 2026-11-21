package app.otoshirase.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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
        }.build()
        val req = Request.Builder().url(url).header("User-Agent", "Otoshirase/0.1 (Android)").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.decodeFromString(Resp.serializer(), r.body!!.string())
        }
    }

    suspend fun searchArtists(term: String): List<Item> =
        get("search", mapOf("term" to term, "entity" to "musicArtist", "limit" to "15"))
            .results.filter { it.wrapperType == "artist" && it.artistId != null }

    /** アーティストのアルバム/EP/シングル一覧（新しい順とは限らない） */
    suspend fun albums(artistId: Long): List<Item> =
        get("lookup", mapOf("id" to artistId.toString(), "entity" to "album", "limit" to "200"))
            .results.filter { it.wrapperType == "collection" && it.collectionId != null }

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

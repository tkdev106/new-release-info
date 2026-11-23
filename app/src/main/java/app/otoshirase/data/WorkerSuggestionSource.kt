package app.otoshirase.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** SuggestionSource を中継（server/ の Cloudflare Worker）で実装したもの。API キーは中継側にだけある */
class WorkerSuggestionSource(
    private val endpoint: String,
    private val auth: RequestAuth,
    private val http: OkHttpClient = defaultClient,
) : SuggestionSource {
    @Serializable private data class Body(val artists: List<String>)
    @Serializable private data class Resp(val suggestions: List<String> = emptyList())

    override suspend fun suggest(likedArtists: List<String>): List<String> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(Body.serializer(), Body(likedArtists))
        val req = Request.Builder()
            .url(endpoint.trimEnd('/') + "/v1/suggestions")
            .apply { auth.headers(body).forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            // 形が崩れていたら SerializationException になり、呼び出し側で失敗として扱う
            json.decodeFromString(Resp.serializer(), r.body!!.string()).suggestions
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val defaultClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

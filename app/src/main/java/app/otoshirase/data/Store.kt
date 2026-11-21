package app.otoshirase.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** 端末内の JSON ファイル1つに全部保存するシンプルなストア */
object Store {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    private lateinit var file: File

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.applicationContext.filesDir, "state.json")
        _state.value = runCatching { json.decodeFromString<AppState>(file.readText()) }
            .getOrDefault(AppState())
    }

    /** テスト用: Context なしでファイルを指定して初期化 */
    internal fun initForTest(f: File) {
        file = f
        _state.value = AppState()
    }

    suspend fun update(transform: (AppState) -> AppState) = mutex.withLock {
        val next = transform(_state.value)
        _state.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(AppState.serializer(), next))
            tmp.renameTo(file)
        }
    }

    suspend fun addArtist(a: Artist) = update { s ->
        if (s.artists.any { it.id == a.id }) s
        else s.copy(artists = s.artists + a, ignored = s.ignored.filterNot { a.matches(it) })
    }

    suspend fun removeArtist(id: Long) = update { s ->
        s.copy(
            artists = s.artists.filterNot { it.id == id },
            seen = s.seen - id,
            releases = s.releases.filterNot { it.artistId == id },
        )
    }

    suspend fun ignorePlay(name: String) = update { s -> s.copy(ignored = (s.ignored + name).distinct()) }

    private val splitter = Regex("""\s+(?:feat\.?|ft\.|featuring|with|×)\s+|、""", RegexOption.IGNORE_CASE)

    suspend fun recordPlay(rawArtist: String, app: String?) {
        val names = rawArtist.split(splitter).map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return
        val now = System.currentTimeMillis()
        update { s ->
            val map = s.plays.associateBy { it.name.lowercase() }.toMutableMap()
            for (n in names) {
                val prev = map[n.lowercase()]
                map[n.lowercase()] = PlayStat(prev?.name ?: n, (prev?.count ?: 0) + 1, now, app)
            }
            val apps = if (app == null) s.appPlays else s.appPlays + (app to (s.appPlays[app] ?: 0) + 1)
            s.copy(plays = map.values.sortedByDescending { it.count }.take(500), appPlays = apps)
        }
    }
}

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
object Store : StateStore {
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

    override fun current(): AppState = _state.value

    override suspend fun update(transform: (AppState) -> AppState): Unit = mutex.withLock {
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
        else s.copy(
            artists = s.artists + a,
            ignored = s.ignored.filterNot { a.matches(it) },
            suggestions = s.suggestions.filterNot { it.artistId == a.id },
        )
    }

    suspend fun removeArtist(id: Long) = update { s ->
        s.copy(
            artists = s.artists.filterNot { it.id == id },
            seen = s.seen - id,
            releases = s.releases.filterNot { it.artistId == id },
        )
    }

    suspend fun saveSuggestions(list: List<Suggestion>, at: Long) = update { s ->
        // 確認している間に登録・興味なしにしたものは出さない
        val excluded = s.artists.map { it.id }.toSet() + s.dismissedSuggestions
        val kept = list.filterNot { it.artistId in excluded }
        s.copy(
            suggestions = kept,
            suggestionsAt = at,
            suggestionHistory = SuggestionSchedule.mergeHistory(kept, s.suggestionHistory),
            // 取り直して一覧から消えた人の NEW は残さない
            newSuggestions = s.newSuggestions.filter { id -> kept.any { it.artistId == id } },
        )
    }

    suspend fun markSuggestionsNew(ids: List<Long>) = update { s -> s.copy(newSuggestions = (ids + s.newSuggestions).distinct()) }

    /** シートで見たので NEW を外す */
    suspend fun clearNewSuggestions() = update { s -> if (s.newSuggestions.isEmpty()) s else s.copy(newSuggestions = emptyList()) }

    suspend fun setSuggestionFrequency(f: SuggestionFrequency) =
        update { s -> s.copy(suggestionFrequency = f, suggestionInviteDone = true) }

    suspend fun closeSuggestionInvite() = update { s -> s.copy(suggestionInviteDone = true) }

    suspend fun saveCatalogLookups(found: Map<String, CatalogLookup>, now: Long) = update { s ->
        val merged = (s.catalogLookups + found)
            .filterValues { now - it.checkedAt < SuggestionFinder.LOOKUP_TTL_MS }
            .entries.sortedByDescending { it.value.checkedAt }.take(1000)
            .associate { it.key to it.value }
        s.copy(catalogLookups = merged)
    }

    suspend fun dismissSuggestion(id: Long) = update { s ->
        s.copy(
            suggestions = s.suggestions.filterNot { it.artistId == id },
            dismissedSuggestions = (s.dismissedSuggestions + id).distinct().takeLast(500),
        )
    }

    /** 興味なしを取り消して、元の位置に戻す */
    suspend fun restoreSuggestion(sg: Suggestion, index: Int) = update { s ->
        val list = if (s.suggestions.any { it.artistId == sg.artistId }) s.suggestions
        else s.suggestions.toMutableList().apply { add(index.coerceIn(0, size), sg) }
        s.copy(suggestions = list, dismissedSuggestions = s.dismissedSuggestions - sg.artistId)
    }

    suspend fun ignorePlay(name: String) = update { s -> s.copy(ignored = (s.ignored + name).distinct()) }

    /** 開いた作品から NEW を外す */
    suspend fun markReleaseOpened(id: Long, today: java.time.LocalDate) = update { s ->
        s.copy(releases = s.releases.map { if (it.id == id) it.copy(openedOn = today.toString()) else it })
    }

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

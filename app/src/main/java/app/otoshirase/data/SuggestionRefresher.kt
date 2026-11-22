package app.otoshirase.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** おすすめを取り直して保存する。失敗しても前回の結果は残す */
class SuggestionRefresher(private val finder: SuggestionFinder) {
    private val running = AtomicBoolean(false)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed
    /** 取り直し中に見つかった分。null なら取り直していない（保存済みの結果を出す） */
    private val _partial = MutableStateFlow<List<Suggestion>?>(null)
    val partial: StateFlow<List<Suggestion>?> = _partial

    /** 利用者がボタンを押したときだけ呼ぶ。自動では取り直さない（通信を増やさないため） */
    suspend fun refresh() {
        if (!running.compareAndSet(false, true)) return
        _refreshing.value = true
        _partial.value = emptyList()
        val s = Store.state.value
        val now = System.currentTimeMillis()
        val looked = mutableMapOf<String, CatalogLookup>()
        try {
            val list = finder.find(
                s.artists, s.ignored, s.dismissedSuggestions.toSet(),
                known = s.catalogLookups,
                onLookup = { k, v -> looked[k] = v },
            ) { _partial.value = it }
            Store.saveSuggestions(list, now)
            _failed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _failed.value = true
        } finally {
            // 途中で失敗しても、引けた分は次に使う
            if (looked.isNotEmpty()) Store.saveCatalogLookups(looked, now)
            _partial.value = null
            _refreshing.value = false
            running.set(false)
        }
    }
}

package app.otoshirase.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** おすすめを必要なときだけ取り直して保存する。失敗しても前回の結果は残す */
class SuggestionRefresher(private val finder: SuggestionFinder) {
    private val running = AtomicBoolean(false)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    suspend fun refreshIfStale(now: Long = System.currentTimeMillis()) {
        val s = Store.state.value
        if (!SuggestionPolicy.needsRefresh(s, now)) return
        if (!running.compareAndSet(false, true)) return
        _refreshing.value = true
        try {
            val list = finder.find(s.artists, s.ignored, s.dismissedSuggestions.toSet())
            Store.saveSuggestions(list, SuggestionPolicy.key(s.artists), now)
            _failed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _failed.value = true
        } finally {
            _refreshing.value = false
            running.set(false)
        }
    }
}

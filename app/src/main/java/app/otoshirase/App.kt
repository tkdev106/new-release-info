package app.otoshirase

import android.app.Application
import app.otoshirase.data.AppleArtistCatalog
import app.otoshirase.data.Store
import app.otoshirase.data.SuggestionFinder
import app.otoshirase.data.SuggestionRefresher
import app.otoshirase.data.WorkerSuggestionSource
import app.otoshirase.notify.Notifier
import app.otoshirase.work.CheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.createChannels(this)
        CheckWorker.schedule(this)
        // 中継の URL とトークンが local.properties に無いビルド（CI など）では、おすすめ自体を出さない
        if (BuildConfig.SUGGEST_URL.isNotBlank() && BuildConfig.SUGGEST_TOKEN.isNotBlank()) {
            suggestions = SuggestionRefresher(
                SuggestionFinder(WorkerSuggestionSource(BuildConfig.SUGGEST_URL, BuildConfig.SUGGEST_TOKEN), AppleArtistCatalog),
            )
        }
    }

    companion object {
        /** 画面を閉じても続けたい処理（アーティスト追加直後の取り込みなど）用 */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** おすすめ。実装（中継・カタログ）はここでだけ選び、画面はインターフェース越しの結果だけを見る */
        var suggestions: SuggestionRefresher? = null
            private set
    }
}

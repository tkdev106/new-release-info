package app.otoshirase

import android.app.Application
import app.otoshirase.data.AppleArtistCatalog
import app.otoshirase.data.PlayIntegrityAuth
import app.otoshirase.data.Store
import app.otoshirase.data.SuggestionFinder
import app.otoshirase.data.SuggestionRefresher
import app.otoshirase.data.TokenAuth
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
        // 開発版・APK で配る版はトークン、Play 版（トークンを入れない）は Play Integrity で中継に名乗る。
        // どちらも無いビルド（CI など）では、おすすめ自体を出さない
        val auth = when {
            BuildConfig.SUGGEST_TOKEN.isNotBlank() -> TokenAuth(BuildConfig.SUGGEST_TOKEN)
            BuildConfig.PLAY_CLOUD_PROJECT_NUMBER > 0 -> PlayIntegrityAuth(this, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER)
            else -> null
        }
        if (BuildConfig.SUGGEST_URL.isNotBlank() && auth != null) {
            suggestions = SuggestionRefresher(
                SuggestionFinder(WorkerSuggestionSource(BuildConfig.SUGGEST_URL, auth), AppleArtistCatalog),
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

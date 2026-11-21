package app.otoshirase

import android.app.Application
import app.otoshirase.data.Store
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
    }

    companion object {
        /** 画面を閉じても続けたい処理（アーティスト追加直後の取り込みなど）用 */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

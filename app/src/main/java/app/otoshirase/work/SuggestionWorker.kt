package app.otoshirase.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.otoshirase.App
import app.otoshirase.data.Store
import app.otoshirase.data.SuggestionFrequency
import app.otoshirase.data.SuggestionSchedule
import app.otoshirase.notify.Notifier
import java.util.concurrent.TimeUnit

/** 利用者が選んだ頻度で裏でおすすめを取り直し、初めて出す人がいれば 1 件にまとめて通知する */
class SuggestionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        val refresher = App.suggestions ?: return Result.success()
        val state = Store.state.value
        if (!SuggestionSchedule.shouldFetchInBackground(state, Notifier.suggestionsAllowed(applicationContext), System.currentTimeMillis())) {
            return Result.success()
        }
        // 失敗してもやり直さない。次の回に回せば足り、中継の 1 日の上限も無駄に使わない
        val fresh = refresher.refresh() ?: return Result.success()
        if (fresh.isNotEmpty()) {
            Store.markSuggestionsNew(fresh.map { it.artistId })
            Notifier.notifySuggestions(applicationContext, fresh)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "suggestions"
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        /**
         * 頻度を変えたときは changed = true で予定を置き換える。起動時は今の予定を残す。
         * 最初の実行は 1 周期あと。選んだ時点ではもうおすすめを見ているため
         */
        fun schedule(ctx: Context, frequency: SuggestionFrequency, changed: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            val days = frequency.days ?: return run { wm.cancelUniqueWork(NAME) }
            val req = PeriodicWorkRequestBuilder<SuggestionWorker>(days.toLong(), TimeUnit.DAYS)
                .setInitialDelay(days.toLong(), TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            val policy = if (changed) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
            wm.enqueueUniquePeriodicWork(NAME, policy, req)
        }
    }
}

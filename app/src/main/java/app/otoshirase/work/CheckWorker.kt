package app.otoshirase.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.otoshirase.data.Store
import java.util.UUID
import java.util.concurrent.TimeUnit

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        // 押して始めた確認は、待たせ続けずにすぐ結果を返す（失敗は画面で知らせる）
        val manual = NOW in tags
        return runCatching { Checker.checkAll(applicationContext) }
            .fold({ Result.success() }, { if (!manual && runAttemptCount < 3) Result.retry() else Result.failure() })
    }

    companion object {
        const val PERIODIC = "periodic-check"
        const val NOW = "check-now"
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 6時間おきにチェック（Android の省電力制御で多少前後します） */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<CheckWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /**
         * 通信の条件は付けない。付けるとオフラインのあいだ待ち続け、画面が「確認中」のまま止まって見える。
         * 通信できるかは押した時点で画面が確かめる
         */
        fun runNow(ctx: Context): UUID {
            val req = OneTimeWorkRequestBuilder<CheckWorker>().addTag(NOW).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
            return req.id
        }
    }
}

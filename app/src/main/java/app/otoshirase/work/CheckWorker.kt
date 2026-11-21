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
import java.util.concurrent.TimeUnit

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        return runCatching { Checker.checkAll(applicationContext) }
            .fold({ Result.success() }, { if (runAttemptCount < 3) Result.retry() else Result.failure() })
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

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<CheckWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
        }
    }
}

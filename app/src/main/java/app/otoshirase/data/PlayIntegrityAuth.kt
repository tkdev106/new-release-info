package app.otoshirase.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Play 版の RequestAuth。Play から入れた本物のアプリであることを、中継が Google に確かめられるトークンを付ける */
class PlayIntegrityAuth(context: Context, private val cloudProjectNumber: Long) : RequestAuth {
    private val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
    private val lock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    override suspend fun headers(body: String): Map<String, String> {
        val token = try {
            request(provider(), body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 準備したものは時間が経つと使えなくなるので、1 回だけ作り直してやり直す
            lock.withLock { provider = null }
            request(provider(), body)
        }
        return mapOf("X-Play-Integrity" to token)
    }

    // 準備は数秒かかるので、おすすめの更新を押すたびではなく最初の 1 回だけにする
    private suspend fun provider(): StandardIntegrityTokenProvider = lock.withLock {
        provider ?: manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build(),
        ).await().also { provider = it }
    }

    private suspend fun request(p: StandardIntegrityTokenProvider, body: String): String =
        p.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash(body)).build())
            .await<StandardIntegrityToken>().token()
}

// 依存を増やさないよう、Task を待つ部分だけを自前で書く
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

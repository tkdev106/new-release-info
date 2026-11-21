package app.otoshirase.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** ジャケット画像を読み込む。通知とウィジェットは Coil の画面描画を使えないので、ここで Bitmap にする */
object Artwork {
    suspend fun load(url: String, px: Int = 300): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            AppleCatalog.http.newCall(Request.Builder().url(url.replace("600x600bb", "${px}x${px}bb")).build())
                .execute().use { BitmapFactory.decodeStream(it.body!!.byteStream()) }
        }.getOrNull()
    }
}

package app.otoshirase.notify

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.otoshirase.App
import app.otoshirase.data.Store
import app.otoshirase.ui.MainActivity
import app.otoshirase.ui.releaseIntent
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 新譜の通知を押したときに、開いた印を付けてから音楽アプリへ渡す。画面は持たない。
 * 通知から直接音楽アプリを開くと、アプリ側で NEW を外す機会がないため
 */
class OpenReleaseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val r = Store.current().releases.firstOrNull { it.id == id }
        if (r != null) App.scope.launch { Store.markReleaseOpened(id, LocalDate.now()) }
        val target = r?.let { releaseIntent(this, it) } ?: Intent(this, MainActivity::class.java)
        runCatching { startActivity(target) }.onFailure { startActivity(Intent(this, MainActivity::class.java)) }
        finish()
    }

    companion object {
        const val EXTRA_ID = "release_id"
    }
}

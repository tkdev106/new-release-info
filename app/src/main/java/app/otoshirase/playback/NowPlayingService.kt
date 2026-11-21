package app.otoshirase.playback

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import app.otoshirase.data.PlaysPrompt
import app.otoshirase.data.Store
import app.otoshirase.notify.AndroidPlaysPromptNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 通知へのアクセス権を使って、音楽アプリの「再生中」情報（MediaSession）を読み取り、
 * アーティストごとの再生回数を端末内に記録する。通知の中身そのものは読まない。
 */
class NowPlayingService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbacks = mutableMapOf<MediaSession, MediaController.Callback>()
    private val lastTrack = mutableMapOf<String, String>()
    private var msm: MediaSessionManager? = null
    private val promptNotifier by lazy { AndroidPlaysPromptNotifier(this) }

    private val musicApps = setOf(
        "com.apple.android.music",
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.amazon.mp3",
        "jp.linecorp.linemusic.android",
        "fm.awa.liverpool",
        "com.bandcamp.android",
        "com.soundcloud.android",
        "deezer.android.app",
        "com.aspiro.tidal",
    )

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    override fun onListenerConnected() {
        Store.init(this)
        val m = getSystemService(MediaSessionManager::class.java)
        msm = m
        val me = ComponentName(this, NowPlayingService::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, me)
            attach(m.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        callbacks.keys.toList().forEach { detach(it) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun attach(controllers: List<MediaController>) {
        val music = controllers.filter { it.packageName in musicApps }
        val tokens = music.map { MediaSession(it.packageName, it.sessionToken.hashCode()) }.toSet()
        callbacks.keys.filterNot { it in tokens }.forEach { detach(it) }
        music.forEach { c ->
            val key = MediaSession(c.packageName, c.sessionToken.hashCode())
            if (key in callbacks) return@forEach
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = record(c.packageName, metadata)
            }
            c.registerCallback(cb)
            callbacks[key] = cb
            controllersByKey[key] = c
            record(c.packageName, c.metadata)
        }
    }

    private val controllersByKey = mutableMapOf<MediaSession, MediaController>()

    private fun detach(key: MediaSession) {
        callbacks.remove(key)?.let { cb -> controllersByKey.remove(key)?.unregisterCallback(cb) }
    }

    private fun record(pkg: String, md: MediaMetadata?) {
        md ?: return
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val track = "$artist\u0000$title"
        if (lastTrack[pkg] == track) return // 同じ曲のメタデータ更新は数えない
        lastTrack[pkg] = track
        scope.launch {
            Store.recordPlay(artist, pkg)
            PlaysPrompt.check(Store, promptNotifier)
        }
    }

    private data class MediaSession(val pkg: String, val token: Int)
}

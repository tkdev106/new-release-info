package app.otoshirase.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicAppsTest {

    @Test
    fun `再生回数がいちばん多いアプリを選ぶ`() {
        val plays = mapOf("com.spotify.music" to 3, "com.google.android.apps.youtube.music" to 12, MusicApps.APPLE_MUSIC to 5)
        assertEquals("com.google.android.apps.youtube.music", MusicApps.preferred(plays))
    }

    @Test
    fun `開き方を知らないアプリは選ばない`() {
        val plays = mapOf("com.example.unknown" to 99, "com.spotify.music" to 1)
        assertEquals("com.spotify.music", MusicApps.preferred(plays))
    }

    @Test
    fun `再生の記録がなければ選ばない`() {
        assertNull(MusicApps.preferred(emptyMap()))
    }

    @Test
    fun `検索語は空白も日本語もエンコードする`() {
        assertEquals("spotify:search:%E7%BE%8A%E6%96%87%E5%AD%A6%20Sugar", MusicApps.searchUrl("com.spotify.music", "羊文学 Sugar"))
        assertEquals("https://music.youtube.com/search?q=A%26B", MusicApps.searchUrl("com.google.android.apps.youtube.music", "A&B"))
    }

    @Test
    fun `Apple Music は検索URLを作らず作品のURLを使う`() {
        assertNull(MusicApps.searchUrl(MusicApps.APPLE_MUSIC, "羊文学"))
    }
}

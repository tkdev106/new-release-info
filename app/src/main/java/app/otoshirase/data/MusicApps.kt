package app.otoshirase.data

import java.net.URLEncoder

/**
 * 新譜やアーティストを、ユーザーがいちばん使っている音楽アプリで開くための判断。
 * Android に依存しないので JVM のテストで確かめられる。
 */
object MusicApps {
    const val APPLE_MUSIC = "com.apple.android.music"

    /** 各アプリの検索画面を開く URL。作品ページへの直リンクは外部の変換サービスが要るので、検索で済ませる */
    private val searchUrls: Map<String, (String) -> String> = mapOf(
        "com.spotify.music" to { q -> "spotify:search:$q" },
        "com.google.android.apps.youtube.music" to { q -> "https://music.youtube.com/search?q=$q" },
        "com.amazon.mp3" to { q -> "https://music.amazon.co.jp/search/$q" },
        "jp.linecorp.linemusic.android" to { q -> "https://music.line.me/search?query=$q" },
        "fm.awa.liverpool" to { q -> "https://s.awa.fm/search?q=$q" },
        "com.soundcloud.android" to { q -> "https://soundcloud.com/search?q=$q" },
        "deezer.android.app" to { q -> "https://www.deezer.com/search/$q" },
        "com.aspiro.tidal" to { q -> "https://tidal.com/search?q=$q" },
    )

    /** 再生回数がいちばん多いアプリ。開き方を知らないアプリは候補にしない */
    fun preferred(appPlays: Map<String, Int>): String? =
        appPlays.filter { (pkg, n) -> n > 0 && (pkg in searchUrls || pkg == APPLE_MUSIC) }
            .maxByOrNull { it.value }?.key

    /** そのアプリで query を検索する URL。Apple Music は作品の URL をそのまま使うので null */
    fun searchUrl(pkg: String, query: String): String? {
        val q = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        return searchUrls[pkg]?.invoke(q)
    }
}

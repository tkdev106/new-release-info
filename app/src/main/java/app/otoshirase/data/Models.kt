package app.otoshirase.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Artist(
    val id: Long,                 // Apple Music (iTunes) artistId
    val name: String,             // 表示名（例: 羊文学）
    val genre: String? = null,
    val url: String? = null,
    val aliases: List<String> = emptyList(), // 再生履歴との照合用（例: Hitsujibungaku）
    val addedAt: Long = System.currentTimeMillis(),
    val image: String? = null,    // アーティスト画像（Apple Music のページから取得）
    val imageCheckedAt: Long = 0, // 画像を最後に取りに行った時刻。取れなかったときに毎回取りに行かないため
) {
    fun matches(n: String): Boolean {
        val k = n.trim().lowercase()
        return name.lowercase() == k || aliases.any { it.lowercase() == k }
    }
}

@Serializable
data class Release(
    val id: Long,                 // collectionId
    val artistId: Long,
    val artistName: String,
    val title: String,            // " - Single" などを除いたタイトル
    val kind: String,             // アルバム / EP / シングル
    val releaseDate: String?,     // yyyy-MM-dd
    val url: String?,
    val artwork: String?,
    val trackCount: Int = 0,
    val foundAt: Long = System.currentTimeMillis(),
    val notified: Boolean = false, // 登録時の取り込み(false) か、新着として通知したか(true)
    val releaseDayNotified: Boolean = false, // 発売予定だった作品の発売日に通知したか
) {
    val date: LocalDate? get() = releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val upcoming: Boolean get() = date?.isAfter(LocalDate.now()) == true
}

@Serializable
data class PlayStat(
    val name: String,
    val count: Int,
    val lastPlayed: Long,
    val app: String? = null,
)

/** Apple のカタログで実在を確かめたおすすめ。表示名も Apple 上の表記 */
@Serializable
data class Suggestion(
    val artistId: Long,
    val name: String,
    val genre: String? = null,
    val url: String? = null,
)

/** 候補名を Apple のカタログで引いた結果。同じ候補を何度も問い合わせないため。見つからなければ artistId は null */
@Serializable
data class CatalogLookup(
    val artistId: Long? = null,
    val name: String? = null,
    val genre: String? = null,
    val url: String? = null,
    val checkedAt: Long = 0,
)

@Serializable
data class AppState(
    val artists: List<Artist> = emptyList(),
    /** artistId -> 既に見たcollectionId */
    val seen: Map<Long, List<Long>> = emptyMap(),
    val releases: List<Release> = emptyList(),
    val plays: List<PlayStat> = emptyList(),
    val ignored: List<String> = emptyList(),
    /** 「新譜の通知を受け取りませんか？」で数え済みのアーティスト（小文字）。次はこれ以外が2組たまったら出す */
    val promptedPlays: List<String> = emptyList(),
    /** 音楽アプリ（パッケージ名）ごとの再生回数。新譜をどのアプリで開くかに使う */
    val appPlays: Map<String, Int> = emptyMap(),
    val lastCheck: Long = 0,
    val suggestions: List<Suggestion> = emptyList(),
    val suggestionsAt: Long = 0,
    /** 「興味なし」にしたおすすめの artistId。次からも出さない */
    val dismissedSuggestions: List<Long> = emptyList(),
    /** 正規化した候補名 → カタログで引いた結果 */
    val catalogLookups: Map<String, CatalogLookup> = emptyMap(),
)

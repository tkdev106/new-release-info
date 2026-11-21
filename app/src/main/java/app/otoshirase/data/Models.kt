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

@Serializable
data class AppState(
    val artists: List<Artist> = emptyList(),
    /** artistId -> 既に見たcollectionId */
    val seen: Map<Long, List<Long>> = emptyMap(),
    val releases: List<Release> = emptyList(),
    val plays: List<PlayStat> = emptyList(),
    val ignored: List<String> = emptyList(),
    val lastCheck: Long = 0,
)

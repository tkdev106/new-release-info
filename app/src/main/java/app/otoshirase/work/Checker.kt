package app.otoshirase.work

import android.content.Context
import app.otoshirase.data.AppleCatalog
import app.otoshirase.data.Artist
import app.otoshirase.data.ArtistImages
import app.otoshirase.data.ArtistNames
import app.otoshirase.data.Release
import app.otoshirase.data.ReleaseDay
import app.otoshirase.data.Store
import app.otoshirase.notify.Notifier
import app.otoshirase.widget.ReleaseWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

object Checker {
    /** 登録時に取り込む「最近のリリース」の範囲 */
    private const val BASELINE_DAYS = 45L
    /** 新しく見つかったものでも、これより古いものは過去作の追加登録とみなして通知しない */
    private const val NEW_DAYS = 60L
    private const val GAP_MS = 3_200L

    suspend fun checkAll(ctx: Context): List<Release> {
        val fresh = mutableListOf<Release>()
        Store.state.value.artists.forEachIndexed { i, a ->
            if (i > 0) delay(GAP_MS)
            runCatching { fresh += checkArtist(a) }
        }
        var released = emptyList<Release>()
        Store.update { s ->
            released = ReleaseDay.due(s.releases, LocalDate.now(), ZoneId.systemDefault())
            val ids = released.map { it.id }.toSet()
            s.copy(
                lastCheck = System.currentTimeMillis(),
                releases = s.releases.map { if (it.id in ids) it.copy(releaseDayNotified = true) else it },
            )
        }
        Notifier.notifyReleases(ctx, fresh + released)
        runCatching { ReleaseWidget().updateAll(ctx) }
        return fresh
    }

    /** @return 新着として通知すべきリリース */
    suspend fun checkArtist(a: Artist): List<Release> {
        refreshImage(a)
        val disco = AppleCatalog.discography(a.id)
        val items = disco.albums
        val today = LocalDate.now()
        var fresh = emptyList<Release>()
        Store.update { s ->
            val current = s.artists.firstOrNull { it.id == a.id } ?: return@update s
            val seenBefore = s.seen[a.id]
            val baseline = seenBefore == null
            val seenSet = seenBefore?.toSet() ?: emptySet()
            val cutoff = today.minusDays(if (baseline) BASELINE_DAYS else NEW_DAYS)
            val known = s.releases.filter { it.artistId == a.id }.map { key(it) }.toMutableSet()

            // 作品に付いた表記（例: Hitsujibungaku → 羊文学）と公式表記から表示名を決める
            val localName = items.mapNotNull { it.artistName }
                .filterNot { it.contains(" & ") || it.contains(", ") }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val officialName = disco.artist?.artistName
            val display = ArtistNames.choose(localName ?: current.localName, officialName ?: current.officialName, current.preferLocalName)
                ?: current.name
            val named = current.copy(
                name = display,
                localName = localName ?: current.localName,
                officialName = officialName ?: current.officialName,
                aliases = (current.aliases + current.name + listOfNotNull(localName, officialName)).distinct() - display,
            )

            val added = items.asSequence()
                .filter { it.collectionId !in seenSet }
                .map { AppleCatalog.toRelease(named, it, notified = !baseline) }
                // 単独名義の作品はカタカナ表記でなく表示名で出す
                .map { if (it.artistName == localName) it.copy(artistName = display) else it }
                .filter { r -> r.date?.let { !it.isBefore(cutoff) } ?: true }
                .filter { known.add(key(it)) } // 通常版/クリーン版などの重複を除く
                .toList()
            fresh = added.filter { it.notified }

            // 登録時と端末の言語が変わっていても、ジャンル名を今の言語に揃える
            val updatedArtist = named.copy(genre = disco.artist?.primaryGenreName ?: named.genre)

            s.copy(
                artists = s.artists.map { if (it.id == a.id) updatedArtist else it },
                seen = s.seen + (a.id to (seenSet + items.mapNotNull { it.collectionId }).toList()),
                releases = (s.releases.map { if (it.artistId == a.id && it.artistName == localName) it.copy(artistName = display) else it } + added)
                    .sortedWith(compareByDescending<Release> { it.releaseDate ?: "" }.thenByDescending { it.foundAt })
                    .take(400),
            )
        }
        return fresh
    }

    /** 画像がまだ無ければ取りに行く。取れなかった場合は一定期間おいてから再挑戦する */
    private suspend fun refreshImage(a: Artist) {
        val now = System.currentTimeMillis()
        if (a.image != null || now - a.imageCheckedAt < IMAGE_RETRY_MS) return
        val url = ArtistImages.fetch(a.id)
        Store.update { s ->
            s.copy(artists = s.artists.map { if (it.id == a.id) it.copy(image = url ?: it.image, imageCheckedAt = now) else it })
        }
    }

    private const val IMAGE_RETRY_MS = 7L * 24 * 60 * 60 * 1000

    private fun key(r: Release) = "${r.artistId}:${r.title.trim().lowercase()}"
}

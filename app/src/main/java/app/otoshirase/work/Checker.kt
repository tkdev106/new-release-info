package app.otoshirase.work

import android.content.Context
import app.otoshirase.data.AppleCatalog
import app.otoshirase.data.Artist
import app.otoshirase.data.Release
import app.otoshirase.data.Store
import app.otoshirase.notify.Notifier
import app.otoshirase.widget.ReleaseWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.delay
import java.time.LocalDate

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
        Store.update { it.copy(lastCheck = System.currentTimeMillis()) }
        Notifier.notifyReleases(ctx, fresh)
        runCatching { ReleaseWidget().updateAll(ctx) }
        return fresh
    }

    /** @return 新着として通知すべきリリース */
    suspend fun checkArtist(a: Artist): List<Release> {
        val items = AppleCatalog.albums(a.id)
        val today = LocalDate.now()
        var fresh = emptyList<Release>()
        Store.update { s ->
            val current = s.artists.firstOrNull { it.id == a.id } ?: return@update s
            val seenBefore = s.seen[a.id]
            val baseline = seenBefore == null
            val seenSet = seenBefore?.toSet() ?: emptySet()
            val cutoff = today.minusDays(if (baseline) BASELINE_DAYS else NEW_DAYS)
            val known = s.releases.filter { it.artistId == a.id }.map { key(it) }.toMutableSet()

            val added = items.asSequence()
                .filter { it.collectionId !in seenSet }
                .map { AppleCatalog.toRelease(current, it, notified = !baseline) }
                .filter { r -> r.date?.let { !it.isBefore(cutoff) } ?: true }
                .filter { known.add(key(it)) } // 通常版/クリーン版などの重複を除く
                .toList()
            fresh = added.filter { it.notified }

            // Apple のカタログ上の表記（例: Hitsujibungaku → 羊文学）を表示名に使う
            val localName = items.mapNotNull { it.artistName }
                .filterNot { it.contains(" & ") || it.contains(", ") }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val updatedArtist = if (localName != null && localName != current.name)
                current.copy(name = localName, aliases = (current.aliases + current.name).distinct())
            else current

            s.copy(
                artists = s.artists.map { if (it.id == a.id) updatedArtist else it },
                seen = s.seen + (a.id to (seenSet + items.mapNotNull { it.collectionId }).toList()),
                releases = (s.releases + added)
                    .sortedWith(compareByDescending<Release> { it.releaseDate ?: "" }.thenByDescending { it.foundAt })
                    .take(400),
            )
        }
        return fresh
    }

    private fun key(r: Release) = "${r.artistId}:${r.title.trim().lowercase()}"
}

package app.otoshirase.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import app.otoshirase.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.otoshirase.App
import app.otoshirase.data.AppleCatalog
import app.otoshirase.data.Artist
import app.otoshirase.data.ArtistImages
import app.otoshirase.data.PlayStat
import app.otoshirase.data.Release
import app.otoshirase.data.Store
import app.otoshirase.data.Suggestion
import app.otoshirase.widget.ReleaseWidget
import app.otoshirase.work.CheckWorker
import app.otoshirase.work.Checker
import androidx.glance.appwidget.updateAll
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AppTheme { Root() } }
    }
}

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Releases(R.string.tab_releases, Icons.Default.Notifications),
    Artists(R.string.tab_artists, Icons.Default.Person),
    Plays(R.string.tab_plays, Icons.Default.Favorite),
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val state by Store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Releases) }
    var searchFor by remember { mutableStateOf<String?>(null) }

    val work by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkFlow(CheckWorker.NOW).collectAsState(initial = emptyList())
    val checking = work.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Header(
                title = stringResource(tab.label),
                subtitle = when (tab) {
                    Tab.Releases -> if (checking) stringResource(R.string.checking) else checkedLabel(ctx, state.lastCheck)
                    Tab.Artists -> pluralStringResource(R.plurals.artist_count, state.artists.size, state.artists.size)
                    Tab.Plays -> null
                },
            ) {
                when (tab) {
                    Tab.Releases -> CircleButton(Icons.Default.Refresh, stringResource(R.string.check_now), enabled = !checking && state.artists.isNotEmpty()) {
                        CheckWorker.runNow(ctx)
                    }
                    Tab.Artists -> CircleButton(Icons.Default.Add, stringResource(R.string.add_artist)) { searchFor = "" }
                    Tab.Plays -> {}
                }
            }
        },
        bottomBar = { TabBar(tab) { tab = it } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (checking && tab == Tab.Releases) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(1.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent,
                )
            }
            when (tab) {
                Tab.Releases -> ReleasesScreen(state.releases, hasArtists = state.artists.isNotEmpty()) { tab = Tab.Artists; searchFor = "" }
                Tab.Artists -> ArtistsScreen(state.artists, state.releases) { searchFor = "" }
                Tab.Plays -> PlaysScreen(state.plays.filter { p ->
                    p.name !in state.ignored && state.artists.none { it.matches(p.name) }
                }, onAdd = { searchFor = it }, onIgnore = { App.scope.launch { Store.ignorePlay(it) } })
            }
        }
    }

    searchFor?.let { initial ->
        SearchSheet(initial, registered = state.artists.map { it.id }.toSet(), suggestions = state.suggestions, onDismiss = { searchFor = null })
    }
}

@Composable
private fun Header(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** 下に浮かぶピル型のタブ */
@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(pill).background(c.surfaceContainer).padding(4.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                Row(
                    Modifier.clip(pill)
                        .background(if (selected) c.secondaryContainer else Color.Transparent)
                        .clickable { onSelect(t) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (selected) c.onSecondaryContainer else c.onSurfaceVariant
                    Icon(t.icon, null, Modifier.size(18.dp), tint = tint)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(t.label), style = MaterialTheme.typography.labelLarge, color = tint)
                }
            }
        }
    }
}

// ---------------- 新譜 ----------------

@Composable
private fun ReleasesScreen(releases: List<Release>, hasArtists: Boolean, addArtist: () -> Unit) {
    val ctx = LocalContext.current
    if (releases.isEmpty()) {
        if (hasArtists) Empty(stringResource(R.string.releases_empty), null)
        else Empty(stringResource(R.string.releases_empty_no_artists), stringResource(R.string.add_artist) to addArtist)
        return
    }
    val (upcoming, out) = releases.partition { it.upcoming }
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        if (upcoming.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.section_upcoming)) }
            items(upcoming, key = { it.id }) { ReleaseRow(it) { open(ctx, releaseIntent(ctx, it)) } }
        }
        if (out.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.section_recent)) }
            items(out, key = { it.id }) { ReleaseRow(it) { open(ctx, releaseIntent(ctx, it)) } }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
)

@Composable
private fun ReleaseRow(r: Release, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = r.artwork, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainer),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(r.artistName, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${kindLabel(ctx, r.kind)} · ${shortDate(ctx, r)}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                if (r.notified) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.badge_new), style = MaterialTheme.typography.labelSmall, color = c.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.primary).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// ---------------- アーティスト ----------------

@Composable
private fun ArtistsScreen(artists: List<Artist>, releases: List<Release>, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Artist?>(null) }
    if (artists.isEmpty()) {
        Empty(stringResource(R.string.artists_empty), stringResource(R.string.add_artist) to onAdd)
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
        items(artists.sortedBy { it.name.lowercase() }, key = { it.id }) { a ->
            // 画像が取れていないアーティストは、最新作のジャケットで代用する
            val image = a.image ?: releases.firstOrNull { it.artistId == a.id }?.artwork
            PersonRow(a.name, a.genre, image = image, onClick = { open(ctx, artistIntent(ctx, a)) }) {
                IconButton(onClick = { confirm = a }) {
                    Icon(Icons.Default.Close, stringResource(R.string.remove), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    confirm?.let { a ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.remove_title, a.name)) },
            text = { Text(stringResource(R.string.remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    App.scope.launch { Store.removeArtist(a.id); ReleaseWidget().updateAll(ctx) }
                    confirm = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** 頭文字の丸・名前・補足を並べた1行。アーティスト・よく聴く・検索結果で共通 */
@Composable
private fun PersonRow(name: String, detail: String?, image: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(name, image)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = trailing)
    }
}

// ---------------- よく聴く ----------------

@Composable
private fun PlaysScreen(plays: List<PlayStat>, onAdd: (String) -> Unit, onIgnore: (String) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(listenerGranted(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = listenerGranted(ctx) }

    if (!granted) {
        Empty(
            stringResource(R.string.plays_intro_body),
            stringResource(R.string.allow) to { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            title = stringResource(R.string.plays_intro_title),
        )
        return
    }
    if (plays.isEmpty()) {
        Empty(stringResource(R.string.plays_empty), null)
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
        items(plays, key = { it.name }) { p ->
            PersonRow(
                p.name, "${pluralStringResource(R.plurals.play_count, p.count, p.count)} · ${agoLabel(ctx, p.lastPlayed)}",
                image = artistImageByName(p.name),
            ) {
                IconButton(onClick = { onIgnore(p.name) }) {
                    Icon(Icons.Default.Close, stringResource(R.string.hide), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PillButton(stringResource(R.string.add)) { onAdd(p.name) }
            }
        }
    }
}

@Composable
private fun artistImageById(id: Long): String? =
    produceState(ArtistImages.peek(id), id) { value = ArtistImages.cached(id) }.value

@Composable
private fun artistImageByName(name: String): String? =
    produceState<String?>(null, name) { value = ArtistImages.forName(name) }.value

private fun listenerGranted(ctx: android.content.Context) =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

// ---------------- 検索シート ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(initial: String, registered: Set<Long>, suggestions: List<Suggestion>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf<List<AppleCatalog.Item>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val added = remember { mutableStateListOf<Long>() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); error = false; return@LaunchedEffect }
        delay(400)
        loading = true; error = false
        try {
            results = AppleCatalog.searchArtists(query.trim())
        } catch (e: CancellationException) {
            // 入力が変わって前の検索が打ち切られただけなので、エラーにしない
            throw e
        } catch (e: Exception) {
            error = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (initial.isBlank()) focus.requestFocus() }

    // シートを閉じても確認は続けたいので App.scope で走らせる
    val refresher = App.suggestions
    LaunchedEffect(Unit) { refresher?.let { r -> App.scope.launch { r.refreshIfStale() } } }
    val suggesting = refresher?.refreshing?.collectAsState()?.value == true
    val suggestFailed = refresher?.failed?.collectAsState()?.value == true
    // 取り直し中は、見つかった分から順に出す（前の結果とは混ぜない）
    val partial = refresher?.partial?.collectAsState()?.value

    fun add(id: Long, name: String, genre: String?, url: String?, alias: String?) {
        added += id
        val artist = Artist(
            id = id,
            name = name,
            genre = genre,
            url = url,
            aliases = listOfNotNull(alias).filter { it.isNotBlank() },
            // 検索結果で取れていれば、登録時に取り直さない
            image = ArtistImages.peek(id),
            imageCheckedAt = if (ArtistImages.peek(id) != null) System.currentTimeMillis() else 0,
        )
        App.scope.launch {
            Store.addArtist(artist)
            // 好みが変わったので、おすすめを一新する
            refresher?.refreshIfStale()
            runCatching { Checker.checkArtist(artist) }
            runCatching { ReleaseWidget().updateAll(ctx) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.background,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).fillMaxHeight(0.85f)) {
            val pill = RoundedCornerShape(50)
            Row(
                Modifier.fillMaxWidth().clip(pill).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 24.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp), color = c.primary, trackColor = Color.Transparent)
            }
            if (error) Text(stringResource(R.string.search_error), style = MaterialTheme.typography.bodySmall, color = c.error, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (query.isBlank() && refresher != null) {
                    val shown = (partial ?: suggestions).filterNot { it.artistId in registered || it.artistId in added }
                    if (shown.isNotEmpty() || suggesting || suggestFailed) {
                        item { SectionLabel(stringResource(R.string.suggestions)) }
                    }
                    if (!suggesting && suggestFailed && shown.isEmpty()) item {
                        Text(stringResource(R.string.suggestions_error), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(shown, key = { "s" + it.artistId }) { sg ->
                        PersonRow(sg.name, sg.genre, image = artistImageById(sg.artistId)) {
                            IconButton(onClick = { App.scope.launch { Store.dismissSuggestion(sg.artistId) } }) {
                                Icon(Icons.Default.Close, stringResource(R.string.not_interested), Modifier.size(18.dp), tint = c.onSurfaceVariant)
                            }
                            PillButton(stringResource(R.string.add)) { add(sg.artistId, sg.name, sg.genre, sg.url, null) }
                        }
                    }
                    if (suggesting) item(key = "suggesting") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.suggestions_loading), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                    }
                }
                items(results, key = { it.artistId!! }) { item ->
                    val id = item.artistId!!
                    val done = id in registered || id in added
                    PersonRow(item.artistName.orEmpty(), item.primaryGenreName, image = artistImageById(id)) {
                        if (done) {
                            Text(stringResource(R.string.added), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                        } else PillButton(stringResource(R.string.add)) {
                            add(id, item.artistName.orEmpty(), item.primaryGenreName, item.artistLinkUrl?.substringBefore("?uo="), initial)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 共通 ----------------

@Composable
private fun Empty(message: String, action: Pair<String, () -> Unit>?, title: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            message, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(20.dp))
            PillButton(label, filled = true, onClick = onClick)
        }
    }
}

private fun open(ctx: android.content.Context, intent: Intent?) {
    intent ?: return
    runCatching { ctx.startActivity(intent) }
}

package app.otoshirase.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import app.otoshirase.data.PlayStat
import app.otoshirase.data.Release
import app.otoshirase.data.Store
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

private enum class Tab(val label: String, val icon: ImageVector) {
    Releases("新譜", Icons.Default.Notifications),
    Artists("アーティスト", Icons.Default.Person),
    Plays("よく聴く", Icons.Default.Favorite),
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
                title = tab.label,
                subtitle = when (tab) {
                    Tab.Releases -> if (checking) "確認中…" else checkedLabel(state.lastCheck)
                    Tab.Artists -> "${state.artists.size}組"
                    Tab.Plays -> null
                },
            ) {
                when (tab) {
                    Tab.Releases -> CircleButton(Icons.Default.Refresh, "今すぐ確認", enabled = !checking && state.artists.isNotEmpty()) {
                        CheckWorker.runNow(ctx)
                    }
                    Tab.Artists -> CircleButton(Icons.Default.Add, "追加") { searchFor = "" }
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
                    color = MaterialTheme.colorScheme.onSurface, trackColor = Color.Transparent,
                )
            }
            when (tab) {
                Tab.Releases -> ReleasesScreen(state.releases, hasArtists = state.artists.isNotEmpty()) { tab = Tab.Artists; searchFor = "" }
                Tab.Artists -> ArtistsScreen(state.artists) { searchFor = "" }
                Tab.Plays -> PlaysScreen(state.plays.filter { p ->
                    p.name !in state.ignored && state.artists.none { it.matches(p.name) }
                }, onAdd = { searchFor = it }, onIgnore = { App.scope.launch { Store.ignorePlay(it) } })
            }
        }
    }

    searchFor?.let { initial ->
        SearchSheet(initial, registered = state.artists.map { it.id }.toSet(), onDismiss = { searchFor = null })
    }
}

@Composable
private fun Header(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Display, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = Meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Modifier.clip(pill).background(c.surfaceContainerLow).border(1.dp, c.outlineVariant, pill).padding(4.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                Row(
                    Modifier.clip(pill)
                        .background(if (selected) c.onSurface else Color.Transparent)
                        .clickable { onSelect(t) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (selected) c.surface else c.onSurfaceVariant
                    Icon(t.icon, null, Modifier.size(18.dp), tint = tint)
                    Spacer(Modifier.width(6.dp))
                    Text(t.label, style = MaterialTheme.typography.labelLarge, color = tint)
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
        if (hasArtists) Empty("新しいリリースはまだありません", null)
        else Empty("アーティストを登録すると\n新譜がここに届きます", "アーティストを追加" to addArtist)
        return
    }
    val (upcoming, out) = releases.partition { it.upcoming }
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        if (upcoming.isNotEmpty()) {
            item { SectionLabel("発売予定") }
            items(upcoming, key = { it.id }) { ReleaseRow(it) { open(ctx, it.url) } }
        }
        if (out.isNotEmpty()) {
            item { SectionLabel("最近のリリース") }
            items(out, key = { it.id }) { ReleaseRow(it) { open(ctx, it.url) } }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text, style = Meta, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
)

@Composable
private fun ReleaseRow(r: Release, onClick: () -> Unit) {
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
                Text("${r.kind} · ${shortDate(r)}", style = Meta, color = c.onSurfaceVariant)
                if (r.notified) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NEW", style = Meta, color = c.surface,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.onSurface).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// ---------------- アーティスト ----------------

@Composable
private fun ArtistsScreen(artists: List<Artist>, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Artist?>(null) }
    if (artists.isEmpty()) {
        Empty("まだ登録がありません", "アーティストを追加" to onAdd)
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
        items(artists.sortedBy { it.name.lowercase() }, key = { it.id }) { a ->
            PersonRow(a.name, a.genre, onClick = { open(ctx, a.url) }) {
                IconButton(onClick = { confirm = a }) {
                    Icon(Icons.Default.Close, "削除", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    confirm?.let { a ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${a.name} を削除しますか？") },
            text = { Text("このアーティストの新譜も一覧から消えます。") },
            confirmButton = {
                TextButton(onClick = {
                    App.scope.launch { Store.removeArtist(a.id); ReleaseWidget().updateAll(ctx) }
                    confirm = null
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("キャンセル") } },
        )
    }
}

/** 頭文字の丸・名前・補足を並べた1行。アーティスト・よく聴く・検索結果で共通 */
@Composable
private fun PersonRow(name: String, detail: String?, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = Meta, color = c.onSurfaceVariant, maxLines = 1)
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
            "再生中の曲から\nよく聴くアーティストを集めます\n（データは端末の外に送りません）",
            "通知へのアクセスを許可" to { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        )
        return
    }
    if (plays.isEmpty()) {
        Empty("音楽を再生すると、ここに並びます", null)
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
        items(plays, key = { it.name }) { p ->
            PersonRow(p.name, "${p.count}曲 · ${agoLabel(p.lastPlayed)}") {
                IconButton(onClick = { onIgnore(p.name) }) {
                    Icon(Icons.Default.Close, "非表示", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PillButton("登録") { onAdd(p.name) }
            }
        }
    }
}

private fun listenerGranted(ctx: android.content.Context) =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

// ---------------- 検索シート ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(initial: String, registered: Set<Long>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf<List<AppleCatalog.Item>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val added = remember { mutableStateListOf<Long>() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); error = null; return@LaunchedEffect }
        delay(400)
        loading = true; error = null
        try {
            results = AppleCatalog.searchArtists(query.trim())
        } catch (e: CancellationException) {
            // 入力が変わって前の検索が打ち切られただけなので、エラーにしない
            throw e
        } catch (e: Exception) {
            error = "検索できませんでした。通信状況を確認してください。"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (initial.isBlank()) focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.background,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).fillMaxHeight(0.85f)) {
            val pill = RoundedCornerShape(50)
            Row(
                Modifier.fillMaxWidth().clip(pill).border(1.dp, c.outline, pill).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("アーティスト名", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                        cursorBrush = SolidColor(c.onSurface),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 24.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp), color = c.onSurface, trackColor = Color.Transparent)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.error, modifier = Modifier.padding(top = 8.dp)) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(results, key = { it.artistId!! }) { item ->
                    val id = item.artistId!!
                    val done = id in registered || id in added
                    PersonRow(item.artistName.orEmpty(), item.primaryGenreName) {
                        if (done) {
                            Text("登録済み", style = Meta, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                        } else PillButton("登録") {
                            added += id
                            val artist = Artist(
                                id = id,
                                name = item.artistName.orEmpty(),
                                genre = item.primaryGenreName,
                                url = item.artistLinkUrl?.substringBefore("?uo="),
                                aliases = listOf(initial).filter { it.isNotBlank() },
                            )
                            App.scope.launch {
                                Store.addArtist(artist)
                                runCatching { Checker.checkArtist(artist) }
                                runCatching { ReleaseWidget().updateAll(ctx) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 共通 ----------------

@Composable
private fun Empty(message: String, action: Pair<String, () -> Unit>?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

private fun open(ctx: android.content.Context, url: String?) {
    url ?: return
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

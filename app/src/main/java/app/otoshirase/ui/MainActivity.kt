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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { Root() }
        }
    }
}

private enum class Tab(val label: String) { Releases("新譜"), Artists("アーティスト"), Plays("よく聴く") }

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("オトシラセ", fontWeight = FontWeight.Bold)
                        Text(
                            if (checking) "チェック中…" else "最終チェック: ${agoLabel(state.lastCheck)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (checking) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { CheckWorker.runNow(ctx) }, enabled = !checking && state.artists.isNotEmpty()) {
                        Icon(Icons.Default.Refresh, "今すぐチェック")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.Releases -> Icons.Default.Notifications
                                    Tab.Artists -> Icons.Default.Person
                                    Tab.Plays -> Icons.Default.Favorite
                                }, null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Artists) {
                ExtendedFloatingActionButton(
                    onClick = { searchFor = "" },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("追加") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.Releases -> ReleasesScreen(state.releases, hasArtists = state.artists.isNotEmpty()) { tab = Tab.Artists; searchFor = "" }
                Tab.Artists -> ArtistsScreen(state.artists)
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

// ---------------- 新譜 ----------------

@Composable
private fun ReleasesScreen(releases: List<Release>, hasArtists: Boolean, goArtists: () -> Unit) {
    val ctx = LocalContext.current
    if (releases.isEmpty()) {
        Empty(
            if (hasArtists) "最近のリリースはまだありません。\n新しいリリースが出たら通知します。"
            else "まずはアーティストを登録しましょう。\n「よく聴く」タブから選ぶこともできます。",
            if (hasArtists) null else ("アーティストを追加" to goArtists),
        )
        return
    }
    val (upcoming, out) = releases.partition { it.upcoming }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (upcoming.isNotEmpty()) {
            item { Header("発売予定") }
            items(upcoming, key = { it.id }) { ReleaseCard(it) { open(ctx, it.url) } }
        }
        if (out.isNotEmpty()) {
            item { Header("リリース済み") }
            items(out, key = { it.id }) { ReleaseCard(it) { open(ctx, it.url) } }
        }
    }
}

@Composable
private fun Header(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
)

@Composable
private fun ReleaseCard(r: Release, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = r.artwork, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(r.artistName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(r.kind)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dateLabel(r), style = MaterialTheme.typography.labelMedium,
                        color = if (r.upcoming) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (r.notified) {
                        Spacer(Modifier.width(8.dp))
                        Text("NEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) = Surface(
    color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp),
) {
    Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
}

// ---------------- アーティスト ----------------

@Composable
private fun ArtistsScreen(artists: List<Artist>) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Artist?>(null) }
    if (artists.isEmpty()) {
        Empty("登録しているアーティストはいません。\n右下の「追加」から名前で検索できます。", null)
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(artists.sortedBy { it.name.lowercase() }, key = { it.id }) { a ->
            ListItem(
                headlineContent = { Text(a.name) },
                supportingContent = a.genre?.let { { Text(it) } },
                trailingContent = {
                    IconButton(onClick = { confirm = a }) { Icon(Icons.Default.Delete, "削除") }
                },
                modifier = Modifier.clickable { open(ctx, a.url) },
            )
            HorizontalDivider()
        }
    }
    confirm?.let { a ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${a.name} を削除") },
            text = { Text("通知の対象から外し、このアーティストのリリース一覧も消します。") },
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

// ---------------- よく聴く ----------------

@Composable
private fun PlaysScreen(plays: List<app.otoshirase.data.PlayStat>, onAdd: (String) -> Unit, onIgnore: (String) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(listenerGranted(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = listenerGranted(ctx) }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!granted) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("聴いているアーティストを自動で集める", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Apple Music や Spotify などで再生中の曲名・アーティスト名を読み取り、よく聴くアーティストをここに並べます。" +
                                "Android の「通知へのアクセス」をオンにしてください。データは端末の外には送りません。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) { Text("設定を開く") }
                    }
                }
            }
        }
        if (plays.isEmpty()) {
            item {
                Text(
                    if (granted) "音楽を再生すると、ここにアーティストが貯まっていきます。" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(plays, key = { it.name }) { p ->
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = { Text("${p.count}曲 · ${agoLabel(p.lastPlayed)}") },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onIgnore(p.name) }) { Icon(Icons.Default.Close, "非表示") }
                        FilledTonalButton(onClick = { onAdd(p.name) }) { Text("登録") }
                    }
                },
            )
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
    var query by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf<List<AppleCatalog.Item>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val added = remember { mutableStateListOf<Long>() }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        loading = true; error = null
        try {
            results = AppleCatalog.searchArtists(query.trim())
        } catch (e: CancellationException) {
            // 入力が変わって前の検索が打ち切られただけなので、エラーにしない
            throw e
        } catch (e: Exception) {
            error = "検索できませんでした（${e.message}）"
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxHeight(0.85f)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("アーティスト名") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Apple Music のカタログから検索します",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.artistId!! }) { item ->
                    val id = item.artistId!!
                    val done = id in registered || id in added
                    ListItem(
                        headlineContent = { Text(item.artistName.orEmpty()) },
                        supportingContent = item.primaryGenreName?.let { { Text(it) } },
                        trailingContent = {
                            if (done) Icon(Icons.Default.Check, "登録済み", tint = MaterialTheme.colorScheme.primary)
                            else FilledTonalButton(onClick = {
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
                            }) { Text("登録") }
                        },
                    )
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
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) { Text(label) }
        }
    }
}

private fun open(ctx: android.content.Context, url: String?) {
    url ?: return
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

package app.otoshirase.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
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
import app.otoshirase.data.AppState
import app.otoshirase.data.AppleArtistCatalog
import app.otoshirase.data.AppleCatalog
import app.otoshirase.data.PlayArtistResolver
import app.otoshirase.data.Artist
import app.otoshirase.data.ArtistImages
import app.otoshirase.data.PlayOrder
import app.otoshirase.data.playSections
import app.otoshirase.data.PlayStat
import app.otoshirase.data.Release
import app.otoshirase.data.ReleaseRules
import app.otoshirase.data.Store
import app.otoshirase.data.Suggestion
import app.otoshirase.data.SuggestionFrequency
import app.otoshirase.data.SuggestionSchedule
import app.otoshirase.notify.Notifier
import app.otoshirase.work.SuggestionWorker
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import app.otoshirase.widget.ReleaseWidget
import app.otoshirase.work.CheckWorker
import app.otoshirase.work.Checker
import androidx.glance.appwidget.updateAll
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 通知から開いたときは、その通知に関係する画面から始める
        val start = Tab.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TAB) } ?: Tab.Releases
        val openSuggestions = intent.getBooleanExtra(EXTRA_SUGGESTIONS, false)
        setContent { AppTheme { Root(start, openSuggestions, ::askNotifications) } }
    }

    // 起動直後にいきなり求めても理由が伝わらないので、通知が要る場面になってから求める
    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        private const val EXTRA_TAB = "tab"
        private const val EXTRA_SUGGESTIONS = "suggestions"

        /** おすすめの通知から開く。おすすめが見える検索シートを開いた状態で始める */
        fun suggestionsIntent(ctx: android.content.Context): Intent =
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_TAB, Tab.Artists.name)
                .putExtra(EXTRA_SUGGESTIONS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        fun playsIntent(ctx: android.content.Context): Intent =
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_TAB, Tab.Plays.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Releases(R.string.tab_releases, Icons.Default.Notifications),
    Artists(R.string.tab_artists, Icons.Default.Person),
    Plays(R.string.tab_plays, Icons.Default.Favorite),
}

@Composable
private fun Root(start: Tab, openSuggestions: Boolean, askNotifications: () -> Unit) {
    val ctx = LocalContext.current
    val state by Store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(start) }
    var searchFor by remember { mutableStateOf(if (openSuggestions) "" else null) }
    // 通知から開いたときは、キーボードでおすすめが隠れないよう検索欄に入らない
    var fromNotification by remember { mutableStateOf(openSuggestions) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 最初のタブ以外で戻ると、いきなりアプリが閉じて驚くので、まず最初のタブに戻す
    BackHandler(enabled = tab != Tab.Releases && searchFor == null) { tab = Tab.Releases }

    // 通知の許可は、初めてアーティストを登録したときと、よく聴くアーティストの集計を有効にしたときに求める
    var hadArtists by remember { mutableStateOf(state.artists.isNotEmpty()) }
    LaunchedEffect(state.artists.isNotEmpty()) {
        if (state.artists.isNotEmpty() && !hadArtists) askNotifications()
        hadArtists = state.artists.isNotEmpty()
    }
    var hadListener by remember { mutableStateOf(listenerGranted(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = listenerGranted(ctx)
        if (now && !hadListener) askNotifications()
        hadListener = now
    }

    val work by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkFlow(CheckWorker.NOW).collectAsState(initial = emptyList())
    val checking = work.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    // 失敗を知らせるのは、この画面で押した確認だけ（前に失敗した記録で毎回出さない）
    var pending by remember { mutableStateOf<java.util.UUID?>(null) }
    LaunchedEffect(work, pending) {
        val info = work.firstOrNull { it.id == pending } ?: return@LaunchedEffect
        if (!info.state.isFinished) return@LaunchedEffect
        pending = null
        if (info.state == WorkInfo.State.FAILED) snackbar.showSnackbar(ctx.getString(R.string.check_failed))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Header(
                title = stringResource(tab.label),
                subtitle = when (tab) {
                    Tab.Releases -> if (checking) stringResource(R.string.checking) else checkedLabel(ctx, state.lastCheck)
                    Tab.Artists -> pluralStringResource(R.plurals.artist_count, state.artists.size, state.artists.size)
                    Tab.Plays -> stringResource(R.string.plays_subtitle)
                },
            ) {
                when (tab) {
                    Tab.Releases -> CircleButton(Icons.Default.Refresh, stringResource(R.string.check_now), enabled = !checking && state.artists.isNotEmpty()) {
                        // 通信できないまま確認を始めると、つながるまで「確認中」のまま止まって見える
                        if (online(ctx)) pending = CheckWorker.runNow(ctx)
                        else scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(ctx.getString(R.string.offline))
                        }
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
                Tab.Plays -> PlaysScreen(state, onSearch = { searchFor = it }, onIgnore = { App.scope.launch { Store.ignorePlay(it) } })
            }
        }
    }

    searchFor?.let { initial ->
        SearchSheet(
            initial, state, autoFocus = !fromNotification, askNotifications = askNotifications,
            onDismiss = {
                searchFor = null
                fromNotification = false
                // シートで見たので、通知で知らせた印は外す
                App.scope.launch { Store.clearNewSuggestions() }
            },
        )
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
    // 文字を大きくしている端末では 3 つのラベルが横に収まらないので、選んでいるタブだけ文字を出す
    val compact = LocalDensity.current.fontScale > 1.15f
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
                    val label = stringResource(t.label)
                    if (compact && !selected) {
                        Icon(t.icon, label, Modifier.size(18.dp), tint = tint)
                    } else {
                        Icon(t.icon, null, Modifier.size(18.dp), tint = tint)
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, softWrap = false)
                    }
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
            items(upcoming, key = { it.id }) { ReleaseRow(it) { openRelease(ctx, it) } }
        }
        if (out.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.section_recent)) }
            items(out, key = { it.id }) { ReleaseRow(it) { openRelease(ctx, it) } }
        }
    }
}

private fun openRelease(ctx: android.content.Context, r: Release) {
    App.scope.launch { Store.markReleaseOpened(r.id, LocalDate.now()) }
    open(ctx, releaseIntent(ctx, r))
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
                if (ReleaseRules.isNew(r, LocalDate.now())) {
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
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** 頭文字の丸・名前・補足を並べた1行。アーティスト・よく聴く・検索結果で共通 */
@Composable
private fun PersonRow(name: String, detail: String?, image: String? = null, onClick: (() -> Unit)? = null, badge: String? = null, trailing: @Composable RowScope.() -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c.onSecondaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.secondaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = trailing)
    }
}

// ---------------- よく聴く ----------------

@Composable
private fun PlaysScreen(state: AppState, onSearch: (String) -> Unit, onIgnore: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // その場で登録した行は画面を離れるまで上の段に「登録済み」で残し、間違えたらすぐ戻せるようにする。
    // 押した行が下の段へ飛ぶと、何が起きたか分からない
    val added = remember { mutableStateMapOf<String, Long>() }
    val resolving = remember { mutableStateListOf<String>() }
    val (fresh, known) = state.playSections()
    val (justAdded, registered) = known.partition { it.name in added }
    val unregistered = (fresh + justAdded).sortedWith(PlayOrder)
    var granted by remember { mutableStateOf(listenerGranted(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = listenerGranted(ctx) }

    if (!granted) {
        // 設定画面を開く前に、手順と何を読むかをシートで見せる（Google Play の「目立つ開示」）。
        // 設定から戻っても許可されていなければシートを残し、手順を見返せるようにする
        var guide by rememberSaveable { mutableStateOf(false) }
        Empty(
            stringResource(R.string.plays_intro_body),
            stringResource(R.string.allow) to { guide = true },
            title = stringResource(R.string.plays_intro_title),
        )
        if (guide) AccessGuideSheet(onDismiss = { guide = false })
        return
    }
    if (unregistered.isEmpty() && registered.isEmpty()) {
        Empty(stringResource(R.string.plays_empty), null)
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        if (unregistered.isNotEmpty()) item(key = "h_unregistered") { SectionLabel(stringResource(R.string.plays_section_unregistered)) }
        items(unregistered, key = { "u" + it.name }) { p ->
            PersonRow(
                p.name, playDetail(p), image = artistImageByName(p.name),
                badge = if (p.name in added) null else stringResource(R.string.badge_new),
            ) {
                val id = added[p.name]
                when {
                    id != null -> {
                        Text(stringResource(R.string.added), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                        PillButton(stringResource(R.string.remove)) {
                            added -= p.name
                            App.scope.launch {
                                Store.removeArtist(id)
                                runCatching { ReleaseWidget().updateAll(ctx) }
                            }
                        }
                    }
                    p.name in resolving -> Box(Modifier.padding(horizontal = 20.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                    else -> {
                        IconButton(onClick = { onIgnore(p.name) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.hide), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PillButton(stringResource(R.string.add)) {
                            resolving += p.name
                            scope.launch {
                                val r = PlayArtistResolver(AppleArtistCatalog).resolve(p.name)
                                resolving -= p.name
                                when (r) {
                                    is PlayArtistResolver.Result.Found -> {
                                        added[p.name] = r.artist.id
                                        registerArtist(ctx, r.artist.id, r.artist.name, r.artist.genre, r.artist.url, p.name)
                                    }
                                    // 別名義で載っていることがあるので、候補から選んでもらう
                                    PlayArtistResolver.Result.NotFound -> onSearch(p.name)
                                    PlayArtistResolver.Result.Failed ->
                                        Toast.makeText(ctx, R.string.add_error, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
        if (registered.isNotEmpty()) item(key = "h_registered") { SectionLabel(stringResource(R.string.plays_section_registered)) }
        items(registered, key = { "r" + it.name }) { p ->
            PersonRow(p.name, playDetail(p), image = artistImageByName(p.name)) {}
        }
    }
}

@Composable
private fun playDetail(p: PlayStat): String {
    val ctx = LocalContext.current
    return "${pluralStringResource(R.plurals.play_count, p.count, p.count)} · ${agoLabel(ctx, p.lastPlayed)}"
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
private fun SearchSheet(initial: String, state: AppState, autoFocus: Boolean, askNotifications: () -> Unit, onDismiss: () -> Unit) {
    val registered = state.artists.map { it.id }.toSet()
    val suggestions = state.suggestions
    val dismissed = state.dismissedSuggestions.toSet()
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf<List<AppleCatalog.Item>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    // 打ち終わる前（検索前）に「見つかりません」を出さないため、最後に検索した語を覚える
    var searched by remember { mutableStateOf<String?>(null) }
    val added = remember { mutableStateListOf<Long>() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); error = false; return@LaunchedEffect }
        delay(400)
        loading = true; error = false
        try {
            results = AppleCatalog.searchArtists(query.trim())
            searched = query
        } catch (e: CancellationException) {
            // 入力が変わって前の検索が打ち切られただけなので、エラーにしない
            throw e
        } catch (e: Exception) {
            error = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (initial.isBlank() && autoFocus) focus.requestFocus() }

    val refresher = App.suggestions
    val suggesting = refresher?.refreshing?.collectAsState()?.value == true
    val suggestFailed = refresher?.failed?.collectAsState()?.value == true
    // 取り直し中は、見つかった分から順に出す（前の結果とは混ぜない）
    val partial = refresher?.partial?.collectAsState()?.value
    var choosingFrequency by remember { mutableStateOf(false) }
    // 設定から戻ったときに出し直すため、画面に戻るたびに確かめる
    var notifyAllowed by remember { mutableStateOf(Notifier.suggestionsAllowed(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notifyAllowed = Notifier.suggestionsAllowed(ctx) }
    val frequency = state.suggestionFrequency

    val focusManager = LocalFocusManager.current
    fun add(id: Long, name: String, genre: String?, url: String?, alias: String?) {
        // 一覧が入れ替わる拍子に検索欄へフォーカスが移り、キーボードが出てしまうのを防ぐ
        focusManager.clearFocus()
        added += id
        registerArtist(ctx, id, name, genre, url, alias)
    }

    val snackbar = remember { SnackbarHostState() }
    val sheetScope = rememberCoroutineScope()
    // 押した瞬間に消えるので、押し間違えたときにその場で戻せるようにする。短いと押す前に消えてしまう
    fun dismiss(sg: Suggestion) {
        val index = suggestions.indexOfFirst { it.artistId == sg.artistId }
        App.scope.launch { Store.dismissSuggestion(sg.artistId) }
        sheetScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(ctx.getString(R.string.suggestion_dismissed), ctx.getString(R.string.undo), duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) App.scope.launch { Store.restoreSuggestion(sg, index) }
        }
    }

    fun remove(id: Long) {
        focusManager.clearFocus()
        added -= id
        App.scope.launch {
            Store.removeArtist(id)
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
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Close, stringResource(R.string.search_clear),
                        Modifier.size(20.dp).clip(RoundedCornerShape(50)).clickable { query = ""; focus.requestFocus() },
                        tint = c.onSurfaceVariant,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 24.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp), color = c.primary, trackColor = Color.Transparent)
            }
            if (error) Text(stringResource(R.string.search_error), style = MaterialTheme.typography.bodySmall, color = c.error, modifier = Modifier.padding(top = 8.dp))
            // Most Played から名前が一致せずに来たときに、空白のままだと何が起きたか分からない
            else if (!loading && query.isNotBlank() && results.isEmpty() && searched == query) Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (query.isBlank() && refresher != null) {
                    // 取り直し中の一覧は保存前なので、興味なしにしたものもここで除く
                    val shown = SuggestionSchedule.ordered(
                        (partial ?: suggestions).filterNot { it.artistId in registered || it.artistId in added || it.artistId in dismissed },
                        state.newSuggestions.toSet(),
                    )
                    item(key = "suggestions_header") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SectionLabel(stringResource(R.string.suggestions)) }
                            SuggestionBell(frequency, notifyAllowed) { choosingFrequency = true }
                            // 取り直し中も同じ場所に残して押せなくする。消えると、下に別のボタンが急に出たように見える
                            if (shown.isNotEmpty() || suggesting) IconButton(
                                onClick = { App.scope.launch { refresher.refresh() } },
                                enabled = !suggesting,
                            ) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.suggestions_refresh), Modifier.size(20.dp))
                            }
                        }
                    }
                    if (frequency != SuggestionFrequency.Off) item(key = "suggestions_status") {
                        if (notifyAllowed) Text(
                            stringResource(frequency.statusText()), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) else NotificationsBlocked { openNotificationSettings(ctx) }
                    }
                    // 「おすすめを探す」は、まだ取っていないときと、興味なしで全部外したときだけ。
                    // 取り直し中は一覧が空になるが、そこで出すと押した直後に急に現れて見える
                    if (shown.isEmpty() && !suggesting) item(key = "suggestions_empty") {
                        val note = when {
                            registered.isEmpty() -> R.string.suggestions_need_artists
                            suggestFailed -> R.string.suggestions_error
                            else -> R.string.suggestions_hint
                        }
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                            // 登録が無いうちは押しても何もできないので、ボタン自体を出さない
                            if (registered.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                FilledTonalButton(onClick = { App.scope.launch { refresher.refresh() } }) {
                                    Icon(painterResource(R.drawable.ic_sparkle), null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.suggestions_find))
                                }
                            }
                        }
                    }
                    val newIds = state.newSuggestions.toSet()
                    items(shown, key = { "s" + it.artistId }) { sg ->
                        PersonRow(
                            sg.name, sg.genre, image = artistImageById(sg.artistId),
                            badge = if (sg.artistId in newIds) stringResource(R.string.suggestion_new) else null,
                        ) {
                            IconButton(onClick = { dismiss(sg) }) {
                                Icon(Icons.Default.Close, stringResource(R.string.not_interested), Modifier.size(18.dp), tint = c.onSurfaceVariant)
                            }
                            PillButton(stringResource(R.string.add)) { add(sg.artistId, sg.name, sg.genre, sg.url, null) }
                        }
                    }
                    // 一度おすすめを見てから案内する。何が届くのか分かってから選べるように
                    if (!state.suggestionInviteDone && frequency == SuggestionFrequency.Off && shown.isNotEmpty() && !suggesting) {
                        item(key = "suggestions_invite") {
                            SuggestionInvite(
                                onLater = { App.scope.launch { Store.closeSuggestionInvite() } },
                                onChoose = { choosingFrequency = true },
                            )
                        }
                    }
                    if (suggesting) item(key = "suggesting") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = c.primary)
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
                            // 間違えて登録してもその場で戻せるようにする。登録し直せばすぐ戻るので確認は出さない
                            PillButton(stringResource(R.string.remove)) { remove(id) }
                        } else PillButton(stringResource(R.string.add)) {
                            add(id, item.artistName.orEmpty(), item.primaryGenreName, item.artistLinkUrl?.substringBefore("?uo="), initial)
                        }
                    }
                }
            }
            SnackbarHost(snackbar)
        }
    }

    if (choosingFrequency) FrequencyDialog(frequency, onDismiss = { choosingFrequency = false }) { picked ->
        choosingFrequency = false
        App.scope.launch { Store.setSuggestionFrequency(picked) }
        SuggestionWorker.schedule(ctx, picked, changed = true)
        // 通知が要る場面になったので、ここで許可を求める
        if (picked != SuggestionFrequency.Off) askNotifications()
    }
}

private fun SuggestionFrequency.statusText(): Int = when (this) {
    SuggestionFrequency.Daily -> R.string.suggest_status_daily
    SuggestionFrequency.Every3Days -> R.string.suggest_status_3days
    else -> R.string.suggest_status_weekly
}

private fun SuggestionFrequency.label(): Int = when (this) {
    SuggestionFrequency.Off -> R.string.suggest_freq_off
    SuggestionFrequency.Daily -> R.string.suggest_freq_daily
    SuggestionFrequency.Every3Days -> R.string.suggest_freq_3days
    SuggestionFrequency.Weekly -> R.string.suggest_freq_weekly
}

/** オフは輪郭だけ、オンは塗りつぶし、選んだのに通知が届かないときは斜線 */
@Composable
private fun SuggestionBell(frequency: SuggestionFrequency, allowed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val on = frequency != SuggestionFrequency.Off
    IconButton(
        onClick = onClick,
        colors = if (on && allowed) IconButtonDefaults.iconButtonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer)
        else IconButtonDefaults.iconButtonColors(),
    ) {
        val label = stringResource(R.string.suggest_bell)
        when {
            !on -> Icon(painterResource(R.drawable.ic_bell_outline), label, Modifier.size(20.dp))
            allowed -> Icon(Icons.Default.Notifications, label, Modifier.size(20.dp))
            else -> Icon(painterResource(R.drawable.ic_bell_off), label, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NotificationsBlocked(onOpenSettings: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.errorContainer)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.suggest_blocked), Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = c.onErrorContainer)
        TextButton(onClick = onOpenSettings, colors = ButtonDefaults.textButtonColors(contentColor = c.onErrorContainer)) {
            Text(stringResource(R.string.open_settings))
        }
    }
}

@Composable
private fun SuggestionInvite(onLater: () -> Unit, onChoose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainer).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_bell_outline), null, Modifier.size(20.dp), tint = c.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.suggest_invite_title), style = MaterialTheme.typography.titleSmall, color = c.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.suggest_invite_body), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onLater) { Text(stringResource(R.string.suggest_invite_later)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onChoose) { Text(stringResource(R.string.suggest_invite_choose)) }
        }
    }
}

@Composable
private fun FrequencyDialog(current: SuggestionFrequency, onDismiss: () -> Unit, onConfirm: (SuggestionFrequency) -> Unit) {
    // 利用者が選んでいない頻度を先に選んでおかない。今の設定（最初はオフ）のまま開く
    var picked by remember { mutableStateOf(current) }
    val c = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.suggest_freq_title)) },
        text = {
            Column {
                Text(stringResource(R.string.suggest_freq_body))
                Spacer(Modifier.height(8.dp))
                SuggestionFrequency.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(selected = picked == f, onClick = { picked = f }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picked == f, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(f.label()), style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.suggest_freq_note), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** このアプリの通知の設定画面。チャンネルだけオフのときも、ここから入れる */
private fun openNotificationSettings(ctx: android.content.Context) {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    runCatching { ctx.startActivity(i) }
}

// ---------------- 共通 ----------------

/** 登録して、その人の新譜をすぐ取りに行く。画面を閉じても続くようにアプリのスコープで行う */
private fun registerArtist(ctx: android.content.Context, id: Long, name: String, genre: String?, url: String?, alias: String?) {
    val artist = Artist(
        id = id,
        name = name,
        genre = genre,
        url = url,
        aliases = listOfNotNull(alias).filter { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
        // 検索結果で取れていれば、登録時に取り直さない
        image = ArtistImages.peek(id),
        imageCheckedAt = if (ArtistImages.peek(id) != null) System.currentTimeMillis() else 0,
    )
    App.scope.launch {
        Store.addArtist(artist)
        runCatching { Checker.checkArtist(artist) }
        runCatching { ReleaseWidget().updateAll(ctx) }
    }
}

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

private fun online(ctx: android.content.Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun open(ctx: android.content.Context, intent: Intent?) {
    intent ?: return
    runCatching { ctx.startActivity(intent) }
}

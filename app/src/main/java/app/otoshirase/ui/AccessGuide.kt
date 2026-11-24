package app.otoshirase.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.annotation.StringRes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otoshirase.R
import app.otoshirase.playback.NowPlayingService

/** 設定のどこを押せばよいかを示す縁取り。アイコンのきらめきと同じ橙 */
private val Hint = Color(0xFFF9A602)

/** 設定画面の図は幅 411dp（1080px の端末）のつもりで描き、枠の幅に合わせて縮める */
private val SettingsWidth = 411.dp

/**
 * 「通知へのアクセス」を有効にする手順のシート。
 * 設定画面を開く前に、何を読むかをここで見せる（Google Play の「目立つ開示」）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessGuideSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val pager = rememberPagerState { 3 }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.guide_subtitle), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            HorizontalPager(pager, pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { page ->
                GuideStep(page)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 16.dp else 6.dp)
                            .clip(CircleShape).background(if (on) c.primary else c.outlineVariant),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = c.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.guide_privacy_heading), style = MaterialTheme.typography.titleSmall, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.listener_disclosure_body), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { openListenerSettings(ctx) }) { Text(stringResource(R.string.guide_open_settings)) }
            }
        }
    }
}

/** 一覧から探さずに済むよう、Android 11 以降はこのアプリの画面を直接開く */
private fun openListenerSettings(ctx: Context) {
    val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    val detail = if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(ctx, NowPlayingService::class.java).flattenToString(),
        )
    } else list
    runCatching { ctx.startActivity(detail) }.onFailure { runCatching { ctx.startActivity(list) } }
}

@Composable
private fun GuideStep(page: Int) {
    val c = MaterialTheme.colorScheme
    val (title, body) = when (page) {
        0 -> R.string.guide_step1_title to R.string.guide_step1_body
        1 -> R.string.guide_step2_title to R.string.guide_step2_body
        else -> R.string.guide_step3_title to R.string.guide_step3_body
    }
    Column {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHighest).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 見せる高さをそろえるため、縦に長いダイアログの図は幅を狭くする
            val frame = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(10.dp))
            when (page) {
                0 -> Box(frame.fillMaxWidth(0.62f)) { Scaled(600.dp) { DetailScreen(hintSwitch = true) } }
                1 -> Box(frame.fillMaxWidth(0.62f * 600 / 700)) {
                    Scaled(700.dp, offset = 105.dp) {
                        DetailScreen(hintSwitch = false)
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
                        AllowDialog(Modifier.padding(start = 26.dp, end = 27.dp, top = 119.dp))
                    }
                }
                else -> Box(frame.fillMaxWidth(0.62f)) { Scaled(600.dp) { PlaysScreenSketch() } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.guide_step_counter, page + 1, 3), style = MaterialTheme.typography.labelMedium, color = c.primary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

/**
 * 端末の画面の大きさで描いたものを、枠の幅に合わせて縮めて見せる。
 * [height] は見せる範囲の高さ、[offset] は上から切り捨てる量（どちらも端末の画面での dp）
 */
@Composable
private fun Scaled(height: Dp, offset: Dp = 0.dp, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = maxWidth / SettingsWidth
        Box(Modifier.fillMaxWidth().height(height * scale).clipToBounds()) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationY = -offset.toPx() * scale
                    }
                    .requiredWidth(SettingsWidth).requiredHeight(height + offset),
                content = content,
            )
        }
    }
}

/**
 * 設定の色。Android 17 の設定は壁紙の色から作られるので、アプリと同じ dynamic color の役割を当てる
 * （エミュレーターで撮った画面と見比べて選んだ）
 */
private class SettingsColors(val page: Color, val card: Color, val back: Color, val dialog: Color)

@Composable
private fun settingsColors(): SettingsColors {
    val c = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) SettingsColors(c.surface, c.surfaceContainerHigh, c.surfaceContainerHigh, c.surfaceContainerHigh)
    else SettingsColors(c.surfaceContainer, c.surfaceBright, c.surfaceContainerHighest, c.surfaceContainerHigh)
}

/** 設定 > 通知の確認、返信、管理 > New Releases の画面 */
@Composable
private fun DetailScreen(hintSwitch: Boolean) {
    val c = MaterialTheme.colorScheme
    val s = settingsColors()
    Column(Modifier.fillMaxSize().background(s.page)) {
        Row(Modifier.padding(start = 24.dp, top = 32.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(s.back), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(24.dp), tint = c.onSurface)
            }
            Spacer(Modifier.width(20.dp))
            Text(stringResource(R.string.guide_sys_listener_title), fontSize = 26.sp, lineHeight = 32.sp, color = c.onSurface)
        }
        Spacer(Modifier.height(36.dp))
        AppIcon(Modifier.align(Alignment.CenterHorizontally), 69.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.app_name), fontSize = 22.sp, color = c.onSurface, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(stringResource(R.string.listener_label), fontSize = 16.sp, color = c.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(s.card).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.guide_sys_allow_access), fontSize = 18.sp, color = c.onSurface, modifier = Modifier.weight(1f))
            Box(if (hintSwitch) Modifier.border(3.dp, Hint, CircleShape).padding(5.dp) else Modifier.padding(5.dp)) {
                Switch(
                    checked = false, onCheckedChange = null,
                    thumbContent = { Icon(Icons.Default.Close, null, Modifier.size(SwitchDefaults.IconSize)) },
                )
            }
        }
        GreyCard(rememberVectorPainter(SyncIcon), R.string.guide_sys_realtime, R.string.guide_sys_realtime_summary, s.card)
        GreyCard(rememberVectorPainter(ChatIcon), R.string.guide_sys_conversations, R.string.guide_sys_conversations_summary, s.card)
        GreyCard(painterResource(R.drawable.ic_bell_outline), R.string.guide_sys_alerting, R.string.guide_sys_alerting_summary, s.card)
        GreyCard(painterResource(R.drawable.ic_bell_off), R.string.guide_sys_silent, R.string.guide_sys_silent_summary, s.card)
    }
}

/** 通知を受け取ったときの種類。アクセスを許可するまでは押せない（灰色）ので、そのまま描く */
@Composable
private fun GreyCard(icon: Painter, @StringRes title: Int, @StringRes summary: Int, card: Color) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Spacer(Modifier.height(2.dp))
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(card)
            .padding(start = 24.dp, end = 26.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = dim)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 18.sp, color = dim)
            Text(stringResource(summary), fontSize = 16.sp, lineHeight = 22.sp, color = dim)
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(dim), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = card)
        }
    }
}

/** トグルスイッチを有効にすると出る確認ダイアログ */
@Composable
private fun AllowDialog(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val s = settingsColors()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(s.dialog).padding(horizontal = 24.dp, vertical = 16.dp)) {
        AppIcon(Modifier.align(Alignment.CenterHorizontally), 36.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.guide_sys_dialog_title), fontSize = 22.sp, lineHeight = 28.sp, color = c.onSurface,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.guide_sys_dialog_intro), fontSize = 16.sp, lineHeight = 24.sp, color = c.onSurface)
        listOf(
            Triple(EyeIcon, R.string.guide_sys_read, R.string.guide_sys_read_summary),
            Triple(ChatIcon, R.string.guide_sys_reply, R.string.guide_sys_reply_summary),
            Triple(MinusCircleIcon, R.string.guide_sys_settings, R.string.guide_sys_settings_summary),
        ).forEach { (icon, title, summary) ->
            Spacer(Modifier.height(20.dp))
            Row {
                Icon(icon, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(title), fontSize = 16.sp, color = c.onSurface)
                    Text(stringResource(summary), fontSize = 14.sp, lineHeight = 20.sp, color = c.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {}, modifier = Modifier.fillMaxWidth().height(40.dp),
            border = BorderStroke(3.dp, Hint),
        ) { Text(stringResource(R.string.guide_sys_allow), fontSize = 16.sp) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text(stringResource(R.string.guide_sys_deny), fontSize = 16.sp)
        }
    }
}

/** 戻ったあとの Most Played の様子。アーティストは架空 */
@Composable
private fun PlaysScreenSketch() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.surface).padding(top = 32.dp)) {
        Text(
            stringResource(R.string.tab_plays), fontSize = 22.sp, color = c.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        listOf(R.string.guide_demo_artist_1 to 42, R.string.guide_demo_artist_2 to 27, R.string.guide_demo_artist_3 to 15).forEach { (name, count) ->
            val n = stringResource(name)
            Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Monogram(n)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(n, fontSize = 16.sp, color = c.onSurface)
                    Text(pluralStringResource(R.plurals.play_count, count, count), fontSize = 14.sp, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

/** ランチャーと同じ形（紺の丸にレコード）。アダプティブアイコンは painterResource で読めないので重ねて描く */
@Composable
private fun AppIcon(modifier: Modifier, size: Dp) {
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_bg), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.requiredSize(size * 1.5f))
    }
}

// 設定画面と同じ形にするため、Material Icons にある形を描く（icons-core に含まれていないもの）
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

private val SyncIcon = icon(
    "sync",
    "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z",
)
private val EyeIcon = icon(
    "visibility",
    "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
)
private val ChatIcon = icon(
    "chat",
    "M20,2H4c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H5.17L4,17.17V4h16v12zM6,12h8v2H6zM6,9h12v2H6zM6,6h12v2H6z",
)
private val MinusCircleIcon = icon(
    "do_not_disturb_on",
    "M7,11v2h10v-2H7zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z",
)

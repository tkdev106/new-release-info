package app.otoshirase.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.currentState
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.otoshirase.R
import app.otoshirase.data.Artwork
import app.otoshirase.data.Release
import app.otoshirase.data.Store
import app.otoshirase.ui.MainActivity
import app.otoshirase.ui.releaseIntent
import app.otoshirase.ui.shortDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** ジャケット画像を新しい順に横に並べる。並ぶ数はウィジェットの幅で決まる */
class ReleaseWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        // 最初の描画で画像が抜けないよう、今ある分は先に読んでおく
        val initialArt = loadArt(Store.state.value.releases.take(MAX_ITEMS))
        provideContent {
            // 表示中のウィジェットは provideGlance が呼び直されないので、保存データの変化をここで拾う
            val state by Store.state.collectAsState()
            val releases = state.releases.take(MAX_ITEMS)
            val art by produceState(initialArt, releases.map { it.id }) {
                if (releases.any { it.id !in value }) value = loadArt(releases)
            }
            val style = WidgetStyle.of(currentState(WidgetStyle.KEY))
            GlanceTheme { Content(context, releases, art, palette(style)) }
        }
    }

    // 横に並ぶのは多くても 6 枚程度。画像はその分だけ読む
    private suspend fun loadArt(releases: List<Release>): Map<Long, Bitmap?> = coroutineScope {
        releases.map { r -> async { r.id to r.artwork?.let { Artwork.load(it, 240) } } }.awaitAll().toMap()
    }

    /** 背景の種類ごとの色。透過では壁紙の上に文字が直接乗る */
    private class Palette(
        val background: ColorProvider, val title: ColorProvider, val text: ColorProvider,
        val sub: ColorProvider, val tile: ColorProvider,
        /** 透過のときの文字色と影の色。壁紙の明るい所・暗い所でも読めるよう、文字に影を付けて画像で描く */
        val clear: ClearText? = null,
    )

    private class ClearText(val text: Int, val sub: Int, val shadow: Int)

    @Composable
    private fun palette(style: WidgetStyle): Palette {
        val c = GlanceTheme.colors
        return when (style) {
            WidgetStyle.Standard -> Palette(c.widgetBackground, c.primary, c.onSurface, c.onSurfaceVariant, c.surfaceVariant)
            WidgetStyle.ClearWhite -> Palette(
                ColorProvider(Color.Transparent), ColorProvider(Color.White), ColorProvider(Color.White),
                ColorProvider(Color.White.copy(alpha = 0.8f)), ColorProvider(Color.White.copy(alpha = 0.18f)),
                ClearText(0xFFFFFFFF.toInt(), 0xE6FFFFFF.toInt(), 0x99000000.toInt()),
            )
            WidgetStyle.ClearBlack -> Palette(
                ColorProvider(Color.Transparent), ColorProvider(Color(0xFF1B1B1F)), ColorProvider(Color(0xFF1B1B1F)),
                ColorProvider(Color(0xCC1B1B1F)), ColorProvider(Color(0x2E1B1B1F)),
                ClearText(0xFF1B1B1F.toInt(), 0xE61B1B1F.toInt(), 0x99FFFFFF.toInt()),
            )
        }
    }

    @Composable
    private fun Content(context: Context, releases: List<Release>, art: Map<Long, Bitmap?>, p: Palette) {
        val size = LocalSize.current
        val openApp = actionStartActivity<MainActivity>()
        Column(GlanceModifier.fillMaxSize().background(p.background).cornerRadius(24.dp).padding(PAD.dp)) {
            Label(
                context, context.getString(R.string.widget_label), 14f, bold = true,
                color = p.title, clearColor = p.clear?.text, p = p,
                widthDp = (size.width.value - PAD * 2).toInt(), modifier = GlanceModifier.clickable(openApp),
            )
            Spacer(GlanceModifier.height(GAP.dp))
            if (releases.isEmpty()) {
                Text(
                    context.getString(R.string.widget_empty),
                    style = TextStyle(color = p.sub, fontSize = 12.sp),
                    modifier = GlanceModifier.clickable(openApp),
                )
                return@Column
            }
            val layout = layoutFor(size)
            Row(GlanceModifier.fillMaxWidth()) {
                releases.take(layout.count).forEachIndexed { i, r ->
                    if (i > 0) Spacer(GlanceModifier.width(GAP.dp))
                    Item(context, r, art[r.id], layout.image, layout.showText, p)
                }
            }
        }
    }

    @Composable
    private fun Item(context: Context, r: Release, bitmap: Bitmap?, imageDp: Int, showText: Boolean, p: Palette) {
        val open = releaseIntent(context, r)
        Column(
            GlanceModifier.width(imageDp.dp).let { m -> if (open != null) m.clickable(actionStartActivity(open)) else m },
        ) {
            Box(
                GlanceModifier.size(imageDp.dp).cornerRadius(12.dp).background(p.tile),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        ImageProvider(bitmap), contentDescription = r.title, contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.size(imageDp.dp).cornerRadius(12.dp),
                    )
                }
            }
            if (!showText) return@Column
            Spacer(GlanceModifier.height(6.dp))
            Label(context, r.title, 12f, bold = true, color = p.text, clearColor = p.clear?.text, p = p, widthDp = imageDp)
            // ジャケットと作品名だけでは誰の作品か分からない
            Label(context, r.artistName, 11f, bold = false, color = p.sub, clearColor = p.clear?.sub, p = p, widthDp = imageDp)
            // 発売予定かどうかは日付で分かるので、発売済みと見た目を分けない
            Label(context, shortDate(context, r), 11f, bold = false, color = p.sub, clearColor = p.clear?.sub, p = p, widthDp = imageDp)
        }
    }

    /** 1 行の文字。透過のときだけ影付きの画像で描く（Glance の文字には影を付けられないため） */
    @Composable
    private fun Label(
        context: Context, text: String, sp: Float, bold: Boolean, color: ColorProvider, clearColor: Int?, p: Palette,
        widthDp: Int, modifier: GlanceModifier = GlanceModifier,
    ) {
        val clear = p.clear
        if (clear == null || clearColor == null) {
            Text(
                text, maxLines = 1, modifier = modifier,
                style = TextStyle(color = color, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, fontSize = sp.sp),
            )
            return
        }
        val bitmap = ShadowText.render(context, text, sp, clearColor, clear.shadow, bold, widthDp)
        val density = context.resources.displayMetrics.density
        Image(
            ImageProvider(bitmap), contentDescription = text, contentScale = ContentScale.Fit,
            modifier = modifier.size(widthDp.dp, (bitmap.height / density).dp),
        )
    }

    private data class Layout(val count: Int, val image: Int, val showText: Boolean)

    /**
     * 高さから画像の大きさを、幅から並べる数を決める。
     * 文字の 3 行まで入らない低いウィジェットでは文字を出さず、ジャケットを残りの高さに収める
     * （無理に入れると、はみ出してジャケットの下が切れる）
     */
    private fun layoutFor(size: DpSize): Layout {
        val w = size.width.value - PAD * 2
        val room = size.height.value - PAD * 2 - HEADER - GAP
        val showText = room - TEXT >= MIN_IMAGE
        val base = (if (showText) room - TEXT else room).coerceIn(24f, 104f)
        val count = ((w + GAP) / (base + GAP)).toInt().coerceIn(1, MAX_ITEMS)
        val image = ((w - GAP * (count - 1)) / count).coerceAtMost(base)
        return Layout(count, image.toInt(), showText)
    }

    private companion object {
        const val MAX_ITEMS = 6
        const val MIN_IMAGE = 48f
        const val PAD = 14
        const val GAP = 10
        const val HEADER = 18
        // 作品名・アーティスト名・日付の 3 行
        const val TEXT = 56
    }
}

class ReleaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReleaseWidget()
}

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
        val sub: ColorProvider, val accent: ColorProvider, val tile: ColorProvider,
    )

    @Composable
    private fun palette(style: WidgetStyle): Palette {
        val c = GlanceTheme.colors
        return when (style) {
            WidgetStyle.Standard -> Palette(c.widgetBackground, c.primary, c.onSurface, c.onSurfaceVariant, c.primary, c.surfaceVariant)
            WidgetStyle.ClearWhite -> Palette(
                ColorProvider(Color.Transparent), ColorProvider(Color.White), ColorProvider(Color.White),
                ColorProvider(Color.White.copy(alpha = 0.8f)), ColorProvider(Color.White), ColorProvider(Color.White.copy(alpha = 0.18f)),
            )
            WidgetStyle.ClearBlack -> Palette(
                ColorProvider(Color.Transparent), ColorProvider(Color(0xFF1B1B1F)), ColorProvider(Color(0xFF1B1B1F)),
                ColorProvider(Color(0xCC1B1B1F)), ColorProvider(Color(0xFF1B1B1F)), ColorProvider(Color(0x2E1B1B1F)),
            )
        }
    }

    @Composable
    private fun Content(context: Context, releases: List<Release>, art: Map<Long, Bitmap?>, p: Palette) {
        val size = LocalSize.current
        val openApp = actionStartActivity<MainActivity>()
        Column(GlanceModifier.fillMaxSize().background(p.background).cornerRadius(24.dp).padding(PAD.dp)) {
            Text(
                context.getString(R.string.widget_label),
                style = TextStyle(color = p.title, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                modifier = GlanceModifier.clickable(openApp),
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
                    Item(context, r, art[r.id], layout.image, p)
                }
            }
        }
    }

    @Composable
    private fun Item(context: Context, r: Release, bitmap: Bitmap?, imageDp: Int, p: Palette) {
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
            Spacer(GlanceModifier.height(6.dp))
            Text(r.title, maxLines = 1, style = TextStyle(color = p.text, fontWeight = FontWeight.Medium, fontSize = 12.sp))
            Text(
                if (r.upcoming) context.getString(R.string.widget_upcoming, shortDate(context, r)) else shortDate(context, r),
                maxLines = 1,
                style = TextStyle(color = if (r.upcoming) p.accent else p.sub, fontSize = 11.sp),
            )
        }
    }

    private data class Layout(val count: Int, val image: Int)

    /** 高さから画像の大きさを、幅から並べる数を決める */
    private fun layoutFor(size: DpSize): Layout {
        val w = size.width.value - PAD * 2
        val h = size.height.value - PAD * 2 - HEADER - GAP - TEXT
        val base = h.coerceIn(48f, 104f)
        val count = ((w + GAP) / (base + GAP)).toInt().coerceIn(1, MAX_ITEMS)
        val image = ((w - GAP * (count - 1)) / count).coerceAtMost(base)
        return Layout(count, image.toInt())
    }

    private companion object {
        const val MAX_ITEMS = 6
        const val PAD = 14
        const val GAP = 10
        const val HEADER = 18
        const val TEXT = 40
    }
}

class ReleaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReleaseWidget()
}

package app.otoshirase.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.otoshirase.data.Release
import app.otoshirase.data.Store
import app.otoshirase.ui.MainActivity
import app.otoshirase.ui.DarkScheme
import app.otoshirase.ui.LightScheme
import app.otoshirase.ui.dateLabel
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontFamily

class ReleaseWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val releases = Store.state.value.releases.take(10)
        provideContent { GlanceTheme(colors = ColorProviders(light = LightScheme, dark = DarkScheme)) { Content(releases) } }
    }

    @Composable
    private fun Content(releases: List<Release>) {
        val c = GlanceTheme.colors
        Column(
            GlanceModifier.fillMaxSize().background(c.background).cornerRadius(20.dp).padding(14.dp)
        ) {
            Text(
                "新譜",
                style = TextStyle(color = c.onSurface, fontFamily = FontFamily.Serif, fontSize = 20.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (releases.isEmpty()) {
                Text(
                    "アーティストを登録すると、新譜がここに出ます",
                    style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                )
            } else {
                LazyColumn {
                    items(releases, itemId = { it.id }) { r ->
                        val open = r.url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
                        Column(
                            GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).let { m ->
                                if (open != null) m.clickable(actionStartActivity(open)) else m
                            }
                        ) {
                            Text(
                                r.title, maxLines = 1,
                                style = TextStyle(color = c.onSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                            )
                            Text(
                                "${r.artistName} · ${r.kind} · ${dateLabel(r)}", maxLines = 1,
                                style = TextStyle(color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

class ReleaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReleaseWidget()
}

package app.otoshirase.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.otoshirase.R
import app.otoshirase.ui.AppTheme
import app.otoshirase.ui.PillButton
import kotlinx.coroutines.launch

/** ウィジェットの設定（背景の種類）。ホーム画面でウィジェットを長押し →「設定」から開く */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // 戻るで閉じたときは何も変えない
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        enableEdgeToEdge()

        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        lifecycleScope.launch {
            val current = WidgetStyle.of(getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)[WidgetStyle.KEY])
            setContent {
                AppTheme {
                    ConfigScreen(current) { chosen ->
                        lifecycleScope.launch {
                            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[WidgetStyle.KEY] = chosen.name }
                            ReleaseWidget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(initial: WidgetStyle, onDone: (WidgetStyle) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(c.background).systemBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(stringResource(R.string.widget_settings_title), style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
        Spacer(Modifier.height(24.dp))
        WidgetStyle.entries.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { selected = s }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == s, onClick = { selected = s })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(s.label), style = MaterialTheme.typography.bodyLarge, color = c.onSurface, modifier = Modifier.weight(1f))
                StylePreview(s)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            PillButton(stringResource(R.string.done), filled = true) { onDone(selected) }
        }
    }
}

/** 壁紙に見立てた背景の上に、ウィジェットの縮小版を置いた見本 */
@Composable
private fun StylePreview(style: WidgetStyle) {
    val c = MaterialTheme.colorScheme
    val wallpaper = when (style) {
        WidgetStyle.ClearBlack -> Brush.linearGradient(listOf(Color(0xFFF3E7DA), Color(0xFFD9E4F5)))
        else -> Brush.linearGradient(listOf(Color(0xFF3B4A7A), Color(0xFF8A5A7A)))
    }
    val (bg, fg) = when (style) {
        WidgetStyle.Standard -> c.surfaceContainerHigh to c.onSurface
        WidgetStyle.ClearWhite -> Color.Transparent to Color.White
        WidgetStyle.ClearBlack -> Color.Transparent to Color(0xFF1B1B1F)
    }
    Box(Modifier.size(width = 120.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)).background(wallpaper).padding(8.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(bg).padding(6.dp)) {
            Box(Modifier.size(width = 28.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(fg))
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(fg.copy(alpha = 0.35f)).border(0.dp, Color.Transparent))
                }
            }
        }
    }
}

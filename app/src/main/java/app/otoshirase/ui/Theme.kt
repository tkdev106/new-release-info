package app.otoshirase.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** 色は端末の壁紙から取る（Android 12 以降）。それより前の端末では青系の既定色にする */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F), secondaryContainer = Color(0xFF2B3A55))
        else -> lightColorScheme(primary = Color(0xFF0B57D0), onPrimary = Color.White, secondaryContainer = Color(0xFFD3E3FD))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun CircleButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(c.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = if (enabled) c.onSurface else c.onSurface.copy(alpha = 0.38f))
    }
}

@Composable
fun PillButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (filled) c.primary else Color.Transparent,
        contentColor = if (filled) c.onPrimary else c.primary,
        border = if (filled) null else BorderStroke(1.dp, c.outlineVariant),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** アーティストの丸いアイコン。画像があれば画像、無ければ頭文字（画像の読み込み中も頭文字が下に見える） */
@Composable
fun Monogram(name: String, image: String? = null) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase(), fontSize = 16.sp, color = c.onPrimaryContainer)
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

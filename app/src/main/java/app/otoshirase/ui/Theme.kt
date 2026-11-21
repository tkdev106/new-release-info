package app.otoshirase.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// 端末の壁紙色（dynamic color）は使わない。どの端末でも同じモノクロの見た目にするため
val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF141413), onPrimary = Color(0xFFF4F3EF),
    primaryContainer = Color(0xFFE8E6E0), onPrimaryContainer = Color(0xFF141413),
    secondary = Color(0xFF141413), onSecondary = Color(0xFFF4F3EF),
    secondaryContainer = Color(0xFFE8E6E0), onSecondaryContainer = Color(0xFF141413),
    tertiary = Color(0xFF141413), onTertiary = Color(0xFFF4F3EF),
    background = Color(0xFFF4F3EF), onBackground = Color(0xFF141413),
    surface = Color(0xFFF4F3EF), onSurface = Color(0xFF141413),
    surfaceVariant = Color(0xFFE8E6E0), onSurfaceVariant = Color(0xFF6E6C66),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F7F3),
    surfaceContainer = Color(0xFFEEEDE8), surfaceContainerHigh = Color(0xFFE8E6E0),
    surfaceContainerHighest = Color(0xFFE1DFD8),
    outline = Color(0xFFBDBAB2), outlineVariant = Color(0xFFDDDAD3),
    inverseSurface = Color(0xFF141413), inverseOnSurface = Color(0xFFF4F3EF),
    error = Color(0xFFB3261E),
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF1F0EC), onPrimary = Color(0xFF0B0B0A),
    primaryContainer = Color(0xFF1F1F1D), onPrimaryContainer = Color(0xFFF1F0EC),
    secondary = Color(0xFFF1F0EC), onSecondary = Color(0xFF0B0B0A),
    secondaryContainer = Color(0xFF1F1F1D), onSecondaryContainer = Color(0xFFF1F0EC),
    tertiary = Color(0xFFF1F0EC), onTertiary = Color(0xFF0B0B0A),
    background = Color(0xFF0B0B0A), onBackground = Color(0xFFF1F0EC),
    surface = Color(0xFF0B0B0A), onSurface = Color(0xFFF1F0EC),
    surfaceVariant = Color(0xFF1F1F1D), onSurfaceVariant = Color(0xFF8F8D87),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF121211),
    surfaceContainer = Color(0xFF181817), surfaceContainerHigh = Color(0xFF1F1F1D),
    surfaceContainerHighest = Color(0xFF272725),
    outline = Color(0xFF4A4945), outlineVariant = Color(0xFF262624),
    inverseSurface = Color(0xFFF1F0EC), inverseOnSurface = Color(0xFF0B0B0A),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)

/** 大きな見出し。端末のセリフ体 */
val Display = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em)

/** 日付や件数などの小さな補足。等幅で揃える */
val Meta = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em)

@Composable
fun CircleButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .border(1.dp, c.outlineVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = if (enabled) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.4f))
    }
}

@Composable
fun PillButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (filled) c.onSurface else Color.Transparent,
        contentColor = if (filled) c.surface else c.onSurface,
        border = if (filled) null else BorderStroke(1.dp, c.outline),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** アーティストの頭文字を丸に入れたもの */
@Composable
fun Monogram(name: String) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            fontFamily = FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.Normal, color = c.onSurface,
        )
    }
}

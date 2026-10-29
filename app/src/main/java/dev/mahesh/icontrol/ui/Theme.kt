package dev.mahesh.icontrol.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Horizontal safe area so content stays clear of TV overscan. */
val ScreenPadding = 56.dp

object Palette {
    val Background = Color(0xFF060A1C)
    val Surface = Color(0xFF101935)
    val SurfaceFocused = Color(0xFF1C2C58)
    val Accent = Color(0xFF3FB4FF)
    val Glow = Color(0xFF2F7BFF)
    val Violet = Color(0xFF7A3CFF)
    val SectionTitle = Color(0xFF8C9BEA)
    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xFF9AA4B5)
}

private val ColorScheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.Background,
    secondary = Palette.Violet,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceFocused,
    onSurfaceVariant = Palette.TextSecondary,
)

/** Dark base with two soft colour washes; drawn once, so it costs nothing per frame. */
fun Modifier.backdrop(): Modifier = drawBehind {
    drawRect(Palette.Background)
    drawRect(
        Brush.radialGradient(
            colors = listOf(Palette.Glow.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(size.width * 0.9f, 0f),
            radius = size.width * 0.55f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(Palette.Violet.copy(alpha = 0.32f), Color.Transparent),
            center = Offset(0f, size.height),
            radius = size.width * 0.5f,
        ),
    )
}

/** Scrolls whatever just took focus to the middle of its list instead of the nearest edge. */
@OptIn(ExperimentalFoundationApi::class)
val CenterFocusSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset + size / 2f - containerSize / 2f
}

@Composable
fun IControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}

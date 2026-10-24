package dev.mahesh.icontrol.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.data.AppEntry
import dev.mahesh.icontrol.data.AppImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// The ring nearly fills the 540dp-tall screen: the selected icon is enlarged 1.6x at the top, and the hint sits below.
private const val RingRadiusDp = 200f
private const val MaxIconDp = 64f
private const val MinIconDp = 28f

// Share of each slot's arc length an icon fills; the rest is the gap between neighbours.
private const val IconFill = 0.9f

/**
 * Every installed app on a ring, with the selected app large in the middle. Left/Up and Right/Down turn the
 * ring (the app at the top is the selected one, endlessly in either direction); OK launches it; Back closes.
 */
@Composable
fun AllAppsDialog(
    apps: List<AppEntry>,
    loadImage: suspend (AppEntry) -> AppImage?,
    loadIcon: suspend (AppEntry) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    if (apps.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val count = apps.size
    val stepDeg = 360f / count
    // Unbounded so the ring keeps turning the same way past the last app instead of spinning back.
    var position by remember { mutableIntStateOf(0) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(position) {
        rotation.animateTo(position * stepDeg, tween(durationMillis = 220, easing = FastOutSlowInEasing))
    }
    val selected = apps[Math.floorMod(position, count)]

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val iconDp = (2f * PI.toFloat() * RingRadiusDp / count * IconFill).coerceIn(MinIconDp, MaxIconDp)
    val radiusPx = with(LocalDensity.current) { RingRadiusDp.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .backdrop()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionUp -> { position -= 1; true }
                        Key.DirectionRight, Key.DirectionDown -> { position += 1; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onDismiss()
                            onLaunch(selected)
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            // Marker ring around the slot at 12 o'clock, where the selected app always sits.
            Box(
                modifier = Modifier
                    .offset(y = (-RingRadiusDp).dp)
                    .size((iconDp * 1.6f + 10f).dp)
                    .border(3.dp, Palette.Accent, CircleShape),
            )

            apps.forEachIndexed { index, app ->
                RingIcon(
                    app = app,
                    loadIcon = loadIcon,
                    size = iconDp.dp,
                    slotDeg = index * stepDeg,
                    stepDeg = stepDeg,
                    radiusPx = radiusPx,
                    rotation = rotation,
                )
            }

            SelectedApp(app = selected, loadImage = loadImage)

            Text(
                text = "Left / Right to turn  ·  OK to open  ·  Back to close",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun RingIcon(
    app: AppEntry,
    loadIcon: suspend (AppEntry) -> ImageBitmap?,
    size: androidx.compose.ui.unit.Dp,
    slotDeg: Float,
    stepDeg: Float,
    radiusPx: Float,
    rotation: Animatable<Float, *>,
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = loadIcon(app)
    }
    Box(
        modifier = Modifier
            .size(size)
            .offset {
                val angle = Math.toRadians((-90f + slotDeg - rotation.value).toDouble())
                IntOffset(
                    (radiusPx * cos(angle)).roundToInt(),
                    (radiusPx * sin(angle)).roundToInt(),
                )
            }
            .graphicsLayer {
                // 1 when this icon is at 12 o'clock, fading to 0 one slot away.
                val offsetDeg = ((slotDeg - rotation.value) % 360f + 540f) % 360f - 180f
                val near = (1f - abs(offsetDeg) / stepDeg).coerceIn(0f, 1f)
                val scale = 1f + 0.6f * near
                scaleX = scale
                scaleY = scale
                alpha = 0.55f + 0.45f * near
            }
            .clip(CircleShape)
            .background(Palette.Surface),
    ) {
        icon?.let {
            Image(
                bitmap = it,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.14f),
            )
        }
    }
}

@Composable
private fun SelectedApp(app: AppEntry, loadImage: suspend (AppEntry) -> AppImage?) {
    val image by produceState<AppImage?>(initialValue = null, app.packageName) {
        value = loadImage(app)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 230.dp, height = 129.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Surface),
        ) {
            AppArtwork(image = image, label = app.label, dimmed = false)
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 10.dp)
                .width(260.dp),
        )
    }
}

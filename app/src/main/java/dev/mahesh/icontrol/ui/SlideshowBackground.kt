package dev.mahesh.icontrol.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import java.io.File
import kotlinx.coroutines.delay

// The smaller this is, the stronger the blur.
private const val BlurWidth = 20
private const val BlurHeight = 11

/**
 * The user's pictures cross-fading behind the home screen, [visibility] percent visible.
 * [photos] must not be empty. It only advances while the launcher is on screen, so it costs nothing in the background.
 */
@Composable
fun SlideshowBackground(
    photos: List<File>,
    seconds: Int,
    visibility: Int,
    modifier: Modifier = Modifier,
    /** Called with each picture as it comes on screen. */
    onShown: ((File) -> Unit)? = null,
    /** Show this picture instead of cycling, e.g. the one the user is looking at in the settings. */
    pinned: File? = null,
) {
    var index by remember(photos) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(photos, seconds) {
        if (photos.size < 2) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(seconds * 1_000L)
                index = (index + 1) % photos.size
            }
        }
    }
    val current = pinned ?: photos[index.coerceIn(photos.indices)]
    LaunchedEffect(current) { onShown?.invoke(current) }
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = current, animationSpec = tween(1_500), label = "slideshow") { file ->
            Box(Modifier.fillMaxSize()) {
                // The same picture shrunk to a few dozen pixels and stretched over the whole screen: a soft blur that
                // fills the margins (Modifier.blur needs Android 12; this TV runs 11).
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(file).size(BlurWidth, BlurHeight).precision(Precision.EXACT).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
                // The whole picture on top, never cropped.
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // The rest of the visibility setting is a dark layer: 90% visible means 10% darkening. The tiles are solid.
        Box(Modifier.fillMaxSize().background(Color(0xFF060A1C).copy(alpha = (100 - visibility) / 100f)))
        // The clock, date and memory text sit straight on the picture, so the top edge gets a soft shade.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.3f)
                .background(Brush.verticalGradient(listOf(Color(0x8C060A1C), Color.Transparent))),
        )
    }
}

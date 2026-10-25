package dev.mahesh.icontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.mahesh.icontrol.data.AppEntry
import dev.mahesh.icontrol.data.AppImage

private val CardShape = RoundedCornerShape(14.dp)

/** The focused item is 30% larger than the rest so it is unmistakable from across the room. */
const val FocusedScale = 1.3f

val CardGap = 44.dp

@Composable
internal fun focusColors() = ClickableSurfaceDefaults.colors(
    containerColor = Palette.Surface,
    focusedContainerColor = Palette.SurfaceFocused,
    pressedContainerColor = Palette.SurfaceFocused,
)

@Composable
internal fun focusBorder() = ClickableSurfaceDefaults.border(
    focusedBorder = androidx.tv.material3.Border(
        border = BorderStroke(3.dp, Palette.Accent),
        shape = CardShape,
    ),
)

@Composable
fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        content()
    }
}

/**
 * Centered row. The gap leaves room for the focused card to grow without covering its neighbours,
 * and [verticalPadding] must be at least that growth or the lazy list clips the enlarged card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardRow(
    verticalPadding: Dp,
    /**
     * Set for a carousel row whose items all have this width: the selection then stays fixed at the
     * screen centre and the items slide past it, even for the first and last item.
     */
    pinnedItemWidth: Dp? = null,
    gap: Dp = CardGap,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val horizontalPadding = if (pinnedItemWidth != null) {
        (LocalConfiguration.current.screenWidthDp.dp - pinnedItemWidth) / 2
    } else {
        ScreenPadding
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides CenterFocusSpec) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = if (pinnedItemWidth != null) {
                Arrangement.spacedBy(gap)
            } else {
                Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            },
            content = content,
        )
    }
}

@Composable
fun AppCard(
    app: AppEntry,
    width: Dp,
    loadImage: suspend (AppEntry) -> AppImage?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusedScale: Float = FocusedScale,
) {
    val image by produceState<AppImage?>(initialValue = null, app.packageName) {
        value = loadImage(app)
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        modifier = modifier
            .size(width = width, height = width * 9 / 16)
            .onFocusChanged { if (it.hasFocus) onFocused(app.label) },
    ) {
        Box(Modifier.fillMaxSize()) {
            AppArtwork(image = image, label = app.label, dimmed = selected)
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Added",
                    tint = Palette.Accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp),
                )
            }
        }
    }
}

/** App-card-sized action tile, e.g. "Add app" / "All apps". */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    focusedLabel: String,
    width: Dp,
    onClick: () -> Unit,
    onFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusedScale: Float = FocusedScale,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        modifier = modifier
            .size(width = width, height = width * 9 / 16)
            .onFocusChanged { if (it.hasFocus) onFocused(focusedLabel) },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Palette.Accent,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun AppArtwork(image: AppImage?, label: String, dimmed: Boolean) {
    val alpha = if (dimmed) 0.45f else 1f
    when {
        image == null -> Box(Modifier.fillMaxSize())
        image.isBanner -> Image(
            bitmap = image.bitmap,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            modifier = Modifier.fillMaxSize(),
        )
        else -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1B2330), Color(0xFF12161E))),
                ),
        ) {
            Image(
                bitmap = image.bitmap,
                contentDescription = label,
                alpha = alpha,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 14.dp)
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun ShortcutTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusedScale),
        modifier = modifier
            .size(width = width, height = 52.dp)
            .onFocusChanged { if (it.hasFocus) onFocused(label) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Palette.Accent,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
            )
        }
    }
}

fun Modifier.optionalFocusRequester(requester: FocusRequester?): Modifier =
    if (requester != null) focusRequester(requester) else this

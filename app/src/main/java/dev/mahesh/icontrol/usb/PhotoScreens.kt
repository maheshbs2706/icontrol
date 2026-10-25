package dev.mahesh.icontrol.usb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.mahesh.icontrol.ui.CenterFocusSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.mahesh.icontrol.ui.FocusedScale
import dev.mahesh.icontrol.ui.Palette
import dev.mahesh.icontrol.ui.ScreenPadding
import dev.mahesh.icontrol.ui.backdrop
import dev.mahesh.icontrol.ui.focusBorder
import dev.mahesh.icontrol.ui.focusColors
import java.io.File
import kotlinx.coroutines.delay

private val GridGap = 28.dp
private const val PhotoColumns = 4

/** Rows of photo thumbnails; the focused row stays vertically centred, beside the fixed slideshow buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridScreen(
    title: String,
    subtitle: String,
    photos: List<File>,
    scanning: Boolean,
    /** The photo to come back to (the one last viewed), or -1 to start at the top. */
    restoreIndex: Int,
    loopSlideshow: Boolean,
    onLoopChange: (Boolean) -> Unit,
    onOpen: (index: Int) -> Unit,
    onSlideshow: (startIndex: Int) -> Unit,
    /** Starts a slideshow of every photo in a shuffled order. */
    onRandom: () -> Unit,
) {
    var focusedName by remember { mutableStateOf("") }
    // The photo the user was last on, kept while focus is on the side buttons so "From here" knows where "here" is.
    var lastFocusedIndex by remember { mutableIntStateOf(restoreIndex.coerceAtLeast(0)) }
    var screenFocused by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    val restoring = restoreIndex in photos.indices
    val rows = remember(photos) { photos.chunked(PhotoColumns) }
    // Start on the row before the remembered photo so it is composed; focusing it then slides it to the middle.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (restoring) (restoreIndex / PhotoColumns - 1).coerceAtLeast(0) else 0,
    )
    val density = LocalDensity.current
    var viewportHeight by remember { mutableStateOf(0.dp) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(photos.isNotEmpty()) {
        if (photos.isNotEmpty()) {
            requestFocusWithRetry(if (restoring) restoreFocus else firstFocus, focusManager) { screenFocused }
        }
    }
    val cardWidth = railGridCardWidth(PhotoColumns, GridGap)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .backdrop()
            .onFocusChanged { screenFocused = it.hasFocus },
    ) {
        ScreenHeader(
            title = title,
            subtitle = if (scanning) "Scanning…" else subtitle,
            focusedName = focusedName,
        )
        if (photos.isEmpty()) {
            CenteredMessage(
                icon = Icons.Rounded.ImageNotSupported,
                title = if (scanning) "Looking for photos…" else "No photos found",
                body = if (scanning) "This can take a moment on a big drive." else "There are no photos on this drive.",
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // The rail never scrolls. "From here" sits at the exact vertical middle, level with the focused row
                // (the list below slides to keep that row centred), so it always refers to the row beside it.
                Box(
                    modifier = Modifier
                        .width(SideRailWidth)
                        .fillMaxHeight()
                        .padding(start = ScreenPadding),
                ) {
                    Text(
                        text = "Slideshow",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = 8.dp, y = (-62).dp),
                    )
                    ActionButton(
                        text = "From here",
                        icon = Icons.Rounded.PlayArrow,
                        // The row's first photo: the row beside the button is the one the user means.
                        onClick = { onSlideshow(lastFocusedIndex / PhotoColumns * PhotoColumns) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .focusRequester(firstFocus),
                    )
                    ActionButton(
                        text = "From start",
                        icon = Icons.Rounded.Slideshow,
                        onClick = { onSlideshow(0) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = 64.dp),
                    )
                    ActionButton(
                        text = "Random",
                        icon = Icons.Rounded.Shuffle,
                        onClick = onRandom,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = 128.dp),
                    )
                    OptionCheckbox(
                        text = "Loop",
                        checked = loopSlideshow,
                        onCheckedChange = onLoopChange,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = 190.dp),
                    )
                }
                // Padding of half the free height above and below lets even the first and last rows reach the middle.
                val centrePadding = ((viewportHeight - cardWidth * 9 / 16) / 2).coerceAtLeast(28.dp)
                CompositionLocalProvider(LocalBringIntoViewSpec provides CenterFocusSpec) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = ScreenPadding,
                            top = centrePadding,
                            bottom = centrePadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(GridGap),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onSizeChanged { viewportHeight = with(density) { it.height.toDp() } },
                    ) {
                        items(rows.size, key = { rows[it].first().path }) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                                rows[rowIndex].forEachIndexed { column, file ->
                                    val index = rowIndex * PhotoColumns + column
                                    ThumbnailCard(
                                        width = cardWidth,
                                        onClick = { onOpen(index) },
                                        onFocused = {
                                            focusedName = file.name
                                            lastFocusedIndex = index
                                        },
                                        modifier = if (restoring && index == restoreIndex) {
                                            Modifier.focusRequester(restoreFocus)
                                        } else {
                                            Modifier
                                        },
                                    ) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = file.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A photo filling its tile; used as a folder cover. */
@Composable
fun PhotoCover(file: File) {
    AsyncImage(
        model = file,
        contentDescription = file.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/** A focusable check box row: OK toggles it. */
@Composable
internal fun OptionCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        modifier = modifier.size(width = 158.dp, height = 48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (checked) "$text on" else "$text off",
                tint = if (checked) Palette.Accent else Palette.TextSecondary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
fun ThumbnailCard(
    width: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusedScale),
        modifier = modifier
            .size(width = width, height = width * 9 / 16)
            .onFocusGained(onFocused),
    ) {
        content()
    }
}

private val SlideshowSeconds = intArrayOf(3, 5, 8, 12)

/**
 * One photo at a time. Left/Right step through them; OK starts or pauses a slideshow; Up/Down change its speed.
 * Back (handled by the caller) returns to the grid.
 */
@Composable
fun PhotoViewerScreen(
    photos: List<File>,
    startIndex: Int,
    startSlideshow: Boolean,
    /** When false the slideshow stops on the last photo instead of wrapping to the first. */
    loopSlideshow: Boolean,
    /** Called with the photo now on screen, so the list it came from can return to it. */
    onIndexChange: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, photos.lastIndex)) }
    LaunchedEffect(index) { onIndexChange(index) }
    var playing by remember { mutableStateOf(startSlideshow && photos.size > 1) }
    var speedIndex by remember { mutableIntStateOf(1) }
    var hudTick by remember { mutableIntStateOf(0) }
    var hudVisible by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }

    KeepScreenOn()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(hudTick) {
        hudVisible = true
        delay(3500)
        hudVisible = false
    }
    // Restarts on every manual step or speed change, so a press never gets cut short by the timer.
    LaunchedEffect(playing, index, speedIndex) {
        if (playing) {
            delay(SlideshowSeconds[speedIndex] * 1000L)
            if (index == photos.lastIndex && !loopSlideshow) {
                // End of the list with Loop off: stop here and bring the controls back so it's clear why.
                playing = false
                hudTick++
            } else {
                index = (index + 1) % photos.size
            }
        }
    }

    fun step(delta: Int) {
        index = Math.floorMod(index + delta, photos.size)
        hudTick++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.MediaPrevious, Key.MediaRewind -> { step(-1); true }
                    Key.DirectionRight, Key.MediaNext, Key.MediaFastForward -> { step(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (photos.size > 1) playing = !playing
                        hudTick++
                        true
                    }
                    Key.MediaPlay -> { if (photos.size > 1) playing = true; hudTick++; true }
                    Key.MediaPause -> { playing = false; hudTick++; true }
                    Key.DirectionUp -> { speedIndex = (speedIndex - 1).coerceAtLeast(0); hudTick++; true }
                    Key.DirectionDown -> {
                        speedIndex = (speedIndex + 1).coerceAtMost(SlideshowSeconds.lastIndex)
                        hudTick++
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        Crossfade(targetState = index, animationSpec = tween(600), label = "photo") { shown ->
            AsyncImage(
                model = photos[shown],
                contentDescription = photos[shown].name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (playing) {
            Text(
                text = "▶  Slideshow",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = ScreenPadding, vertical = 16.dp),
            ) {
                Text(
                    text = "${index + 1} / ${photos.size}  ·  ${photos[index].name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The OK key does this; the pill just tells the user so (it isn't a separate focus target).
                Text(
                    text = if (photos.size < 2) {
                        "Only one photo"
                    } else if (playing) {
                        "⏸  OK: pause slideshow  (every ${SlideshowSeconds[speedIndex]} s)"
                    } else {
                        "▶  OK: start slideshow from this photo"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Background,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .background(Palette.Accent, RoundedCornerShape(24.dp))
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                )
                Text(
                    text = "Left / Right: previous / next  ·  Up / Down: slideshow speed",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Keeps the TV from dimming or sleeping while a slideshow or video is on screen. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

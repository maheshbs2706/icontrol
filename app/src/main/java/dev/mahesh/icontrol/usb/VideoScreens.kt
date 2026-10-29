package dev.mahesh.icontrol.usb

import android.media.ThumbnailUtils
import android.net.Uri
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.ui.CenterFocusSpec
import dev.mahesh.icontrol.ui.Palette
import dev.mahesh.icontrol.ui.ScreenPadding
import dev.mahesh.icontrol.ui.backdrop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val VideoGap = 32.dp
private const val VideoColumns = 3

/**
 * Rows of video thumbnails; the focused row stays vertically centred, beside the fixed Play all buttons. Pick one to
 * play it alone, or use Play all to run through the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridScreen(
    title: String,
    subtitle: String,
    videos: List<File>,
    scanning: Boolean,
    /** The video to come back to (the one last played on its own), or -1 to start at "Play all". */
    restoreIndex: Int,
    loopVideos: Boolean,
    onLoopChange: (Boolean) -> Unit,
    onPlay: (index: Int) -> Unit,
    /** Plays the whole list, beginning at [startIndex]. */
    onPlayAll: (startIndex: Int) -> Unit,
    /** Plays every video in a shuffled order. */
    onRandom: () -> Unit,
) {
    var focusedName by remember { mutableStateOf("") }
    // The video the user was last on, kept while focus is on the side buttons so "From here" knows where "here" is.
    var lastFocusedIndex by remember { mutableIntStateOf(restoreIndex.coerceAtLeast(0)) }
    var screenFocused by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    val restoring = restoreIndex in videos.indices
    val rows = remember(videos) { videos.chunked(VideoColumns) }
    // Start on the row before the remembered video so it is composed; focusing it then slides it to the middle.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (restoring) (restoreIndex / VideoColumns - 1).coerceAtLeast(0) else 0,
    )
    val density = LocalDensity.current
    var viewportHeight by remember { mutableStateOf(0.dp) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(videos.isNotEmpty()) {
        if (videos.isNotEmpty()) {
            requestFocusWithRetry(if (restoring) restoreFocus else firstFocus, focusManager) { screenFocused }
        }
    }
    val cardWidth = railGridCardWidth(VideoColumns, VideoGap)

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
        if (videos.isEmpty()) {
            CenteredMessage(
                icon = Icons.Rounded.VideocamOff,
                title = if (scanning) "Looking for videos…" else "No videos found",
                body = if (scanning) "This can take a moment on a big drive." else "There are no videos on this drive.",
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
                        text = "Play all",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = 8.dp, y = (-62).dp),
                    )
                    ActionButton(
                        text = "From here",
                        icon = Icons.Rounded.PlayArrow,
                        // The row's first video: the row beside the button is the one the user means.
                        onClick = { onPlayAll(lastFocusedIndex / VideoColumns * VideoColumns) },
                        modifier = Modifier
                            .width(RailButtonWidth)
                            .align(Alignment.CenterStart)
                            .focusRequester(firstFocus),
                    )
                    ActionButton(
                        text = "From start",
                        icon = Icons.Rounded.PlaylistPlay,
                        onClick = { onPlayAll(0) },
                        modifier = Modifier
                            .width(RailButtonWidth)
                            .align(Alignment.CenterStart)
                            .offset(y = 64.dp),
                    )
                    ActionButton(
                        text = "Random",
                        icon = Icons.Rounded.Shuffle,
                        onClick = onRandom,
                        modifier = Modifier
                            .width(RailButtonWidth)
                            .align(Alignment.CenterStart)
                            .offset(y = 128.dp),
                    )
                    OptionCheckbox(
                        text = "Loop",
                        checked = loopVideos,
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
                        verticalArrangement = Arrangement.spacedBy(VideoGap),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onSizeChanged { viewportHeight = with(density) { it.height.toDp() } },
                    ) {
                        items(rows.size, key = { rows[it].first().path }) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(VideoGap)) {
                                rows[rowIndex].forEachIndexed { column, file ->
                                    val index = rowIndex * VideoColumns + column
                                    ThumbnailCard(
                                        width = cardWidth,
                                        onClick = { onPlay(index) },
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
                                        VideoThumbnail(file)
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

private object VideoThumbnails {
    private val cache = LruCache<String, ImageBitmap>(40)

    suspend fun load(file: File): ImageBitmap? {
        cache.get(file.path)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                ThumbnailUtils.createVideoThumbnail(file, Size(480, 270), null).asImageBitmap()
            }.getOrNull()?.also { cache.put(file.path, it) }
        }
    }
}

@Composable
fun VideoThumbnail(file: File) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, file.path) {
        value = VideoThumbnails.load(file)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12161E)),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color(0xCCFFFFFF),
            modifier = Modifier
                .size(40.dp)
                .background(Color(0x66000000), androidx.compose.foundation.shape.CircleShape),
        )
    }
}

/**
 * Plays [videos] from [startIndex] onward. With [loop] off it leaves the screen when the list ends; with it on the
 * list starts over, until Back. A single-item list plays just that file; a longer one has next/previous in the
 * player controls.
 */
@Composable
fun VideoPlayerScreen(videos: List<File>, startIndex: Int, loop: Boolean, onFinished: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    KeepScreenOn()

    DisposableEffect(player) {
        // Files that failed since something last played fine. With Loop on, a list of unplayable files would
        // otherwise go round forever.
        var failures = 0
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onFinished()
                if (playbackState == Player.STATE_READY) failures = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "Can't play this file", Toast.LENGTH_SHORT).show()
                failures++
                if (failures < videos.size && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    onFinished()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(loop) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(videos, startIndex) {
        player.setMediaItems(videos.map { MediaItem.fromUri(Uri.fromFile(it)) }, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = 4000
                isFocusable = true
                // Must hold focus or the remote's keys never reach the player controls.
                post { requestFocus() }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import dev.mahesh.icontrol.ui.Palette
import dev.mahesh.icontrol.ui.ScreenPadding
import dev.mahesh.icontrol.ui.backdrop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val VideoGap = 32.dp
private const val VideoColumns = 3

/** Every video on the drive. Pick one to play it alone, or use Play all to run through the whole list. */
@Composable
fun VideoGridScreen(
    title: String,
    subtitle: String,
    videos: List<File>,
    scanning: Boolean,
    /** The video to come back to (the one last played on its own), or -1 to start at "Play all". */
    restoreIndex: Int,
    onPlay: (index: Int) -> Unit,
    onPlayAll: () -> Unit,
) {
    var focusedName by remember { mutableStateOf("") }
    var screenFocused by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    val restoring = restoreIndex in videos.indices
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (restoring) (restoreIndex / VideoColumns * VideoColumns - VideoColumns).coerceAtLeast(0) else 0,
    )
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
                SideActionRail {
                    ActionButton(
                        text = "Play all",
                        icon = Icons.Rounded.PlaylistPlay,
                        onClick = onPlayAll,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(VideoColumns),
                    contentPadding = railGridPadding(),
                    horizontalArrangement = Arrangement.spacedBy(VideoGap),
                    verticalArrangement = Arrangement.spacedBy(VideoGap),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    items(videos.size, key = { videos[it].path }) { index ->
                        val file = videos[index]
                        ThumbnailCard(
                            width = cardWidth,
                            onClick = { onPlay(index) },
                            onFocused = { focusedName = file.name },
                            modifier = if (restoring && index == restoreIndex) Modifier.focusRequester(restoreFocus) else Modifier,
                        ) {
                            VideoThumbnail(file)
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
 * Plays [videos] from [startIndex] onward and leaves the screen when the list ends. A single-item list plays
 * just that file; the full drive list gives "Play all", with next/previous in the player controls.
 */
@Composable
fun VideoPlayerScreen(videos: List<File>, startIndex: Int, onFinished: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    KeepScreenOn()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onFinished()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "Can't play this file", Toast.LENGTH_SHORT).show()
                if (player.hasNextMediaItem()) {
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

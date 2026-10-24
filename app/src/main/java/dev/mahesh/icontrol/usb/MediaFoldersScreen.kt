package dev.mahesh.icontrol.usb

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.ui.CenterFocusSpec
import dev.mahesh.icontrol.ui.Palette
import dev.mahesh.icontrol.ui.backdrop
import java.io.File

/** A folder that directly contains media files of one kind (photos or videos). */
data class MediaFolder(val dir: File, val files: List<File>)

fun groupByFolder(files: List<File>): List<MediaFolder> =
    files
        .groupBy { it.parentFile }
        .mapNotNull { (dir, inDir) -> dir?.let { MediaFolder(it, inDir) } }
        .sortedBy { it.dir.path.lowercase() }

/** Key under which "All photos" / "All videos" is remembered, as opposed to a folder's path. */
const val ALL_KEY = ""

/** One choice in the bottom row: everything, or a single folder. */
private data class Choice(
    val name: String,
    val where: String,
    val count: Int,
    val cover: File,
    /** The folder, or null for "everything". */
    val folder: File?,
)

private val TileWidth = 168.dp
private val TileGap = 40.dp

/** The bottom row takes this share of the screen height; the preview and title get the rest. */
private const val BottomShare = 0.25f

/**
 * Bottom quarter of the screen: a row of choices ("All …" first, then each folder that holds files of this kind),
 * with the focused one kept in the middle. The rest of the screen previews the focused choice. [onOpen] gets the
 * folder, or null for everything. [cover] draws the picture for a file.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaFoldersScreen(
    title: String,
    /** Singular, e.g. "photo", for the counts. */
    noun: String,
    root: File?,
    folders: List<MediaFolder>,
    total: Int,
    scanning: Boolean,
    /** [ALL_KEY] or the path of the folder to come back to, or null for the first choice. */
    restoreKey: String?,
    cover: @Composable (File) -> Unit,
    onOpen: (folder: File?) -> Unit,
) {
    val plural = "${noun}s"
    val choices = remember(folders, total, root) {
        if (folders.isEmpty()) {
            emptyList()
        } else {
            listOf(Choice("All $plural", "Every folder on the drive", total, folders.first().files.first(), null)) +
                folders.map { folder ->
                    val relative = root?.let { folder.dir.relativeTo(it).path }.orEmpty()
                    Choice(folder.dir.name, relative.ifEmpty { "Drive root" }, folder.files.size, folder.files.first(), folder.dir)
                }
        }
    }

    val restoreIndex = when (restoreKey) {
        null -> -1
        ALL_KEY -> 0
        else -> choices.indexOfFirst { it.folder?.path == restoreKey }
    }
    var focusedIndex by remember { mutableIntStateOf(restoreIndex.coerceAtLeast(0)) }
    var screenFocused by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = restoreIndex.coerceAtLeast(0))
    LaunchedEffect(choices.isNotEmpty()) {
        if (choices.isNotEmpty()) {
            requestFocusWithRetry(if (restoreIndex >= 0) restoreFocus else firstFocus, focusManager) { screenFocused }
        }
    }

    val configuration = LocalConfiguration.current
    val bottomHeight = configuration.screenHeightDp.dp * BottomShare
    // Padding of half the free width on each side lets even the first and last choice reach the middle.
    val sidePadding = (configuration.screenWidthDp.dp - TileWidth) / 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .backdrop()
            .onFocusChanged { screenFocused = it.hasFocus },
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ScreenHeader(
                title = title,
                subtitle = if (scanning) "Scanning…" else "${folders.size} folders  ·  $total $plural",
            )
            if (choices.isEmpty()) {
                CenteredMessage(
                    icon = Icons.Rounded.FolderOff,
                    title = if (scanning) "Looking for $plural…" else "No $plural found",
                    body = if (scanning) "This can take a moment on a big drive." else "There are no $plural on this drive.",
                )
            } else {
                Crossfade(
                    targetState = focusedIndex.coerceIn(0, choices.lastIndex),
                    animationSpec = tween(200),
                    label = "preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp),
                ) { index ->
                    Preview(choice = choices[index], noun = noun, cover = cover)
                }
            }
        }

        if (choices.isNotEmpty()) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides CenterFocusSpec) {
                LazyRow(
                    state = listState,
                    verticalAlignment = Alignment.CenterVertically,
                    // More room below than above keeps the enlarged focused tile clear of the screen's bottom edge.
                    contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 6.dp, bottom = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomHeight),
                ) {
                    itemsIndexed(choices, key = { _, choice -> choice.folder?.path ?: ALL_KEY }) { index, choice ->
                        ChoiceTile(
                            choice = choice,
                            noun = noun,
                            cover = cover,
                            onClick = { onOpen(choice.folder) },
                            onFocused = { focusedIndex = index },
                            modifier = when {
                                index == restoreIndex -> Modifier.focusRequester(restoreFocus)
                                index == 0 -> Modifier.focusRequester(firstFocus)
                                else -> Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The big picture of the focused choice, with its name, count and where it lives. */
@Composable
private fun Preview(choice: Choice, noun: String, cover: @Composable (File) -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 400.dp, height = 225.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF12161E)),
            ) {
                cover(choice.cover)
            }
            Column(modifier = Modifier.padding(start = 36.dp).widthIn(max = 380.dp)) {
                Text(
                    text = choice.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (choice.count == 1) "1 $noun" else "${choice.count} ${noun}s",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.Accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = choice.where,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceTile(
    choice: Choice,
    noun: String,
    cover: @Composable (File) -> Unit,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ThumbnailCard(width = TileWidth, onClick = onClick, onFocused = onFocused, modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF12161E)),
        ) {
            cover(choice.cover)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                    .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 6.dp),
            ) {
                Text(
                    text = choice.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (choice.count == 1) "1 $noun" else "${choice.count} ${noun}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                )
            }
        }
    }
}

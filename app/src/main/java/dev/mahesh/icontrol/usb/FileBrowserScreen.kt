package dev.mahesh.icontrol.usb

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.mahesh.icontrol.ui.Palette
import dev.mahesh.icontrol.ui.ScreenPadding
import dev.mahesh.icontrol.ui.backdrop
import dev.mahesh.icontrol.ui.focusBorder
import dev.mahesh.icontrol.ui.focusColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One folder of the drive. Opening a sub-folder pushes another instance, so Back walks up the tree. */
@Composable
fun FileBrowserScreen(
    root: File,
    dir: File,
    /** Path of the entry to come back to (the one last opened from here), or null to start at the top. */
    restorePath: String?,
    onOpenFolder: (File) -> Unit,
    onOpenFile: (file: File, siblings: List<File>) -> Unit,
) {
    val context = LocalContext.current
    val entries by produceState<List<File>?>(initialValue = null, dir) {
        value = withContext(Dispatchers.IO) { UsbStorage.listFolder(dir) }
    }
    var screenFocused by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(entries) {
        val list = entries
        if (!list.isNullOrEmpty()) {
            val at = if (restorePath == null) -1 else list.indexOfFirst { it.path == restorePath }
            // Scroll so the remembered entry is composed (and a little context shows above it) before focusing it.
            if (at >= 0) listState.scrollToItem((at - 2).coerceAtLeast(0))
            requestFocusWithRetry(if (at >= 0) restoreFocus else firstFocus, focusManager) { screenFocused }
        }
    }

    val trail = dir.relativeTo(root).path
        .split(File.separatorChar)
        .filter { it.isNotEmpty() }
    val location = (listOf("USB") + trail).joinToString("  ›  ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .backdrop()
            .onFocusChanged { screenFocused = it.hasFocus },
    ) {
        ScreenHeader(title = "All files", subtitle = location)
        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> CenteredMessage(
                icon = Icons.Rounded.FolderOff,
                title = "This folder is empty",
                body = "Press Back to go up.",
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.path }) { file ->
                    val kind = UsbStorage.kindOf(file)
                    FileRow(
                        file = file,
                        kind = kind,
                        detail = when (kind) {
                            FileKind.Folder -> "Folder"
                            else -> Formatter.formatFileSize(context, file.length())
                        },
                        onClick = {
                            if (kind == FileKind.Folder) onOpenFolder(file) else onOpenFile(file, list)
                        },
                        modifier = when {
                            file.path == restorePath -> Modifier.focusRequester(restoreFocus)
                            file == list.first() -> Modifier.focusRequester(firstFocus)
                            else -> Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: File,
    kind: FileKind,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Icon(
                imageVector = when (kind) {
                    FileKind.Folder -> Icons.Rounded.Folder
                    FileKind.Photo -> Icons.Rounded.Image
                    FileKind.Video -> Icons.Rounded.Movie
                    FileKind.Audio -> Icons.Rounded.MusicNote
                    FileKind.Other -> Icons.Rounded.InsertDriveFile
                },
                contentDescription = null,
                tint = if (kind == FileKind.Other) Palette.TextSecondary else Palette.Accent,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextSecondary,
            )
        }
    }
}

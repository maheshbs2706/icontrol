package dev.mahesh.icontrol.usb

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.mahesh.icontrol.ui.FocusedScale
import dev.mahesh.icontrol.ui.Palette
import dev.mahesh.icontrol.ui.backdrop
import dev.mahesh.icontrol.ui.focusBorder
import dev.mahesh.icontrol.ui.focusColors

@Composable
fun UsbMenuScreen(
    state: UsbState,
    onFiles: () -> Unit,
    onPhotos: () -> Unit,
    onVideos: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val drive = state.drive
    var screenFocused by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(drive != null, state.canRead) {
        requestFocusWithRetry(firstFocus, focusManager) { screenFocused }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .backdrop()
            .onFocusChanged { screenFocused = it.hasFocus },
    ) {
        ScreenHeader(
            title = "USB drive",
            subtitle = drive?.let {
                val free = Formatter.formatFileSize(context, it.root.freeSpace)
                val total = Formatter.formatFileSize(context, it.root.totalSpace)
                "${it.name}  ·  $free free of $total"
            } ?: "No USB drive connected",
        )
        // The three tiles are always on screen, with or without a drive, so the screen never changes shape.
        // Anything the user needs to do about it goes in the notice underneath.
        val needsPermission = drive != null && !state.canRead
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The gap must exceed how far a focused (1.3x) big tile grows sideways: 300dp * 0.15 = 45dp.
                Row(horizontalArrangement = Arrangement.spacedBy(100.dp)) {
                    BigTile(
                        icon = Icons.Rounded.Image,
                        label = "Photos",
                        detail = countText(state.scanning, state.photos.size, "photo"),
                        onClick = onPhotos,
                        modifier = if (needsPermission) Modifier else Modifier.focusRequester(firstFocus),
                    )
                    BigTile(
                        icon = Icons.Rounded.Movie,
                        label = "Videos",
                        detail = countText(state.scanning, state.videos.size, "video"),
                        onClick = onVideos,
                    )
                }
                Spacer(Modifier.height(44.dp))
                SmallTile(
                    icon = Icons.Rounded.Folder,
                    label = "All files",
                    onClick = onFiles,
                )
                when {
                    drive == null -> Notice("Plug a USB drive into the TV. It will show up here by itself.")
                    needsPermission -> {
                        Notice("iControl needs your permission to read the files on the drive.")
                        ActionButton(
                            text = "Allow access",
                            icon = Icons.Rounded.Usb,
                            onClick = onRequestPermission,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .focusRequester(firstFocus),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(top = 18.dp),
    )
}

private fun countText(scanning: Boolean, count: Int, noun: String): String = when {
    scanning -> "Scanning…"
    count == 1 -> "1 $noun"
    else -> "$count ${noun}s"
}

@Composable
private fun BigTile(
    icon: ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusedScale),
        modifier = modifier.size(width = 300.dp, height = 170.dp),
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
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextSecondary,
            )
        }
    }
}

@Composable
private fun SmallTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusedScale),
        modifier = modifier.size(width = 200.dp, height = 64.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Palette.Accent,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

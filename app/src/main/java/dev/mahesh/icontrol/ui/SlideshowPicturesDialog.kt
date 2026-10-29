package dev.mahesh.icontrol.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.mahesh.icontrol.data.SlideshowPhotos
import dev.mahesh.icontrol.usb.ActionButton
import dev.mahesh.icontrol.usb.MediaFolder
import dev.mahesh.icontrol.usb.RailButtonWidth
import dev.mahesh.icontrol.usb.SideRailWidth
import dev.mahesh.icontrol.usb.SideActionRail
import dev.mahesh.icontrol.usb.ThumbnailCard
import dev.mahesh.icontrol.usb.UsbStorage
import dev.mahesh.icontrol.usb.groupByFolder
import dev.mahesh.icontrol.usb.railGridCardWidth
import dev.mahesh.icontrol.usb.railGridPadding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PicturesMode { Manage, PickFromUsb }

private val GridGap = 36.dp
private const val GridColumns = 4

/** Beside the action rail the grid is five across, so two rows (ten pictures) are in view at once. */
private const val RailColumns = 5

/** A tighter gap beside the rail, but still wider than a focused card's growth (15% of its width each side). */
private val RailGap = 22.dp

/** Three across (the USB picker) has large thumbnails, so a focused one grows more and needs a wider gap. */
private const val PickerColumns = 3
private val PickerGap = 40.dp

/** The first column's focused thumbnail grows about 28dp, so the grid starts further from the rail's buttons. */
private val PickerStartPadding = 44.dp

private fun railPadding(columns: Int) =
    if (columns <= PickerColumns) {
        PaddingValues(start = PickerStartPadding, end = ScreenPadding, top = 28.dp, bottom = 28.dp)
    } else {
        railGridPadding()
    }

private fun gridGap(nextToRail: Boolean, columns: Int) =
    if (!nextToRail) GridGap else if (columns <= PickerColumns) PickerGap else RailGap

@Composable
private fun RailButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ActionButton(text = text, icon = icon, onClick = onClick, modifier = modifier.width(RailButtonWidth), enabled = enabled)
}

/** Card width for a grid of [columns], with or without the action rail taking space on the left. */
@Composable
private fun gridCardWidth(columns: Int, nextToRail: Boolean): Dp {
    val gap = gridGap(nextToRail, columns)
    return if (nextToRail && columns <= PickerColumns) {
        (LocalConfiguration.current.screenWidthDp.dp - SideRailWidth - PickerStartPadding - ScreenPadding - gap * (columns - 1)) / columns
    } else if (nextToRail) railGridCardWidth(columns, gap)
    else (LocalConfiguration.current.screenWidthDp.dp - ScreenPadding * 2 - gap * (columns - 1)) / columns
}

/**
 * The slideshow's pictures: every one in [SlideshowPhotos.FOLDER] as a thumbnail (OK removes one), and a way to add
 * more from the USB drive. [photos] is the live list; [onChanged] asks for it to be re-read after a change.
 */
@Composable
fun SlideshowPicturesDialog(photos: List<File>, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(PicturesMode.Manage) }
    // Pictures waiting for the user to confirm their removal.
    var toRemove by remember { mutableStateOf<List<File>>(emptyList()) }
    // Multi-select mode: OK ticks pictures instead of removing one.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<File>()) }
    // The USB folder being browsed, or null while the folder list is showing.
    var usbFolder by remember { mutableStateOf<File?>(null) }

    val askForWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!SlideshowPhotos.canWrite(context)) askForWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    Dialog(
        // Back steps out of a USB folder to the folder list, then out of the picker, before it closes the dialog.
        onDismissRequest = {
            when {
                mode == PicturesMode.PickFromUsb && usbFolder != null -> usbFolder = null
                mode == PicturesMode.PickFromUsb -> mode = PicturesMode.Manage
                selecting -> {
                    selecting = false
                    selected = emptySet()
                }
                else -> onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().backdrop()) {
            when (mode) {
                PicturesMode.Manage -> ManagePictures(
                    photos = photos,
                    onAdd = {
                        if (SlideshowPhotos.canWrite(context)) {
                            mode = PicturesMode.PickFromUsb
                        } else {
                            Toast.makeText(context, "Allow iControl to change files first", Toast.LENGTH_SHORT).show()
                            askForWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
                    selecting = selecting,
                    selected = selected,
                    onSelectingChange = {
                        selecting = it
                        selected = emptySet()
                    },
                    onSelectedChange = { selected = it },
                    onRemove = { toRemove = it },
                    onClose = onDismiss,
                )
                PicturesMode.PickFromUsb -> PickFromUsb(
                    openFolder = usbFolder,
                    onOpenFolder = { usbFolder = it },
                    onAdded = { result ->
                        onChanged()
                        usbFolder = null
                        mode = PicturesMode.Manage
                        val message = buildString {
                            append(if (result.added == 1) "Added 1 picture" else "Added ${result.added} pictures")
                            if (result.alreadyThere > 0) append(", ${result.alreadyThere} already there")
                            if (result.failed > 0) append(", ${result.failed} could not be copied")
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    },
                    onCancel = {
                        usbFolder = null
                        mode = PicturesMode.Manage
                    },
                )
            }
        }
    }

    if (toRemove.isNotEmpty()) {
        val files = toRemove
        ConfirmRemove(
            files = files,
            onRemove = {
                toRemove = emptyList()
                selecting = false
                selected = emptySet()
                scope.launch {
                    val failed = withContext(Dispatchers.IO) { files.count { !SlideshowPhotos.remove(it) } }
                    if (failed > 0) {
                        Toast.makeText(context, "$failed could not be removed. Allow iControl to change files.", Toast.LENGTH_LONG).show()
                    }
                    onChanged()
                }
            },
            onKeep = { toRemove = emptyList() },
        )
    }
}

@Composable
private fun ManagePictures(
    photos: List<File>,
    selecting: Boolean,
    selected: Set<File>,
    onSelectingChange: (Boolean) -> Unit,
    onSelectedChange: (Set<File>) -> Unit,
    onAdd: () -> Unit,
    onRemove: (List<File>) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    // The first button differs between the two modes, so focus is placed again whenever the mode changes.
    LaunchedEffect(selecting) { runCatching { firstFocus.requestFocus() } }
    var focusedName by remember { mutableStateOf("") }
    // Only pictures that still exist count (a removal may have just happened).
    val chosen = remember(photos, selected) { photos.filter { it in selected } }
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp),
    ) {
        PicturesHeader(
            title = "Slideshow pictures",
            subtitle = when {
                focusedName.isNotEmpty() && photos.any { it.name == focusedName } -> focusedName
                photos.isEmpty() -> "No pictures yet"
                selecting -> "${chosen.size} of ${photos.size} chosen. Press OK on a picture to choose it."
                else -> "${photos.size} picture${if (photos.size == 1) "" else "s"}. Press OK on one to remove it."
            },
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The buttons stay pinned on the left while the grid scrolls, like the USB picker.
            SideActionRail {
                if (selecting) {
                    RailButton(
                        text = "Remove ${chosen.size}",
                        icon = Icons.Rounded.Delete,
                        onClick = {
                            if (chosen.isEmpty()) {
                                Toast.makeText(context, "Choose some pictures first", Toast.LENGTH_SHORT).show()
                            } else {
                                onRemove(chosen)
                            }
                        },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    val all = chosen.size == photos.size
                    RailButton(
                        text = if (all) "Clear all" else "Choose all",
                        icon = Icons.Rounded.CheckCircle,
                        onClick = { onSelectedChange(if (all) emptySet() else photos.toSet()) },
                    )
                    RailButton(text = "Done", icon = Icons.Rounded.Done, onClick = { onSelectingChange(false) })
                } else {
                    RailButton(
                        text = "From USB",
                        icon = Icons.Rounded.Usb,
                        onClick = onAdd,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    if (photos.isNotEmpty()) {
                        RailButton(text = "Select", icon = Icons.Rounded.CheckCircle, onClick = { onSelectingChange(true) })
                        RailButton(text = "Remove all", icon = Icons.Rounded.DeleteSweep, onClick = { onRemove(photos) })
                    }
                    RailButton(text = "Close", icon = Icons.Rounded.Close, onClick = onClose)
                }
            }
            if (photos.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxSize()) {
                    Text(
                        text = "Add pictures from a USB drive, or copy them into \"${SlideshowPhotos.FOLDER}\" on this TV.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 640.dp),
                    )
                }
            } else {
                PictureGrid(
                    files = photos,
                    selected = if (selecting) chosen.toSet() else emptySet(),
                    onClick = { file ->
                        if (selecting) {
                            onSelectedChange(if (file in selected) selected - file else selected + file)
                        } else {
                            onRemove(listOf(file))
                        }
                    },
                    onFocused = { focusedName = it.name },
                    nextToRail = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Choose photos on the USB drive, folder by folder: the folder list first, then the photos inside the folder that was
 * opened ([openFolder]). OK ticks a photo; the ticks are kept as the user moves between folders, and Add copies every
 * ticked photo into the slideshow.
 */
@Composable
private fun PickFromUsb(
    openFolder: File?,
    onOpenFolder: (File?) -> Unit,
    onAdded: (SlideshowPhotos.AddResult) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanned by remember { mutableStateOf(false) }
    var driveName by remember { mutableStateOf<String?>(null) }
    var driveRoot by remember { mutableStateOf<File?>(null) }
    var photos by remember { mutableStateOf<List<File>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<File>()) }
    var copying by remember { mutableStateOf(false) }
    var focusedName by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val drive = UsbStorage.findDrive(context)
        driveName = drive?.name
        driveRoot = drive?.root
        photos = if (drive == null) emptyList() else withContext(Dispatchers.IO) { UsbStorage.scan(drive.root).photos }
        scanned = true
    }
    val folders = remember(photos) { groupByFolder(photos) }
    val current = folders.firstOrNull { it.dir == openFolder }
    // Each level starts on its first button.
    LaunchedEffect(openFolder, scanned) {
        focusedName = ""
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp),
    ) {
        PicturesHeader(
            title = current?.let { folderLabel(it.dir, driveRoot) } ?: "Add from USB",
            subtitle = when {
                focusedName.isNotEmpty() -> focusedName
                !scanned -> "Looking for pictures…"
                copying -> "Adding pictures…"
                driveName == null -> "No USB drive connected"
                current != null -> "${current.files.count { it in selected }} of ${current.files.size} chosen here. Press OK on a picture to choose it."
                else -> "${folders.size} folder${if (folders.size == 1) "" else "s"}, ${selected.size} of ${photos.size} chosen. Press OK on a folder to open it."
            },
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The actions stay pinned on the left while the grid scrolls, like the USB photo screens.
            SideActionRail {
                // Add stays disabled until something is chosen. A disabled button cannot take focus, so the first
                // enabled button in the rail (Add, else Choose all, else Cancel) is the one that gets it.
                val canAdd = selected.isNotEmpty() && !copying
                val chooseExists = photos.isNotEmpty()
                RailButton(
                    text = when {
                        copying -> "Adding…"
                        selected.isEmpty() -> "Add"
                        else -> "Add ${selected.size}"
                    },
                    icon = Icons.Rounded.Add,
                    enabled = canAdd,
                    onClick = {
                        copying = true
                        val chosen = photos.filter { it in selected }
                        scope.launch { onAdded(withContext(Dispatchers.IO) { SlideshowPhotos.add(chosen) }) }
                    },
                    modifier = if (canAdd) Modifier.focusRequester(firstFocus) else Modifier,
                )
                if (current != null) {
                    val allHere = current.files.all { it in selected }
                    RailButton(
                        text = if (allHere) "Clear" else "Choose all",
                        icon = Icons.Rounded.CheckCircle,
                        onClick = { selected = if (allHere) selected - current.files.toSet() else selected + current.files },
                        modifier = if (!canAdd) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                    RailButton(text = "Folders", icon = Icons.Rounded.Folder, onClick = { onOpenFolder(null) })
                } else if (photos.isNotEmpty()) {
                    val all = selected.size == photos.size
                    RailButton(
                        text = if (all) "Clear all" else "Choose all",
                        icon = Icons.Rounded.CheckCircle,
                        onClick = { selected = if (all) emptySet() else photos.toSet() },
                        modifier = if (!canAdd) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
                RailButton(
                    text = "Cancel",
                    icon = Icons.Rounded.Close,
                    onClick = onCancel,
                    modifier = if (!canAdd && !chooseExists) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            when {
                // Scanning a big drive, or copying the chosen pictures, takes a while: show that something is happening.
                !scanned -> Loading(
                    title = "Looking for pictures…",
                    body = "This can take a moment on a big drive.",
                    modifier = Modifier.weight(1f),
                )
                copying -> Loading(
                    title = if (selected.size == 1) "Adding 1 picture…" else "Adding ${selected.size} pictures…",
                    body = "Please wait.",
                    modifier = Modifier.weight(1f),
                )
                photos.isEmpty() -> Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxSize()) {
                    Text(
                        text = when {
                            driveName == null -> "Plug a USB drive into the TV, then open this again."
                            else -> "There are no pictures on this drive."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                current != null -> PictureGrid(
                    files = current.files,
                    selected = selected,
                    onClick = { file -> selected = if (file in selected) selected - file else selected + file },
                    onFocused = { focusedName = it.name },
                    nextToRail = true,
                    railColumns = PickerColumns,
                    modifier = Modifier.weight(1f),
                )
                else -> FolderGrid(
                    folders = folders,
                    root = driveRoot,
                    selected = selected,
                    onOpen = { onOpenFolder(it.dir) },
                    onFocused = { focusedName = folderLabel(it.dir, driveRoot) },
                    railColumns = PickerColumns,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The folder's name, or "Drive root" for the top of the drive. */
private fun folderLabel(dir: File, root: File?): String = if (dir == root) "Drive root" else dir.name

/** Folders five across beside the rail, each showing a cover picture, its name and photo count, and how many are chosen. */
@Composable
private fun FolderGrid(
    folders: List<MediaFolder>,
    root: File?,
    selected: Set<File>,
    onOpen: (MediaFolder) -> Unit,
    onFocused: (MediaFolder) -> Unit,
    modifier: Modifier = Modifier,
    nextToRail: Boolean = true,
    railColumns: Int = RailColumns,
) {
    val columns = if (nextToRail) railColumns else GridColumns
    val cardWidth = gridCardWidth(columns, nextToRail)
    val gap = gridGap(nextToRail, columns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = if (nextToRail) railPadding(columns) else PaddingValues(vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(folders, key = { it.dir.path }) { folder ->
            val chosen = folder.files.count { it in selected }
            ThumbnailCard(width = cardWidth, onClick = { onOpen(folder) }, onFocused = { onFocused(folder) }) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = folder.files.first(),
                        contentDescription = folder.dir.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                            .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = folderLabel(folder.dir, root),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (folder.files.size == 1) "1 picture" else "${folder.files.size} pictures",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextSecondary,
                        )
                    }
                    if (chosen > 0) {
                        Text(
                            text = "$chosen chosen",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.Background,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Palette.Accent, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A rotating arc, drawn by hand because this app has no progress indicator library. */
@Composable
private fun Spinner(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "turn",
    )
    val track = Palette.SurfaceFocused
    val arc = Palette.Accent
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() / 9f, cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val box = Size(this.size.width - stroke.width, this.size.height - stroke.width)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = box, style = stroke)
        drawArc(arc, turn - 90f, 100f, useCenter = false, topLeft = Offset(inset, inset), size = box, style = stroke)
    }
}

/** The spinner with a message, centred in the space beside the rail. */
@Composable
private fun Loading(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Spinner()
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PicturesHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.SemiBold,
        color = Palette.TextPrimary,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Thumbnails four across; ticked ones are dimmed with a check. The gap leaves room for a focused card to grow. */
@Composable
private fun PictureGrid(
    files: List<File>,
    selected: Set<File>,
    onClick: (File) -> Unit,
    onFocused: (File) -> Unit,
    modifier: Modifier = Modifier,
    nextToRail: Boolean = false,
    railColumns: Int = RailColumns,
) {
    val columns = if (nextToRail) railColumns else GridColumns
    val cardWidth = gridCardWidth(columns, nextToRail)
    val gap = gridGap(nextToRail, columns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = if (nextToRail) railPadding(columns) else PaddingValues(vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(files, key = { it.path }) { file ->
            val ticked = file in selected
            ThumbnailCard(width = cardWidth, onClick = { onClick(file) }, onFocused = { onFocused(file) }) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = file,
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        alpha = if (ticked) 0.45f else 1f,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (ticked) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Chosen",
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
    }
}

/** Removing deletes the files from the TV, so ask first; the safe choice has focus. */
@Composable
private fun ConfirmRemove(files: List<File>, onRemove: () -> Unit, onKeep: () -> Unit) {
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keepFocus.requestFocus() } }
    val single = files.singleOrNull()
    Dialog(onDismissRequest = onKeep, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().backdrop()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (single != null) {
                    AsyncImage(
                        model = single,
                        contentDescription = single.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 400.dp, height = 225.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    // A few of them side by side, so it is clear what is about to go.
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        files.take(4).forEach { file ->
                            AsyncImage(
                                model = file,
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 176.dp, height = 99.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
                Text(
                    text = if (single != null) "Remove this picture from the slideshow?"
                    else "Remove ${files.size} pictures from the slideshow?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    modifier = Modifier.padding(top = 28.dp),
                )
                Text(
                    text = (if (single != null) "${single.name} is deleted from this TV." else "They are deleted from this TV.") +
                        " The copies on your USB drive are not touched.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .widthIn(max = 640.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(top = 32.dp)) {
                    ActionButton(
                        text = "Keep",
                        icon = Icons.Rounded.Close,
                        onClick = onKeep,
                        modifier = Modifier.width(170.dp).focusRequester(keepFocus),
                    )
                    ActionButton(text = "Remove", icon = Icons.Rounded.Delete, onClick = onRemove, modifier = Modifier.width(170.dp))
                }
            }
        }
    }
}

package dev.mahesh.icontrol.usb

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

sealed interface UsbScreen {
    data object Menu : UsbScreen
    data class Files(val dir: File) : UsbScreen

    /** The folder chooser for photos. */
    data object PhotoFolders : UsbScreen

    /** Photos of one folder, or of the whole drive when [folder] is null. */
    data class Photos(val folder: File?) : UsbScreen

    /** The folder chooser for videos. */
    data object VideoFolders : UsbScreen

    /** Videos of one folder, or of the whole drive when [folder] is null. */
    data class Videos(val folder: File?) : UsbScreen

    /**
     * [fromGrid]: the grid should return to the photo last shown. [photos] may be a shuffled copy of the grid's own
     * list, which is [source], so a photo's place in the grid is looked up there.
     */
    data class PhotoViewer(
        val photos: List<File>,
        val index: Int,
        val slideshow: Boolean,
        val fromGrid: Boolean,
        val source: List<File> = photos,
    ) : UsbScreen

    data class VideoPlayer(val videos: List<File>, val startIndex: Int = 0) : UsbScreen
}

/** Back stack of screens; Back pops one, and leaves the USB reader from the menu. */
@Composable
fun UsbApp(viewModel: UsbViewModel, onRequestPermission: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf<UsbScreen>(UsbScreen.Menu) }

    val photoFolders = remember(state.photos) { groupByFolder(state.photos) }
    val videoFolders = remember(state.videos) { groupByFolder(state.videos) }

    // Where each list was left. Only the top screen is composed, so a list screen is rebuilt from scratch every
    // time you come back to it; these let it scroll back to, and focus, the item you came from.
    var lastPhotoIndex by remember { mutableIntStateOf(-1) }
    var lastVideoIndex by remember { mutableIntStateOf(-1) }
    var lastPhotoFolderKey by remember { mutableStateOf<String?>(null) }
    var lastVideoFolderKey by remember { mutableStateOf<String?>(null) }
    val lastOpenedInFolder = remember { HashMap<String, String>() }
    // On by default, which is how the slideshow behaved before the option existed.
    var loopSlideshow by remember { mutableStateOf(true) }
    // Off by default: videos have always stopped at the end of the list.
    var loopVideos by remember { mutableStateOf(false) }

    val goBack: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onExit() }
    BackHandler(onBack = goBack)

    // Drive pulled out while browsing: nothing on the stack is valid any more.
    LaunchedEffect(state.drive == null) {
        if (state.drive == null && stack.size > 1) {
            stack.clear()
            stack.add(UsbScreen.Menu)
            lastPhotoIndex = -1
            lastVideoIndex = -1
            lastPhotoFolderKey = null
            lastVideoFolderKey = null
            lastOpenedInFolder.clear()
        }
    }

    when (val screen = stack.last()) {
        UsbScreen.Menu -> UsbMenuScreen(
            state = state,
            onFiles = {
                val drive = state.drive
                when {
                    drive == null -> Toast.makeText(context, "Plug in a USB drive first", Toast.LENGTH_SHORT).show()
                    !state.canRead -> Toast.makeText(context, "Allow access to the drive first", Toast.LENGTH_SHORT).show()
                    else -> stack.add(UsbScreen.Files(drive.root))
                }
            },
            onPhotos = { stack.add(UsbScreen.PhotoFolders) },
            onVideos = { stack.add(UsbScreen.VideoFolders) },
            onRequestPermission = onRequestPermission,
        )

        is UsbScreen.Files -> FileBrowserScreen(
            root = state.drive?.root ?: screen.dir,
            dir = screen.dir,
            restorePath = lastOpenedInFolder[screen.dir.path],
            onOpenFolder = {
                lastOpenedInFolder[screen.dir.path] = it.path
                stack.add(UsbScreen.Files(it))
            },
            onOpenFile = { file, siblings ->
                lastOpenedInFolder[screen.dir.path] = file.path
                when (UsbStorage.kindOf(file)) {
                    FileKind.Photo -> {
                        val photos = siblings.filter { UsbStorage.kindOf(it) == FileKind.Photo }
                        stack.add(UsbScreen.PhotoViewer(photos, photos.indexOf(file), slideshow = false, fromGrid = false))
                    }
                    FileKind.Video, FileKind.Audio -> stack.add(UsbScreen.VideoPlayer(listOf(file)))
                    else -> Toast.makeText(context, "iControl can't open this kind of file", Toast.LENGTH_SHORT).show()
                }
            },
        )

        UsbScreen.PhotoFolders -> MediaFoldersScreen(
            title = "Photos",
            noun = "photo",
            root = state.drive?.root,
            folders = photoFolders,
            total = state.photos.size,
            scanning = state.scanning,
            restoreKey = lastPhotoFolderKey,
            cover = { PhotoCover(it) },
            onOpen = { folder ->
                lastPhotoFolderKey = folder?.path ?: ALL_KEY
                lastPhotoIndex = -1
                stack.add(UsbScreen.Photos(folder))
            },
        )

        is UsbScreen.Photos -> {
            val photos = if (screen.folder == null) {
                state.photos
            } else {
                photoFolders.firstOrNull { it.dir == screen.folder }?.files.orEmpty()
            }
            PhotoGridScreen(
                title = screen.folder?.name ?: "All photos",
                subtitle = if (photos.size == 1) "1 photo" else "${photos.size} photos",
                photos = photos,
                scanning = state.scanning,
                restoreIndex = lastPhotoIndex,
                loopSlideshow = loopSlideshow,
                onLoopChange = { loopSlideshow = it },
                onOpen = {
                    lastPhotoIndex = it
                    stack.add(UsbScreen.PhotoViewer(photos, it, slideshow = false, fromGrid = true))
                },
                onSlideshow = { start ->
                    lastPhotoIndex = start
                    stack.add(UsbScreen.PhotoViewer(photos, start, slideshow = true, fromGrid = true))
                },
                onRandom = {
                    lastPhotoIndex = -1
                    stack.add(UsbScreen.PhotoViewer(photos.shuffled(), 0, slideshow = true, fromGrid = true, source = photos))
                },
            )
        }

        UsbScreen.VideoFolders -> MediaFoldersScreen(
            title = "Videos",
            noun = "video",
            root = state.drive?.root,
            folders = videoFolders,
            total = state.videos.size,
            scanning = state.scanning,
            restoreKey = lastVideoFolderKey,
            cover = { VideoThumbnail(it) },
            onOpen = { folder ->
                lastVideoFolderKey = folder?.path ?: ALL_KEY
                lastVideoIndex = -1
                stack.add(UsbScreen.Videos(folder))
            },
        )

        is UsbScreen.Videos -> {
            val videos = if (screen.folder == null) {
                state.videos
            } else {
                videoFolders.firstOrNull { it.dir == screen.folder }?.files.orEmpty()
            }
            VideoGridScreen(
                title = screen.folder?.name ?: "All videos",
                subtitle = if (videos.size == 1) "1 video" else "${videos.size} videos",
                videos = videos,
                scanning = state.scanning,
                restoreIndex = lastVideoIndex,
                loopVideos = loopVideos,
                onLoopChange = { loopVideos = it },
                onPlay = {
                    lastVideoIndex = it
                    stack.add(UsbScreen.VideoPlayer(listOf(videos[it])))
                },
                onPlayAll = { start ->
                    lastVideoIndex = start
                    stack.add(UsbScreen.VideoPlayer(videos, start))
                },
                onRandom = {
                    lastVideoIndex = -1
                    stack.add(UsbScreen.VideoPlayer(videos.shuffled()))
                },
            )
        }

        is UsbScreen.PhotoViewer -> PhotoViewerScreen(
            photos = screen.photos,
            startIndex = screen.index,
            startSlideshow = screen.slideshow,
            loopSlideshow = loopSlideshow,
            onIndexChange = {
                if (screen.fromGrid) {
                    lastPhotoIndex = if (screen.photos === screen.source) it else screen.source.indexOf(screen.photos[it])
                }
            },
        )

        is UsbScreen.VideoPlayer -> VideoPlayerScreen(
            videos = screen.videos,
            startIndex = screen.startIndex,
            loop = loopVideos,
            // The end-of-playback callback can fire after the screen is already gone; only leave if still showing.
            onFinished = { if (stack.lastOrNull() is UsbScreen.VideoPlayer) goBack() },
        )
    }
}

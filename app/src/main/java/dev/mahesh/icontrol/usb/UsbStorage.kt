package dev.mahesh.icontrol.usb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

enum class FileKind { Folder, Photo, Video, Audio, Other }

data class UsbDrive(val name: String, val root: File)

data class MediaScan(val photos: List<File>, val videos: List<File>)

object UsbStorage {

    /**
     * TEST HOOK: when true and /sdcard/iControlUsbTest exists, that folder is used instead of the real drive, so
     * the reader can be exercised with synthetic files. Must be false in anything shipped.
     */
    private const val TEST_ROOT_ENABLED = false
    private const val TEST_ROOT_NAME = "iControlUsbTest"

    private val PHOTO_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm", "3gp", "ts", "mpg", "mpeg", "wmv", "flv")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")

    private val SKIPPED_DIRS = setOf("Android", "LOST.DIR", "System Volume Information", "\$RECYCLE.BIN")

    /** Stops a huge drive from exhausting memory in the photo/video lists. */
    private const val MAX_SCANNED_FILES = 20_000

    fun kindOfName(name: String): FileKind {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in PHOTO_EXT -> FileKind.Photo
            in VIDEO_EXT -> FileKind.Video
            in AUDIO_EXT -> FileKind.Audio
            else -> FileKind.Other
        }
    }

    fun kindOf(file: File): FileKind = if (file.isDirectory) FileKind.Folder else kindOfName(file.name)

    fun findDrive(context: Context): UsbDrive? {
        if (TEST_ROOT_ENABLED) {
            val test = File(Environment.getExternalStorageDirectory(), TEST_ROOT_NAME)
            if (test.isDirectory) return UsbDrive("Test drive", test)
        }
        val manager = context.getSystemService(StorageManager::class.java)
        val volume = manager?.storageVolumes?.firstOrNull {
            it.isRemovable && it.state == Environment.MEDIA_MOUNTED && it.directory != null
        }
        volume?.directory?.let { dir ->
            return UsbDrive(volume.getDescription(context) ?: "USB drive", dir)
        }
        return null
    }

    fun canRead(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            Environment.isExternalStorageManager()

    /** Folders first, then files, each A-Z; hidden entries (leading dot) left out. */
    fun listFolder(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

    fun scan(root: File): MediaScan {
        val photos = ArrayList<File>()
        val videos = ArrayList<File>()
        var seen = 0
        val walk = root.walkTopDown().onEnter { dir ->
            dir == root || (!dir.name.startsWith(".") && dir.name !in SKIPPED_DIRS)
        }
        for (file in walk) {
            if (seen >= MAX_SCANNED_FILES) break
            if (file.name.startsWith(".") || !file.isFile) continue
            when (kindOfName(file.name)) {
                FileKind.Photo -> photos += file
                FileKind.Video -> videos += file
                else -> Unit
            }
            seen++
        }
        val byPath = compareBy<File> { it.path.lowercase() }
        return MediaScan(photos.sortedWith(byPath), videos.sortedWith(byPath))
    }
}

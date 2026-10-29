package dev.mahesh.icontrol.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import dev.mahesh.icontrol.usb.FileKind
import dev.mahesh.icontrol.usb.UsbStorage
import java.io.File
import java.io.IOException

/**
 * The pictures for the home-screen background and the screensaver: whatever photos sit directly in
 * Pictures/iControl on the TV's own storage. Android TV has no file picker, so a fixed folder is how pictures get in
 * (copy them there from a USB drive, or with adb).
 */
object SlideshowPhotos {

    const val FOLDER = "Pictures/iControl"

    /** Seconds each picture stays up; the settings button steps through these. */
    val Intervals = listOf(5, 10, 20, 30, 60)

    /** How much of the picture shows through behind the home screen, in percent; the settings button steps through these. */
    val Visibilities = listOf(50, 60, 70, 80, 90, 100)

    /** Keeps a huge folder from filling memory with file handles. */
    private const val MAX_PHOTOS = 300

    fun dir(): File = File(Environment.getExternalStorageDirectory(), FOLDER)

    /** Empty when the folder is missing or the app has no permission to read storage. */
    fun list(context: Context): List<File> {
        if (!UsbStorage.canRead(context)) return emptyList()
        return (dir().listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.startsWith(".") && UsbStorage.kindOfName(it.name) == FileKind.Photo }
            .sortedBy { it.name.lowercase() }
            .take(MAX_PHOTOS)
    }

    /** Adding and removing pictures changes files, which needs the write half of the storage permission. */
    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            Environment.isExternalStorageManager()

    data class AddResult(val added: Int, val alreadyThere: Int, val failed: Int)

    /**
     * Copies [sources] (photos on a USB drive) into the folder. A photo with the same name and size is skipped as
     * already added; one with the same name but different content gets a numbered name instead of overwriting.
     */
    fun add(sources: List<File>): AddResult {
        val target = dir()
        target.mkdirs()
        var added = 0
        var alreadyThere = 0
        var failed = 0
        for (source in sources) {
            val same = File(target, source.name)
            if (same.exists() && same.length() == source.length()) {
                alreadyThere++
                continue
            }
            val destination = uniqueFile(target, source.name)
            try {
                source.copyTo(destination)
                added++
            } catch (_: IOException) {
                destination.delete()
                failed++
            }
        }
        return AddResult(added, alreadyThere, failed)
    }

    /** Deletes the picture from the TV; false if Android refused. */
    fun remove(file: File): Boolean = file.delete()

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (candidate.exists()) candidate = File(dir, "$base ($n)${extension}").also { n++ }
        return candidate
    }
}

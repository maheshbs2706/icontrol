package dev.mahesh.icontrol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.media.ExifInterface
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import dev.mahesh.icontrol.data.PrefsStore
import dev.mahesh.icontrol.data.SlideshowPhotos
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The TV's screensaver: the pictures in [SlideshowPhotos.FOLDER], cross-fading full screen (whole pictures, not cropped) while the TV is idle.
 * Android TV has no lock screen, so this is what shows when nobody is using it. It is picked in
 * Settings, Device Preferences, Screensaver. Plain views rather than Compose: a dream has no activity to host it.
 */
class SlideshowDream : DreamService() {

    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = job
        job.launch { play(root) }
    }

    override fun onDreamingStopped() {
        scope?.cancel()
        scope = null
        super.onDreamingStopped()
    }

    private suspend fun play(root: FrameLayout) {
        val seconds = PrefsStore(applicationContext).slideshow.first().seconds
        val photos = withContext(Dispatchers.IO) { SlideshowPhotos.list(applicationContext) }
        if (photos.isEmpty()) {
            root.addView(
                TextView(this).apply {
                    text = "Add pictures to ${SlideshowPhotos.FOLDER} on this TV to see them here."
                    setTextColor(Color.LTGRAY)
                    textSize = 22f
                    gravity = Gravity.CENTER
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            return
        }
        var index = 0
        var previous: View? = null
        while (currentCoroutineContext().isActive) {
            val bitmap = withContext(Dispatchers.IO) { decode(photos[index % photos.size]) }
            index++
            if (bitmap != null) {
                val next = FrameLayout(this).apply {
                    alpha = 0f
                    // A blurred, darkened copy of the picture fills the screen behind the whole, uncropped picture.
                    addView(
                        ImageView(context).apply {
                            setImageBitmap(blurred(bitmap))
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            colorFilter = LightingColorFilter(0xFF707070.toInt(), 0)
                        },
                        matchParent(),
                    )
                    addView(
                        ImageView(context).apply {
                            setImageBitmap(bitmap)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        },
                        matchParent(),
                    )
                }
                root.addView(next, matchParent())
                val old = previous
                next.animate().alpha(1f).setDuration(FADE_MS).withEndAction { old?.let(root::removeView) }.start()
                previous = next
            }
            delay(seconds * 1_000L)
        }
    }

    private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    /**
     * A soft blur without RenderEffect (Android 12+): halve the picture down to a few dozen pixels, then let the view
     * stretch it back up smoothly.
     */
    private fun blurred(source: Bitmap): Bitmap {
        var small = source
        while (small.width / 2 >= BLUR_WIDTH * 2) {
            small = Bitmap.createScaledBitmap(small, small.width / 2, small.height / 2, true)
        }
        val height = (BLUR_WIDTH.toFloat() * source.height / source.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(small, BLUR_WIDTH, height, true)
    }

    /** Decodes at about screen size (a full-resolution photo would be tens of MB) and applies the camera's rotation. */
    private fun decode(file: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH && bounds.outHeight / (sample * 2) >= MAX_HEIGHT) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
    }.getOrNull()

    private companion object {
        const val FADE_MS = 1_500L
        const val MAX_WIDTH = 1920
        const val MAX_HEIGHT = 1080
        const val BLUR_WIDTH = 20
    }
}

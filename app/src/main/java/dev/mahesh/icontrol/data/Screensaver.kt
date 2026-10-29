package dev.mahesh.icontrol.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dev.mahesh.icontrol.SlideshowDream

/**
 * Switching the TV's screensaver to the slideshow from inside the app. The user can always do it in the TV's own
 * Settings (on this OnePlus TV: Personalize, Screensaver); this saves the trip. It needs WRITE_SECURE_SETTINGS, which
 * Android only lets adb grant: `adb shell pm grant dev.mahesh.icontrol android.permission.WRITE_SECURE_SETTINGS`.
 */
object Screensaver {

    private const val KEY = "screensaver_components"
    private const val PREFS = "screensaver"
    private const val PREVIOUS = "previous"

    /** What this TV used before, if nothing was remembered (Google's own screensaver). */
    private const val FALLBACK = "com.google.android.backdrop/.Backdrop"

    private fun component(context: Context) = ComponentName(context, SlideshowDream::class.java).flattenToString()

    fun isOn(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, KEY)?.contains(component(context)) == true

    fun canChange(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Turns the slideshow screensaver on, or puts back the one that was set before. False if Android refused. */
    fun set(context: Context, on: Boolean): Boolean {
        val resolver = context.contentResolver
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            if (on) {
                val current = Settings.Secure.getString(resolver, KEY)
                if (current != null && !current.contains(component(context))) {
                    prefs.edit().putString(PREVIOUS, current).apply()
                }
                Settings.Secure.putString(resolver, KEY, component(context)) &&
                    Settings.Secure.putInt(resolver, "screensaver_enabled", 1)
            } else {
                Settings.Secure.putString(resolver, KEY, prefs.getString(PREVIOUS, null) ?: FALLBACK)
            }
        } catch (_: SecurityException) {
            false
        }
    }
}

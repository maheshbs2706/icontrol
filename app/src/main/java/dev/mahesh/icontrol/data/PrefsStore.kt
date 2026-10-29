package dev.mahesh.icontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

/** The user's slideshow choices; the pictures themselves are read from [SlideshowPhotos]. */
data class SlideshowSettings(
    val homeBackground: Boolean = false,
    val seconds: Int = SlideshowPhotos.Intervals[1],
    /** Percent of the picture visible behind the home screen. */
    val visibility: Int = 90,
)

/** Persists which apps are pinned as favorites, in the order they were added, and the slideshow settings. */
class PrefsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val favorites: Flow<List<String>> = store.data.map { prefs ->
        prefs[FAVORITES]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun toggleFavorite(packageName: String) {
        store.edit { prefs ->
            val current = prefs[FAVORITES]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
            val updated = if (packageName in current) current - packageName else current + packageName
            prefs[FAVORITES] = updated.joinToString(SEPARATOR)
        }
    }

    val slideshow: Flow<SlideshowSettings> = store.data.map { prefs ->
        SlideshowSettings(
            homeBackground = prefs[SLIDESHOW_HOME] ?: false,
            seconds = prefs[SLIDESHOW_SECONDS] ?: SlideshowSettings().seconds,
            visibility = prefs[SLIDESHOW_VISIBILITY] ?: SlideshowSettings().visibility,
        )
    }

    suspend fun setSlideshowHome(enabled: Boolean) {
        store.edit { it[SLIDESHOW_HOME] = enabled }
    }

    /** Steps to the next entry of [SlideshowPhotos.Intervals], wrapping round. */
    suspend fun cycleSlideshowSeconds() {
        store.edit { prefs ->
            val current = prefs[SLIDESHOW_SECONDS] ?: SlideshowSettings().seconds
            val next = SlideshowPhotos.Intervals.firstOrNull { it > current } ?: SlideshowPhotos.Intervals.first()
            prefs[SLIDESHOW_SECONDS] = next
        }
    }

    /** Steps to the next entry of [SlideshowPhotos.Visibilities], wrapping round. */
    suspend fun cycleSlideshowVisibility() {
        store.edit { prefs ->
            val current = prefs[SLIDESHOW_VISIBILITY] ?: SlideshowSettings().visibility
            prefs[SLIDESHOW_VISIBILITY] = SlideshowPhotos.Visibilities.firstOrNull { it > current } ?: SlideshowPhotos.Visibilities.first()
        }
    }

    private companion object {
        const val SEPARATOR = "|"
        val FAVORITES = stringPreferencesKey("favorites")
        val SLIDESHOW_HOME = booleanPreferencesKey("slideshow_home")
        val SLIDESHOW_SECONDS = intPreferencesKey("slideshow_seconds")
        val SLIDESHOW_VISIBILITY = intPreferencesKey("slideshow_visibility")
    }
}

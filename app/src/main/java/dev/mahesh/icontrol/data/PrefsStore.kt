package dev.mahesh.icontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

/** Persists which apps are pinned as favorites, in the order they were added, and which input is the sound bar. */
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

    /** The input the user marked as their sound bar ([SoundbarLink.NONE] for "none"), or null if they never chose. */
    val soundbarInput: Flow<String?> = store.data.map { it[SOUNDBAR_INPUT] }

    suspend fun setSoundbarInput(id: String?) {
        store.edit { prefs ->
            if (id == null) prefs.remove(SOUNDBAR_INPUT) else prefs[SOUNDBAR_INPUT] = id
        }
    }

    private companion object {
        const val SEPARATOR = "|"
        val FAVORITES = stringPreferencesKey("favorites")
        val SOUNDBAR_INPUT = stringPreferencesKey("soundbar_input")
    }
}

package dev.mahesh.icontrol

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mahesh.icontrol.data.AppEntry
import dev.mahesh.icontrol.data.AppImage
import dev.mahesh.icontrol.data.AppRepository
import dev.mahesh.icontrol.data.InputEntry
import dev.mahesh.icontrol.data.PrefsStore
import dev.mahesh.icontrol.data.SoundbarLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(
    val loaded: Boolean = false,
    val favorites: List<AppEntry> = emptyList(),
    /** Every launchable app, for the "add favorite" picker. */
    val allApps: List<AppEntry> = emptyList(),
    val inputs: List<InputEntry> = emptyList(),
    val favoritePackages: Set<String> = emptySet(),
    /** The input that is (or is taken to be) the sound bar, if any. */
    val soundbarInputId: String? = null,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    private val prefs = PrefsStore(application)

    private val installed = MutableStateFlow<List<AppEntry>>(emptyList())
    private val inputs = MutableStateFlow<List<InputEntry>>(emptyList())
    private val loaded = MutableStateFlow(false)

    val state: StateFlow<HomeState> = combine(
        installed, inputs, prefs.favorites, loaded, prefs.soundbarInput,
    ) { apps, inputList, favs, isLoaded, soundbarChoice ->
        val byPackage = apps.associateBy { it.packageName }
        HomeState(
            loaded = isLoaded,
            favorites = favs.mapNotNull { byPackage[it] },
            allApps = apps,
            inputs = inputList,
            favoritePackages = favs.toSet(),
            soundbarInputId = SoundbarLink.pick(inputList, soundbarChoice)?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let(repository::evict)
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        application.registerReceiver(packageReceiver, filter)
        refresh()
    }

    private val _homePresses = MutableStateFlow(0)

    /** Counts Home presses, so the screen can close any open dialog and show the landing page again. */
    val homePresses: StateFlow<Int> = _homePresses

    fun onHomePressed() {
        _homePresses.value++
        reconnectSoundbarIfNeeded()
    }

    private var lastReconnect = 0L

    /**
     * Wakes the sound bar and re-establishes ARC when the link has dropped: tunes to the bar's input, waits for ARC to
     * come up, then returns to the launcher. Tried at most once every couple of minutes, so a bar that is switched
     * off on purpose doesn't pull the screen away on every Home press.
     */
    fun reconnectSoundbarIfNeeded() {
        val app = getApplication<Application>()
        if (SystemClock.elapsedRealtime() - lastReconnect < RECONNECT_COOLDOWN_MS && lastReconnect != 0L) return
        viewModelScope.launch {
            val input = SoundbarLink.pick(
                inputs.value.ifEmpty { withContext(Dispatchers.Default) { repository.loadInputs() } },
                prefs.soundbarInput.first(),
            ) ?: return@launch
            if (SoundbarLink.isLinked(app)) return@launch
            lastReconnect = SystemClock.elapsedRealtime()
            Toast.makeText(app, "Reconnecting the sound bar…", Toast.LENGTH_SHORT).show()
            app.startActivity(repository.inputIntent(input))
            // ARC usually takes a few seconds; give up waiting after 10.
            var waited = 0L
            while (waited < RECONNECT_MAX_WAIT_MS && !SoundbarLink.isLinked(app)) {
                delay(500)
                waited += 500
            }
            delay(1_000)
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    /** [id] is an input id, [SoundbarLink.NONE] for "none of them", or null to go back to guessing. */
    fun setSoundbarInput(id: String?) {
        viewModelScope.launch { prefs.setSoundbarInput(id) }
    }

    fun refresh() {
        viewModelScope.launch {
            val (apps, inputList) = withContext(Dispatchers.Default) {
                repository.loadApps() to repository.loadInputs()
            }
            installed.value = apps
            inputs.value = inputList
            loaded.value = true
        }
    }

    suspend fun image(app: AppEntry): AppImage? = repository.image(app)

    suspend fun icon(app: AppEntry): ImageBitmap? = repository.icon(app)

    fun toggleFavorite(app: AppEntry) {
        viewModelScope.launch { prefs.toggleFavorite(app.packageName) }
    }

    private companion object {
        const val RECONNECT_COOLDOWN_MS = 2 * 60_000L
        const val RECONNECT_MAX_WAIT_MS = 10_000L
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }
}

package dev.mahesh.icontrol

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mahesh.icontrol.data.AppEntry
import dev.mahesh.icontrol.data.AppImage
import dev.mahesh.icontrol.data.AppRepository
import dev.mahesh.icontrol.data.InputEntry
import dev.mahesh.icontrol.data.PrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    private val prefs = PrefsStore(application)

    private val installed = MutableStateFlow<List<AppEntry>>(emptyList())
    private val inputs = MutableStateFlow<List<InputEntry>>(emptyList())
    private val loaded = MutableStateFlow(false)

    val state: StateFlow<HomeState> = combine(
        installed, inputs, prefs.favorites, loaded,
    ) { apps, inputList, favs, isLoaded ->
        val byPackage = apps.associateBy { it.packageName }
        HomeState(
            loaded = isLoaded,
            favorites = favs.mapNotNull { byPackage[it] },
            allApps = apps,
            inputs = inputList,
            favoritePackages = favs.toSet(),
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

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }
}

package dev.mahesh.icontrol.usb

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsbState(
    val canRead: Boolean = false,
    val drive: UsbDrive? = null,
    val scanning: Boolean = false,
    val photos: List<File> = emptyList(),
    val videos: List<File> = emptyList(),
)

class UsbViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UsbState())
    val state: StateFlow<UsbState> = _state

    private var scanJob: Job? = null
    private var scannedRoot: File? = null

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        application.registerReceiver(mediaReceiver, filter)
        refresh()
    }

    /** Re-checks the drive and permission; rescans only when the drive or the permission has changed. */
    fun refresh() {
        val context = getApplication<Application>()
        val drive = UsbStorage.findDrive(context)
        val canRead = UsbStorage.canRead(context)
        _state.update { it.copy(canRead = canRead, drive = drive) }

        if (drive == null || !canRead) {
            scanJob?.cancel()
            scannedRoot = null
            _state.update { it.copy(scanning = false, photos = emptyList(), videos = emptyList()) }
            return
        }
        if (drive.root == scannedRoot) return

        scannedRoot = drive.root
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scanning = true, photos = emptyList(), videos = emptyList()) }
            val result = withContext(Dispatchers.IO) { UsbStorage.scan(drive.root) }
            _state.update { it.copy(scanning = false, photos = result.photos, videos = result.videos) }
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(mediaReceiver)
    }
}

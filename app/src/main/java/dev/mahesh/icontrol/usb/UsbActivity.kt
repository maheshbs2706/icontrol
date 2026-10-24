package dev.mahesh.icontrol.usb

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import dev.mahesh.icontrol.ui.IControlTheme

class UsbActivity : ComponentActivity() {

    private val viewModel: UsbViewModel by viewModels()

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This TV has ~1.8 GB of RAM; keep the photo cache small so full-screen photos don't starve it.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.12).build() }
                .build()
        }
        setContent {
            IControlTheme {
                UsbApp(
                    viewModel = viewModel,
                    onRequestPermission = { storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

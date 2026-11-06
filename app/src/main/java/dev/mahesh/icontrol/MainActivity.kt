package dev.mahesh.icontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mahesh.icontrol.audio.KeepAwakeAudio
import dev.mahesh.icontrol.ui.HomeScreen
import dev.mahesh.icontrol.ui.IControlTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps a soundbar awake while the home screen is in front, if the user has turned that on.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.keepSoundbarAwake.collectLatest { enabled ->
                    if (!enabled) return@collectLatest
                    KeepAwakeAudio.acquire()
                    try {
                        awaitCancellation()
                    } finally {
                        KeepAwakeAudio.release()
                    }
                }
            }
        }
        setContent {
            IControlTheme {
                HomeScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home arrives here (singleTask): go back to the landing page, not whatever dialog was left open.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        // Pick up app/input changes that happened while another app was in front.
        viewModel.refresh()
    }
}

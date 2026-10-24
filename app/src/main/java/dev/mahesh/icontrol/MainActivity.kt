package dev.mahesh.icontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.mahesh.icontrol.ui.HomeScreen
import dev.mahesh.icontrol.ui.IControlTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IControlTheme {
                HomeScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up app/input changes that happened while another app was in front.
        viewModel.refresh()
    }
}

package dev.mahesh.icontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.mahesh.icontrol.data.AdbReboot
import kotlinx.coroutines.launch

private enum class RestartStep { Confirm, Working, NoAdb }

/**
 * Asks before restarting the TV, so a stray click can't do it. The restart goes through the TV's own ADB service (see
 * [AdbReboot]); the first time, the TV shows an "Allow debugging?" prompt that needs a tick on Always allow.
 */
@Composable
fun RestartDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(RestartStep.Confirm) }
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { firstButton.requestFocus() } }

    Dialog(
        // Back is ignored while the restart is under way.
        onDismissRequest = { if (step != RestartStep.Working) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = Palette.Surface),
            modifier = Modifier.width(520.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(28.dp),
            ) {
                Text(
                    text = when (step) {
                        RestartStep.Confirm -> "Restart the TV?"
                        RestartStep.Working -> "Restarting…"
                        RestartStep.NoAdb -> "Can't restart from here"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                )
                Text(
                    text = when (step) {
                        RestartStep.Confirm -> "The TV will switch off and start again. This takes about a minute."
                        RestartStep.Working ->
                            "If the TV asks \"Allow USB debugging?\", tick Always allow and choose OK."
                        RestartStep.NoAdb ->
                            "The TV did not allow it. Turn on USB debugging or ADB over network in Developer " +
                                "options, then try again and choose Always allow when asked."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                when (step) {
                    RestartStep.Confirm -> {
                        Button(
                            onClick = {
                                step = RestartStep.Working
                                scope.launch {
                                    // Success needs no handling: the TV is on its way down.
                                    if (AdbReboot.restart(context) == AdbReboot.Result.NoAdb) step = RestartStep.NoAdb
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(firstButton),
                        ) { Text("Restart") }
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    }
                    RestartStep.Working -> Unit
                    RestartStep.NoAdb -> Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(firstButton),
                    ) { Text("Close") }
                }
            }
        }
    }
}

package dev.mahesh.icontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.mahesh.icontrol.data.InputEntry

/**
 * Long-press menu on an input: says whether it is the sound bar. The sound bar's input is what iControl tunes to
 * on Home to wake the bar and bring its ARC link back.
 */
@Composable
fun SoundbarDialog(
    input: InputEntry,
    isSoundbar: Boolean,
    onSet: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = Palette.Surface),
            modifier = Modifier.width(420.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = input.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "If the sound bar's sound drops out, pressing Home brings it back by switching to " +
                        "its input for a few seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Button(
                    onClick = {
                        onSet(!isSoundbar)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstButton),
                ) {
                    Text(if (isSoundbar) "This is not my sound bar" else "This is my sound bar")
                }
            }
        }
    }
}

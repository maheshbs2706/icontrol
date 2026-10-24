package dev.mahesh.icontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.data.AppEntry
import dev.mahesh.icontrol.data.AppImage

private const val PickerColumns = 5

// Smaller than CardGap so five columns fit; still wider than the 15% a focused card grows by.
private val PickerGap = 28.dp

/** Full-screen grid of every installed app; selecting one toggles it in Favorites. Back closes. */
@Composable
fun AppPickerDialog(
    apps: List<AppEntry>,
    favoritePackages: Set<String>,
    loadImage: suspend (AppEntry) -> AppImage?,
    onToggle: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .backdrop()
                .padding(top = 36.dp),
        ) {
            Text(
                text = "Add favorites",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            Text(
                text = "Select an app to add or remove it. Press Back when you're done.",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(start = ScreenPadding, top = 4.dp, bottom = 12.dp),
            )
            val cardWidth = (LocalConfiguration.current.screenWidthDp.dp - ScreenPadding * 2 - PickerGap * (PickerColumns - 1)) /
                PickerColumns
            LazyVerticalGrid(
                columns = GridCells.Fixed(PickerColumns),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(PickerGap),
                verticalArrangement = Arrangement.spacedBy(PickerGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val added = app.packageName in favoritePackages
                    AppCard(
                        app = app,
                        width = cardWidth,
                        loadImage = loadImage,
                        onClick = { onToggle(app) },
                        onLongClick = null,
                        onFocused = {},
                        selected = added,
                        modifier = if (app == apps.first()) {
                            Modifier.optionalFocusRequester(firstFocus)
                        } else Modifier,
                    )
                }
            }
        }
    }
}

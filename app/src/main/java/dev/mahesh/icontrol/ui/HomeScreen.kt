package dev.mahesh.icontrol.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.LauncherViewModel
import dev.mahesh.icontrol.data.AppEntry
import dev.mahesh.icontrol.usb.UsbActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private data class Shortcut(
    val label: String,
    val icon: ImageVector,
    val key: String,
    /** Null for tiles that open a screen inside the launcher instead of another activity. */
    val intent: ((Context) -> Intent)?,
)

private val SystemShortcuts = listOf(
    Shortcut("USB", Icons.Rounded.Usb, "usb") { Intent(it, UsbActivity::class.java) },
    Shortcut("Mirror", Icons.Rounded.Cast, "mirror", intent = null),
    Shortcut("Apps", Icons.Rounded.Tune, "manage-apps", intent = null),
    Shortcut("Wi-Fi", Icons.Rounded.Wifi, "wifi") { Intent(Settings.ACTION_WIFI_SETTINGS) },
    Shortcut("Sound", Icons.Rounded.VolumeUp, "sound") { Intent(Settings.ACTION_SOUND_SETTINGS) },
    Shortcut("Settings", Icons.Rounded.Settings, "settings") { Intent(Settings.ACTION_SETTINGS) },
)

@Composable
fun HomeScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasInputs = state.inputs.isNotEmpty()
    var focusedLabel by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showAllApps by remember { mutableStateOf(false) }
    var showMirror by remember { mutableStateOf(false) }
    var showManageApps by remember { mutableStateOf(false) }
    // First tile of the apps row: the first favorite, or "Add app" while there are none.
    val favoriteFocus = remember { FocusRequester() }
    val favoritesState = rememberLazyListState()

    // The row can be scrolled away from its first tile (favorites that load late are inserted off-screen to the
    // left), so bring it back first; the tile may also take a moment to be laid out, hence the retries.
    suspend fun focusFirstFavorite() {
        favoritesState.scrollToItem(0)
        repeat(10) {
            if (runCatching { favoriteFocus.requestFocus() }.getOrDefault(false)) return
            delay(100)
        }
    }

    LaunchedEffect(state.loaded) {
        if (state.loaded) focusFirstFavorite()
    }

    // Home closes every dialog (the mirror guide is left open behind the receiver app, for one).
    val homePresses by viewModel.homePresses.collectAsStateWithLifecycle()
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            menuApp = null
            showPicker = false
            showAllApps = false
            showMirror = false
            showManageApps = false
            // Let the dialogs leave composition first, so focus doesn't return to whatever opened them.
            delay(150)
            focusFirstFavorite()
        }
    }

    val launch: (Intent) -> Unit = { intent -> context.launchSafely(intent) }
    val onFocused: (String) -> Unit = { focusedLabel = it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .backdrop()
            .padding(top = 12.dp),
    ) {
        Header(focusedLabel = focusedLabel)

        // A plain column rather than a list: the page is sized to fit, so moving between rows never scrolls it.
        Column(modifier = Modifier.fillMaxSize()) {
            if (hasInputs) {
                SectionRow("Inputs") {
                    CardRow(verticalPadding = TileFocusPadding, gap = 24.dp) {
                        items(state.inputs, key = { "input-" + it.id }) { input ->
                            ShortcutTile(
                                label = input.label,
                                icon = Icons.Rounded.Cable,
                                onClick = { launch(viewModel.repository.inputIntent(input)) },
                                onFocused = onFocused,
                                width = 200.dp,
                            )
                        }
                    }
                }
            }

            SectionRow("Favorite apps") {
                CardRow(
                    state = favoritesState,
                    verticalPadding = FavoriteFocusPadding,
                    pinnedItemWidth = FavoriteCardWidth,
                    gap = FavoriteGap,
                ) {
                    items(state.favorites, key = { "fav-" + it.packageName }) { app ->
                        AppCard(
                            app = app,
                            width = FavoriteCardWidth,
                            loadImage = viewModel::image,
                            onClick = { launch(viewModel.repository.launchIntent(app)) },
                            onLongClick = { menuApp = app },
                            onFocused = onFocused,
                            focusedScale = FavoriteFocusedScale,
                            modifier = if (app == state.favorites.first()) {
                                Modifier.focusRequester(favoriteFocus)
                            } else Modifier,
                        )
                    }
                    item(key = "add-favorite") {
                        ActionTile(
                            icon = Icons.Rounded.Add,
                            label = "Add app",
                            focusedLabel = "Add a favorite app",
                            width = FavoriteCardWidth,
                            onClick = { showPicker = true },
                            onFocused = onFocused,
                            focusedScale = FavoriteFocusedScale,
                            modifier = if (state.favorites.isEmpty()) {
                                Modifier.focusRequester(favoriteFocus)
                            } else Modifier,
                        )
                    }
                    item(key = "all-apps") {
                        ActionTile(
                            icon = Icons.Rounded.Apps,
                            label = "All apps",
                            focusedLabel = "All apps",
                            width = FavoriteCardWidth,
                            onClick = { showAllApps = true },
                            onFocused = onFocused,
                            focusedScale = FavoriteFocusedScale,
                        )
                    }
                }
            }

            SectionRow("System") {
                // Six narrower tiles with a tighter gap so the row fits the screen (a focused tile grows ~18dp each side).
                CardRow(verticalPadding = TileFocusPadding, gap = 14.dp) {
                    items(SystemShortcuts, key = { it.key }) { shortcut ->
                        ShortcutTile(
                            label = shortcut.label,
                            icon = shortcut.icon,
                            onClick = {
                                val intent = shortcut.intent
                                when {
                                    intent != null -> launch(intent(context))
                                    shortcut.key == "manage-apps" -> showManageApps = true
                                    else -> showMirror = true
                                }
                            },
                            onFocused = onFocused,
                            width = 132.dp,
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps = state.allApps,
            favoritePackages = state.favoritePackages,
            loadImage = viewModel::image,
            onToggle = viewModel::toggleFavorite,
            onDismiss = { showPicker = false },
        )
    }

    if (showMirror) {
        MirrorGuideDialog(onDismiss = { showMirror = false })
    }

    if (showManageApps) {
        ManageAppsDialog(repository = viewModel.repository, onDismiss = { showManageApps = false })
    }

    if (showAllApps) {
        AllAppsDialog(
            apps = state.allApps,
            loadImage = viewModel::image,
            loadIcon = viewModel::icon,
            onLaunch = { app -> launch(viewModel.repository.launchIntent(app)) },
            onDismiss = { showAllApps = false },
        )
    }

    menuApp?.let { app ->
        AppMenuDialog(
            app = app,
            onRemoveFavorite = { viewModel.toggleFavorite(app) },
            onAppInfo = {
                launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
                )
            },
            onUninstall = {
                launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))
            },
            onDismiss = { menuApp = null },
        )
    }
}

// Favorites is the main row, so its cards are the largest thing on the home screen.
private val FavoriteCardWidth = 230.dp
private const val FavoriteFocusedScale = FocusedScale

// The selected card grows 15% of its width each side (about 35dp), so the gap has to exceed that or it covers
// its neighbours.
private val FavoriteGap = 56.dp

// Room each row needs above/below so a focused card isn't clipped by the lazy list: 15% of its height, rounded up.
// Tiles are 52dp tall; favorite cards are about 129dp tall.
private val TileFocusPadding = 12.dp
private val FavoriteFocusPadding = 24.dp

@Composable
private fun Header(focusedLabel: String) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val time = now.format(DateTimeFormatter.ofPattern("h:mm", Locale.getDefault()))
    val meridiem = now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    color = Palette.TextPrimary,
                )
                Text(
                    text = meridiem,
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.SectionTitle,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.SectionTitle,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
            MemoryUsage(modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = focusedLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

internal fun Context.launchSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(this, "Can't open that on this TV", Toast.LENGTH_SHORT).show()
    }
}

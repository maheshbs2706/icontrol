package dev.mahesh.icontrol.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.SlideshowState
import dev.mahesh.icontrol.data.Screensaver
import dev.mahesh.icontrol.data.SlideshowPhotos
import coil3.compose.AsyncImage
import dev.mahesh.icontrol.usb.ActionButton
import dev.mahesh.icontrol.usb.ThumbnailCard
import java.io.File

/**
 * Settings for the picture slideshow, which backs both the home screen and the TV's screensaver. The pictures are
 * whatever sits in [SlideshowPhotos.FOLDER] on the TV.
 */
@Composable
fun SlideshowDialog(
    state: SlideshowState,
    onHomeBackground: (Boolean) -> Unit,
    onCycleSeconds: () -> Unit,
    onCycleVisibility: () -> Unit,
    onStorageAllowed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showPictures by remember { mutableStateOf(false) }
    val askForStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onStorageAllowed() }
    LaunchedEffect(Unit) {
        if (!state.canRead) askForStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    // Looked up again on return, so the button flips once the user has chosen iControl in Settings.
    var screensaverOn by remember { mutableStateOf(Screensaver.isOn(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { screensaverOn = Screensaver.isOn(context) }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The options in a column on the left; a live preview of the slideshow on the right.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .backdrop()
                .padding(horizontal = ScreenPadding),
        ) {
            // Every button fills this width, so they are all the same size.
            Column(modifier = Modifier.width(320.dp)) {
                Text(
                    text = "Slideshow",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                )
                Text(
                    text = when {
                        !state.canRead -> "Allow iControl to read files on this TV, then reopen this screen."
                        state.photos.isEmpty() ->
                            "No pictures yet. Copy some into the \"${SlideshowPhotos.FOLDER}\" folder on this TV."
                        else -> {
                            val count = state.photos.size
                            "$count picture${if (count == 1) "" else "s"} in \"${SlideshowPhotos.FOLDER}\""
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    ActionButton(
                        text = "Pictures (${state.photos.size})",
                        icon = Icons.Rounded.PhotoLibrary,
                        onClick = { showPictures = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(
                        text = "Home background: ${if (state.settings.homeBackground) "On" else "Off"}",
                        icon = Icons.Rounded.Image,
                        onClick = { onHomeBackground(!state.settings.homeBackground) },
                        modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                    )
                    ActionButton(
                        text = "Picture visibility: ${state.settings.visibility}%",
                        icon = Icons.Rounded.Visibility,
                        onClick = onCycleVisibility,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(
                        text = "Change every ${state.settings.seconds} seconds",
                        icon = Icons.Rounded.Timer,
                        onClick = onCycleSeconds,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(
                        text = if (screensaverOn) "Screensaver: On" else "Screensaver: Off",
                        icon = Icons.Rounded.Slideshow,
                        onClick = {
                            when {
                                // Switch it here when the app has been allowed to (see the README); otherwise point to the TV's own screen.
                                Screensaver.canChange(context) -> {
                                    if (Screensaver.set(context, !screensaverOn)) {
                                        screensaverOn = Screensaver.isOn(context)
                                    } else {
                                        Toast.makeText(context, "Couldn't change the screensaver", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                else -> {
                                    Toast.makeText(
                                        context,
                                        "Choose it in Settings, Personalize, Screensaver",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    openScreensaverSettings(context)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(text = "Close", icon = Icons.Rounded.Close, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
            SlideshowPreview(state = state, modifier = Modifier.weight(1f).padding(start = 40.dp))
        }
    }

    if (showPictures) {
        SlideshowPicturesDialog(
            photos = state.photos,
            onChanged = onStorageAllowed,
            onDismiss = { showPictures = false },
        )
    }
}

/**
 * The picture the slideshow is showing, as it will look behind the home screen (visibility, whole picture, blurred
 * margins), cycling at the chosen interval, with its name and position underneath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlideshowPreview(state: SlideshowState, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<File?>(null) }
    // The thumbnail the user is on, shown instead of the cycling picture; null when focus is elsewhere.
    var pinned by remember { mutableStateOf<File?>(null) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Surface),
        ) {
            if (state.photos.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = Palette.TextSecondary,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        text = "Your pictures show here",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else {
                SlideshowBackground(
                    photos = state.photos,
                    seconds = state.settings.seconds,
                    visibility = state.settings.visibility,
                    onShown = { shown = it },
                    pinned = pinned,
                )
            }
        }
        val file = shown
        Text(
            text = when {
                state.photos.isEmpty() || file == null -> "Preview"
                else -> "${file.name}  (${state.photos.indexOf(file) + 1} of ${state.photos.size})"
            },
            style = MaterialTheme.typography.titleMedium,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = if (state.settings.homeBackground) "How the home screen background looks" else "Home background is off",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (state.photos.isNotEmpty()) {
            // Every picture as a thumbnail: move onto one to see it above; the cycling resumes when focus leaves.
            CompositionLocalProvider(LocalBringIntoViewSpec provides CenterFocusSpec) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .onFocusChanged { if (!it.hasFocus) pinned = null },
                ) {
                    items(state.photos, key = { it.path }) { photo ->
                        ThumbnailCard(
                            width = 92.dp,
                            onClick = { pinned = photo },
                            onFocused = { pinned = photo },
                        ) {
                            AsyncImage(
                                model = photo,
                                contentDescription = photo.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The screensaver chooser if this TV has one, otherwise the main Settings screen. */
private fun openScreensaverSettings(context: Context) {
    val chooser = Intent(Settings.ACTION_DREAM_SETTINGS)
    val target = if (chooser.resolveActivity(context.packageManager) != null) chooser else Intent(Settings.ACTION_SETTINGS)
    context.launchSafely(target)
}

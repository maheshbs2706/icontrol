package dev.mahesh.icontrol.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.data.AppRepository
import dev.mahesh.icontrol.data.ManagedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every app on the TV in one list, each with the actions that apply to it. Android only lets the system change another
 * app's enabled state, so Enable and Disable open that app's App info page, where the real button is. Uninstall asks
 * Android to remove it, which it does for apps the user installed and for updates over a system app.
 * The list is rebuilt whenever we come back from one of those screens.
 */
@Composable
fun ManageAppsDialog(repository: AppRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<ManagedApp>?>(null) }
    suspend fun reload() {
        apps = withContext(Dispatchers.IO) { repository.loadManagedApps() }
    }
    LaunchedEffect(Unit) { reload() }
    // The first row's Disable/Enable button takes focus once, when the list first appears.
    val firstFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(apps) {
        if (apps?.isNotEmpty() == true && !focused) {
            focused = runCatching { firstFocus.requestFocus() }.isSuccess
        }
    }
    var resumed by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    LaunchedEffect(resumed) { if (resumed > 1) reload() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .backdrop()
                .padding(horizontal = 80.dp, vertical = 24.dp),
        ) {
            Text(
                text = "Apps",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
            )
            Text(
                text = "Disable and Enable open the app's settings page, where you press the button.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            val list = apps
            if (list != null) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(list, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            repository = repository,
                            context = context,
                            modifier = if (app == list.first()) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: ManagedApp, repository: AppRepository, context: Context, modifier: Modifier = Modifier) {
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = repository.managedIcon(app.packageName)
    }
    val details = remember(app) { appDetailsIntent(app.packageName) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            icon?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (app.isEnabled) Palette.TextPrimary else Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = listOfNotNull("System".takeIf { app.isSystem }, "Disabled".takeIf { !app.isEnabled })
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(min = 0.dp)) {
            // Android only offers Disable for system apps; an app the user installed can only be uninstalled.
            val canToggle = app.isSystem || !app.isEnabled
            if (canToggle) {
                RowButton(if (app.isEnabled) "Disable" else "Enable", modifier) { context.launchSafely(details) }
            }
            if (app.canUninstall) {
                RowButton("Uninstall", if (canToggle) Modifier else modifier) {
                    context.launchSafely(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))
                }
            }
        }
    }
}

@Composable
private fun RowButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, scale = ButtonDefaults.scale(focusedScale = 1.08f), modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

private fun appDetailsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

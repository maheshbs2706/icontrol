package dev.mahesh.icontrol.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.usb.ActionButton
import dev.mahesh.icontrol.usb.KeepScreenOn

/**
 * How to mirror a phone onto this TV. The user first says which phone they have. Android uses the TV's own Google
 * Cast receiver (an app cannot be a receiver itself), so that path just tells the user the TV's name and where to tap.
 * An iPhone needs an AirPlay receiver app installed on the TV; this opens it and walks through the iPhone's side.
 * When mirroring starts the receiver takes the screen over, and the user lands back here when it ends.
 */
@Composable
fun MirrorGuideDialog(onDismiss: () -> Unit) {
    var phone by remember { mutableStateOf<PhoneKind?>(null) }
    val context = LocalContext.current
    // The name the Cast receiver advertises is the TV's device name.
    val tvName = remember {
        Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
    // Null while the name can't be read: no location permission, or the TV's location setting is off.
    var wifiName by remember { mutableStateOf(currentWifiName(context)) }
    val askForLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        wifiName = currentWifiName(context)
    }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askForLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(phone) { runCatching { firstFocus.requestFocus() } }
    KeepScreenOn()

    Dialog(
        // Back steps out of a guide to the phone choice before it closes the dialog.
        onDismissRequest = { if (phone == null) onDismiss() else phone = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .backdrop(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (phone) {
                    null -> PhoneChoice(
                        onPick = { phone = it },
                        onClose = onDismiss,
                        firstFocus = firstFocus,
                    )
                    PhoneKind.Android -> AndroidGuide(
                        tvName = tvName,
                        wifiName = wifiName,
                        onBack = { phone = null },
                        firstFocus = firstFocus,
                    )
                    PhoneKind.IPhone -> IPhoneGuide(
                        wifiName = wifiName,
                        onBack = { phone = null },
                        firstFocus = firstFocus,
                    )
                }
            }
        }
    }
}

private enum class PhoneKind { Android, IPhone }

/** The AirPlay receiver apps we know how to open, best first. */
private val AirPlayReceivers = listOf("com.neoyantra.airplaymirror.airplaymirrorappdemo")

private fun airPlayReceiverIntent(context: Context): Intent? {
    val pm = context.packageManager
    return AirPlayReceivers.firstNotNullOfOrNull {
        pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it)
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
private fun GuideTitle(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.SemiBold,
        color = Palette.TextPrimary,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(top = 18.dp),
    )
}

@Composable
private fun PhoneChoice(onPick: (PhoneKind) -> Unit, onClose: () -> Unit, firstFocus: FocusRequester) {
    GuideTitle("Mirror your phone", "Which phone do you have?")
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(top = 36.dp)) {
        ActionButton(
            text = "Android phone",
            icon = Icons.Rounded.PhoneAndroid,
            onClick = { onPick(PhoneKind.Android) },
            modifier = Modifier.focusRequester(firstFocus),
        )
        ActionButton(text = "iPhone", icon = Icons.Rounded.PhoneIphone, onClick = { onPick(PhoneKind.IPhone) })
    }
    ActionButton(
        text = "Close",
        icon = Icons.Rounded.Close,
        onClick = onClose,
        modifier = Modifier.padding(top = 36.dp),
    )
}

@Composable
private fun AndroidGuide(tvName: String, wifiName: String?, onBack: () -> Unit, firstFocus: FocusRequester) {
    GuideTitle("Mirror your Android phone", "Look for this name on your phone")
    Text(
        text = tvName,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = Palette.Background,
        modifier = Modifier
            .padding(top = 8.dp)
            .background(Palette.Accent, RoundedCornerShape(28.dp))
            .padding(horizontal = 36.dp, vertical = 10.dp),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(top = 28.dp)
            .widthIn(max = 620.dp),
    ) {
        Step(
            1,
            wifiName?.let { "Connect your phone to the Wi-Fi \"$it\", the same one this TV is on." }
                ?: "Connect your phone to the same Wi-Fi as this TV.",
        )
        Step(2, "Swipe down on your phone and tap Cast, Smart View or Screen mirroring. The name differs by phone.")
        Step(3, "Choose \"$tvName\". Your phone's screen appears here.")
    }
    ActionButton(
        text = "Back",
        icon = Icons.Rounded.Close,
        onClick = onBack,
        modifier = Modifier
            .padding(top = 28.dp)
            .focusRequester(firstFocus),
    )
}

@Composable
private fun IPhoneGuide(wifiName: String?, onBack: () -> Unit, firstFocus: FocusRequester) {
    val context = LocalContext.current
    val receiver = remember { airPlayReceiverIntent(context) }
    GuideTitle("Mirror your iPhone", "Uses the AirPlay receiver app on this TV")
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(top = 28.dp)
            .widthIn(max = 620.dp),
    ) {
        Step(
            1,
            wifiName?.let { "Connect your iPhone to the Wi-Fi \"$it\", the same one this TV is on." }
                ?: "Connect your iPhone to the same Wi-Fi as this TV.",
        )
        Step(2, "Open the AirPlay receiver below and leave it on the TV.")
        Step(3, "On your iPhone, swipe down for Control Center and tap Screen Mirroring.")
        Step(4, "Choose the TV from the list. Your iPhone's screen appears here.")
    }
    if (receiver == null) {
        Text(
            text = "No AirPlay receiver app is installed on this TV yet.",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(top = 28.dp)) {
        if (receiver != null) {
            ActionButton(
                text = "Open AirPlay receiver",
                icon = Icons.Rounded.PlayArrow,
                onClick = { runCatching { context.startActivity(receiver) } },
                modifier = Modifier.focusRequester(firstFocus),
            )
            ActionButton(text = "Back", icon = Icons.Rounded.Close, onClick = onBack)
        } else {
            ActionButton(
                text = "Back",
                icon = Icons.Rounded.Close,
                onClick = onBack,
                modifier = Modifier.focusRequester(firstFocus),
            )
        }
    }
}

/**
 * The name of the Wi-Fi network the TV is on, or null if Android won't say. It only reveals it with location
 * permission and the system location setting on; otherwise it returns a placeholder, which is treated as unknown.
 */
@Suppress("DEPRECATION")
private fun currentWifiName(context: Context): String? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    val ssid = wifi.connectionInfo?.ssid?.removeSurrounding("\"") ?: return null
    return ssid.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(Palette.Surface, CircleShape),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Palette.Accent,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

package dev.mahesh.icontrol.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Laptop
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.mahesh.icontrol.usb.ActionButton
import dev.mahesh.icontrol.usb.KeepScreenOn

/**
 * How to mirror a phone or laptop onto this TV. The user first says which device they have; a laptop uses Chrome's
 * Cast, which talks to the same receiver as Android. Android uses the TV's own Google
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
                    PhoneKind.Laptop -> LaptopGuide(
                        tvName = tvName,
                        wifiName = wifiName,
                        onBack = { phone = null },
                        firstFocus = firstFocus,
                    )
                }
            }
        }
    }
}

private enum class PhoneKind { Android, IPhone, Laptop }

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

/** One of the equally sized device cards on the first screen. */
@Composable
private fun ChoiceCard(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = focusColors(),
        border = focusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        modifier = modifier.size(width = 280.dp, height = 240.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(88.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun PhoneChoice(onPick: (PhoneKind) -> Unit, onClose: () -> Unit, firstFocus: FocusRequester) {
    GuideTitle("Mirror","What do you want to show on this TV?")
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp), modifier = Modifier.padding(top = 36.dp)) {
        ChoiceCard(
            text = "Android phone",
            icon = Icons.Rounded.PhoneAndroid,
            onClick = { onPick(PhoneKind.Android) },
            modifier = Modifier.focusRequester(firstFocus),
        )
        ChoiceCard(text = "iPhone", icon = Icons.Rounded.PhoneIphone, onClick = { onPick(PhoneKind.IPhone) })
        ChoiceCard(text = "Laptop", icon = Icons.Rounded.Laptop, onClick = { onPick(PhoneKind.Laptop) })
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

/** A laptop casts through the TV's own Google Cast receiver, using Chrome, which works on Windows, Mac and Chromebook. */
@Composable
private fun LaptopGuide(tvName: String, wifiName: String?, onBack: () -> Unit, firstFocus: FocusRequester) {
    GuideTitle("Mirror your laptop", "Look for this name in Chrome on your laptop")
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
            wifiName?.let { "Connect your laptop to the Wi-Fi \"$it\", the same one this TV is on." }
                ?: "Connect your laptop to the same Wi-Fi as this TV.",
        )
        Step(2, "In Chrome, click the three dots at the top right, then Save, share and cast, then Cast.")
        Step(3, "Click Sources and choose Cast screen. To hear sound, tick Share system audio.")
        Step(4, "Choose \"$tvName\". Your laptop's screen appears here.")
    }
    Text(
        text = "An HDMI cable from the laptop to the TV also works, with no delay.",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 18.dp)
            .widthIn(max = 620.dp),
    )
    ActionButton(
        text = "Back",
        icon = Icons.Rounded.Close,
        onClick = onBack,
        modifier = Modifier
            .padding(top = 22.dp)
            .focusRequester(firstFocus),
    )
}

@Composable
private fun IPhoneGuide(wifiName: String?, onBack: () -> Unit, firstFocus: FocusRequester) {
    val context = LocalContext.current
    // Looked up again each time we come back, so the button flips once the user has installed the receiver.
    var receiver by remember { mutableStateOf(airPlayReceiverIntent(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { receiver = airPlayReceiverIntent(context) }
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
    val launch = receiver
    if (launch == null) {
        Text(
            text = "To mirror an iPhone, this TV needs a free AirPlay receiver app. Install one, then come back here.",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 22.dp)
                .widthIn(max = 620.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(top = 28.dp)) {
        if (launch != null) {
            ActionButton(
                text = "Open AirPlay receiver",
                icon = Icons.Rounded.PlayArrow,
                onClick = { runCatching { context.startActivity(launch) } },
                modifier = Modifier.focusRequester(firstFocus),
            )
        } else {
            ActionButton(
                text = "Get it on Google Play",
                icon = Icons.Rounded.Download,
                onClick = { openReceiverInStore(context) },
                modifier = Modifier.focusRequester(firstFocus),
            )
        }
        ActionButton(text = "Back", icon = Icons.Rounded.Close, onClick = onBack)
    }
    if (launch == null) {
        Text(
            text = "No Play Store? Search for \"AirPlayMirror Lite\" in your TV's app store.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .widthIn(max = 620.dp),
        )
    }
}

/** Opens the receiver's Play Store page; the web page if the Store app can't take the market link. */
private fun openReceiverInStore(context: Context) {
    val id = AirPlayReceivers.first()
    fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(view("market://details?id=$id")) }
        .recoverCatching { context.startActivity(view("https://play.google.com/store/apps/details?id=$id")) }
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

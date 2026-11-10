package dev.mahesh.icontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.WifiDevicesController
import dev.mahesh.icontrol.data.NetDevice
import dev.mahesh.icontrol.usb.ActionButton

/**
 * Who is connected to the Wi-Fi, with a button to cut a device off the internet (or let it back on). The router does
 * the blocking; this screen signs in to it with the admin password, which can be saved on this TV.
 */
@Composable
fun WifiDevicesDialog(controller: WifiDevicesController, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { controller.open() }
    val state by controller.state.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .backdrop()
                .padding(horizontal = 80.dp, vertical = 24.dp),
        ) {
            Text(
                text = "Wi-Fi devices",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
            )
            val subtitle = when {
                state.message != null -> state.message
                state.needsPassword -> "Enter your router's admin password to see and control the devices on your Wi-Fi."
                state.busy && !state.loaded -> "Talking to the router…"
                state.busy -> "Updating…"
                else -> "Block internet stops a device reaching the internet. Allow lets it back on."
            }
            Text(
                text = subtitle.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.message != null) Palette.Accent else Palette.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            if (state.needsPassword) {
                PasswordForm(onConnect = controller::submitPassword, onClose = onDismiss)
            } else {
                DeviceList(
                    devices = state.devices,
                    selfIp = state.selfIp,
                    onToggle = controller::toggle,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                    ActionButton("Refresh", Icons.Rounded.Refresh, controller::refresh)
                    if (state.passwordSaved) ActionButton("Forget password", Icons.Rounded.Delete, controller::forgetPassword)
                    ActionButton("Close", Icons.Rounded.Close, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun PasswordForm(onConnect: (String, Boolean) -> Unit, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 24.dp).width(480.dp),
    ) {
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(14.dp))
                .border(if (focused) 3.dp else 1.dp, if (focused) Palette.Accent else Palette.TextSecondary, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (password.isEmpty()) {
                Text("Router admin password", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
            }
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Palette.TextPrimary),
                cursorBrush = SolidColor(Palette.Accent),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConnect(password, remember) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        ActionButton(
            text = "Save password: ${if (remember) "Yes" else "No"}",
            icon = Icons.Rounded.Check,
            onClick = { remember = !remember },
            modifier = Modifier.fillMaxWidth(),
        )
        ActionButton(
            text = if (visible) "Hide password" else "Show password",
            icon = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            onClick = { visible = !visible },
            modifier = Modifier.fillMaxWidth(),
        )
        ActionButton(
            text = "Connect",
            icon = Icons.Rounded.Check,
            onClick = { onConnect(password, remember) },
            enabled = password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        ActionButton("Close", Icons.Rounded.Close, onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeviceList(
    devices: List<NetDevice>,
    selfIp: String?,
    onToggle: (NetDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(devices) {
        if (devices.isNotEmpty() && !focused) focused = runCatching { firstFocus.requestFocus() }.isSuccess
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        items(devices, key = { it.mac }) { device ->
            val isThisTv = device.ip.isNotEmpty() && device.ip == selfIp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isThisTv) "${device.name} (this TV)" else device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(device.ip.ifEmpty { null }, device.connection, if (device.blocked) "No internet" else null)
                            .filterNotNull()
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (device.blocked) Palette.Accent else Palette.TextSecondary,
                        maxLines = 1,
                    )
                }
                if (!isThisTv) {
                    Button(
                        onClick = { onToggle(device) },
                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                        modifier = if (device == devices.first()) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        Text(
                            text = if (device.blocked) "Allow internet" else "Block internet",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

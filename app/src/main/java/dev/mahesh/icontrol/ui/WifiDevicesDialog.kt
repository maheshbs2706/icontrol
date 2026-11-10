package dev.mahesh.icontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
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
 * the blocking; this screen signs in to it with the admin password, which can be saved on this TV. Devices can be
 * given names of your own, because many (iPhones, iPads, this TV) tell the router none.
 */
@Composable
fun WifiDevicesDialog(controller: WifiDevicesController, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { controller.open() }
    val state by controller.state.collectAsStateWithLifecycle()
    val names by controller.names.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<NetDevice?>(null) }

    /** Your own name first, then (for this TV) its own name when the router has none, then the router's. */
    fun nameOf(device: NetDevice): String {
        names[device.mac]?.let { return it }
        val isThisTv = device.ip.isNotEmpty() && device.ip == state.selfIp
        return if (isThisTv && device.name == UNKNOWN) controller.selfName else device.name
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .backdrop()
                .padding(horizontal = 56.dp, vertical = 24.dp),
        ) {
            // Left: the title, what is happening, and the buttons that act on the whole screen.
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(300.dp).fillMaxHeight(),
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
                    state.loaded -> "${state.devices.size} devices. Block internet stops a device reaching the internet; Allow lets it back on."
                    else -> ""
                }
                Text(
                    text = subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.message != null) Palette.Accent else Palette.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                )
                if (!state.needsPassword) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton("Refresh", Icons.Rounded.Refresh, controller::refresh, modifier = Modifier.fillMaxWidth())
                        if (state.passwordSaved) {
                            ActionButton("Forget password", Icons.Rounded.Delete, controller::forgetPassword, modifier = Modifier.fillMaxWidth())
                        }
                        ActionButton("Close", Icons.Rounded.Close, onDismiss, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Right: the password form, or the scrolling list of devices.
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp)) {
                if (state.needsPassword) {
                    PasswordForm(onConnect = controller::submitPassword, onClose = onDismiss)
                } else {
                    DeviceList(
                        devices = state.devices,
                        selfIp = state.selfIp,
                        nameOf = ::nameOf,
                        onToggle = controller::toggle,
                        onRename = { renaming = it },
                    )
                }
            }
        }
    }

    renaming?.let { device ->
        RenameDialog(
            current = names[device.mac].orEmpty(),
            routerName = device.name,
            onSave = { controller.rename(device.mac, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

private const val UNKNOWN = "Unknown device"

@Composable
private fun PasswordForm(onConnect: (String, Boolean) -> Unit, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxHeight().width(480.dp),
    ) {
        InputBox(
            value = password,
            onValue = { password = it },
            hint = "Router admin password",
            hidden = !visible,
            keyboardType = KeyboardType.Password,
            onDone = { onConnect(password, remember) },
            focusRequester = field,
        )
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

/** A text field that shows a focus ring and, when empty, a hint. */
@Composable
private fun InputBox(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    hidden: Boolean,
    keyboardType: KeyboardType,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(14.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) Palette.Accent else Palette.TextSecondary, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (value.isEmpty()) {
            Text(hint, style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Palette.TextPrimary),
            cursorBrush = SolidColor(Palette.Accent),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                // A single-line field keeps Up and Down for itself; hand them on so the remote can reach the buttons.
                .onPreviewKeyEvent { event ->
                    val direction = when (event.key) {
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionUp -> FocusDirection.Up
                        else -> null
                    }
                    if (direction == null) false
                    else {
                        if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                        true
                    }
                },
        )
    }
}

@Composable
private fun RenameDialog(current: String, routerName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().backdrop().padding(horizontal = 80.dp),
        ) {
            Text(
                text = "Name this device",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
            )
            Text(
                text = "The router calls it \"$routerName\". Your name is kept on this TV.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(480.dp)) {
                InputBox(
                    value = text,
                    onValue = { text = it },
                    hint = "For example: Dad's iPad",
                    hidden = false,
                    keyboardType = KeyboardType.Text,
                    onDone = { onSave(text) },
                    focusRequester = field,
                )
                ActionButton("Save", Icons.Rounded.Check, { onSave(text) }, modifier = Modifier.fillMaxWidth())
                ActionButton("Use the router's name", Icons.Rounded.Delete, { onSave("") }, modifier = Modifier.fillMaxWidth())
                ActionButton("Cancel", Icons.Rounded.Close, onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<NetDevice>,
    selfIp: String?,
    nameOf: (NetDevice) -> String,
    onToggle: (NetDevice) -> Unit,
    onRename: (NetDevice) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(devices) {
        if (devices.isNotEmpty() && !focused) focused = runCatching { firstFocus.requestFocus() }.isSuccess
    }
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
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
                            text = if (isThisTv) "${nameOf(device)} (this TV)" else nameOf(device),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The first button in the list takes focus when it opens; for this TV that is Rename.
                        val first = device == devices.first()
                        RowButton("Rename", if (first && isThisTv) Modifier.focusRequester(firstFocus) else Modifier) { onRename(device) }
                        if (!isThisTv) {
                            RowButton(
                                if (device.blocked) "Allow internet" else "Block internet",
                                if (first) Modifier.focusRequester(firstFocus) else Modifier,
                            ) { onToggle(device) }
                        }
                    }
                }
            }
        }
        Scrollbar(listState, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun RowButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, scale = ButtonDefaults.scale(focusedScale = 1.08f), modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

/** A thin bar beside the list: the thumb's size and place show how much of the list is on screen and where. */
@Composable
private fun Scrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val bar by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount.coerceAtLeast(1)
            val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
            val start = state.firstVisibleItemIndex.toFloat() / total
            if (visible >= total) 0f to 1f else start to (visible.toFloat() / total)
        }
    }
    val (top, size) = bar
    Column(modifier = modifier.width(6.dp).fillMaxHeight().background(Palette.Surface, RoundedCornerShape(3.dp))) {
        if (top > 0f) Spacer(Modifier.weight(top))
        Box(Modifier.weight(size.coerceAtLeast(0.05f)).fillMaxWidth().background(Palette.Accent, RoundedCornerShape(3.dp)))
        val rest = 1f - top - size
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

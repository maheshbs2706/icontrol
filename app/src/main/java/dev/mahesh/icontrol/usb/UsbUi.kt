package dev.mahesh.icontrol.usb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.mahesh.icontrol.ui.Palette
import dev.mahesh.icontrol.ui.ScreenPadding
import kotlinx.coroutines.delay

/**
 * Gives [target] focus, retrying for a moment until [hasFocus] reports that something on the screen has it.
 * One request isn't enough: after the video player's Android view is torn down, Compose's focus gets cleared a
 * beat after the new screen asks for it, leaving a screen the remote can't control.
 */
suspend fun requestFocusWithRetry(
    target: FocusRequester,
    focusManager: FocusManager,
    hasFocus: () -> Boolean,
) {
    // Compose may already have handed focus to the first control by itself when the previous screen went away.
    // That focus is real (OK works) but the control is never drawn highlighted, so reset and ask explicitly.
    focusManager.clearFocus(force = true)
    delay(50)
    repeat(6) {
        runCatching { target.requestFocus() }
        delay(250)
        if (hasFocus()) return
    }
}

/** Runs [block] whenever this element or something inside it takes focus. */
fun Modifier.onFocusGained(block: () -> Unit): Modifier =
    onFocusChanged { if (it.hasFocus) block() }

/** Title on the left; on the right, the name of whatever currently has focus (like the home screen). */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, focusedName: String = "") {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 24.dp, bottom = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = focusedName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

/** Width of the fixed left panel that holds a grid screen's main action (Slideshow, Play all). */
val SideRailWidth = ScreenPadding + 170.dp

/**
 * Every button in a side rail has this width, so they line up with each other and with the 158dp check box. It fits the
 * longest label ("Remove all") and, once the focused button has grown 20%, still stops short of the grid beside it.
 */
val RailButtonWidth = 158.dp
private val RailGridStartPadding = 12.dp

/**
 * The grid's main action, pinned at the left and vertically centred. It stays put while the grid scrolls, so from
 * any row one press of Left reaches it; at the top of a long list it would be hundreds of presses away.
 */
@Composable
fun SideActionRail(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .width(SideRailWidth)
            .fillMaxHeight()
            .padding(start = ScreenPadding),
        content = content,
    )
}

/** Card width for a grid of [columns] sharing the space to the right of the [SideActionRail]. */
@Composable
fun railGridCardWidth(columns: Int, gap: Dp): Dp =
    (LocalConfiguration.current.screenWidthDp.dp - SideRailWidth - RailGridStartPadding - ScreenPadding - gap * (columns - 1)) /
        columns

/** Content padding for a grid beside the rail; the vertical padding leaves room for focused cards to grow. */
fun railGridPadding(): PaddingValues =
    PaddingValues(start = RailGridStartPadding, end = ScreenPadding, top = 28.dp, bottom = 28.dp)

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A disabled button is dimmed and cannot take focus. */
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        scale = ButtonDefaults.scale(focusedScale = 1.2f),
        modifier = modifier,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Centered icon, title and explanation, with an optional action under them. */
@Composable
fun CenteredMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Palette.Accent,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .widthIn(max = 640.dp),
        )
        if (action != null) {
            Spacer(Modifier.size(24.dp))
            action()
        }
    }
}

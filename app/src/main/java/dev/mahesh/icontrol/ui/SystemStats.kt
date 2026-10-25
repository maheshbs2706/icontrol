package dev.mahesh.icontrol.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * How much of the TV's memory is in use, refreshed every couple of seconds. There is no CPU figure: Android 11 stops
 * apps reading /proc/stat, and this TV's load average is not a usable substitute.
 */
@Composable
fun MemoryUsage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var percent by remember { mutableStateOf(memoryPercent(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000L)
            percent = memoryPercent(context)
        }
    }
    percent?.let {
        Row(modifier = modifier) {
            Text(text = "Memory", style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary)
            Text(
                text = "$it%",
                style = MaterialTheme.typography.titleSmall,
                color = if (it >= 85) Palette.Accent else Palette.TextPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun memoryPercent(context: Context): Int? {
    val info = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(info)
    if (info.totalMem <= 0) return null
    return ((info.totalMem - info.availMem) * 100 / info.totalMem).toInt()
}

package dev.mahesh.icontrol.data

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Restarts the TV. Android only lets the system do that (the restart screen needs the SHUTDOWN permission), but the
 * TV's ADB service runs as the shell user, which may. So this connects to that service on the TV itself, the same
 * way `adb connect` from a PC does, and runs `reboot`. It needs "ADB over network" switched on, and the TV asks
 * once to allow this app's key (tick "Always allow").
 */
object AdbReboot {

    sealed interface Result {
        data object Restarting : Result

        /** The TV's ADB service did not answer: ADB over network is off, or the "Allow debugging?" prompt was ignored. */
        data object NoAdb : Result
    }

    private const val PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 40_000L

    suspend fun restart(context: Context): Result = withContext(Dispatchers.IO) {
        val dadb = try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val dadb = Dadb.create("127.0.0.1", PORT, keyPair(context))
                    // Opening the connection is lazy; this is what makes the TV show its "Allow debugging?" prompt.
                    dadb.shell("echo ok")
                    dadb
                }
            }
        } catch (_: TimeoutCancellationException) {
            return@withContext Result.NoAdb
        } catch (_: Exception) {
            return@withContext Result.NoAdb
        }
        // The TV drops the connection as it goes down, so an error here just means the restart began.
        runCatching { dadb.shell("reboot") }
        runCatching { dadb.close() }
        Result.Restarting
    }

    private fun keyPair(context: Context): AdbKeyPair {
        val private = File(context.filesDir, "adbkey")
        val public = File(context.filesDir, "adbkey.pub")
        if (!private.exists() || !public.exists()) AdbKeyPair.generate(private, public)
        return AdbKeyPair.read(private, public)
    }
}

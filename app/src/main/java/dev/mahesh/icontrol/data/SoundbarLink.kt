package dev.mahesh.icontrol.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * The sound bar hangs off an HDMI ARC port. After a while the bar drops into standby and the ARC link is lost, and
 * the TV never brings it back (a restart does). Tuning to the input on the bar's HDMI port makes the TV wake the bar
 * and set ARC up again, which an app can do without any system permission.
 */
object SoundbarLink {

    /** Stored in place of an input id when the user says none of their inputs is a sound bar. */
    const val NONE = ""

    private val GUESS = Regex("speaker|sound|soundbar|bar|arc|audio|home theat", RegexOption.IGNORE_CASE)

    /** True while the TV is sending audio out over ARC. */
    fun isLinked(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_HDMI_ARC }
    }

    /** The user's choice if they made one; otherwise the only input whose name sounds like a sound bar. */
    fun pick(inputs: List<InputEntry>, saved: String?): InputEntry? = when (saved) {
        null -> inputs.filter { GUESS.containsMatchIn(it.label) }.singleOrNull()
        NONE -> null
        else -> inputs.firstOrNull { it.id == saved }
    }
}

package dev.mahesh.icontrol.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a very quiet, inaudible tone so a soundbar on HDMI ARC sees a signal and does not go to standby while the
 * TV sits on the home screen or screensaver. Exact digital silence would not do: many soundbars read it as "no audio".
 *
 * The tone is 20 kHz, above what most adults hear (and above what a TV speaker reproduces), at about -50 dBFS.
 * It takes no audio focus, so it never interrupts anything else. Callers pair [acquire] with [release]; the tone
 * plays while at least one holder remains (the home screen and the screensaver can briefly overlap).
 */
object KeepAwakeAudio {

    private const val SAMPLE_RATE = 48_000
    private const val TONE_HZ = 20_000.0
    private const val AMPLITUDE = 0.003

    private var holders = 0
    private var track: AudioTrack? = null

    @Synchronized
    fun acquire() {
        holders++
        if (track == null) track = build()?.also { it.play() }
    }

    @Synchronized
    fun release() {
        if (holders > 0) holders--
        if (holders == 0) {
            track?.let {
                runCatching { it.stop() }
                it.release()
            }
            track = null
        }
    }

    private fun build(): AudioTrack? = runCatching {
        // One second of stereo, looped: 20 kHz at 48 kHz repeats exactly every 12 samples, so the loop is seamless.
        val frames = SAMPLE_RATE
        val samples = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val value = (sin(2 * PI * TONE_HZ * i / SAMPLE_RATE) * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
            samples[2 * i] = value
            samples[2 * i + 1] = value
        }
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        audioTrack.write(samples, 0, samples.size)
        audioTrack.setLoopPoints(0, frames, -1)
        audioTrack
    }.getOrNull()
}

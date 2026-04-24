package com.dinorun.game.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.dinorun.game.R
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays game sounds. The "bark" is a short dog-bark synthesized in code if the user
 * has not provided a real audio asset at res/raw/dog_bark — that way the app works
 * out of the box, but a real bark file is automatically picked up if dropped in.
 */
class SoundManager(context: Context) {

    private val ctx = context.applicationContext
    private val soundPool: SoundPool
    private val barkSoundId: Int
    private val hasRealBark: Boolean

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (_: Throwable) { null }

    init {
        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_GAME)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        val resId = ctx.resources.getIdentifier("dog_bark", "raw", ctx.packageName)
        if (resId != 0) {
            barkSoundId = soundPool.load(ctx, resId, 1)
            hasRealBark = true
        } else {
            barkSoundId = -1
            hasRealBark = false
        }
    }

    fun bark() {
        if (hasRealBark && barkSoundId != -1) {
            soundPool.play(barkSoundId, 1f, 1f, 1, 0, 1f)
        } else {
            playSynthBark()
        }
    }

    fun crash() {
        toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
    }

    fun pickup() {
        toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
    }

    fun levelUp() {
        toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, 180)
    }

    fun vibrate(ms: Long) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    fun release() {
        soundPool.release()
        toneGen?.release()
    }

    /**
     * Synthesizes a short, two-syllable "woof-woof" using FM synthesis with a fast
     * decay envelope. ~340 ms total.
     */
    private fun playSynthBark() {
        try {
            val sampleRate = 22050
            val durationMs = 340
            val numSamples = sampleRate * durationMs / 1000
            val buf = ShortArray(numSamples)
            val syllableLen = numSamples / 2
            val gapStart = (syllableLen * 0.85f).toInt()
            for (i in 0 until numSamples) {
                val withinSyllable = i % syllableLen
                if (withinSyllable > gapStart) { buf[i] = 0; continue }
                val t = withinSyllable.toFloat() / sampleRate
                val env = exp(-t * 14f)
                val baseFreq = 320f - (withinSyllable.toFloat() / syllableLen) * 120f
                val mod = sin(2.0 * PI * 80.0 * t).toFloat() * 30f
                val sample = sin(2.0 * PI * (baseFreq + mod) * t).toFloat() * env
                val growl = sin(2.0 * PI * (baseFreq * 0.5f) * t).toFloat() * env * 0.4f
                buf[i] = ((sample + growl) * 0.55f * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buf.size * 2,
                AudioTrack.MODE_STATIC
            )
            track.write(buf, 0, buf.size)
            track.setNotificationMarkerPosition(buf.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) { try { t?.release() } catch (_: Throwable) {} }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            track.play()
        } catch (_: Throwable) {
            // Final fallback so we never crash for sound issues
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }
}

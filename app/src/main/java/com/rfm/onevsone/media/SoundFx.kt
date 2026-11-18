package com.rfm.onevsone.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.rfm.onevsone.data.SettingsStore

enum class Sfx { X, O, CLICK, JOIN, WIN, LOSE, DRAW, SPLASH }

/** مؤثرات صوتية مولَّدة برمجيًا (بدون ملفات صوت خارجية) لتعمل دون إنترنت. */
class SoundFx(private val ctx: Context, private val settings: SettingsStore) {
    private val rate = 22050
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<Sfx, ShortArray>()

    private class Note(
        val startMs: Int, val durMs: Int, val freq: Double,
        val vol: Double = 0.5, val sweepTo: Double = 0.0
    )

    private fun synth(totalMs: Int, notes: List<Note>): ShortArray {
        val n = rate * totalMs / 1000
        val buf = FloatArray(n)
        for (nt in notes) {
            val s0 = rate * nt.startMs / 1000
            val len = rate * nt.durMs / 1000
            var phase = 0.0
            for (i in 0 until len) {
                val idx = s0 + i
                if (idx >= n) break
                val t = i.toDouble() / len
                val f = if (nt.sweepTo > 0) nt.freq + (nt.sweepTo - nt.freq) * t else nt.freq
                phase += 2.0 * Math.PI * f / rate
                val env = minOf(1.0, i / (rate * 0.005)) * (1.0 - t) * (1.0 - t)
                val w = Math.sin(phase) * 0.75 + Math.sin(2 * phase) * 0.15 + Math.sin(3 * phase) * 0.1
                buf[idx] += (w * env * nt.vol).toFloat()
            }
        }
        return ShortArray(n) { (buf[it].coerceIn(-1f, 1f) * 32000).toInt().toShort() }
    }

    private fun splash(): ShortArray {
        val l = ArrayList<Note>()
        l.add(Note(0, 1000, 120.0, 0.25, 900.0))
        val arp = listOf(261.63, 329.63, 392.0, 523.25, 659.25, 783.99, 1046.5)
        arp.forEachIndexed { i, f -> l.add(Note(i * 200, 260, f, 0.35)) }
        for (f in listOf(523.25, 659.25, 783.99, 1046.5)) l.add(Note(1500, 1500, f, 0.22))
        l.add(Note(1500, 1500, 130.81, 0.3))
        return synth(3000, l)
    }

    private fun build(s: Sfx): ShortArray = when (s) {
        Sfx.X -> synth(90, listOf(Note(0, 90, 880.0)))
        Sfx.O -> synth(90, listOf(Note(0, 90, 660.0)))
        Sfx.CLICK -> synth(50, listOf(Note(0, 50, 1200.0, 0.35)))
        Sfx.JOIN -> synth(300, listOf(Note(0, 140, 660.0), Note(140, 160, 990.0)))
        Sfx.WIN -> synth(
            900, listOf(Note(0, 150, 523.25), Note(150, 150, 659.25), Note(300, 150, 783.99), Note(450, 450, 1046.5))
        )
        Sfx.LOSE -> synth(900, listOf(Note(0, 250, 392.0), Note(250, 250, 329.63), Note(500, 400, 261.63)))
        Sfx.DRAW -> synth(500, listOf(Note(0, 200, 440.0), Note(220, 280, 440.0, 0.4)))
        Sfx.SPLASH -> splash()
    }

    fun play(s: Sfx) {
        if (!settings.flow.value.sound) return
        try {
            val data = synchronized(cache) { cache.getOrPut(s) { build(s) } }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.play()
            val ms = data.size * 1000L / rate
            main.postDelayed({
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                }
            }, ms + 200)
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    fun vibrate(ms: Long) {
        if (!settings.flow.value.vibrate) return
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
        }
    }
}

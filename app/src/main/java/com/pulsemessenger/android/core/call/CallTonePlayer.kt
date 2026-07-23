package com.pulsemessenger.android.core.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Reliable local call tones.
 *
 * Do not use ToneGenerator.TONE_SUP_RINGTONE here: on some Android builds it plays
 * only one short internal ringtone sequence and then ignores looping. We generate
 * our own PCM tone through AudioTrack, so ringback repeats until stopRingback().
 */
class CallTonePlayer(
    private val context: Context,
) {
    private val ringbackRunning = AtomicBoolean(false)
    private val connectionLostRunning = AtomicBoolean(false)

    @Volatile
    private var ringbackThread: Thread? = null

    @Volatile
    private var lostToneThread: Thread? = null

    @Volatile
    private var endedToneThread: Thread? = null

    fun startRingback() {
        stopConnectionLost()

        if (!ringbackRunning.compareAndSet(false, true)) return

        prepareCallAudioMode()

        ringbackThread = thread(
            start = true,
            isDaemon = true,
            name = "PulseRingbackTone",
        ) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            try {
                while (ringbackRunning.get()) {
                    // Классический гудок: тон около 425 Гц, затем пауза.
                    playToneBlocking(
                        frequencyHz = 425.0,
                        durationMs = 1200,
                        volume = 0.42f,
                        keepPlaying = ringbackRunning,
                    )

                    sleepWhileRunning(2400L, ringbackRunning)
                }
            } catch (_: InterruptedException) {
                // stopRingback() interrupts this thread intentionally.
            } catch (error: Throwable) {
                Log.w("WEBRTC_CALL", "ringback audio thread failed: ${error.message}")
            } finally {
                ringbackRunning.set(false)
            }
        }
    }

    fun stopRingback() {
        ringbackRunning.set(false)
        ringbackThread?.interrupt()
        ringbackThread = null
    }

    /**
     * Повторяющийся высокий сигнал проблемы соединения.
     * Он должен звучать не один раз, а пока звонок находится в состоянии
     * "нет соединения / восстанавливаем". Повторный вызов не создаёт второй поток.
     */
    fun startConnectionLostLoop() {
        stopRingback()

        if (!connectionLostRunning.compareAndSet(false, true)) return

        prepareCallAudioMode()

        lostToneThread?.interrupt()
        lostToneThread = thread(
            start = true,
            isDaemon = true,
            name = "PulseConnectionLostTone",
        ) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            try {
                while (connectionLostRunning.get()) {
                    playConnectionLostPattern(connectionLostRunning)
                    sleepWhileRunning(1900L, connectionLostRunning)
                }
            } catch (_: InterruptedException) {
                // stopConnectionLost() interrupts this thread intentionally.
            } catch (error: Throwable) {
                Log.w("WEBRTC_CALL", "connection lost tone failed: ${error.message}")
            } finally {
                connectionLostRunning.set(false)
            }
        }
    }

    fun stopConnectionLost() {
        connectionLostRunning.set(false)
        lostToneThread?.interrupt()
        lostToneThread = null
    }

    fun playConnectionLost() {
        startConnectionLostLoop()
    }

    @Throws(InterruptedException::class)
    private fun playConnectionLostPattern(running: AtomicBoolean) {
        // Был низкий 330/260 Гц. Делаем заметно выше и тревожнее.
        playToneBlocking(880.0, 150, 0.56f, running)
        sleepWhileRunning(80L, running)
        playToneBlocking(740.0, 150, 0.56f, running)
        sleepWhileRunning(80L, running)
        playToneBlocking(620.0, 260, 0.56f, running)
    }

    fun playCallEnded() {
        stopRingback()
        stopConnectionLost()

        // Важно: звук сброса запускаем отдельным не-daemon потоком и не через
        // VOICE_COMMUNICATION. После callManager.end() Android переводит аудио из
        // режима звонка обратно в normal, и короткий сигнал через voice-call stream
        // на части телефонов просто не слышен.
        endedToneThread?.interrupt()
        endedToneThread = thread(
            start = true,
            isDaemon = false,
            name = "PulseCallEndedTone",
        ) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            try {
                playHangupToneBlocking()
            } catch (_: InterruptedException) {
                // ignored
            } catch (error: Throwable) {
                Log.w("WEBRTC_CALL", "call ended tone failed: ${error.message}")
            }
        }
    }

    fun release() {
        stopRingback()
        stopConnectionLost()
        // endedToneThread не прерываем специально: звук сброса очень короткий,
        // и его нужно дать доиграть даже когда UI уже закрывает звонок.
    }

    private fun prepareCallAudioMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        // Важно: громкую связь здесь не трогаем. Ей управляет WebRtcCallManager.
    }

    @Throws(InterruptedException::class)
    private fun playHangupToneBlocking() {
        // Более заметный нисходящий сигнал сброса. Идёт через media stream,
        // чтобы не пропадать после переключения AudioManager.MODE_NORMAL.
        playToneBlocking(
            frequencyHz = 980.0,
            durationMs = 130,
            volume = 0.78f,
            usage = AudioAttributes.USAGE_MEDIA,
            legacyStream = AudioManager.STREAM_MUSIC,
        )
        Thread.sleep(45)
        playToneBlocking(
            frequencyHz = 650.0,
            durationMs = 160,
            volume = 0.78f,
            usage = AudioAttributes.USAGE_MEDIA,
            legacyStream = AudioManager.STREAM_MUSIC,
        )
        Thread.sleep(45)
        playToneBlocking(
            frequencyHz = 360.0,
            durationMs = 320,
            volume = 0.78f,
            usage = AudioAttributes.USAGE_MEDIA,
            legacyStream = AudioManager.STREAM_MUSIC,
        )
    }

    @Throws(InterruptedException::class)
    private fun sleepWhileRunning(durationMs: Long, running: AtomicBoolean) {
        val stepMs = 100L
        var elapsed = 0L

        while (elapsed < durationMs && running.get()) {
            Thread.sleep(minOf(stepMs, durationMs - elapsed))
            elapsed += stepMs
        }
    }

    private fun playToneBlocking(
        frequencyHz: Double,
        durationMs: Int,
        volume: Float,
        keepPlaying: AtomicBoolean? = null,
        usage: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION,
        legacyStream: Int = AudioManager.STREAM_VOICE_CALL,
    ) {
        if (durationMs <= 0) return
        if (keepPlaying != null && !keepPlaying.get()) return

        val sampleRate = 16_000
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBufferSize, sampleRate / 4)

        val audioTrack = createAudioTrack(sampleRate, bufferSize, usage, legacyStream)
        val chunk = ShortArray(512)
        val totalSamples = sampleRate * durationMs / 1000
        val phaseStep = 2.0 * PI * frequencyHz / sampleRate.toDouble()
        var phase = 0.0
        var writtenSamples = 0

        try {
            audioTrack.play()

            while (writtenSamples < totalSamples && (keepPlaying == null || keepPlaying.get())) {
                val count = minOf(chunk.size, totalSamples - writtenSamples)

                for (i in 0 until count) {
                    chunk[i] = (sin(phase) * Short.MAX_VALUE * volume).toInt().toShort()
                    phase += phaseStep
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }

                val result = audioTrack.write(chunk, 0, count)
                if (result < 0) {
                    Log.w("WEBRTC_CALL", "AudioTrack write failed: $result")
                    break
                }

                writtenSamples += count
            }
        } finally {
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
        }
    }

    private fun createAudioTrack(
        sampleRate: Int,
        bufferSize: Int,
        usage: Int,
        legacyStream: Int,
    ): AudioTrack {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                legacyStream,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM,
            )
        }
    }
}
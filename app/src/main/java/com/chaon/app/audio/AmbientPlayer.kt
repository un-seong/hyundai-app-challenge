package com.chaon.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * 저작권 걱정 없는 자체 사운드 (정의서 10장 의사결정 ④).
 * 음원 파일 없이 코드로 실시간 생성한다: 브라운 노이즈 기반 빗소리 / 파도, 종료 차임.
 *
 * 소리 깨짐 방지
 * - 넉넉한 출력 버퍼(약 0.5초)로 끊김(underrun)을 줄인다.
 * - 가벼운 난수기(xorshift)로 생성 부담을 줄이고, 오디오 우선순위 스레드에서 돌린다.
 * - 소프트 리미터로 큰 음량에서도 찢어지는 클리핑이 나지 않게 한다.
 * - 음성 안내 중에는 배경음을 낮춘다(duck).
 */
enum class RestSound(val label: String) {
    RAIN("잔잔한 빗소리"),
    WAVES("느린 파도"),
    SILENCE("무음"),
}

/** 기상 알람음 — 모두 코드로 만든 자체 사운드 (저작권 문제 없음) */
enum class AlarmSound(val label: String) {
    CHIME("맑은 차임"),
    BELL("부드러운 벨"),
    MELODY("아침 멜로디"),
    BIRDS("새소리 느낌"),
}

class AmbientPlayer {

    /** 재생할 때마다 1씩 늘어난다. 이전 재생 스레드는 자기 번호가 아니면 페이드 아웃 후 끝난다. */
    @Volatile private var generation = 0
    @Volatile private var volume = 0.6f
    /** 음성 안내 중 배경음 배율 (1 = 그대로) */
    @Volatile private var duckFactor = 1f
    private var worker: Thread? = null

    fun play(sound: RestSound, volume: Float) {
        stop()
        this.volume = volume.coerceIn(0f, 1f)
        if (sound == RestSound.SILENCE) return
        val myGeneration = ++generation
        worker = Thread({ loop(sound, myGeneration) }, "chaon-ambient").apply { start() }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
    }

    /** 음성 안내가 나오는 동안 배경음을 낮춘다 */
    fun duck(on: Boolean) {
        duckFactor = if (on) DUCK_LEVEL else 1f
    }

    fun stop() {
        generation++ // 현재 재생 스레드에게 페이드 아웃 신호
        worker = null
    }

    /** 부드러운 두 음 차임 (종료 예고·종료 알림) */
    fun playChime(volume: Float = 0.7f) {
        playAlarm(AlarmSound.CHIME, volume)
    }

    /** 기상 알람음 한 번 (약 2.5초) */
    fun playAlarm(sound: AlarmSound, volume: Float) {
        val vol = volume.coerceIn(0f, 1f)
        Thread({ playPcm(synth(sound, vol)) }, "chaon-alarm").start()
    }

    private fun newTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf * 4, BUFFER_BYTES))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun loop(sound: RestSound, myGeneration: Int) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        val track = runCatching { newTrack() }.getOrElse {
            Log.w(TAG, "AudioTrack create failed", it); return
        }
        val buffer = ShortArray(CHUNK)
        val rng = XorShift(System.nanoTime())
        var brown = 0.0
        var light = 0.0
        var t = 0L
        var gain = 0.0 // 페이드 인/아웃 + 덕킹
        try {
            track.play()
            while (true) {
                val active = generation == myGeneration
                val target = if (active) volume.toDouble() * duckFactor else 0.0
                for (i in buffer.indices) {
                    val white = rng.nextSigned()
                    brown = (brown + 0.02 * white) / 1.02
                    light = 0.97 * light + 0.03 * white
                    val raw = when (sound) {
                        RestSound.RAIN -> brown * 2.4 + light * 0.7
                        RestSound.WAVES -> {
                            val seconds = t.toDouble() / SAMPLE_RATE
                            val swell = 0.5 - 0.5 * cos(2 * PI * seconds / WAVE_PERIOD_S)
                            brown * 2.8 * (0.25 + 0.75 * swell)
                        }
                        RestSound.SILENCE -> 0.0
                    }
                    gain += (target - gain) * FADE_STEP
                    buffer[i] = toPcm(softLimit(raw * gain * 0.7))
                    t++
                }
                track.write(buffer, 0, buffer.size)
                if (!active && gain < 0.002) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "ambient playback stopped", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun playPcm(pcm: ShortArray) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        val track = runCatching { newTrack() }.getOrElse { return }
        try {
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                val n = minOf(CHUNK, pcm.size - offset)
                track.write(pcm, offset, n)
                offset += n
            }
        } catch (e: Exception) {
            Log.w(TAG, "alarm playback failed", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** 알람음 합성. 모두 약 2.4초 */
    private fun synth(sound: AlarmSound, vol: Float): ShortArray {
        val total = (SAMPLE_RATE * 2.4).toInt()
        val pcm = ShortArray(total)
        for (i in 0 until total) {
            val s = i.toDouble() / SAMPLE_RATE
            val v = when (sound) {
                AlarmSound.CHIME -> notes(s, listOf(659.25 to 0.0, 987.77 to 0.35), decay = 2.6) * 0.3
                AlarmSound.BELL -> bell(s, 784.0, 0.0) + bell(s, 784.0, 1.1)
                AlarmSound.MELODY -> notes(
                    s,
                    // 도-미-솔-미-높은 도 (직접 만든 짧은 상행 선율)
                    listOf(523.25 to 0.0, 659.25 to 0.28, 783.99 to 0.56, 659.25 to 0.84, 1046.5 to 1.12),
                    decay = 3.5,
                    harmonic = 0.25,
                ) * 0.22
                AlarmSound.BIRDS -> birds(s)
            }
            pcm[i] = toPcm(softLimit(v * vol))
        }
        return pcm
    }

    /** 사인파 음들을 시간차로 겹친다 */
    private fun notes(s: Double, list: List<Pair<Double, Double>>, decay: Double, harmonic: Double = 0.0): Double {
        var v = 0.0
        list.forEach { (freq, startAt) ->
            if (s >= startAt) {
                val local = s - startAt
                val env = exp(-local * decay) * (1 - exp(-local * 60))
                v += (sin(2 * PI * freq * local) + harmonic * sin(4 * PI * freq * local)) * env
            }
        }
        return v
    }

    /** 종 소리: 비정수배 배음이 빠르게 사라지는 소리 */
    private fun bell(s: Double, freq: Double, startAt: Double): Double {
        if (s < startAt) return 0.0
        val t = s - startAt
        val attack = 1 - exp(-t * 80)
        return attack * (
            0.30 * sin(2 * PI * freq * t) * exp(-t * 1.6) +
                0.15 * sin(2 * PI * freq * 2.76 * t) * exp(-t * 3.5) +
                0.08 * sin(2 * PI * freq * 5.4 * t) * exp(-t * 6.0)
            )
    }

    /** 새소리 느낌: 짧게 위로 미끄러지는 고음 세 번, 잠깐 쉬고 두 번 */
    private fun birds(s: Double): Double {
        val starts = listOf(0.0, 0.16, 0.32, 0.9, 1.06)
        var v = 0.0
        starts.forEach { st ->
            val t = s - st
            if (t in 0.0..0.11) {
                val sweep = 2600.0 + 1600.0 * (t / 0.11)
                // 위상 = 주파수 적분 (선형 스윕)
                val phase = 2 * PI * (2600.0 * t + 0.5 * (1600.0 / 0.11) * t * t)
                val env = sin(PI * t / 0.11)
                v += sin(phase) * env * 0.22 * (sweep / 4200.0)
            }
        }
        return v
    }

    /** 0.6 까지는 그대로, 그 위는 부드럽게 눌러서 ±1 을 넘지 않게 (딱딱한 클리핑 방지) */
    private fun softLimit(x: Double): Double {
        val a = abs(x)
        if (a <= KNEE) return x
        val over = a - KNEE
        val limited = KNEE + (1 - KNEE) * (over / (over + (1 - KNEE)))
        return if (x < 0) -limited else limited
    }

    private fun toPcm(x: Double): Short = (x.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()

    /** 빠르고 가벼운 난수기 (-1..1) */
    private class XorShift(seed: Long) {
        private var s = if (seed == 0L) 0x2545F4914F6CDD1DL else seed
        fun nextSigned(): Double {
            s = s xor (s shl 13)
            s = s xor (s ushr 7)
            s = s xor (s shl 17)
            return ((s ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
        }
    }

    private companion object {
        const val TAG = "Chaon/Audio"
        const val SAMPLE_RATE = 22_050
        const val CHUNK = 2_048
        /** 약 0.5초 분량 (16bit mono) */
        const val BUFFER_BYTES = SAMPLE_RATE
        const val WAVE_PERIOD_S = 9.0
        const val FADE_STEP = 0.0002 // 약 1초 페이드
        const val KNEE = 0.6
        const val DUCK_LEVEL = 0.25f
    }
}

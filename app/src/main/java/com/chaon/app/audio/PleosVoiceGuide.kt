package com.chaon.app.audio

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

/**
 * Pleos TextToSpeech SDK (3.3.1, ON_DEVICE 모드) 음성 안내.
 *
 * - ON_DEVICE 모드는 앱 등록(Client ID) 없이 쓸 수 있다.
 * - 초기화 실패 · 5초 내 응답 없음 · 재생 오류가 나면 안드로이드 기본 TTS 로 자동 전환한다.
 * - 권한: pleos.car.permission.TTS_SERVICE
 */
class PleosVoiceGuide(context: Context) : VoiceGuide {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var pleos: TextToSpeech? = null
    private var fallback: AndroidVoiceGuide? = null
    @Volatile private var ready = false
    private var pending: String? = null

    /** 지금 쓰는 음성 엔진 이름 (화면 표시용) */
    @Volatile var engineName: String = "음성 준비 중"
        private set

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = Unit
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "Pleos TTS error=$errorType → 기본 TTS 로 전환")
            main.post { switchToFallback() }
        }
    }

    init {
        val tts = runCatching { TextToSpeech(appContext, TtsMode.ON_DEVICE) }
            .onFailure { Log.w(TAG, "Pleos TTS 생성 실패", it) }
            .getOrNull()
        if (tts == null) {
            switchToFallback()
        } else {
            pleos = tts
            runCatching {
                tts.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        main.post {
                            runCatching {
                                tts.addEventListener(events)
                                tts.setLocale(Locale.KOREA)
                            }.onFailure { Log.w(TAG, "Pleos TTS 설정 실패", it) }
                            ready = true
                            engineName = "Pleos TTS"
                            pending?.let { pending = null; speak(it) }
                        }
                    }

                    override fun onFailure(errorType: TtsError) {
                        Log.w(TAG, "Pleos TTS initialize 실패=$errorType")
                        main.post { switchToFallback() }
                    }
                })
            }.onFailure {
                Log.w(TAG, "Pleos TTS initialize 예외", it)
                switchToFallback()
            }
            // 음성 서비스가 없는 기기에서는 응답이 오지 않을 수 있다
            main.postDelayed({ if (!ready && fallback == null) switchToFallback() }, INIT_TIMEOUT_MS)
        }
    }

    override val isAvailable: Boolean
        get() = ready || (fallback?.isAvailable == true)

    override fun speak(text: String) {
        fallback?.let { it.speak(text); return }
        val tts = pleos
        if (!ready || tts == null) {
            pending = text
            return
        }
        runCatching { tts.speak(text, true) }.onFailure {
            Log.w(TAG, "Pleos TTS speak 실패 → 기본 TTS", it)
            pending = text
            switchToFallback()
        }
    }

    override fun stop() {
        fallback?.stop() ?: runCatching { pleos?.stop() }
    }

    override fun shutdown() {
        fallback?.shutdown()
        pleos?.let { runCatching { it.stop(); it.removeEventListener(events); it.release() } }
        pleos = null
        ready = false
    }

    private fun switchToFallback() {
        if (fallback != null) return
        ready = false
        pleos?.let { runCatching { it.removeEventListener(events); it.release() } }
        pleos = null
        val f = AndroidVoiceGuide(appContext)
        fallback = f
        engineName = "기본 TTS (Pleos TTS 사용 불가)"
        // 기본 TTS 초기화가 끝난 뒤 밀린 문장을 말한다
        pending?.let { text ->
            pending = null
            main.postDelayed({ f.speak(text) }, 1_200)
        }
    }

    private companion object {
        const val TAG = "Chaon/PleosTTS"
        const val INIT_TIMEOUT_MS = 5_000L
    }
}

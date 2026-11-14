package com.chaon.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 음성 안내 (FR-05).
 *
 * v0.1 은 안드로이드 기본 TextToSpeech 를 쓴다. 공모전 제출 버전에서는 같은 인터페이스 뒤에
 * Pleos TextToSpeech SDK 구현을 끼워 넣으면 된다 (정의서 07장: 가용성·SDK 버전 확인 필요).
 *
 * 음성이 안 나와도 화면 안내는 항상 함께 표시하므로(호출하는 쪽에서 처리) 실패해도 흐름은 멈추지 않는다.
 */
interface VoiceGuide {
    val isAvailable: Boolean
    fun speak(text: String)
    /** 말하는 중이면 멈춤 (음성 명령을 듣기 전에) */
    fun stop() = Unit
    fun shutdown()
}

class AndroidVoiceGuide(context: Context) : VoiceGuide {

    @Volatile
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = runCatching {
            TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS && configureKorean()
                if (!ready) Log.w(TAG, "TTS not available (status=$status)")
            }
        }.getOrNull()
    }

    override val isAvailable: Boolean get() = ready

    override fun speak(text: String) {
        if (!ready) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chaon-${System.nanoTime()}") }
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }

    private fun configureKorean(): Boolean {
        val engine = tts ?: return false
        val result = engine.setLanguage(Locale.KOREAN)
        engine.setSpeechRate(0.9f)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private companion object {
        const val TAG = "Chaon/TTS"
    }
}

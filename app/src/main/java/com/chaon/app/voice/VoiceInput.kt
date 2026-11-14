package com.chaon.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener

/** 짧은 음성 명령 한 번 듣기 */
interface VoiceInput {
    val isAvailable: Boolean
    /** 결과는 메인 스레드로 한 번 전달된다. 못 알아들으면 null */
    fun listen(onResult: (String?) -> Unit)
    fun stop()
    fun release()
}

/**
 * Pleos SpeechToText SDK (3.3.1, ON_DEVICE 모드).
 * - 권한: pleos.car.permission.STT_SERVICE
 * - 초기화에 실패하면 음성 명령 버튼을 숨기고 화면 버튼으로만 쓴다.
 * - SDK 호출은 별도 스레드에서 한다 (화면이 멈추지 않게).
 */
class PleosVoiceInput(context: Context) : VoiceInput {

    private val main = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("chaon-stt").apply { start() }
    private val worker = Handler(workerThread.looper)
    private var stt: SpeechToText? = null
    @Volatile private var ready = false
    private var callback: ((String?) -> Unit)? = null
    private var last: String? = null
    private val timeout = Runnable { finish(last, stopRecognizer = true) }

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            last = stt
            if (completed) main.post { finish(stt) }
        }
        override fun onEndedRecognition() {
            main.post { finish(last) }
        }
        override fun onError(errorType: SttError) {
            Log.w(TAG, "STT error=$errorType")
            main.post { finish(null) }
        }
    }

    init {
        stt = runCatching { SpeechToText(context.applicationContext, SttMode.ON_DEVICE) }
            .onFailure { Log.w(TAG, "STT 생성 실패", it) }
            .getOrNull()
        stt?.let { s ->
            runCatching {
                s.initialize(object : SttInitListener {
                    override fun onSuccess() {
                        runCatching { s.addListener(listener); s.setLocale(Locale.KOREA) }
                        ready = true
                    }

                    override fun onFailure(errorType: SttError) {
                        Log.w(TAG, "STT initialize 실패=$errorType")
                    }
                })
            }.onFailure { Log.w(TAG, "STT initialize 예외", it) }
        }
    }

    override val isAvailable: Boolean get() = ready

    override fun listen(onResult: (String?) -> Unit) {
        val s = stt
        if (!ready || s == null) {
            main.post { onResult(null) }
            return
        }
        callback = onResult
        last = null
        worker.post {
            runCatching { s.request() }.onFailure {
                Log.w(TAG, "STT request 실패", it)
                main.post { finish(null) }
            }
        }
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, LISTEN_MS)
    }

    /** 결과 전달. 인식이 스스로 끝난 경우엔 stop 을 부르지 않는다 (시간 초과·취소일 때만) */
    private fun finish(text: String?, stopRecognizer: Boolean = false) {
        val cb = callback ?: return
        callback = null
        main.removeCallbacks(timeout)
        if (stopRecognizer) stt?.let { s -> worker.post { runCatching { s.stop() } } }
        cb(text)
    }

    override fun stop() {
        finish(null, stopRecognizer = true)
    }

    override fun release() {
        main.removeCallbacks(timeout)
        callback = null
        val s = stt ?: return
        stt = null
        worker.post {
            runCatching { s.stop(); s.removeListener(listener); s.release() }
            workerThread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "Chaon/PleosSTT"
        const val LISTEN_MS = 7_000L
    }
}

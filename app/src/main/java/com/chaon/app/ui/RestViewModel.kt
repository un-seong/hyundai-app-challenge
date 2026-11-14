package com.chaon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaon.app.audio.AmbientPlayer
import com.chaon.app.audio.AlarmSound
import com.chaon.app.audio.PleosVoiceGuide
import com.chaon.app.audio.RestSound
import com.chaon.app.audio.VoiceGuide
import com.chaon.app.data.LocalStore
import com.chaon.app.data.RestProfile
import com.chaon.app.data.TodayStats
import com.chaon.app.session.EndReason
import com.chaon.app.session.NightEnergyModel
import com.chaon.app.session.NightForecast
import com.chaon.app.session.NightInputs
import com.chaon.app.session.NightVerdict
import com.chaon.app.session.RestMode
import com.chaon.app.session.RestSessionEngine
import com.chaon.app.session.SessionEvent
import com.chaon.app.session.SessionPhase
import com.chaon.app.session.SessionState
import com.chaon.app.vehicle.DemoVehicleGateway
import com.chaon.app.vehicle.Gear
import com.chaon.app.vehicle.RealVehicleGateway
import com.chaon.app.vehicle.SdkDiagEntry
import com.chaon.app.vehicle.StopSafety
import com.chaon.app.vehicle.VehicleSnapshot
import com.chaon.app.voice.PleosVoiceInput
import com.chaon.app.voice.VoiceCommand
import com.chaon.app.voice.VoiceCommandParser
import com.chaon.app.voice.VoiceInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.time.LocalDateTime

enum class Screen { HOME, SETUP, SESSION, WAKE, RETURN, DIAG }

/** 온밤 중 배터리 경고 단계 */
enum class NightAlert { NONE, WARN, CRITICAL }

data class UiState(
    val screen: Screen = Screen.HOME,
    val profile: RestProfile = RestProfile(),
    /** 설정 화면에서 보고 있는 모드 */
    val setupMode: RestMode = RestMode.ONSWIM,
    val session: SessionState = SessionState(),
    val remainingMs: Long = 0L,
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val demoMode: Boolean = true,
    val realAvailable: Boolean = false,
    /** 정차 상태를 확인할 수 없을 때 사용자가 직접 "안전하게 정차했어요"를 체크 (FR-04) */
    val manualStopConfirmed: Boolean = false,
    /** 화면 안내 문구 (음성이 안 나와도 항상 표시, FR-05) */
    val banner: String? = null,
    val voiceAvailable: Boolean = false,
    val today: TodayStats = TodayStats(),
    val diag: List<SdkDiagEntry> = emptyList(),
    val nightAlert: NightAlert = NightAlert.NONE,
    /** 온밤 사운드가 수면 타이머로 꺼졌는지 */
    val nightSoundOff: Boolean = false,
    /** 음성 안내 엔진 이름 (Pleos TTS / 기본 TTS) */
    val voiceEngine: String = "음성 준비 중",
    /** Pleos STT 음성 명령 사용 가능 */
    val sttAvailable: Boolean = false,
    val listening: Boolean = false,
)

class RestViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LocalStore(app)
    private val pleosVoice = PleosVoiceGuide(app)
    private val voice: VoiceGuide = pleosVoice
    private val voiceInput: VoiceInput = PleosVoiceInput(app)
    private val ambient = AmbientPlayer()
    private val real = RealVehicleGateway(app, viewModelScope)
    private val demo = DemoVehicleGateway()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var vehicleJob: Job? = null
    private var bannerJob: Job? = null
    private var alarmJob: Job? = null
    private var duckJob: Job? = null

    init {
        val profile = store.loadProfile()
        val restored = store.loadSession()
        _ui.update {
            it.copy(
                profile = profile,
                today = store.loadToday(),
                realAvailable = real.isAutomotive,
                demoMode = !real.isAutomotive,
            )
        }

        real.start()
        observeVehicle()
        restoreSession(restored)
        startTicker()

        // TTS·STT 초기화는 비동기라 잠시 뒤 상태를 반영 (Pleos TTS 응답 대기 5초 포함)
        viewModelScope.launch {
            repeat(4) {
                delay(2_000)
                _ui.update {
                    it.copy(
                        voiceAvailable = voice.isAvailable,
                        voiceEngine = pleosVoice.engineName,
                        sttAvailable = voiceInput.isAvailable,
                    )
                }
            }
        }
    }

    // ───────────── 차량 상태 ─────────────

    private fun observeVehicle() {
        vehicleJob?.cancel()
        val gateway = if (_ui.value.demoMode) demo else real
        vehicleJob = viewModelScope.launch {
            gateway.snapshot.collect { snap ->
                _ui.update { it.copy(vehicle = snap) }
                onVehicleChanged(snap)
            }
        }
    }

    fun setDemoMode(on: Boolean) {
        if (on == _ui.value.demoMode) return
        if (!on && !_ui.value.realAvailable) {
            showBanner("이 기기는 차량 환경이 아니라서 실제 차량 데이터를 쓸 수 없어요.")
            return
        }
        _ui.update { it.copy(demoMode = on, manualStopConfirmed = false) }
        observeVehicle()
    }

    /** 데모 모드에서 기어 바꾸기 (주행 감지 시연용) */
    fun demoSetGear(gear: Gear) = demo.setGear(gear)

    /** 데모 모드에서 배터리 바꾸기 (온밤 경고 시연용) */
    fun demoChangeSoc(delta: Float) = demo.changeSoc(delta)

    private fun onVehicleChanged(snap: VehicleSnapshot) {
        val phase = _ui.value.session.phase
        val active = phase == SessionPhase.RUNNING || phase == SessionPhase.PAUSED
        if (snap.safety == StopSafety.MOVING) {
            if (active) endSession(EndReason.DRIVING_DETECTED)
            if (_ui.value.screen == Screen.WAKE) {
                // 기상 알람이 울리는 중 주행 시작 → 알람을 끄고 복귀 화면으로
                stopAlarm()
                _ui.update { it.copy(screen = Screen.RETURN) }
                showBanner("주행이 감지되어 알람을 껐어요.")
            }
        }
        if (snap.safety == StopSafety.PARKED && _ui.value.manualStopConfirmed) {
            _ui.update { it.copy(manualStopConfirmed = false) }
        }
        val s = _ui.value.session
        if (s.mode == RestMode.ONBAM && (s.phase == SessionPhase.RUNNING || s.phase == SessionPhase.PAUSED)) {
            snap.socPercent?.let { checkNightBattery(it) }
        }
    }

    /** 온밤 배터리 감시: 기준+10% 이하면 조용히 경고, 기준 이하면 깨운다 */
    private fun checkNightBattery(soc: Float) {
        val state = _ui.value
        val reserve = state.profile.reservePercent.toFloat()
        when {
            soc <= reserve -> if (state.nightAlert != NightAlert.CRITICAL) {
                _ui.update { it.copy(nightAlert = NightAlert.CRITICAL) }
                endSession(EndReason.LOW_BATTERY)
            }
            state.nightAlert == NightAlert.NONE -> {
                val remainingHours = state.remainingMs / 3_600_000.0
                val f = nightForecast(state, remainingHours, soc)
                val nearReserve = soc <= reserve + 10f
                if (nearReserve || f.verdict == NightVerdict.SHORT) {
                    _ui.update { it.copy(nightAlert = NightAlert.WARN) }
                    ambient.playChime(state.profile.volume * 0.4f)
                    // 자는 사람을 말로 깨우지 않고 화면으로만 알린다
                    showBanner("배터리 ${soc.toInt()}%. 이대로면 기상 전에 기준(${reserve.toInt()}%) 아래로 내려갈 수 있어요. 공조를 낮춰 주세요.")
                }
            }
        }
    }

    /** 온밤 예측. soc 가 없으면 차량 값 → 없으면 사용자가 입력한 값 */
    fun nightForecast(state: UiState = _ui.value, neededHours: Double, soc: Float? = null): NightForecast =
        NightEnergyModel.forecast(nightInputs(state, neededHours, soc))

    fun nightInputs(state: UiState, neededHours: Double, soc: Float? = null): NightInputs {
        val p = state.profile
        return NightInputs(
            socPercent = (soc ?: state.vehicle.socPercent ?: p.manualSocPercent.toFloat()).toDouble(),
            capacityKWh = (state.vehicle.batteryCapacityKWh ?: p.capacityKWh).toDouble(),
            sohPercent = p.sohPercent.toDouble(),
            reservePercent = p.reservePercent.toDouble(),
            hvac = p.hvacLevel,
            neededHours = neededHours,
        )
    }

    fun setManualStopConfirmed(value: Boolean) = _ui.update { it.copy(manualStopConfirmed = value) }

    /** 지금 휴식을 시작할 수 있는가 */
    fun canStart(state: UiState = _ui.value): Boolean = when (state.vehicle.safety) {
        StopSafety.PARKED -> true
        StopSafety.UNKNOWN -> state.manualStopConfirmed
        StopSafety.MOVING -> false
    }

    // ───────────── 화면 이동 ─────────────

    fun goTo(screen: Screen) {
        val phase = _ui.value.session.phase
        val sessionActive = phase == SessionPhase.RUNNING || phase == SessionPhase.PAUSED
        // 휴식 중에 진단 화면을 봤다가 "홈으로"를 누르면 진행 중인 세션 화면으로 돌아간다
        val target = if (screen == Screen.HOME && sessionActive) Screen.SESSION else screen
        _ui.update { it.copy(screen = target) }
        if (screen == Screen.DIAG) runDiagnostics()
    }

    fun openSetup(mode: RestMode) {
        _ui.update { it.copy(setupMode = mode) }
        goTo(Screen.SETUP)
    }

    fun setSetupMode(mode: RestMode) = _ui.update { it.copy(setupMode = mode) }

    /** 온밤 기상 시각을 분 단위로 옮기기 */
    fun shiftWake(deltaMin: Int) {
        val p = _ui.value.profile
        val total = ((p.wakeHour * 60 + p.wakeMinute + deltaMin) % 1440 + 1440) % 1440
        updateProfile { it.copy(wakeHour = total / 60, wakeMinute = total % 60) }
    }

    fun updateProfile(transform: (RestProfile) -> RestProfile) {
        val next = transform(_ui.value.profile)
        _ui.update { it.copy(profile = next) }
        store.saveProfile(next)
        if (_ui.value.session.phase == SessionPhase.RUNNING) ambient.setVolume(next.volume)
    }

    /** 홈의 빠른 시작 버튼 (온쉼 10/20/30분) */
    fun quickStart(minutes: Int) {
        updateProfile { it.copy(durationMin = minutes) }
        startSession(RestMode.ONSWIM)
    }

    // ───────────── 세션 ─────────────

    fun startSession(mode: RestMode) {
        val state = _ui.value
        if (!canStart(state)) {
            showBanner(
                if (state.vehicle.safety == StopSafety.MOVING) "주행 중에는 시작할 수 없어요. 안전한 곳에 정차해 주세요."
                else "정차 상태를 확인할 수 없어요. 안전한 곳에 정차했다면 확인란을 체크해 주세요."
            )
            return
        }
        val p = state.profile
        val minutes = minutesFor(mode, p)
        val preAlertMin = when (mode) {
            RestMode.ONSWIM -> p.preAlertMin
            RestMode.ONJAM -> NAP_WAKE_RAMP_MIN      // 2분 전부터 천천히 깨움(말 없이 소리만)
            RestMode.ONKKI -> MEAL_WRAP_UP_MIN       // 5분 전 정리 안내
            RestMode.ONJEONG -> 0
            RestMode.ONBAM -> NIGHT_WAKE_RAMP_MIN    // 5분 전부터 천천히 깨움
        }
        val session = RestSessionEngine.start(
            durationMs = minutes * 60_000L,
            preAlertMs = preAlertMin * 60_000L,
            now = now(),
            mode = mode,
            midAlert = mode == RestMode.ONKKI && minutes >= 10,
            startSocPercent = if (mode == RestMode.ONBAM) (state.vehicle.socPercent ?: p.manualSocPercent.toFloat()) else null,
            minDurationMs = if (state.demoMode) RestSessionEngine.DEMO_MIN_DURATION_MS else RestSessionEngine.MIN_DURATION_MS,
        )
        stopAlarm()
        applySession(session)
        _ui.update { it.copy(screen = Screen.SESSION, nightAlert = NightAlert.NONE, nightSoundOff = false) }
        ambient.play(soundFor(mode), p.volume)

        val text = when (mode) {
            RestMode.ONSWIM -> {
                val alertText = if (session.preAlertMs > 0) " 끝나기 ${p.preAlertMin}분 전에 알려드릴게요." else ""
                "${minutes}분 온쉼을 시작할게요.$alertText"
            }
            RestMode.ONJAM -> "${minutes}분 온잠을 시작할게요. 시간이 되면 천천히 깨워드릴게요."
            RestMode.ONKKI -> "${minutes}분 온끼를 시작할게요. 맛있게 드세요."
            RestMode.ONJEONG -> "${minutes}분 온정을 시작할게요. 화면의 원에 맞춰 천천히 숨 쉬어요."
            RestMode.ONBAM -> "온밤을 시작할게요. %02d:%02d에 깨워드릴게요. 사운드는 ${NIGHT_SOUND_OFF_MIN}분 뒤 자동으로 꺼져요."
                .format(p.wakeHour, p.wakeMinute)
        }
        announce(text)
    }

    /** 복귀 화면의 "같은 설정으로 다시" */
    fun restartSameMode() = startSession(_ui.value.session.mode)

    fun pauseOrResume() {
        val s = _ui.value.session
        when (s.phase) {
            SessionPhase.RUNNING -> {
                applySession(RestSessionEngine.pause(s, now()))
                ambient.stop()
                showBanner("잠시 멈췄어요.")
            }
            SessionPhase.PAUSED -> {
                applySession(RestSessionEngine.resume(s, now()))
                ambient.play(soundFor(s.mode), _ui.value.profile.volume)
                showBanner("다시 쉬어요.")
            }
            else -> Unit
        }
    }

    fun stopByUser() = endSession(EndReason.USER_STOPPED)

    fun backToHome() {
        stopAlarm()
        applySession(RestSessionEngine.reset())
        _ui.update { it.copy(screen = Screen.HOME) }
    }

    // ───────────── 음성 명령 (Pleos STT) ─────────────

    fun listenCommand() {
        if (_ui.value.listening) {
            voiceInput.stop()
            return
        }
        if (!voiceInput.isAvailable) {
            showBanner("음성 명령을 쓸 수 없는 환경이에요. 화면 버튼을 이용해 주세요.")
            return
        }
        voice.stop()
        // 배경 사운드가 인식을 방해하지 않게 잠시 줄인다
        val running = _ui.value.session.phase == SessionPhase.RUNNING
        if (running) ambient.setVolume(0.08f)
        _ui.update { it.copy(listening = true) }
        showBanner("듣고 있어요…  예) \"20분 쉬기\", \"낮잠 15분\", \"그만\"")
        voiceInput.listen { text ->
            _ui.update { it.copy(listening = false) }
            if (_ui.value.session.phase == SessionPhase.RUNNING) ambient.setVolume(_ui.value.profile.volume)
            handleVoice(text)
        }
    }

    private fun handleVoice(text: String?) {
        val state = _ui.value
        val phase = state.session.phase
        val active = phase == SessionPhase.RUNNING || phase == SessionPhase.PAUSED
        when (val cmd = VoiceCommandParser.parse(text)) {
            is VoiceCommand.Start -> when {
                active -> showBanner("지금 ${state.session.mode.label} 중이에요. 끝내려면 \"그만\"이라고 말해 주세요.")
                state.screen == Screen.WAKE -> showBanner("먼저 \"일어났어\" 또는 \"5분만 더\"라고 말해 주세요.")
                else -> {
                    val m = cmd.minutes
                    var note: String? = null
                    if (m != null && cmd.mode != RestMode.ONBAM) {
                        val range = minutesRange(cmd.mode, state.demoMode)
                        val fixed = m.coerceIn(range)
                        if (fixed != m) {
                            val topic = if (cmd.mode == RestMode.ONKKI) "온끼는" else "${cmd.mode.label}은"
                            note = "$topic ${range.first}~${range.last}분 사이로 정할 수 있어서 ${fixed}분으로 맞췄어요." +
                                if (!state.demoMode && m < range.first) " (데모 모드에서는 1분부터 돼요)" else ""
                        }
                        updateProfile { withMinutes(it, cmd.mode, fixed) }
                    }
                    startSession(cmd.mode)
                    note?.let { showBanner(it) }
                }
            }
            VoiceCommand.Stop -> when {
                state.screen == Screen.WAKE -> wakeUp()
                active -> stopByUser()
                else -> showBanner("지금은 끝낼 쉼이 없어요.")
            }
            VoiceCommand.Snooze ->
                if (state.screen == Screen.WAKE && canSnooze(state)) snooze()
                else showBanner("지금은 더 자기를 할 수 없어요.")
            VoiceCommand.WakeUp ->
                if (state.screen == Screen.WAKE) wakeUp() else showBanner("알겠어요.")
            VoiceCommand.Unknown -> showBanner(
                if (text.isNullOrBlank()) "잘 못 들었어요. 다시 한 번 말해 주세요."
                else "\"$text\" — 잘 모르겠어요. 예) \"20분 쉬기\", \"밥 30분\", \"차박할래\""
            )
        }
    }

    // ───────────── 온잠 기상 ─────────────

    /** "일어났어요" */
    fun wakeUp() {
        stopAlarm()
        _ui.update { it.copy(screen = Screen.RETURN) }
        val s = _ui.value.session
        announce(
            when {
                s.endReason == EndReason.LOW_BATTERY -> "충전 가능한 곳을 먼저 확인해 주세요."
                s.mode == RestMode.ONBAM -> "좋은 아침이에요. 창문을 열어 환기하고 배터리를 확인해요."
                else -> "잘 잤어요. 창문을 열고 물을 한 모금 마셔 보세요."
            }
        )
    }

    /** "5분만 더" (최대 2번) */
    fun snooze() {
        val next = RestSessionEngine.snooze(_ui.value.session, now()) ?: return
        stopAlarm()
        applySession(next)
        _ui.update { it.copy(screen = Screen.SESSION) }
        ambient.play(soundFor(next.mode), _ui.value.profile.volume)
        showBanner("5분 뒤에 다시 깨워드릴게요. (${next.snoozeCount}/${RestSessionEngine.MAX_SNOOZE})")
    }

    fun canSnooze(state: UiState = _ui.value): Boolean = RestSessionEngine.canSnooze(state.session)

    /**
     * 기상 알람.
     * 고른 알람음을 작게 시작해 점점 키우고, 알람음 사이 조용한 틈에만 잠깐 마이크를 열어
     * "일어났어" / "5분만 더" / "확인"을 듣는다 (Pleos STT 가 될 때만). 약 4~5분 뒤 자동으로 멈춘다.
     */
    private fun startAlarm(message: String = "일어날 시간이에요. 천천히 눈을 떠 보세요.") {
        alarmJob?.cancel()
        alarmJob = viewModelScope.launch {
            var volume = 0.3f
            var cycle = 0
            while (isActive && cycle < MAX_ALARM_CYCLES) {
                val p = _ui.value.profile
                ambient.playAlarm(p.alarmSound, volume)
                delay(ALARM_SOUND_MS)

                if (cycle % 5 == 0 && p.voiceOn) {
                    voice.speak(message)
                    delay(1_000L + message.length * 120L)
                }

                if (voiceInput.isAvailable && !_ui.value.listening) {
                    val heard = listenOnce()
                    if (heard != null && handleWakeSpeech(heard)) return@launch
                } else {
                    delay(1_500)
                }
                volume = (volume + 0.08f).coerceAtMost(1f)
                cycle++
            }
        }
    }

    /** 마이크를 한 번 열어 결과를 기다린다 (알람 루프 안에서만 사용) */
    private suspend fun listenOnce(): String? {
        _ui.update { it.copy(listening = true) }
        val result = withTimeoutOrNull(ALARM_LISTEN_MS) {
            suspendCancellableCoroutine<String?> { cont ->
                voiceInput.listen { text -> if (cont.isActive) cont.resume(text) }
                cont.invokeOnCancellation { voiceInput.stop() }
            }
        }
        _ui.update { it.copy(listening = false) }
        return result
    }

    /** 기상 화면에서 들은 말 처리. 알람을 끝냈으면 true */
    private fun handleWakeSpeech(text: String): Boolean {
        if (_ui.value.screen != Screen.WAKE) return false
        return when (VoiceCommandParser.parse(text)) {
            VoiceCommand.WakeUp, VoiceCommand.Stop -> { wakeUp(); true }
            VoiceCommand.Snooze -> if (canSnooze()) { snooze(); true } else {
                showBanner("더 자기는 ${RestSessionEngine.MAX_SNOOZE}번까지예요. \"일어났어\"라고 말해 주세요.")
                false
            }
            else -> {
                showBanner("들은 말: \"$text\" — \"일어났어요\" 또는 \"5분만 더\"라고 말해 주세요.")
                false
            }
        }
    }

    fun previewAlarm(sound: AlarmSound) {
        ambient.playAlarm(sound, _ui.value.profile.volume.coerceAtLeast(0.4f))
    }

    private fun stopAlarm() {
        alarmJob?.cancel()
        alarmJob = null
    }

    // ───────────── 세션 내부 ─────────────

    private fun endSession(reason: EndReason) {
        val result = RestSessionEngine.stop(_ui.value.session, reason, now())
        applySession(result.state)
        result.events.forEach(::handleEvent)
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (isActive) {
                val s = _ui.value.session
                if (s.phase == SessionPhase.RUNNING) {
                    val r = RestSessionEngine.tick(s, now())
                    if (r.state != s) applySession(r.state)
                    r.events.forEach(::handleEvent)
                    // 온밤: 잠들 때만 소리를 켜 두고 일정 시간 뒤 자동으로 끈다 (배터리 절약)
                    if (r.state.mode == RestMode.ONBAM && r.state.phase == SessionPhase.RUNNING &&
                        !_ui.value.nightSoundOff && r.state.snoozeCount == 0 &&
                        RestSessionEngine.elapsedMs(r.state, now()) >= NIGHT_SOUND_OFF_MIN * 60_000L
                    ) {
                        ambient.stop()
                        _ui.update { it.copy(nightSoundOff = true) }
                    }
                }
                _ui.update { it.copy(remainingMs = RestSessionEngine.remainingMs(it.session, now())) }
                delay(500)
            }
        }
    }

    private fun restoreSession(saved: SessionState) {
        when (saved.phase) {
            SessionPhase.RUNNING, SessionPhase.PAUSED -> {
                val r = RestSessionEngine.tick(saved, now())
                applySession(r.state)
                if (r.state.phase == SessionPhase.FINISHED) {
                    // 앱을 나가 있는 동안 시간이 다 됨 → 조용히 복귀 화면으로
                    _ui.update { it.copy(today = store.addToday(r.state.restedMs), screen = Screen.RETURN) }
                } else {
                    _ui.update { it.copy(screen = Screen.SESSION) }
                    val nightQuiet = r.state.mode == RestMode.ONBAM &&
                        RestSessionEngine.elapsedMs(r.state, now()) >= NIGHT_SOUND_OFF_MIN * 60_000L
                    if (nightQuiet) _ui.update { it.copy(nightSoundOff = true) }
                    if (r.state.phase == SessionPhase.RUNNING && !nightQuiet) {
                        ambient.play(soundFor(r.state.mode), _ui.value.profile.volume)
                    }
                }
            }
            else -> applySession(RestSessionEngine.reset())
        }
    }

    private fun handleEvent(event: SessionEvent) {
        val p = _ui.value.profile
        val mode = _ui.value.session.mode
        when (event) {
            is SessionEvent.MidPoint -> {
                ambient.playChime(p.volume * 0.6f)
                announce("창문을 조금 열어 한 번 환기해요.")
            }
            is SessionEvent.PreAlert -> when (mode) {
                RestMode.ONSWIM -> {
                    ambient.playChime(p.volume)
                    val min = ((event.remainingMs + 59_999) / 60_000).coerceAtLeast(1)
                    announce("${min}분 뒤에 휴식이 끝나요. 천천히 깨어날 준비를 해요.")
                }
                RestMode.ONJAM -> {
                    // 자는 사람을 말로 깨우지 않고, 소리를 조금씩 키우며 작은 차임만
                    ambient.setVolume((p.volume + 0.25f).coerceAtMost(1f))
                    ambient.playChime(p.volume * 0.4f)
                    showBanner("곧 깨워드릴게요.")
                }
                RestMode.ONKKI -> {
                    ambient.playChime(p.volume)
                    val min = ((event.remainingMs + 59_999) / 60_000).coerceAtLeast(1)
                    announce("${min}분 뒤에 식사 시간이 끝나요. 슬슬 정리해요.")
                }
                RestMode.ONJEONG -> Unit
                RestMode.ONBAM -> {
                    // 꺼져 있던 사운드를 작게 다시 켜서 천천히 깨운다
                    ambient.play(soundFor(RestMode.ONBAM), (p.volume * 0.5f).coerceAtLeast(0.2f))
                    ambient.setVolume((p.volume + 0.2f).coerceAtMost(1f))
                    ambient.playChime(p.volume * 0.4f)
                    showBanner("곧 기상 시각이에요.")
                }
            }
            is SessionEvent.Ended -> {
                ambient.stop()
                val today = store.addToday(event.restedMs)
                _ui.update { it.copy(today = today) }
                when {
                    event.reason == EndReason.LOW_BATTERY -> {
                        _ui.update { it.copy(screen = Screen.WAKE) }
                        startAlarm(message = "배터리가 기준보다 낮아졌어요. 일어나서 차량 상태를 확인해 주세요.")
                    }
                    event.reason == EndReason.COMPLETED && (mode == RestMode.ONJAM || mode == RestMode.ONBAM) -> {
                        _ui.update { it.copy(screen = Screen.WAKE) }
                        startAlarm()
                    }
                    event.reason == EndReason.COMPLETED -> {
                        _ui.update { it.copy(screen = Screen.RETURN) }
                        ambient.playChime(p.volume)
                        announce(
                            when (mode) {
                                RestMode.ONKKI -> "식사 시간이 끝났어요. 정리하고 출발 준비를 해요."
                                RestMode.ONJEONG -> "수고했어요. 천천히 눈을 뜨고 하루를 내려놓아요."
                                else -> "휴식이 끝났어요. 기지개를 켜고 천천히 출발 준비를 해요."
                            }
                        )
                    }
                    event.reason == EndReason.USER_STOPPED -> {
                        _ui.update { it.copy(screen = Screen.RETURN) }
                        announce(if (mode == RestMode.ONKKI) "온끼를 마쳤어요." else "${mode.label}을 마쳤어요.")
                    }
                    else -> {
                        _ui.update { it.copy(screen = Screen.RETURN) }
                        announce("주행이 감지되어 휴식을 안전하게 종료했어요.")
                    }
                }
            }
        }
    }

    private fun applySession(s: SessionState) {
        _ui.update { it.copy(session = s, remainingMs = RestSessionEngine.remainingMs(s, now())) }
        store.saveSession(s)
    }

    private fun soundFor(mode: RestMode): RestSound = soundFor(mode, _ui.value.profile)

    // ───────────── 안내 ─────────────

    /** 음성 + 화면 동시 안내. 음성이 꺼져 있거나 실패해도 화면 문구는 남는다. */
    private fun announce(text: String) {
        if (_ui.value.profile.voiceOn) {
            // 말하는 동안 배경음을 낮춰 음성이 묻히거나 섞여 깨지지 않게
            ambient.duck(true)
            duckJob?.cancel()
            duckJob = viewModelScope.launch {
                delay(1_500L + text.length * 120L)
                ambient.duck(false)
            }
            voice.speak(text)
        }
        showBanner(text)
    }

    private fun showBanner(text: String) {
        bannerJob?.cancel()
        _ui.update { it.copy(banner = text) }
        bannerJob = viewModelScope.launch {
            delay(7_000)
            _ui.update { it.copy(banner = null) }
        }
    }

    fun previewSound(sound: RestSound) {
        if (_ui.value.session.phase == SessionPhase.RUNNING) return
        ambient.play(sound, _ui.value.profile.volume)
        viewModelScope.launch {
            delay(4_000)
            if (_ui.value.session.phase != SessionPhase.RUNNING) ambient.stop()
        }
    }

    fun clearHistory() {
        store.clearHistory()
        _ui.update { it.copy(today = TodayStats()) }
        showBanner("오늘의 쉼 기록을 지웠어요.")
    }

    // ───────────── SDK 진단 ─────────────

    fun runDiagnostics() {
        val voiceRows = listOf(
            SdkDiagEntry(
                "TextToSpeech", "initialize (ON_DEVICE)", pleosVoice.engineName,
                ok = pleosVoice.engineName.startsWith("Pleos"),
            ),
            SdkDiagEntry(
                "SpeechToText", "initialize (ON_DEVICE)",
                if (voiceInput.isAvailable) "연결됨 · 상단 \"말로 하기\"로 사용" else "사용 불가 (음성 서비스 없음)",
                ok = voiceInput.isAvailable,
            ),
        )
        _ui.update { it.copy(diag = voiceRows) }
        if (!real.isAutomotive) {
            _ui.update {
                it.copy(diag = voiceRows + SdkDiagEntry("Vehicle", "-", "차량(Automotive) 환경이 아니어서 Vehicle SDK를 호출하지 않았어요.", ok = false))
            }
            return
        }
        if (!real.pleos.isConnected) real.pleos.connect()
        viewModelScope.launch {
            delay(300)
            real.pleos.diagnose { entry ->
                _ui.update { it.copy(diag = it.diag + entry) }
            }
        }
    }

    override fun onCleared() {
        voiceInput.release()
        stopAlarm()
        ambient.stop()
        voice.shutdown()
        real.stop()
        super.onCleared()
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val NAP_WAKE_RAMP_MIN = 2
        const val MEAL_WRAP_UP_MIN = 5
        const val NIGHT_WAKE_RAMP_MIN = 5
        const val NIGHT_SOUND_OFF_MIN = 30
        const val MAX_ALARM_CYCLES = 30
        const val ALARM_SOUND_MS = 2_600L
        const val ALARM_LISTEN_MS = 6_000L

        /** 다음 hh:mm 까지 남은 분 (지났으면 내일) */
        fun minutesUntil(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Int {
            var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return java.time.Duration.between(now, target).toMinutes().toInt().coerceAtLeast(1)
        }

        fun minutesFor(mode: RestMode, p: RestProfile): Int = when (mode) {
            RestMode.ONSWIM -> p.durationMin
            RestMode.ONJAM -> p.napDurationMin
            RestMode.ONKKI -> p.mealDurationMin
            RestMode.ONJEONG -> p.breathDurationMin
            RestMode.ONBAM -> minutesUntil(p.wakeHour, p.wakeMinute)
        }

        /** 모드별로 정할 수 있는 길이(분). 데모 모드에서는 시연을 위해 1분부터 허용 */
        fun minutesRange(mode: RestMode, demo: Boolean): IntRange {
            val normal = when (mode) {
                RestMode.ONSWIM -> 5..60
                RestMode.ONJAM -> 5..40
                RestMode.ONKKI -> 10..60
                RestMode.ONJEONG -> 3..15
                RestMode.ONBAM -> 30..720
            }
            return if (demo && mode != RestMode.ONBAM) 1..normal.last else normal
        }

        /** 범위 확인은 호출하는 쪽에서 한다 (minutesRange) */
        fun withMinutes(p: RestProfile, mode: RestMode, m: Int): RestProfile = when (mode) {
            RestMode.ONSWIM -> p.copy(durationMin = m)
            RestMode.ONJAM -> p.copy(napDurationMin = m)
            RestMode.ONKKI -> p.copy(mealDurationMin = m)
            RestMode.ONJEONG -> p.copy(breathDurationMin = m)
            RestMode.ONBAM -> p
        }

        fun soundFor(mode: RestMode, p: RestProfile): RestSound = when (mode) {
            RestMode.ONSWIM -> p.sound
            RestMode.ONJAM -> p.napSound
            RestMode.ONKKI -> p.mealSound
            RestMode.ONJEONG -> p.breathSound
            RestMode.ONBAM -> p.nightSound
        }
    }
}

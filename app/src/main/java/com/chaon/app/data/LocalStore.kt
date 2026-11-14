package com.chaon.app.data

import android.content.Context
import com.chaon.app.audio.AlarmSound
import com.chaon.app.audio.RestSound
import com.chaon.app.session.BreathPattern
import com.chaon.app.session.EndReason
import com.chaon.app.session.HvacLevel
import com.chaon.app.session.RestMode
import com.chaon.app.session.SessionPhase
import com.chaon.app.session.SessionState
import java.time.LocalDate

/** 사용자 휴식 프로필 (FR-03). 로그인 없이 기기 안에만 저장한다 (정의서 05장 개인정보). */
data class RestProfile(
    val durationMin: Int = 20,
    val sound: RestSound = RestSound.RAIN,
    val volume: Float = 0.6f,
    val preAlertMin: Int = 3,
    val voiceOn: Boolean = true,
    // 온잠 전용
    val napDurationMin: Int = 20,
    val napSound: RestSound = RestSound.WAVES,
    // 온끼 전용
    val mealDurationMin: Int = 20,
    val mealSound: RestSound = RestSound.SILENCE,
    // 온정 전용
    val breathDurationMin: Int = 5,
    val breathSound: RestSound = RestSound.WAVES,
    val breathPattern: BreathPattern = BreathPattern.CALM,
    // 온밤 전용
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val hvacLevel: HvacLevel = HvacLevel.MID,
    val reservePercent: Int = 20,
    /** 배터리 용량(제원) — 차량이 값을 주면 그 값을 우선 쓴다 */
    val capacityKWh: Float = 77.4f,
    /** SOH 추정값 — SDK 미제공으로 보수적 가정 */
    val sohPercent: Int = 90,
    val nightSound: RestSound = RestSound.RAIN,
    /** 차량 SOC를 못 읽을 때 사용자가 입력하는 배터리(%) */
    val manualSocPercent: Int = 70,
    /** 온잠·온밤 기상 알람음 */
    val alarmSound: AlarmSound = AlarmSound.CHIME,
)

data class TodayStats(val sessions: Int = 0, val restedMin: Int = 0)

class LocalStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("chaon", Context.MODE_PRIVATE)

    fun loadProfile(): RestProfile = RestProfile(
        durationMin = prefs.getInt("p_duration", 20),
        sound = runCatching { RestSound.valueOf(prefs.getString("p_sound", null) ?: "") }.getOrDefault(RestSound.RAIN),
        volume = prefs.getFloat("p_volume", 0.6f),
        preAlertMin = prefs.getInt("p_prealert", 3),
        voiceOn = prefs.getBoolean("p_voice", true),
        napDurationMin = prefs.getInt("p_napDuration", 20),
        napSound = sound("p_napSound", RestSound.WAVES),
        mealDurationMin = prefs.getInt("p_mealDuration", 20),
        mealSound = sound("p_mealSound", RestSound.SILENCE),
        breathDurationMin = prefs.getInt("p_breathDuration", 5),
        breathSound = sound("p_breathSound", RestSound.WAVES),
        breathPattern = runCatching { BreathPattern.valueOf(prefs.getString("p_breathPattern", null) ?: "") }
            .getOrDefault(BreathPattern.CALM),
        wakeHour = prefs.getInt("p_wakeHour", 7),
        wakeMinute = prefs.getInt("p_wakeMinute", 0),
        hvacLevel = runCatching { HvacLevel.valueOf(prefs.getString("p_hvac", null) ?: "") }.getOrDefault(HvacLevel.MID),
        reservePercent = prefs.getInt("p_reserve", 20),
        capacityKWh = prefs.getFloat("p_capacity", 77.4f),
        sohPercent = prefs.getInt("p_soh", 90),
        nightSound = sound("p_nightSound", RestSound.RAIN),
        manualSocPercent = prefs.getInt("p_manualSoc", 70),
        alarmSound = runCatching { AlarmSound.valueOf(prefs.getString("p_alarm", null) ?: "") }.getOrDefault(AlarmSound.CHIME),
    )

    private fun sound(key: String, default: RestSound): RestSound =
        runCatching { RestSound.valueOf(prefs.getString(key, null) ?: "") }.getOrDefault(default)

    fun saveProfile(p: RestProfile) {
        prefs.edit()
            .putInt("p_duration", p.durationMin)
            .putString("p_sound", p.sound.name)
            .putFloat("p_volume", p.volume)
            .putInt("p_prealert", p.preAlertMin)
            .putBoolean("p_voice", p.voiceOn)
            .putInt("p_napDuration", p.napDurationMin)
            .putString("p_napSound", p.napSound.name)
            .putInt("p_mealDuration", p.mealDurationMin)
            .putString("p_mealSound", p.mealSound.name)
            .putInt("p_breathDuration", p.breathDurationMin)
            .putString("p_breathSound", p.breathSound.name)
            .putString("p_breathPattern", p.breathPattern.name)
            .putInt("p_wakeHour", p.wakeHour)
            .putInt("p_wakeMinute", p.wakeMinute)
            .putString("p_hvac", p.hvacLevel.name)
            .putInt("p_reserve", p.reservePercent)
            .putFloat("p_capacity", p.capacityKWh)
            .putInt("p_soh", p.sohPercent)
            .putString("p_nightSound", p.nightSound.name)
            .putInt("p_manualSoc", p.manualSocPercent)
            .putString("p_alarm", p.alarmSound.name)
            .apply()
    }

    /** 진행 중 세션을 저장 → 앱을 다시 열어도 남은 시간 유지 (FR-02, T2) */
    fun saveSession(s: SessionState) {
        prefs.edit()
            .putString("s_phase", s.phase.name)
            .putString("s_mode", s.mode.name)
            .putInt("s_snooze", s.snoozeCount)
            .putLong("s_total", s.totalMs)
            .putLong("s_endAt", s.endAtMs ?: -1L)
            .putLong("s_paused", s.pausedRemainingMs)
            .putLong("s_prealert", s.preAlertMs)
            .putBoolean("s_prealertFired", s.preAlertFired)
            .putBoolean("s_mid", s.midAlert)
            .putBoolean("s_midFired", s.midAlertFired)
            .putFloat("s_startSoc", s.startSocPercent ?: -1f)
            .putLong("s_startedAt", s.startedAtMs ?: -1L)
            .putString("s_reason", s.endReason?.name)
            .putLong("s_rested", s.restedMs)
            .apply()
    }

    fun loadSession(): SessionState {
        val phase = runCatching { SessionPhase.valueOf(prefs.getString("s_phase", null) ?: "") }
            .getOrDefault(SessionPhase.IDLE)
        return SessionState(
            phase = phase,
            mode = runCatching { RestMode.valueOf(prefs.getString("s_mode", null) ?: "") }.getOrDefault(RestMode.ONSWIM),
            snoozeCount = prefs.getInt("s_snooze", 0),
            totalMs = prefs.getLong("s_total", 0L),
            endAtMs = prefs.getLong("s_endAt", -1L).takeIf { it >= 0 },
            pausedRemainingMs = prefs.getLong("s_paused", 0L),
            preAlertMs = prefs.getLong("s_prealert", 0L),
            preAlertFired = prefs.getBoolean("s_prealertFired", false),
            midAlert = prefs.getBoolean("s_mid", false),
            midAlertFired = prefs.getBoolean("s_midFired", false),
            startSocPercent = prefs.getFloat("s_startSoc", -1f).takeIf { it >= 0f },
            startedAtMs = prefs.getLong("s_startedAt", -1L).takeIf { it >= 0 },
            endReason = prefs.getString("s_reason", null)?.let { n -> runCatching { EndReason.valueOf(n) }.getOrNull() },
            restedMs = prefs.getLong("s_rested", 0L),
        )
    }

    fun loadToday(): TodayStats {
        if (prefs.getString("t_date", null) != today()) return TodayStats()
        return TodayStats(prefs.getInt("t_sessions", 0), prefs.getInt("t_minutes", 0))
    }

    fun addToday(restedMs: Long): TodayStats {
        val current = loadToday()
        val next = TodayStats(current.sessions + 1, current.restedMin + (restedMs / 60_000L).toInt())
        prefs.edit()
            .putString("t_date", today())
            .putInt("t_sessions", next.sessions)
            .putInt("t_minutes", next.restedMin)
            .apply()
        return next
    }

    /** 설정 화면의 "기록 삭제" */
    fun clearHistory() {
        prefs.edit().remove("t_date").remove("t_sessions").remove("t_minutes").apply()
    }

    private fun today(): String = LocalDate.now().toString()
}

package com.chaon.app.session

/**
 * 차온 세션 상태 머신 (온쉼 · 온잠 · 온끼 · 온정 공통).
 *
 * 안드로이드에 의존하지 않는 순수 Kotlin 코드라서 단위 테스트로 검증한다
 * (정의서 07장: "핵심 상태 머신과 시간 계산은 플랫폼 어댑터에서 분리해 단위 테스트").
 *
 * 시간은 항상 호출하는 쪽이 넘겨주는 now(ms)로 계산한다. 종료 시각(endAtMs)을 저장해 두기 때문에
 * 앱을 나갔다 들어와도 남은 시간이 일관된다(FR-02).
 */
enum class SessionPhase { IDLE, RUNNING, PAUSED, FINISHED }

/** 쉼 모드. 같은 세션 엔진을 쓰고 안내·종료 방식만 다르다. */
enum class RestMode(val label: String, val tagline: String) {
    ONSWIM("온쉼", "잠깐 쉬기"),
    ONJAM("온잠", "짧은 낮잠"),
    ONKKI("온끼", "차 안 식사"),
    ONJEONG("온정", "호흡 가이드"),
    ONBAM("온밤", "차박 취침"),
}

enum class EndReason { COMPLETED, USER_STOPPED, DRIVING_DETECTED, LOW_BATTERY }

data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val mode: RestMode = RestMode.ONSWIM,
    /** 온잠·온밤에서 "5분만 더"를 누른 횟수 */
    val snoozeCount: Int = 0,
    /** 온밤: 시작할 때의 배터리(%) — 아침에 사용량 요약에 쓴다 */
    val startSocPercent: Float? = null,
    /** 처음 선택한 길이 */
    val totalMs: Long = 0L,
    /** RUNNING 중에는 종료 예정 시각, 그 외에는 null */
    val endAtMs: Long? = null,
    /** PAUSED 일 때 남아 있던 시간 */
    val pausedRemainingMs: Long = 0L,
    /** 종료 몇 ms 전에 예고할지 (0이면 예고 없음) */
    val preAlertMs: Long = 0L,
    val preAlertFired: Boolean = false,
    /** 중간 지점 알림 사용 여부 (온끼: 환기 알림) */
    val midAlert: Boolean = false,
    val midAlertFired: Boolean = false,
    val startedAtMs: Long? = null,
    val endReason: EndReason? = null,
    /** 실제로 쉰 시간 (FINISHED 일 때 채워짐) */
    val restedMs: Long = 0L,
)

sealed interface SessionEvent {
    data object MidPoint : SessionEvent
    data class PreAlert(val remainingMs: Long) : SessionEvent
    data class Ended(val reason: EndReason, val restedMs: Long) : SessionEvent
}

data class TickResult(val state: SessionState, val events: List<SessionEvent>)

object RestSessionEngine {

    const val MIN_DURATION_MS = 3 * 60_000L
    /** 데모(시연) 모드에서는 1분짜리도 허용해 흐름을 빨리 보여줄 수 있게 한다 */
    const val DEMO_MIN_DURATION_MS = 60_000L
    const val MAX_DURATION_MS = 60 * 60_000L
    /** 온밤은 기상 시각까지라서 최대 12시간 */
    const val MAX_NIGHT_MS = 12 * 60 * 60_000L

    fun maxDurationMs(mode: RestMode): Long = if (mode == RestMode.ONBAM) MAX_NIGHT_MS else MAX_DURATION_MS
    const val SNOOZE_MS = 5 * 60_000L
    const val MAX_SNOOZE = 2

    fun start(
        durationMs: Long,
        preAlertMs: Long,
        now: Long,
        mode: RestMode = RestMode.ONSWIM,
        snoozeCount: Int = 0,
        midAlert: Boolean = false,
        startSocPercent: Float? = null,
        minDurationMs: Long = MIN_DURATION_MS,
    ): SessionState {
        val duration = durationMs.coerceIn(minDurationMs, maxDurationMs(mode))
        // 예고 시간이 휴식 길이보다 길거나 같으면 예고를 하지 않는다.
        val alert = if (preAlertMs in 1 until duration) preAlertMs else 0L
        return SessionState(
            phase = SessionPhase.RUNNING,
            mode = mode,
            snoozeCount = snoozeCount,
            totalMs = duration,
            endAtMs = now + duration,
            preAlertMs = alert,
            midAlert = midAlert,
            startSocPercent = startSocPercent,
            startedAtMs = now,
        )
    }

    fun remainingMs(state: SessionState, now: Long): Long = when (state.phase) {
        SessionPhase.RUNNING -> ((state.endAtMs ?: now) - now).coerceAtLeast(0L)
        SessionPhase.PAUSED -> state.pausedRemainingMs
        SessionPhase.IDLE -> 0L
        SessionPhase.FINISHED -> 0L
    }

    /** 시작 후 흐른 시간 (일시정지 시간 제외) */
    fun elapsedMs(state: SessionState, now: Long): Long =
        (state.totalMs - remainingMs(state, now)).coerceIn(0L, state.totalMs)

    fun pause(state: SessionState, now: Long): SessionState {
        if (state.phase != SessionPhase.RUNNING) return state
        return state.copy(
            phase = SessionPhase.PAUSED,
            pausedRemainingMs = remainingMs(state, now),
            endAtMs = null,
        )
    }

    fun resume(state: SessionState, now: Long): SessionState {
        if (state.phase != SessionPhase.PAUSED) return state
        return state.copy(
            phase = SessionPhase.RUNNING,
            endAtMs = now + state.pausedRemainingMs,
            pausedRemainingMs = 0L,
        )
    }

    /** 사용자가 끝내거나, 주행이 감지돼 끝낼 때 */
    fun stop(state: SessionState, reason: EndReason, now: Long): TickResult {
        if (state.phase != SessionPhase.RUNNING && state.phase != SessionPhase.PAUSED) {
            return TickResult(state, emptyList())
        }
        val rested = elapsedMs(state, now)
        val ended = state.copy(
            phase = SessionPhase.FINISHED,
            endAtMs = null,
            pausedRemainingMs = 0L,
            endReason = reason,
            restedMs = rested,
        )
        return TickResult(ended, listOf(SessionEvent.Ended(reason, rested)))
    }

    /** 주기적으로(예: 0.5초마다) 호출. 중간·예고·종료 이벤트를 한 번씩만 낸다. */
    fun tick(state: SessionState, now: Long): TickResult {
        if (state.phase != SessionPhase.RUNNING) return TickResult(state, emptyList())

        val remaining = remainingMs(state, now)
        val events = mutableListOf<SessionEvent>()
        var next = state

        if (next.midAlert && !next.midAlertFired && remaining > 0 && elapsedMs(next, now) * 2 >= next.totalMs) {
            next = next.copy(midAlertFired = true)
            events += SessionEvent.MidPoint
        }

        if (!next.preAlertFired && next.preAlertMs > 0 && remaining in 1..next.preAlertMs) {
            next = next.copy(preAlertFired = true)
            events += SessionEvent.PreAlert(remaining)
        }

        if (remaining <= 0L) {
            next = next.copy(
                phase = SessionPhase.FINISHED,
                endAtMs = null,
                endReason = EndReason.COMPLETED,
                restedMs = next.totalMs,
            )
            events += SessionEvent.Ended(EndReason.COMPLETED, next.totalMs)
        }
        return TickResult(next, events)
    }

    /** 온잠·온밤이 시간대로 끝난 뒤 "5분만 더" 가능한가 */
    fun canSnooze(state: SessionState): Boolean =
        (state.mode == RestMode.ONJAM || state.mode == RestMode.ONBAM) &&
            state.phase == SessionPhase.FINISHED &&
            state.endReason == EndReason.COMPLETED &&
            state.snoozeCount < MAX_SNOOZE

    /** 5분 더 자기. 불가능하면 null */
    fun snooze(state: SessionState, now: Long): SessionState? {
        if (!canSnooze(state)) return null
        return start(
            SNOOZE_MS, preAlertMs = 0L, now = now, mode = state.mode,
            snoozeCount = state.snoozeCount + 1, startSocPercent = state.startSocPercent,
        )
    }

    /** IDLE 로 되돌리기 (복귀 화면에서 홈으로) */
    fun reset(): SessionState = SessionState()
}

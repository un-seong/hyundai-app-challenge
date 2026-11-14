package com.chaon.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestSessionEngineTest {

    private val min = 60_000L

    @Test
    fun `T1 20분 세션이 시작되고 시간이 다 되면 COMPLETED 로 끝난다`() {
        val s = RestSessionEngine.start(20 * min, 3 * min, now = 0L)
        assertEquals(SessionPhase.RUNNING, s.phase)
        assertEquals(20 * min, RestSessionEngine.remainingMs(s, 0L))

        val done = RestSessionEngine.tick(s, now = 20 * min)
        assertEquals(SessionPhase.FINISHED, done.state.phase)
        assertEquals(EndReason.COMPLETED, done.state.endReason)
        assertTrue(done.events.any { it is SessionEvent.Ended })
    }

    @Test
    fun `종료 3분 전 예고는 한 번만 나간다`() {
        var s = RestSessionEngine.start(20 * min, 3 * min, now = 0L)
        assertTrue(RestSessionEngine.tick(s, 16 * min).events.isEmpty())

        val first = RestSessionEngine.tick(s, 17 * min)
        assertTrue(first.events.single() is SessionEvent.PreAlert)
        s = first.state

        assertTrue(RestSessionEngine.tick(s, 17 * min + 500).events.isEmpty())
    }

    @Test
    fun `T2 앱을 나갔다 와도 저장된 종료 시각 기준으로 남은 시간이 같다`() {
        val s = RestSessionEngine.start(20 * min, 0L, now = 1_000L)
        // 저장 → 복원은 같은 data class 값이므로 now 만 다르게 넣어 확인
        assertEquals(15 * min, RestSessionEngine.remainingMs(s, 1_000L + 5 * min))
    }

    @Test
    fun `일시정지하면 시간이 멈추고 재개하면 이어진다`() {
        var s = RestSessionEngine.start(10 * min, 0L, now = 0L)
        s = RestSessionEngine.pause(s, now = 4 * min)
        assertEquals(6 * min, RestSessionEngine.remainingMs(s, 100 * min))

        s = RestSessionEngine.resume(s, now = 100 * min)
        assertEquals(6 * min, RestSessionEngine.remainingMs(s, 100 * min))
        assertEquals(SessionPhase.FINISHED, RestSessionEngine.tick(s, 106 * min).state.phase)
    }

    @Test
    fun `T3 주행이 감지되면 그때까지 쉰 시간을 기록하고 끝낸다`() {
        val s = RestSessionEngine.start(20 * min, 3 * min, now = 0L)
        val r = RestSessionEngine.stop(s, EndReason.DRIVING_DETECTED, now = 7 * min)
        assertEquals(SessionPhase.FINISHED, r.state.phase)
        assertEquals(EndReason.DRIVING_DETECTED, r.state.endReason)
        assertEquals(7 * min, r.state.restedMs)
    }

    @Test
    fun `길이는 3분에서 60분 사이로 제한된다`() {
        assertEquals(3 * min, RestSessionEngine.start(1 * min, 0L, 0L).totalMs)
        assertEquals(60 * min, RestSessionEngine.start(90 * min, 0L, 0L).totalMs)
    }

    @Test
    fun `휴식보다 긴 예고 시간은 무시한다`() {
        val s = RestSessionEngine.start(5 * min, 10 * min, 0L)
        assertEquals(0L, s.preAlertMs)
    }

    @Test
    fun `온잠은 시간이 다 되면 5분 더 자기를 두 번까지 할 수 있다`() {
        var s = RestSessionEngine.start(20 * min, 2 * min, 0L, mode = RestMode.ONJAM)
        s = RestSessionEngine.tick(s, 20 * min).state
        assertTrue(RestSessionEngine.canSnooze(s))

        s = RestSessionEngine.snooze(s, 20 * min)!!
        assertEquals(SessionPhase.RUNNING, s.phase)
        assertEquals(5 * min, s.totalMs)
        assertEquals(1, s.snoozeCount)

        s = RestSessionEngine.tick(s, 25 * min).state
        s = RestSessionEngine.snooze(s, 25 * min)!!
        s = RestSessionEngine.tick(s, 30 * min).state
        assertEquals(2, s.snoozeCount)
        assertEquals(null, RestSessionEngine.snooze(s, 30 * min))
    }

    @Test
    fun `온쉼이나 주행 종료 후에는 5분 더 자기가 안 된다`() {
        val swim = RestSessionEngine.tick(RestSessionEngine.start(10 * min, 0L, 0L), 10 * min).state
        assertEquals(false, RestSessionEngine.canSnooze(swim))

        val nap = RestSessionEngine.start(20 * min, 0L, 0L, mode = RestMode.ONJAM)
        val stopped = RestSessionEngine.stop(nap, EndReason.DRIVING_DETECTED, 5 * min).state
        assertEquals(false, RestSessionEngine.canSnooze(stopped))
    }

    @Test
    fun `온끼는 중간 지점에서 환기 알림을 한 번 낸다`() {
        var s = RestSessionEngine.start(20 * min, 5 * min, 0L, mode = RestMode.ONKKI, midAlert = true)
        assertTrue(RestSessionEngine.tick(s, 9 * min).events.isEmpty())

        val mid = RestSessionEngine.tick(s, 10 * min)
        assertEquals(listOf<SessionEvent>(SessionEvent.MidPoint), mid.events)
        s = mid.state
        assertTrue(RestSessionEngine.tick(s, 11 * min).events.isEmpty())

        val pre = RestSessionEngine.tick(s, 15 * min)
        assertTrue(pre.events.single() is SessionEvent.PreAlert)
    }

    @Test
    fun `중간 알림을 끄면 이벤트가 없다`() {
        val s = RestSessionEngine.start(20 * min, 0L, 0L)
        assertTrue(RestSessionEngine.tick(s, 10 * min).events.isEmpty())
    }

    @Test
    fun `온밤은 12시간까지 허용하고 다른 모드는 60분까지다`() {
        val night = RestSessionEngine.start(9 * 60 * min, 0L, 0L, mode = RestMode.ONBAM)
        assertEquals(9 * 60 * min, night.totalMs)
        assertEquals(12 * 60 * min, RestSessionEngine.start(20 * 60 * min, 0L, 0L, mode = RestMode.ONBAM).totalMs)
        assertEquals(60 * min, RestSessionEngine.start(9 * 60 * min, 0L, 0L, mode = RestMode.ONSWIM).totalMs)
    }

    @Test
    fun `온밤도 5분 더 자기를 할 수 있고 시작 배터리 값은 유지된다`() {
        var s = RestSessionEngine.start(8 * 60 * min, 0L, 0L, mode = RestMode.ONBAM, startSocPercent = 72f)
        s = RestSessionEngine.tick(s, 8 * 60 * min).state
        val snoozed = RestSessionEngine.snooze(s, 8 * 60 * min)!!
        assertEquals(RestMode.ONBAM, snoozed.mode)
        assertEquals(72f, snoozed.startSocPercent)
    }

    @Test
    fun `데모 모드 최소값을 주면 1분 세션도 시작된다`() {
        val s = RestSessionEngine.start(
            1 * min, 2 * min, 0L, mode = RestMode.ONJAM,
            minDurationMs = RestSessionEngine.DEMO_MIN_DURATION_MS,
        )
        assertEquals(1 * min, s.totalMs)
        // 예고(2분)가 길이보다 길면 예고 없음
        assertEquals(0L, s.preAlertMs)
    }
}

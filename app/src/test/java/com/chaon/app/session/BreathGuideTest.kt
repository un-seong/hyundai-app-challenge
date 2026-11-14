package com.chaon.app.session

import org.junit.Assert.assertEquals
import org.junit.Test

class BreathGuideTest {

    @Test
    fun `편안 호흡은 4초 들이쉬고 6초 내쉰다`() {
        val p = BreathPattern.CALM
        assertEquals("들이쉬기", BreathGuide.positionAt(p, 0).step.label)
        assertEquals(4, BreathGuide.positionAt(p, 0).secondsLeft)
        assertEquals(1, BreathGuide.positionAt(p, 3_500).secondsLeft)
        assertEquals("내쉬기", BreathGuide.positionAt(p, 4_000).step.label)
        assertEquals(6, BreathGuide.positionAt(p, 4_000).secondsLeft)
        assertEquals("들이쉬기", BreathGuide.positionAt(p, 10_000).step.label)
        assertEquals(1, BreathGuide.positionAt(p, 10_000).cycle)
    }

    @Test
    fun `박스 호흡은 네 단계를 4초씩 돈다`() {
        val p = BreathPattern.BOX
        val labels = (0 until 4).map { BreathGuide.positionAt(p, it * 4_000L).stepIndex }
        assertEquals(listOf(0, 1, 2, 3), labels)
        assertEquals(0, BreathGuide.positionAt(p, 16_000).stepIndex)
    }
}

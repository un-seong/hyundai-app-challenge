package com.chaon.app.session

/** 온정 호흡의 한 단계. scale 은 화면 원 크기(0~1) */
data class BreathStep(val label: String, val seconds: Int, val scale: Float)

/** 온정 호흡 패턴 */
enum class BreathPattern(val label: String, val desc: String, val steps: List<BreathStep>) {
    CALM(
        "편안 호흡 4-6",
        "4초 들이쉬고 6초 내쉬어요. 내쉬는 숨을 길게 하면 몸이 차분해지기 쉬워요.",
        listOf(BreathStep("들이쉬기", 4, 1f), BreathStep("내쉬기", 6, 0.45f)),
    ),
    BOX(
        "박스 호흡 4-4-4-4",
        "들이쉬기 · 멈추기 · 내쉬기 · 멈추기를 4초씩 반복해요.",
        listOf(
            BreathStep("들이쉬기", 4, 1f),
            BreathStep("멈추기", 4, 1f),
            BreathStep("내쉬기", 4, 0.45f),
            BreathStep("멈추기", 4, 0.45f),
        ),
    );

    val cycleMs: Long get() = steps.sumOf { it.seconds } * 1_000L
}

/** 지금 몇 번째 단계이고 그 단계에서 몇 초 남았는지 */
data class BreathPosition(val step: BreathStep, val stepIndex: Int, val secondsLeft: Int, val cycle: Int)

object BreathGuide {
    fun positionAt(pattern: BreathPattern, elapsedMs: Long): BreathPosition {
        val safe = elapsedMs.coerceAtLeast(0L)
        val cycle = (safe / pattern.cycleMs).toInt()
        var t = safe % pattern.cycleMs
        pattern.steps.forEachIndexed { i, step ->
            val len = step.seconds * 1_000L
            if (t < len) {
                val left = ((len - t + 999) / 1_000).toInt()
                return BreathPosition(step, i, left, cycle)
            }
            t -= len
        }
        // 이론상 도달하지 않음
        return BreathPosition(pattern.steps.first(), 0, pattern.steps.first().seconds, cycle)
    }
}

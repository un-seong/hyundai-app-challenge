package com.chaon.app.session

import kotlin.math.max

/**
 * 온밤 에너지 예측 (정의서 12장).
 *
 * 원칙
 * - 차량이 주는 값(SOC, 용량)을 우선 쓰고, 없는 값은 사용자가 고른 "가정값"으로 채운다.
 * - SOH(배터리 건강도)는 SDK 제공이 확인되지 않아 보수적인 추정값을 쓰고, 화면에 "추정"으로 표시한다.
 * - 결과는 한 숫자가 아니라 범위(낙관~보수)로 보여주고, 판정은 보수적인 쪽 기준으로 한다.
 *
 * 공조 소비전력은 실측이 아닌 가정값이다. 실차 데이터로 보정해야 한다.
 */
enum class HvacLevel(val label: String, val desc: String, val kw: Double) {
    OFF("공조 끔", "창문 살짝 · 침낭 사용", 0.0),
    LOW("약하게", "송풍 · 선선한 날씨", 0.5),
    MID("보통", "봄·가을 밤 난방/냉방", 1.0),
    HIGH("강하게", "한겨울 난방 · 한여름 냉방", 2.0),
}

enum class NightVerdict(val label: String) {
    /** 보수적으로 봐도 기상 시각까지 충분 */
    OK("충분해요"),
    /** 평균적으로는 되지만 보수적으로는 빠듯함 */
    TIGHT("빠듯해요"),
    /** 평균적으로도 부족 */
    SHORT("부족해요"),
}

data class NightInputs(
    val socPercent: Double,
    val capacityKWh: Double,
    val sohPercent: Double,
    /** 아침에 남겨 둘 배터리(%) — 충전소까지 갈 최소량 */
    val reservePercent: Double,
    val hvac: HvacLevel,
    /** 기상까지 필요한 시간 */
    val neededHours: Double,
)

data class NightForecast(
    /** 지금 쓸 수 있는 에너지 (남길 배터리 제외) */
    val usableKWh: Double,
    /** 평균 소비전력 가정 (kW) */
    val loadKW: Double,
    /** 버틸 수 있는 시간: 보수 / 평균 / 낙관 */
    val hoursLow: Double,
    val hoursExpected: Double,
    val hoursHigh: Double,
    /** 기상 시각의 배터리(%): 보수 / 평균 */
    val socAtWakeLow: Double,
    val socAtWakeExpected: Double,
    val verdict: NightVerdict,
)

object NightEnergyModel {

    /** 공조 외 기본 전력(인포테인먼트, 12V 계통 등) 가정값 */
    const val BASE_LOAD_KW = 0.25

    /** 가정값의 불확실성: 실제 소비가 ±25% 다를 수 있다고 본다 */
    const val UNCERTAINTY = 0.25

    fun forecast(input: NightInputs): NightForecast {
        val effectiveCapacity = input.capacityKWh * (input.sohPercent / 100.0)
        val usablePercent = (input.socPercent - input.reservePercent).coerceAtLeast(0.0)
        val usableKWh = effectiveCapacity * usablePercent / 100.0

        val load = BASE_LOAD_KW + input.hvac.kw
        val loadHigh = load * (1 + UNCERTAINTY)
        val loadLow = load * (1 - UNCERTAINTY)

        val hoursExpected = usableKWh / load
        val hoursLow = usableKWh / loadHigh
        val hoursHigh = usableKWh / loadLow

        val socAtWakeExpected = socAfter(input, effectiveCapacity, load)
        val socAtWakeLow = socAfter(input, effectiveCapacity, loadHigh)

        val verdict = when {
            hoursLow >= input.neededHours -> NightVerdict.OK
            hoursExpected >= input.neededHours -> NightVerdict.TIGHT
            else -> NightVerdict.SHORT
        }
        return NightForecast(
            usableKWh = usableKWh,
            loadKW = load,
            hoursLow = hoursLow,
            hoursExpected = hoursExpected,
            hoursHigh = hoursHigh,
            socAtWakeLow = socAtWakeLow,
            socAtWakeExpected = socAtWakeExpected,
            verdict = verdict,
        )
    }

    /** 부족할 때: 공조를 몇 단계 낮추면 충분해지는지 (없으면 null) */
    fun suggestLowerHvac(input: NightInputs): HvacLevel? =
        HvacLevel.entries
            .filter { it.ordinal < input.hvac.ordinal }
            .sortedByDescending { it.ordinal }
            .firstOrNull { forecast(input.copy(hvac = it)).verdict == NightVerdict.OK }

    private fun socAfter(input: NightInputs, effectiveCapacity: Double, loadKW: Double): Double {
        if (effectiveCapacity <= 0.0) return 0.0
        val usedPercent = loadKW * input.neededHours / effectiveCapacity * 100.0
        return max(0.0, input.socPercent - usedPercent)
    }
}

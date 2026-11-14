package com.chaon.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightEnergyModelTest {

    private val base = NightInputs(
        socPercent = 80.0,
        capacityKWh = 80.0,
        sohPercent = 100.0,
        reservePercent = 20.0,
        hvac = HvacLevel.MID,
        neededHours = 8.0,
    )

    @Test
    fun `쓸 수 있는 에너지는 남길 배터리를 빼고 계산한다`() {
        // 80kWh × (80% - 20%) = 48kWh
        val f = NightEnergyModel.forecast(base)
        assertEquals(48.0, f.usableKWh, 1e-9)
        // 소비 1.25kW → 평균 38.4시간
        assertEquals(1.25, f.loadKW, 1e-9)
        assertEquals(38.4, f.hoursExpected, 1e-9)
        assertEquals(NightVerdict.OK, f.verdict)
    }

    @Test
    fun `SOH가 낮으면 유지시간이 비례해서 줄어든다`() {
        val full = NightEnergyModel.forecast(base)
        val aged = NightEnergyModel.forecast(base.copy(sohPercent = 80.0))
        assertEquals(full.hoursExpected * 0.8, aged.hoursExpected, 1e-9)
    }

    @Test
    fun `보수적으로 모자라면 빠듯함, 평균으로도 모자라면 부족`() {
        // 쓸 수 있는 에너지 80 × 10% = 8kWh, 강하게(2.25kW) → 평균 3.56시간
        val low = base.copy(socPercent = 30.0, hvac = HvacLevel.HIGH)
        assertEquals(NightVerdict.SHORT, NightEnergyModel.forecast(low).verdict)

        // 평균 시간 = 필요 시간 이 되도록 맞추면 평균으로는 되지만 보수로는 모자람
        val f = NightEnergyModel.forecast(low)
        val tight = low.copy(neededHours = f.hoursExpected * 0.95)
        assertEquals(NightVerdict.TIGHT, NightEnergyModel.forecast(tight).verdict)
    }

    @Test
    fun `기상 시 배터리는 0 아래로 내려가지 않는다`() {
        val f = NightEnergyModel.forecast(base.copy(socPercent = 25.0, hvac = HvacLevel.HIGH, neededHours = 12.0))
        assertTrue(f.socAtWakeLow >= 0.0)
        assertTrue(f.socAtWakeExpected >= f.socAtWakeLow)
    }

    @Test
    fun `부족하면 충분해지는 가장 높은 공조 단계를 제안한다`() {
        val input = base.copy(socPercent = 40.0, hvac = HvacLevel.HIGH, neededHours = 8.0)
        // 80 × 20% = 16kWh. 강하게 2.25kW → 7.1h(부족), 보통 1.25kW → 보수 10.2h(충분)
        assertEquals(HvacLevel.MID, NightEnergyModel.suggestLowerHvac(input))
    }

    @Test
    fun `이미 공조를 껐으면 더 낮출 단계가 없다`() {
        val input = base.copy(socPercent = 21.0, hvac = HvacLevel.OFF)
        assertNull(NightEnergyModel.suggestLowerHvac(input))
    }
}

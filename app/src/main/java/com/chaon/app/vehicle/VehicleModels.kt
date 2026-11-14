package com.chaon.app.vehicle

import kotlinx.coroutines.flow.StateFlow

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 데이터가 어디서 왔는지 화면에 항상 표시한다 (정의서 T6: 시연 데이터와 실제 데이터 구분) */
enum class DataSource { VEHICLE, DEMO }

enum class StopSafety { PARKED, MOVING, UNKNOWN }

data class VehicleSnapshot(
    val gear: Gear = Gear.UNKNOWN,
    val parkingBrakeOn: Boolean? = null,
    /** EV 배터리 SOC(%) — Pleos Vehicle SDK EvBattery 에서 읽은 값, 없으면 null */
    val socPercent: Float? = null,
    /** 배터리 용량(kWh) — SDK가 주면 채워짐, 없으면 null (온밤은 사용자 설정값 사용) */
    val batteryCapacityKWh: Float? = null,
    val source: DataSource = DataSource.DEMO,
    /** 사용자에게 보여줄 연결 상태 설명 */
    val statusNote: String = "",
) {
    /**
     * 정차 판단 (FR-04).
     * - 기어 D/R → 주행 중
     * - 기어 P 이거나 주차 브레이크 체결 → 정차
     * - 그 외(값을 못 읽음 등) → 불확실: 앱은 수동 확인을 요구한다
     */
    val safety: StopSafety
        get() = when {
            gear == Gear.DRIVE || gear == Gear.REVERSE -> StopSafety.MOVING
            gear == Gear.PARK || parkingBrakeOn == true -> StopSafety.PARKED
            else -> StopSafety.UNKNOWN
        }
}

/** 정의서 07장 VehicleGateway: 실제 차량 연동과 데모 구현을 바꿔 끼울 수 있게 한다 */
interface VehicleGateway {
    val snapshot: StateFlow<VehicleSnapshot>
    fun start()
    fun stop()
}

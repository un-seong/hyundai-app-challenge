package com.chaon.app.vehicle

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 실제 차량 연동.
 * - 기어·주차 브레이크: AAOS 표준 속성 (실시간 구독)
 * - SOC·주차 브레이크(보조): Pleos Vehicle SDK (30초마다 갱신)
 */
class RealVehicleGateway(
    context: Context,
    private val scope: CoroutineScope,
    val pleos: PleosSdkProbe = PleosSdkProbe(context),
) : VehicleGateway {

    private val appContext = context.applicationContext
    private val _snapshot = MutableStateFlow(VehicleSnapshot(source = DataSource.VEHICLE, statusNote = "차량 연결 중…"))
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private var aaos: AaosPowertrainReader? = null
    private var pollJob: Job? = null

    val isAutomotive: Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    override fun start() {
        if (!isAutomotive) {
            _snapshot.update { it.copy(statusNote = "차량 환경이 아님 (데모 모드를 사용하세요)") }
            return
        }
        val notes = mutableListOf<String>()

        val reader = AaosPowertrainReader(
            appContext,
            onGear = { gear -> _snapshot.update { it.copy(gear = gear) } },
            onParkingBrake = { on -> _snapshot.update { it.copy(parkingBrakeOn = on) } },
            onError = { msg -> _snapshot.update { it.copy(statusNote = msg) } },
        )
        notes += if (reader.start()) "기어·주차브레이크: AAOS 연결됨" else "기어: 읽기 실패"
        aaos = reader

        notes += if (pleos.connect().isSuccess) "Pleos Vehicle SDK 연결됨" else "Pleos Vehicle SDK 연결 실패"
        _snapshot.update { it.copy(statusNote = notes.joinToString(" · ")) }

        pollJob = scope.launch {
            delay(1_000) // SDK 서비스 바인딩 대기
            pleos.readCapacityKWh { kwh -> if (kwh != null) _snapshot.update { it.copy(batteryCapacityKWh = kwh) } }
            while (isActive) {
                pleos.readStateOfCharge { soc -> _snapshot.update { it.copy(socPercent = soc) } }
                // AAOS 가 주차 브레이크를 못 줬을 때만 Pleos 값으로 보완
                if (_snapshot.value.parkingBrakeOn == null) {
                    pleos.readParkingBrake { on -> if (on != null) _snapshot.update { it.copy(parkingBrakeOn = on) } }
                }
                delay(30_000)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        aaos?.stop()
        aaos = null
        pleos.release()
    }
}

/**
 * 시연·개발용 가상 차량. 화면에서 기어를 직접 바꿀 수 있다.
 * 화면에는 항상 "데모 데이터"로 표시된다 (T6).
 */
class DemoVehicleGateway : VehicleGateway {
    private val _snapshot = MutableStateFlow(
        VehicleSnapshot(
            gear = Gear.PARK,
            parkingBrakeOn = true,
            socPercent = 78f,
            source = DataSource.DEMO,
            statusNote = "데모 데이터 (실제 차량 값 아님)",
        )
    )
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    override fun start() = Unit
    override fun stop() = Unit

    fun setGear(gear: Gear) {
        _snapshot.update { it.copy(gear = gear, parkingBrakeOn = gear == Gear.PARK) }
    }

    /** 온밤 시연용: 배터리를 줄여서 경고 흐름을 보여준다 */
    fun changeSoc(delta: Float) {
        _snapshot.update { it.copy(socPercent = ((it.socPercent ?: 0f) + delta).coerceIn(0f, 100f)) }
    }
}

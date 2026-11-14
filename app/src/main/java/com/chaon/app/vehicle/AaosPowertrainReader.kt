package com.chaon.app.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * AAOS 표준 차량 속성으로 기어(P/R/N/D)와 주차 브레이크를 읽는다.
 *
 * Pleos Vehicle SDK 공개 카탈로그에는 Gear 가 없어서, 다른 Pleos 앱들처럼
 * Android Automotive 표준 속성(GEAR_SELECTION, PARKING_BRAKE_ON)을 사용한다.
 * 권한: android.car.permission.CAR_POWERTRAIN (normal)
 *
 * 주의: 이 클래스는 차량(Automotive) 환경에서만 생성해야 한다.
 * 일반 폰에는 android.car 라이브러리가 없어서 클래스 로딩 자체가 실패한다.
 */
internal class AaosPowertrainReader(
    context: Context,
    private val onGear: (Gear) -> Unit,
    private val onParkingBrake: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            handle(value.propertyId, value.value)
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            onError("차량 속성 오류 (id=$propId)")
        }
    }

    /** @return 연결에 성공했으면 true */
    fun start(): Boolean {
        return try {
            val connected = Car.createCar(appContext) ?: return false
            car = connected
            val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ?: return false
            propertyManager = manager

            // 현재 값 먼저 한 번 읽기
            runCatching { handle(VehiclePropertyIds.GEAR_SELECTION, manager.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0)) }
                .onFailure { Log.w(TAG, "gear read failed", it) }
            runCatching { handle(VehiclePropertyIds.PARKING_BRAKE_ON, manager.getBooleanProperty(VehiclePropertyIds.PARKING_BRAKE_ON, 0)) }
                .onFailure { Log.w(TAG, "parking brake read failed", it) }

            // 이후 변화 구독
            val gearOk = runCatching {
                manager.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            }.getOrDefault(false)
            val brakeOk = runCatching {
                manager.registerCallback(callback, VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            }.getOrDefault(false)

            if (!gearOk && !brakeOk) onError("기어·주차 브레이크 구독 실패 (권한 또는 지원 여부 확인 필요)")
            gearOk || brakeOk
        } catch (t: Throwable) {
            Log.w(TAG, "Car service unavailable", t)
            onError("차량 서비스 연결 실패: ${t.javaClass.simpleName}")
            false
        }
    }

    fun stop() {
        runCatching { propertyManager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        propertyManager = null
        car = null
    }

    private fun handle(propertyId: Int, value: Any?) {
        when (propertyId) {
            VehiclePropertyIds.GEAR_SELECTION -> onGear(gearOf((value as? Int) ?: -1))
            VehiclePropertyIds.PARKING_BRAKE_ON -> (value as? Boolean)?.let(onParkingBrake)
        }
    }

    private fun gearOf(raw: Int): Gear = when (raw) {
        GEAR_PARK -> Gear.PARK
        GEAR_REVERSE -> Gear.REVERSE
        GEAR_NEUTRAL -> Gear.NEUTRAL
        GEAR_DRIVE -> Gear.DRIVE
        // 수동 기어 단수(1단~9단)는 주행으로 본다
        in GEAR_FIRST..GEAR_NINTH -> Gear.DRIVE
        else -> Gear.UNKNOWN
    }

    private companion object {
        const val TAG = "Chaon/Aaos"
        // android.car.VehicleGear 값
        const val GEAR_NEUTRAL = 0x0001
        const val GEAR_REVERSE = 0x0002
        const val GEAR_PARK = 0x0004
        const val GEAR_DRIVE = 0x0008
        const val GEAR_FIRST = 0x0010
        const val GEAR_NINTH = 0x1000
    }
}

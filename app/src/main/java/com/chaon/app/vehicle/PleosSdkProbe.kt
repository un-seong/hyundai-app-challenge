package com.chaon.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.EvBattery
import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions

/** SDK 진단 화면에 보여줄 한 줄 */
data class SdkDiagEntry(
    val domain: String,
    val method: String,
    val result: String,
    val ok: Boolean,
    /** 실패가 아니라 "이 차량/에뮬레이터에 해당 속성이 없음" */
    val unsupported: Boolean = false,
    /** 성공/실패가 아닌 참고 정보 (예: SOH 메서드 존재 여부) */
    val info: Boolean = false,
)

/**
 * Pleos Vehicle SDK 연동.
 *
 * - EvBattery: SOC(%) 조회 → 홈 화면 차량 상태 / 온밤 에너지 예측의 입력
 * - Brake: 주차 브레이크 조회 → 정차 판단 보조 근거
 * - 진단: Brake·EvBattery 의 "인자 없는 get 메서드"를 모두 호출해서 결과를 보여준다
 *   (SOH 같은 값이 실제로 제공되는지 정의서 12장 검증용)
 *
 * 정확한 메서드 이름이 SDK 버전마다 달라질 수 있어서, 공식 샘플 앱의 API 카탈로그와 같은
 * 방식(kotlin-reflect)으로 메서드를 찾아 호출한다. 모든 호출은 비동기(onComplete/onFailed)다.
 */
class PleosSdkProbe(context: Context) {

    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null

    val isConnected: Boolean get() = vehicle != null

    fun connect(): Result<Unit> = runCatching {
        if (vehicle != null) return@runCatching
        val v = Vehicle(appContext)
        v.initialize()
        vehicle = v
    }.onFailure { Log.w(TAG, "Vehicle SDK initialize failed", it) }

    fun release() {
        runCatching { vehicle?.release() }
        vehicle = null
    }

    /** EV 배터리 SOC(%) */
    fun readStateOfCharge(onResult: (Float?) -> Unit) {
        val ev = evBattery() ?: return onResult(null)
        val getter = noAreaGetters(EvBattery::class)
            .firstOrNull { it.name.contains("StateOfCharge", ignoreCase = true) }
            ?: return onResult(null)
        invokeGetter(getter, ev,
            onValue = { onResult((it as? Number)?.toFloat()) },
            onError = { onResult(null) })
    }

    /**
     * 배터리 용량(kWh). 이름에 Capacity 가 들어간 get 메서드가 있으면 읽는다.
     * 단위가 문서로 확인되지 않아 1000 이상이면 Wh 로 보고 kWh 로 바꾼다. 범위를 벗어나면 버린다.
     */
    fun readCapacityKWh(onResult: (Float?) -> Unit) {
        val ev = evBattery() ?: return onResult(null)
        val getter = noAreaGetters(EvBattery::class)
            .firstOrNull { it.name.contains("Capacity", ignoreCase = true) }
            ?: return onResult(null)
        invokeGetter(getter, ev,
            onValue = { v ->
                val raw = (v as? Number)?.toFloat()
                val kwh = raw?.let { if (it >= 1000f) it / 1000f else it }
                onResult(kwh?.takeIf { it in 10f..200f })
            },
            onError = { onResult(null) })
    }

    /** 주차 브레이크 체결 여부 */
    fun readParkingBrake(onResult: (Boolean?) -> Unit) {
        val brake = brake() ?: return onResult(null)
        val getter = noAreaGetters(Brake::class)
            .firstOrNull { it.name.contains("Parking", ignoreCase = true) }
            ?: return onResult(null)
        invokeGetter(getter, brake,
            onValue = { onResult(asEngaged(it)) },
            onError = { onResult(null) })
    }

    /** SDK 진단: 도메인별 get 메서드를 모두 호출해 결과를 하나씩 전달한다 */
    fun diagnose(onEntry: (SdkDiagEntry) -> Unit) {
        val targets: List<Triple<String, KClass<*>, Any?>> = listOf(
            Triple("Brake", Brake::class, brake()),
            Triple("EvBattery", EvBattery::class, evBattery()),
        )
        targets.forEach { (domain, kClass, instance) ->
            if (instance == null) {
                onEntry(SdkDiagEntry(domain, "-", "SDK 연결 안 됨", ok = false))
                return@forEach
            }
            // SOH(배터리 건강도) 관련 메서드가 있는지 이름으로 먼저 확인
            if (domain == "EvBattery") {
                val sohLike = kClass.declaredMemberFunctions
                    .map { it.name }
                    .filter { n -> SOH_KEYWORDS.any { n.contains(it, ignoreCase = true) } }
                onEntry(
                    SdkDiagEntry(
                        domain, "SOH 관련 메서드",
                        if (sohLike.isEmpty()) "없음 → 온밤은 SOH를 추정값으로 계산 (정의서 12장대로)" else sohLike.joinToString(),
                        ok = sohLike.isNotEmpty(),
                        info = true,
                    )
                )
            }
            val getters = noAreaGetters(kClass)
            if (getters.isEmpty()) {
                onEntry(SdkDiagEntry(domain, "-", "호출 가능한 get 메서드 없음", ok = false))
            }
            getters.forEach { f ->
                invokeGetter(f, instance,
                    onValue = { onEntry(SdkDiagEntry(domain, f.name, format(it), ok = true)) },
                    onError = { onEntry(SdkDiagEntry(domain, f.name, explain(it), ok = false, unsupported = isUnsupported(it))) })
            }
        }
    }

    private fun brake(): Any? = runCatching { vehicle?.getBrake() }.getOrNull()
    private fun evBattery(): Any? = runCatching { vehicle?.getEvBattery() }.getOrNull()

    /** 지역(area) 인자 없이 콜백만 받는 get 메서드 */
    private fun noAreaGetters(kClass: KClass<*>): List<KFunction<*>> =
        runCatching {
            kClass.declaredMemberFunctions
                .filter { f ->
                    f.name.startsWith("get") &&
                        f.annotations.none { it is Deprecated } &&
                        f.parameters.filter { it.kind == KParameter.Kind.VALUE && !it.isOptional }
                            .let { ps -> ps.isNotEmpty() && ps.all { isCallbackType(it) } }
                }
                .sortedBy { it.name }
        }.getOrElse {
            Log.w(TAG, "reflection failed for ${kClass.simpleName}", it)
            emptyList()
        }

    private fun invokeGetter(
        function: KFunction<*>,
        instance: Any,
        onValue: (Any?) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        var delivered = false
        val deliverValue: (Any?) -> Unit = { if (!delivered) { delivered = true; onValue(it) } }
        val deliverError: (Throwable) -> Unit = { if (!delivered) { delivered = true; onError(it) } }

        try {
            val args = mutableMapOf<KParameter, Any?>()
            function.parameters.forEach { p ->
                val isFailure = p.name?.let { n -> n.contains("fail", true) || n.contains("error", true) } == true
                when {
                    p.kind == KParameter.Kind.INSTANCE -> args[p] = instance
                    p.kind != KParameter.Kind.VALUE -> Unit
                    p.isOptional -> Unit
                    isFailure -> {
                        val cb: (Any?) -> Unit = { e -> deliverError(e as? Throwable ?: RuntimeException(e.toString())) }
                        args[p] = cb
                    }
                    p.type.classifier == Function1::class -> {
                        val cb: (Any?) -> Unit = { v -> deliverValue(v) }
                        args[p] = cb
                    }
                    p.type.classifier == Function2::class -> {
                        // (area, value) 형태: 두 번째 값을 결과로 본다
                        val cb: (Any?, Any?) -> Unit = { _, v -> deliverValue(v) }
                        args[p] = cb
                    }
                }
            }
            function.callBy(args)
        } catch (e: InvocationTargetException) {
            deliverError(e.targetException)
        } catch (t: Throwable) {
            deliverError(t)
        }
    }

    private fun isCallbackType(p: KParameter): Boolean {
        val c = p.type.classifier
        return c == Function1::class || c == Function2::class
    }

    private fun isUnsupported(t: Throwable): Boolean {
        val m = (t.message ?: "") + t.javaClass.simpleName
        return UNSUPPORTED_HINTS.any { m.contains(it, ignoreCase = true) }
    }

    private fun explain(t: Throwable): String =
        if (isUnsupported(t)) "이 기기에서 지원하지 않는 속성 (에뮬레이터에 값 없음)"
        else "실패: ${t.message ?: t.javaClass.simpleName}"

    private fun asEngaged(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        null -> null
        else -> value.toString().uppercase().let { s ->
            // "UNLOCK" 안에 "LOCK" 이 들어 있으므로 해제 단어를 먼저 본다
            when {
                s == "OFF" || RELEASED_WORDS.any { s.contains(it) } -> false
                s == "ON" || ENGAGED_WORDS.any { s.contains(it) } -> true
                else -> null
            }
        }
    }

    private fun format(value: Any?): String = when (value) {
        null -> "null"
        is Float, is Double -> "%.2f".format((value as Number).toDouble())
        is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { format(it) }
        else -> value.toString()
    }

    private companion object {
        const val TAG = "Chaon/Pleos"
        val SOH_KEYWORDS = listOf("Health", "Soh", "Degrad", "Aging")
        val UNSUPPORTED_HINTS = listOf("not supported", "NOT_SUPPORTED", "not in carPropertyConfig")
        val ENGAGED_WORDS = listOf("ENGAGED", "APPLIED", "LOCKED")
        val RELEASED_WORDS = listOf("RELEASED", "UNLOCK", "DISENGAGED")
    }
}

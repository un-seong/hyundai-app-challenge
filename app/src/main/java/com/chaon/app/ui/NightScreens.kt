package com.chaon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaon.app.audio.RestSound
import com.chaon.app.session.HvacLevel
import com.chaon.app.session.NightEnergyModel
import com.chaon.app.session.NightForecast
import com.chaon.app.session.NightVerdict
import com.chaon.app.session.RestMode
import com.chaon.app.session.SessionPhase
import com.chaon.app.vehicle.DataSource
import java.time.LocalTime
import kotlin.math.roundToInt

private val nightSlider: SliderColors
    @Composable get() = SliderDefaults.colors(
    thumbColor = ChaonColors.Amber,
    activeTrackColor = ChaonColors.Amber,
    inactiveTrackColor = ChaonColors.Divider,
)

fun formatHours(hours: Double): String {
    val totalMin = (hours * 60).roundToInt().coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0 -> "${m}분"
        m == 0 -> "${h}시간"
        else -> "${h}시간 ${m}분"
    }
}

private fun verdictColors(v: NightVerdict): Pair<Color, Color> = when (v) {
    NightVerdict.OK -> ChaonColors.Green to ChaonColors.GreenBg
    NightVerdict.TIGHT -> ChaonColors.Yellow to ChaonColors.YellowBg
    NightVerdict.SHORT -> ChaonColors.Red to ChaonColors.RedBg
}

// ═════════════════════ 온밤 설정 ═════════════════════

@Composable
fun NightSetup(state: UiState, vm: RestViewModel, modifier: Modifier = Modifier) {
    val p = state.profile
    val neededMin = RestViewModel.minutesUntil(p.wakeHour, p.wakeMinute)
    val neededHours = neededMin / 60.0
    val forecast = vm.nightForecast(state, neededHours)
    val inputs = vm.nightInputs(state, neededHours)
    val suggestion = if (forecast.verdict == NightVerdict.OK) null else NightEnergyModel.suggestLowerHvac(inputs)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            // 왼쪽: 사용자가 정하는 것
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ChaonCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("기상 시각", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text("지금부터 ${formatHours(neededHours)}", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallButton("−30분", onClick = { vm.shiftWake(-30) })
                        Text(
                            "%02d:%02d".format(p.wakeHour, p.wakeMinute),
                            style = MaterialTheme.typography.headlineLarge,
                            color = ChaonColors.Text,
                        )
                        SmallButton("+30분", onClick = { vm.shiftWake(30) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(6 to 0, 6 to 30, 7 to 0, 7 to 30, 8 to 0).forEach { (h, m) ->
                            ChoiceChip(
                                "%02d:%02d".format(h, m),
                                selected = p.wakeHour == h && p.wakeMinute == m,
                                onClick = { vm.updateProfile { it.copy(wakeHour = h, wakeMinute = m) } },
                            )
                        }
                    }
                }

                ChaonCard(Modifier.fillMaxWidth()) {
                    Text("밤새 공조 수준", style = MaterialTheme.typography.titleLarge)
                    Text("차량에서 직접 맞출 공조 세기를 골라 주세요. 차온은 공조를 조작하지 않고 계산에만 써요.", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HvacLevel.entries.forEach { lv ->
                            ChoiceChip(lv.label, selected = p.hvacLevel == lv, onClick = { vm.updateProfile { it.copy(hvacLevel = lv) } })
                        }
                    }
                    Text("${p.hvacLevel.label}: ${p.hvacLevel.desc}", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                }

                ChaonCard(Modifier.fillMaxWidth()) {
                    Text("아침에 남길 배터리  ${p.reservePercent}%", style = MaterialTheme.typography.titleLarge)
                    Slider(
                        value = p.reservePercent.toFloat(),
                        onValueChange = { v -> vm.updateProfile { it.copy(reservePercent = ((v / 5).roundToInt() * 5).coerceIn(10, 50)) } },
                        valueRange = 10f..50f,
                        steps = 7,
                        colors = nightSlider,
                    )
                    Text("충전소까지 갈 만큼은 꼭 남겨요. 밤사이 이 값 아래로 내려가면 깨워드려요.", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                }

                ChaonCard(Modifier.fillMaxWidth()) {
                    AlarmSoundPicker(state, vm)
                }

                ChaonCard(Modifier.fillMaxWidth()) {
                    Text("잠들 때 사운드 · ${RestViewModel.NIGHT_SOUND_OFF_MIN}분 뒤 자동 꺼짐", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RestSound.entries.forEach { snd ->
                            ChoiceChip(snd.label, selected = p.nightSound == snd, onClick = {
                                vm.updateProfile { it.copy(nightSound = snd) }
                                vm.previewSound(snd)
                            })
                        }
                    }
                }
            }

            // 오른쪽: 예측 결과와 계산에 쓴 값
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ForecastCard(forecast, neededHours, suggestion)

                ChaonCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("계산에 쓴 값", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Tag("가정값 포함 · 실차 측정으로 보정 예정", ChaonColors.Yellow, ChaonColors.YellowBg)
                    }

                    // 현재 배터리
                    val soc = state.vehicle.socPercent
                    if (soc != null) {
                        ValueRow("현재 배터리", "${soc.roundToInt()}%") { SourceTag(state.vehicle.source) }
                    } else {
                        ValueRow("현재 배터리", "${p.manualSocPercent}%") { Tag("차량 값 없음 · 직접 입력") }
                        Slider(
                            value = p.manualSocPercent.toFloat(),
                            onValueChange = { v -> vm.updateProfile { it.copy(manualSocPercent = v.roundToInt().coerceIn(5, 100)) } },
                            valueRange = 5f..100f,
                            colors = nightSlider,
                        )
                    }

                    // 용량
                    val cap = state.vehicle.batteryCapacityKWh
                    if (cap != null) {
                        ValueRow("배터리 용량", "%.1f kWh".format(cap)) { Tag("차량 데이터", ChaonColors.Cyan, ChaonColors.CyanBg) }
                    } else {
                        ValueRow("배터리 용량", "%.1f kWh".format(p.capacityKWh)) { Tag("제원 기준 직접 선택") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(58f, 63f, 77.4f, 84f).forEach { c ->
                                ChoiceChip("%.1f".format(c), selected = p.capacityKWh == c, onClick = { vm.updateProfile { it.copy(capacityKWh = c) } })
                            }
                        }
                    }

                    // SOH
                    ValueRow("배터리 건강도(SOH)", "${p.sohPercent}%") { Tag("추정 · SDK 미제공", ChaonColors.Yellow, ChaonColors.YellowBg) }
                    Slider(
                        value = p.sohPercent.toFloat(),
                        onValueChange = { v -> vm.updateProfile { it.copy(sohPercent = v.roundToInt().coerceIn(70, 100)) } },
                        valueRange = 70f..100f,
                        colors = nightSlider,
                    )

                    ValueRow(
                        "평균 소비전력",
                        "%.2f kW".format(forecast.loadKW),
                    ) { Tag("기본 ${NightEnergyModel.BASE_LOAD_KW}kW + 공조 ${p.hvacLevel.kw}kW") }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1.4f)) { NightSafetyLine(state) }
            LineButton("취소", onClick = { vm.goTo(Screen.HOME) }, modifier = Modifier.weight(0.6f), minHeight = 88)
            AmberButton(
                if (forecast.verdict == NightVerdict.OK) "온밤 시작" else "알고 시작하기",
                onClick = { vm.startSession(RestMode.ONBAM) },
                enabled = vm.canStart(state),
                modifier = Modifier.weight(1f),
                minHeight = 88,
            )
        }
    }
}

@Composable
private fun NightSafetyLine(state: UiState) {
    Text(
        when {
            state.vehicle.safety == com.chaon.app.vehicle.StopSafety.PARKED -> "정차 확인됨 · 주행이 시작되면 자동 종료"
            state.vehicle.safety == com.chaon.app.vehicle.StopSafety.MOVING -> "주행 중에는 시작할 수 없어요"
            state.manualStopConfirmed -> "직접 정차 확인함 · 주행이 시작되면 자동 종료"
            else -> "정차 상태 확인 불가 · 홈 화면에서 정차 확인을 체크해 주세요"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = ChaonColors.Muted,
    )
}

@Composable
private fun ValueRow(label: String, value: String, tag: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Muted, modifier = Modifier.width(190.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        tag()
    }
}

@Composable
private fun ForecastCard(f: NightForecast, neededHours: Double, suggestion: HvacLevel?) {
    val (fg, bg) = verdictColors(f.verdict)
    ChaonCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("기상 시각까지", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Tag(f.verdict.label, fg, bg)
        }
        Text(
            "약 ${formatHours(f.hoursExpected)} 버틸 수 있어요",
            style = MaterialTheme.typography.headlineMedium,
            color = fg,
        )
        Text(
            "보수 ${formatHours(f.hoursLow)} ~ 낙관 ${formatHours(f.hoursHigh)} · 필요 ${formatHours(neededHours)}",
            style = MaterialTheme.typography.bodyLarge,
            color = ChaonColors.Muted,
        )
        HorizontalLine()
        Text(
            "기상 시 배터리 예상 ${f.socAtWakeExpected.roundToInt()}%  (보수 ${f.socAtWakeLow.roundToInt()}%)",
            style = MaterialTheme.typography.titleMedium,
        )
        when {
            f.verdict == NightVerdict.OK -> Unit
            suggestion != null -> Text(
                "공조를 '${suggestion.label}'(으)로 낮추면 보수적으로 봐도 충분해요.",
                style = MaterialTheme.typography.bodyLarge,
                color = ChaonColors.Yellow,
            )
            else -> Text(
                "공조를 낮춰도 빠듯해요. 충전 가능한 곳으로 이동하거나 기상 시각을 앞당겨 주세요.",
                style = MaterialTheme.typography.bodyLarge,
                color = ChaonColors.Red,
            )
        }
        Text(
            "판정은 보수적인 값(소비전력 +25%) 기준이에요. 실제 차량·날씨에 따라 달라질 수 있어요.",
            style = MaterialTheme.typography.labelSmall,
            color = ChaonColors.Muted,
        )
    }
}

// ═════════════════════ 온밤 진행 (어두운 화면) ═════════════════════

@Composable
fun NightSessionContent(state: UiState, vm: RestViewModel) {
    val s = state.session
    val p = state.profile
    val paused = s.phase == SessionPhase.PAUSED
    val remainingHours = state.remainingMs / 3_600_000.0
    val forecast = vm.nightForecast(state, remainingHours)
    val (fg, bg) = verdictColors(forecast.verdict)
    val now = LocalTime.now()
    val dim = ChaonColors.Muted

    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("%02d:%02d".format(now.hour, now.minute), style = MaterialTheme.typography.displayLarge, color = dim)
            Text(
                "기상 %02d:%02d까지 %s".format(p.wakeHour, p.wakeMinute, formatHours(remainingHours)),
                style = MaterialTheme.typography.titleLarge,
                color = dim,
            )
            Text(
                when {
                    paused -> "잠시 멈춤"
                    state.nightSoundOff -> "사운드 꺼짐 · 화면을 어둡게 유지해요"
                    else -> "${RestViewModel.soundFor(RestMode.ONBAM, p).label} · 잠들면 자동으로 꺼져요"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = dim.copy(alpha = 0.7f),
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ChaonCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("배터리", style = MaterialTheme.typography.titleLarge, color = dim, modifier = Modifier.weight(1f))
                    Tag(forecast.verdict.label, fg, bg)
                }
                val socNow = state.vehicle.socPercent ?: p.manualSocPercent.toFloat()
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("${socNow.roundToInt()}%", style = MaterialTheme.typography.headlineLarge, color = ChaonColors.Text)
                        Text(
                            "지금" + (s.startSocPercent?.let { " (시작 ${it.roundToInt()}%)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = dim,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${forecast.socAtWakeExpected.roundToInt()}%", style = MaterialTheme.typography.headlineLarge, color = fg)
                        Text("기상 시 예상 (보수 ${forecast.socAtWakeLow.roundToInt()}%)", style = MaterialTheme.typography.bodyMedium, color = dim)
                    }
                }
                Text(
                    "아침에 남길 배터리 ${p.reservePercent}% · 공조 ${p.hvacLevel.label} 가정 · " +
                        if (state.vehicle.source == DataSource.VEHICLE) "차량 데이터" else "데모/입력값",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
                if (state.nightAlert == NightAlert.WARN) {
                    Text(
                        "배터리가 빠르게 줄고 있어요. 공조를 한 단계 낮춰 주세요. 기준 아래로 내려가면 깨워드려요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChaonColors.Yellow,
                    )
                }
            }
            if (state.demoMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallButton("배터리 −10% (데모)", onClick = { vm.demoChangeSoc(-10f) })
                    SmallButton("배터리 +10% (데모)", onClick = { vm.demoChangeSoc(10f) })
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LineButton(if (paused) "다시 시작" else "일시정지", onClick = vm::pauseOrResume, modifier = Modifier.weight(1f), minHeight = 88, color = dim)
                LineButton("알람 끄고 종료", onClick = vm::stopByUser, modifier = Modifier.weight(1f), color = ChaonColors.Red, minHeight = 88)
            }
        }
    }
}

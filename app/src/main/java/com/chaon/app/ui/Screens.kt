package com.chaon.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chaon.app.audio.AlarmSound
import com.chaon.app.audio.RestSound
import com.chaon.app.data.RestProfile
import com.chaon.app.session.BreathGuide
import com.chaon.app.session.BreathPattern
import com.chaon.app.session.EndReason
import com.chaon.app.session.RestMode
import com.chaon.app.session.RestSessionEngine
import com.chaon.app.session.SessionPhase
import com.chaon.app.vehicle.DataSource
import com.chaon.app.vehicle.StopSafety
import kotlin.math.roundToInt

private val ScreenPadding = Modifier.padding(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 32.dp)
private val sliderColors: SliderColors
    @Composable get() = SliderDefaults.colors(
    thumbColor = ChaonColors.Amber,
    activeTrackColor = ChaonColors.Amber,
    inactiveTrackColor = ChaonColors.Divider,
)

// ═════════════════════ 홈 ═════════════════════

@Composable
fun HomeScreen(state: UiState, vm: RestViewModel) {
    val p = state.profile
    Row(Modifier.fillMaxSize().then(ScreenPadding), horizontalArrangement = Arrangement.spacedBy(32.dp)) {

        // 왼쪽: 바로 시작
        Column(Modifier.weight(1.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("지금, 차 안에서 잠깐 쉬어요", style = MaterialTheme.typography.displayMedium)
            Text(
                "시간만 고르면 정차 확인 · 사운드 · 종료 예고 · 복귀까지 차온이 챙겨요.",
                style = MaterialTheme.typography.bodyLarge,
                color = ChaonColors.Muted,
            )
            SafetyNotice(state, vm)

            val enabled = vm.canStart(state)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(10, 20, 30).forEach { m ->
                    AmberButton(
                        text = "${m}분 온쉼",
                        sub = if (m == p.durationMin) "최근 설정" else null,
                        onClick = { vm.quickStart(m) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            LineButton("직접 설정하고 시작", onClick = { vm.openSetup(RestMode.ONSWIM) }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.weight(1f))
            HorizontalLine()
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ChaonColors.Text)) { append("최근 설정: ") }
                    append(
                        "${p.durationMin}분 · ${p.sound.label} · 음량 ${(p.volume * 100).roundToInt()}% · " +
                            if (p.preAlertMin > 0) "종료 ${p.preAlertMin}분 전 예고" else "예고 없음"
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ChaonColors.Muted,
            )
        }

        // 오른쪽: 오늘의 쉼 + 쉼 모드
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            ChaonCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("오늘의 쉼", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("이 기기에만 저장", style = MaterialTheme.typography.labelSmall, color = ChaonColors.Muted)
                }
                Row {
                    Stat("번 쉬었어요", "${state.today.sessions}")
                    Stat("분 쉬었어요", "${state.today.restedMin}")
                }
            }
            ChaonCard(Modifier.fillMaxWidth().weight(1f)) {
                Text("다른 쉼 모드", style = MaterialTheme.typography.titleLarge)
                Text("하나의 세션 엔진 위에서 상황별로 쉬어요.", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                Spacer(Modifier.height(4.dp))
                ModeRow("온잠", "짧은 낮잠 · 천천히 깨우기", ready = true, onClick = { vm.openSetup(RestMode.ONJAM) })
                ModeRow("온끼", "주차 후 식사 · 환기와 정리 알림", ready = true, onClick = { vm.openSetup(RestMode.ONKKI) })
                ModeRow("온정", "업무 후 호흡 가이드 · 하루 내려놓기", ready = true, onClick = { vm.openSetup(RestMode.ONJEONG) })
                ModeRow("온밤", "차박 취침 · 배터리 기반 유지시간 예측", ready = true, onClick = { vm.openSetup(RestMode.ONBAM) })
                Spacer(Modifier.weight(1f))
                HorizontalLine()
                // 실제 연결 상태만 정직하게 표시 (T6)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.vehicle.statusNote.ifBlank { "차량 연결 상태 확인 중" },
                        style = MaterialTheme.typography.labelSmall,
                        color = ChaonColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Dot(if (state.vehicle.source == DataSource.VEHICLE) ChaonColors.Green else ChaonColors.Yellow, size = 8)
                }
            }
        }
    }
}

@Composable
private fun ModeRow(name: String, desc: String, ready: Boolean, onClick: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = ready, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = ChaonColors.Amber)
        Spacer(Modifier.width(12.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Text, modifier = Modifier.weight(1f))
        if (ready) Tag("시작하기 ›", ChaonColors.OnAmber, ChaonColors.Amber) else Tag("준비 중")
    }
}

/** 정차 조건 안내 (FR-04) */
@Composable
private fun SafetyNotice(state: UiState, vm: RestViewModel) {
    when (state.vehicle.safety) {
        StopSafety.PARKED -> NoticeBox(NoticeKind.OK) {
            Checkbox(
                checked = true, onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = ChaonColors.Green, checkmarkColor = ChaonColors.Bg),
            )
            Text("정차가 확인됐어요. 바로 시작할 수 있어요.", style = MaterialTheme.typography.titleMedium, color = ChaonColors.Green)
        }
        StopSafety.MOVING -> NoticeBox(NoticeKind.DANGER) {
            Dot(ChaonColors.Red, size = 14)
            Text("주행 중이에요. 안전한 곳에 정차(P)하면 시작할 수 있어요.", style = MaterialTheme.typography.titleMedium, color = ChaonColors.Red)
        }
        StopSafety.UNKNOWN -> NoticeBox(
            NoticeKind.WARN,
            Modifier.clickable { vm.setManualStopConfirmed(!state.manualStopConfirmed) },
        ) {
            Checkbox(
                checked = state.manualStopConfirmed,
                onCheckedChange = { vm.setManualStopConfirmed(it) },
                colors = CheckboxDefaults.colors(checkedColor = ChaonColors.Yellow, uncheckedColor = ChaonColors.Yellow, checkmarkColor = ChaonColors.Bg),
            )
            Text(
                "정차 상태를 확인할 수 없어요. 안전한 곳에 정차했다면 체크해 주세요.",
                style = MaterialTheme.typography.titleMedium,
                color = ChaonColors.Yellow,
            )
        }
    }
}

// ═════════════════════ 모드 설정 ═════════════════════

/** 모드별 길이 범위와 프리셋 */
private data class DurationSpec(val min: Int, val max: Int, val step: Int, val presets: List<Int>)

private fun durationSpec(mode: RestMode) = when (mode) {
    RestMode.ONSWIM -> DurationSpec(5, 60, 5, listOf(10, 15, 20, 30, 45))
    RestMode.ONJAM -> DurationSpec(5, 40, 5, listOf(10, 15, 20, 25, 30))
    RestMode.ONKKI -> DurationSpec(10, 60, 5, listOf(15, 20, 30, 40))
    RestMode.ONJEONG -> DurationSpec(3, 15, 1, listOf(3, 5, 10))
    RestMode.ONBAM -> DurationSpec(30, 720, 30, emptyList()) // 온밤은 기상 시각으로 정함
}

private fun RestProfile.withMinutes(mode: RestMode, m: Int): RestProfile = when (mode) {
    RestMode.ONSWIM -> copy(durationMin = m)
    RestMode.ONJAM -> copy(napDurationMin = m)
    RestMode.ONKKI -> copy(mealDurationMin = m)
    RestMode.ONJEONG -> copy(breathDurationMin = m)
    RestMode.ONBAM -> this
}

private fun RestProfile.withSound(mode: RestMode, s: RestSound): RestProfile = when (mode) {
    RestMode.ONSWIM -> copy(sound = s)
    RestMode.ONJAM -> copy(napSound = s)
    RestMode.ONKKI -> copy(mealSound = s)
    RestMode.ONJEONG -> copy(breathSound = s)
    RestMode.ONBAM -> copy(nightSound = s)
}

@Composable
fun SetupScreen(state: UiState, vm: RestViewModel) {
    val p = state.profile
    val mode = state.setupMode
    val minutes = RestViewModel.minutesFor(mode, p)
    val sound = RestViewModel.soundFor(mode, p)
    val spec = durationSpec(mode)
    val lengthTitle = when (mode) {
        RestMode.ONSWIM -> "휴식 길이"
        RestMode.ONJAM -> "낮잠 길이"
        RestMode.ONKKI -> "식사 시간"
        RestMode.ONJEONG -> "호흡 시간"
        RestMode.ONBAM -> "취침 시간"
    }

    Column(Modifier.fillMaxSize().then(ScreenPadding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RestMode.entries.forEach { m ->
                ChoiceChip("${m.label} · ${m.tagline}", selected = m == mode, onClick = { vm.setSetupMode(m) })
            }
        }

        if (mode == RestMode.ONBAM) {
            NightSetup(state, vm, Modifier.weight(1f))
            return@Column
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ChaonCard(Modifier.fillMaxWidth()) {
                    Text("$lengthTitle  ${minutes}분", style = MaterialTheme.typography.headlineMedium)
                    Slider(
                        value = minutes.toFloat().coerceIn(spec.min.toFloat(), spec.max.toFloat()),
                        onValueChange = { v ->
                            val m = ((v / spec.step).roundToInt() * spec.step).coerceIn(spec.min, spec.max)
                            vm.updateProfile { it.withMinutes(mode, m) }
                        },
                        valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        steps = (spec.max - spec.min) / spec.step - 1,
                        colors = sliderColors,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 데모 모드에서는 시연용 1분 버튼을 하나 더 보여준다
                        if (state.demoMode) {
                            ChoiceChip("1분 (시연)", selected = minutes == 1, onClick = { vm.updateProfile { it.withMinutes(mode, 1) } })
                        }
                        spec.presets.forEach { m ->
                            ChoiceChip("${m}분", selected = minutes == m, onClick = { vm.updateProfile { it.withMinutes(mode, m) } })
                        }
                    }
                    val hint = when (mode) {
                        RestMode.ONJAM -> "짧은 낮잠은 20분 안팎이 깨어나기 편해요. 너무 길면 일어난 뒤 더 몽롱할 수 있어요."
                        RestMode.ONJEONG -> "3~5분만으로도 충분해요. 운전 전 마음을 정리하는 시간이에요."
                        else -> null
                    }
                    if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                }

                ChaonCard(Modifier.fillMaxWidth()) {
                    when (mode) {
                        RestMode.ONSWIM -> {
                            Text("종료 예고", style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(0 to "끄기", 1 to "1분 전", 3 to "3분 전", 5 to "5분 전").forEach { (m, label) ->
                                    ChoiceChip(label, selected = p.preAlertMin == m, onClick = { vm.updateProfile { it.copy(preAlertMin = m) } })
                                }
                            }
                        }
                        RestMode.ONJAM -> {
                            Text("깨우는 방식", style = MaterialTheme.typography.titleLarge)
                            NapStep("1", "끝나기 2분 전", "소리를 조금씩 키우고 작은 차임으로 신호를 줘요. 말은 하지 않아요.")
                            NapStep("2", "시간이 되면", "작게 시작해 점점 커지는 알람. \"일어났어요\" 또는 \"5분만 더\"(최대 ${RestSessionEngine.MAX_SNOOZE}번).")
                            NapStep("3", "일어난 뒤", "환기 · 물 · 스트레칭 체크로 출발 전 정신을 깨워요.")
                            AlarmSoundPicker(state, vm)
                        }
                        RestMode.ONKKI -> {
                            Text("식사 중 알림", style = MaterialTheme.typography.titleLarge)
                            NapStep("1", "식사 중간", "창문을 조금 열어 한 번 환기하라고 알려드려요. (10분 이상일 때)")
                            NapStep("2", "끝나기 5분 전", "슬슬 정리할 시간이라고 알려드려요.")
                            NapStep("3", "끝나면", "쓰레기 · 환기 · 손과 핸들 정리 체크 후 출발해요.")
                        }
                        RestMode.ONJEONG -> {
                            Text("호흡 패턴", style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BreathPattern.entries.forEach { bp ->
                                    ChoiceChip(bp.label, selected = p.breathPattern == bp, onClick = { vm.updateProfile { it.copy(breathPattern = bp) } })
                                }
                            }
                            Text(p.breathPattern.desc, style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                        }
                        RestMode.ONBAM -> Unit
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("음성 안내", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!state.voiceAvailable) {
                            Tag("음성 엔진 없음 · 화면으로 안내", ChaonColors.Yellow, ChaonColors.YellowBg)
                        } else {
                            Tag(
                                state.voiceEngine,
                                if (state.voiceEngine.startsWith("Pleos")) ChaonColors.Cyan else ChaonColors.Muted,
                                if (state.voiceEngine.startsWith("Pleos")) ChaonColors.CyanBg else ChaonColors.Divider,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Switch(
                            checked = p.voiceOn,
                            onCheckedChange = { on -> vm.updateProfile { it.copy(voiceOn = on) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = ChaonColors.Amber, checkedThumbColor = ChaonColors.OnAmber),
                        )
                    }
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ChaonCard(Modifier.fillMaxWidth()) {
                    Text(if (mode == RestMode.ONKKI) "배경 사운드 (식사 중엔 무음 추천)" else "사운드", style = MaterialTheme.typography.titleLarge)
                    Text("직접 만든 사운드라 저작권 걱정이 없어요. 누르면 4초 미리듣기.", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RestSound.entries.forEach { snd ->
                            ChoiceChip(snd.label, selected = sound == snd, onClick = {
                                vm.updateProfile { it.withSound(mode, snd) }
                                vm.previewSound(snd)
                            })
                        }
                    }
                    Text("음량  ${(p.volume * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = p.volume,
                        onValueChange = { v -> vm.updateProfile { it.copy(volume = v) } },
                        colors = sliderColors,
                    )
                }
                Spacer(Modifier.weight(1f))
                SafetyNotice(state, vm)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LineButton("취소", onClick = { vm.goTo(Screen.HOME) }, modifier = Modifier.weight(1f), minHeight = 96)
                    AmberButton(
                        "${minutes}분 ${mode.label} 시작",
                        onClick = { vm.startSession(mode) },
                        enabled = vm.canStart(state),
                        modifier = Modifier.weight(2f),
                        minHeight = 96,
                    )
                }
            }
        }
    }
}

@Composable
private fun NapStep(no: String, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Dot(ChaonColors.Amber.copy(alpha = 0.18f), size = 34)
            Text(no, style = MaterialTheme.typography.labelMedium, color = ChaonColors.Amber)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
        }
    }
}

// ═════════════════════ 세션 진행 ═════════════════════

@Composable
fun SessionScreen(state: UiState, vm: RestViewModel) {
    val s = state.session
    val mode = s.mode
    val progress = if (s.totalMs > 0) state.remainingMs.toFloat() / s.totalMs else 0f
    val paused = s.phase == SessionPhase.PAUSED
    val sound = RestViewModel.soundFor(mode, state.profile)

    if (mode == RestMode.ONBAM) {
        Box(Modifier.fillMaxSize().then(ScreenPadding)) { NightSessionContent(state, vm) }
        return
    }

    Row(
        Modifier.fillMaxSize().then(ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (mode == RestMode.ONJEONG) {
                BreathingVisual(
                    pattern = state.profile.breathPattern,
                    elapsedMs = (s.totalMs - state.remainingMs).coerceAtLeast(0L),
                    remainingMs = state.remainingMs,
                    paused = paused,
                )
            } else {
                TimerRing(
                    progress = progress,
                    paused = paused,
                    label = if (paused) "잠시 멈춤" else "${mode.label} 중",
                    remainingMs = state.remainingMs,
                    caption = if (mode == RestMode.ONJAM) "깨울 때까지" else "남은 시간",
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                when (mode) {
                    RestMode.ONSWIM -> "눈을 감고 편하게 쉬세요"
                    RestMode.ONJAM -> "편하게 주무세요"
                    RestMode.ONKKI -> "맛있게 드세요"
                    RestMode.ONJEONG -> "숨에만 집중해요"
                    RestMode.ONBAM -> "편안한 밤 되세요"
                },
                style = MaterialTheme.typography.displayMedium,
            )
            ChaonCard(Modifier.fillMaxWidth()) {
                InfoLine("사운드", sound.label)
                when (mode) {
                    RestMode.ONSWIM -> InfoLine("종료 예고", if (s.preAlertMs > 0) "끝나기 ${s.preAlertMs / 60_000}분 전" else "없음")
                    RestMode.ONJAM -> InfoLine(
                        "깨우기",
                        if (s.snoozeCount > 0) "5분 더 자는 중 (${s.snoozeCount}/${RestSessionEngine.MAX_SNOOZE})" else "끝나기 2분 전부터 천천히",
                    )
                    RestMode.ONKKI -> {
                        InfoLine("환기 알림", if (!s.midAlert) "없음" else if (s.midAlertFired) "완료" else "식사 중간에")
                        InfoLine("정리 알림", if (s.preAlertMs > 0) "끝나기 ${s.preAlertMs / 60_000}분 전" else "없음")
                    }
                    RestMode.ONJEONG -> InfoLine("패턴", state.profile.breathPattern.label)
                    RestMode.ONBAM -> Unit
                }
                InfoLine("안전", "주행이 시작되면 자동으로 안전하게 종료")
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                AmberButton(if (paused) "다시 시작" else "일시정지", onClick = vm::pauseOrResume, modifier = Modifier.weight(1f), minHeight = 96)
                LineButton("지금 끝내기", onClick = vm::stopByUser, modifier = Modifier.weight(1f), color = ChaonColors.Red, minHeight = 96)
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, paused: Boolean, label: String, remainingMs: Long, caption: String) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(500.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ChaonColors.Divider,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = if (paused) ChaonColors.Muted else ChaonColors.Amber,
                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = if (paused) ChaonColors.Muted else ChaonColors.Amber)
            Text(formatClock(remainingMs), style = MaterialTheme.typography.displayLarge)
            Text(caption, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Muted)
        }
    }
}

/** 온정: 숨에 맞춰 커졌다 작아지는 원 */
@Composable
private fun BreathingVisual(pattern: BreathPattern, elapsedMs: Long, remainingMs: Long, paused: Boolean) {
    val pos = BreathGuide.positionAt(pattern, elapsedMs)
    val scale by animateFloatAsState(
        targetValue = if (paused) 0.6f else pos.step.scale,
        animationSpec = tween(durationMillis = pos.step.seconds * 1_000, easing = FastOutSlowInEasing),
        label = "breath",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(480.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val maxR = size.minDimension / 2
                drawCircle(color = ChaonColors.Divider, radius = maxR, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = ChaonColors.Amber.copy(alpha = 0.12f), radius = maxR * scale)
                drawCircle(color = ChaonColors.Amber.copy(alpha = 0.28f), radius = maxR * scale * 0.72f)
                drawCircle(color = ChaonColors.Amber, radius = maxR * scale, style = Stroke(width = 4.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (paused) "잠시 멈춤" else pos.step.label,
                    style = MaterialTheme.typography.headlineLarge,
                    color = ChaonColors.Text,
                )
                if (!paused) {
                    Text("${pos.secondsLeft}", style = MaterialTheme.typography.displayMedium, color = ChaonColors.Amber)
                }
            }
        }
        Text(
            "남은 시간 ${formatClock(remainingMs)} · ${pos.cycle + 1}번째 호흡",
            style = MaterialTheme.typography.bodyLarge,
            color = ChaonColors.Muted,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Muted, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Text)
    }
}

// ═════════════════════ 온잠 기상 ═════════════════════

@Composable
fun WakeScreen(state: UiState, vm: RestViewModel) {
    val pulse by rememberInfiniteTransition(label = "wake").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        Modifier.fillMaxSize().then(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Dot(ChaonColors.Amber.copy(alpha = 0.15f * pulse), size = 260)
            Dot(ChaonColors.Amber.copy(alpha = 0.35f * pulse), size = 170)
            Text("☀", style = MaterialTheme.typography.headlineLarge, color = ChaonColors.Amber)
        }
        val lowBattery = state.session.endReason == EndReason.LOW_BATTERY
        val (wakeTitle, wakeDesc) = when {
            lowBattery -> "배터리가 기준보다 낮아졌어요" to
                "아침에 남길 배터리(${state.profile.reservePercent}%)에 닿았어요. 공조를 끄거나 충전 가능한 곳으로 이동을 준비해 주세요."
            state.session.mode == RestMode.ONBAM -> "좋은 아침이에요" to "천천히 눈을 뜨고, 창문을 열어 바깥 공기를 들여 보세요."
            else -> "일어날 시간이에요" to "천천히 눈을 뜨고, 손발을 가볍게 움직여 보세요."
        }
        Spacer(Modifier.height(32.dp))
        Text(wakeTitle, style = MaterialTheme.typography.displayMedium, color = if (lowBattery) ChaonColors.Red else ChaonColors.Text)
        Spacer(Modifier.height(10.dp))
        Text(
            wakeDesc,
            style = MaterialTheme.typography.bodyLarge,
            color = ChaonColors.Muted,
        )
        if (state.sttAvailable) {
            Spacer(Modifier.height(18.dp))
            Text(
                if (state.listening) "🎤 듣고 있어요 — \"일어났어요\"" + (if (lowBattery) " 또는 \"확인\"" else " 또는 \"5분만 더\"")
                else "🎤 알람 사이사이에 말로 답해도 돼요",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.listening) ChaonColors.Amber else ChaonColors.Muted,
            )
        }
        Spacer(Modifier.height(40.dp))
        Row(Modifier.width(820.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AmberButton(if (lowBattery) "확인했어요" else "일어났어요", onClick = vm::wakeUp, modifier = Modifier.weight(1.4f))
            if (!lowBattery) {
                val left = RestSessionEngine.MAX_SNOOZE - state.session.snoozeCount
                LineButton(
                    if (left > 0) "5분만 더 (${left}번 남음)" else "5분만 더 (끝)",
                    onClick = vm::snooze,
                    enabled = vm.canSnooze(state),
                    modifier = Modifier.weight(1f),
                    minHeight = 104,
                )
            }
        }
    }
}

// ═════════════════════ 복귀 ═════════════════════

@Composable
fun ReturnScreen(state: UiState, vm: RestViewModel) {
    val s = state.session
    val mode = s.mode
    val completed = s.endReason == EndReason.COMPLETED
    val (title, desc) = when (s.endReason) {
        EndReason.COMPLETED -> when (mode) {
            RestMode.ONSWIM -> "잘 쉬었어요" to "기지개를 켜고, 물 한 모금 마신 뒤 천천히 출발 준비를 해요."
            RestMode.ONJAM -> "잘 잤어요" to "깬 직후엔 몽롱할 수 있어요. 아래 세 가지로 정신을 깨운 뒤 출발하세요."
            RestMode.ONKKI -> "식사 시간이 끝났어요" to "차 안을 정리하고 냄새를 빼면 다음 운전이 더 쾌적해요."
            RestMode.ONJEONG -> "수고했어요" to "오늘 하루를 차 안에 내려놓고, 가벼운 마음으로 출발해요."
            RestMode.ONBAM -> "좋은 아침이에요" to "밤사이 쓴 배터리를 확인하고, 출발 전에 차 안을 정돈해요."
        }
        EndReason.USER_STOPPED -> "${mode.label}${if (mode == RestMode.ONKKI) "를" else "을"} 마쳤어요" to "다음엔 원하는 만큼 더 쉬어 보세요."
        EndReason.DRIVING_DETECTED -> "주행이 감지되어 종료했어요" to "안전을 위해 화면과 사운드를 자동으로 끝냈어요."
        EndReason.LOW_BATTERY -> "배터리 때문에 온밤을 끝냈어요" to "아침에 남길 배터리 기준에 닿았어요. 충전 가능한 곳을 먼저 확인해 주세요."
        null -> "마쳤어요" to ""
    }
    Row(
        Modifier.fillMaxSize().then(ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(title, style = MaterialTheme.typography.displayMedium)
            Text(desc, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Muted)
            if (completed) {
                when (mode) {
                    RestMode.ONJAM -> Checklist("출발 전 정신 깨우기", listOf("창문을 열어 환기하기", "물 한 모금 마시기", "목·어깨 스트레칭"))
                    RestMode.ONKKI -> Checklist("출발 전 정리", listOf("쓰레기·포장 정리", "창문 열어 냄새 빼기", "손과 핸들 닦기"))
                    RestMode.ONJEONG -> MoodCheck()
                    RestMode.ONBAM -> Checklist("출발 전 확인", listOf("창문 열어 환기하기", "유리 김서림·성에 확인", "배터리와 충전 계획 확인"))
                    RestMode.ONSWIM -> Unit
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                AmberButton("같은 설정으로 다시 ${mode.label}", onClick = vm::restartSameMode, enabled = vm.canStart(state), modifier = Modifier.weight(1f), minHeight = 96)
                LineButton("홈으로", onClick = vm::backToHome, modifier = Modifier.weight(1f), minHeight = 96)
            }
        }
        ChaonCard(Modifier.weight(1f)) {
            Text("이번 ${mode.label}", style = MaterialTheme.typography.titleLarge)
            if (mode == RestMode.ONBAM) {
                val start = s.startSocPercent
                val nowSoc = state.vehicle.socPercent ?: state.profile.manualSocPercent.toFloat()
                Row {
                    Stat("잔 시간", formatHours(s.restedMs / 3_600_000.0))
                    Stat(
                        "배터리 사용" + (start?.let { " (${it.roundToInt()}→${nowSoc.roundToInt()}%)" } ?: ""),
                        start?.let { "${(it - nowSoc).coerceAtLeast(0f).roundToInt()}%p" } ?: "-",
                    )
                }
            } else {
                Row {
                    Stat("분 동안", "${s.restedMs / 60_000}")
                    Stat("분 목표", "${s.totalMs / 60_000}")
                }
            }
            HorizontalLine()
            Text("오늘의 쉼", style = MaterialTheme.typography.titleLarge)
            Row {
                Stat("번", "${state.today.sessions}")
                Stat("분", "${state.today.restedMin}")
            }
            Text(
                "기록은 이 기기에만 저장돼요 · 기록 지우기",
                style = MaterialTheme.typography.labelSmall,
                color = ChaonColors.Muted,
                modifier = Modifier.clickable(onClick = vm::clearHistory),
            )
        }
    }
}

/** 출발 전 체크 (사용자가 직접 체크, 저장하지 않음) */
@Composable
private fun Checklist(title: String, items: List<String>) {
    val checked = remember(title) { mutableStateListOf<Int>() }
    ChaonCard(Modifier.fillMaxWidth()) {
        Text("$title  ${checked.size}/${items.size}", style = MaterialTheme.typography.titleLarge)
        items.forEachIndexed { i, label ->
            val on = i in checked
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { if (on) checked.remove(i) else checked.add(i) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = on,
                    onCheckedChange = { if (it) checked.add(i) else checked.remove(i) },
                    colors = CheckboxDefaults.colors(checkedColor = ChaonColors.Green, checkmarkColor = ChaonColors.Bg),
                )
                Text(label, style = MaterialTheme.typography.titleMedium, color = if (on) ChaonColors.Green else ChaonColors.Text)
            }
        }
    }
}

/** 온정 후 마음 체크. 개인 감정 데이터라 저장하지 않고 화면에서만 반응한다. */
@Composable
private fun MoodCheck() {
    val options = listOf("한결 가벼워요", "비슷해요", "아직 무거워요")
    var picked by remember { mutableStateOf<Int?>(null) }
    ChaonCard(Modifier.fillMaxWidth()) {
        Text("지금 마음은 어때요?", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { i, label ->
                ChoiceChip(label, selected = picked == i, onClick = { picked = i })
            }
        }
        val reply = when (picked) {
            0 -> "좋아요. 그 느낌 그대로 천천히 출발해요."
            1 -> "괜찮아요. 몇 번 더 숨 고르고 가도 좋아요."
            2 -> "오늘 많이 힘들었나 봐요. 한 번 더 하거나 잠깐 더 쉬어도 괜찮아요."
            else -> null
        }
        if (reply != null) Text(reply, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Muted)
        Text("이 답은 저장하지 않아요.", style = MaterialTheme.typography.labelSmall, color = ChaonColors.Muted)
    }
}

// ═════════════════════ SDK 진단 ═════════════════════

@Composable
fun DiagScreen(state: UiState, vm: RestViewModel) {
    Column(Modifier.fillMaxSize().then(ScreenPadding), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SDK 진단", style = MaterialTheme.typography.displayMedium, color = ChaonColors.Text)
                Text(
                    "Pleos Vehicle SDK의 Brake · EvBattery 조회 메서드를 실제로 호출한 결과예요. " +
                        "기어·주차브레이크 실시간 값은 상단 상태(AAOS 표준 속성)에 표시돼요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChaonColors.Muted,
                )
                Text("연결 상태: ${state.vehicle.statusNote}", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
                Text(
                    "● 초록 = 값 읽기 성공 · ● 회색 = 이 기기에 해당 속성 없음(정상) · ● 빨강 = 오류 · ● 파랑 = 참고",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChaonColors.Muted,
                )
            }
            LineButton("다시 실행", onClick = vm::runDiagnostics)
            AmberButton("홈으로", onClick = { vm.goTo(Screen.HOME) }, minHeight = 72)
        }
        ChaonCard(Modifier.fillMaxWidth().weight(1f)) {
            if (state.diag.isEmpty()) {
                Text("호출 중…", style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Muted)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 정보 → 성공 → 미지원 → 실패 순으로 보여준다
                val sorted = state.diag.sortedBy { e ->
                    when {
                        e.info -> 0
                        e.ok -> 1
                        e.unsupported -> 2
                        else -> 3
                    }
                }
                items(sorted) { e ->
                    val dot = when {
                        e.info -> ChaonColors.Cyan
                        e.ok -> ChaonColors.Green
                        e.unsupported -> ChaonColors.Muted
                        else -> ChaonColors.Red
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(dot)
                        Text(e.domain, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Amber, modifier = Modifier.weight(0.6f))
                        Text(e.method, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.6f))
                        Text(e.result, style = MaterialTheme.typography.bodyLarge, color = ChaonColors.Muted, modifier = Modifier.weight(2f))
                    }
                }
            }
        }
    }
}

/** 기상 알람음 고르기 (온잠·온밤 공통) */
@Composable
fun AlarmSoundPicker(state: UiState, vm: RestViewModel) {
    Text("기상 알람음 · 누르면 미리듣기", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AlarmSound.entries.forEach { a ->
            ChoiceChip(a.label, selected = state.profile.alarmSound == a, onClick = {
                vm.updateProfile { it.copy(alarmSound = a) }
                vm.previewAlarm(a)
            })
        }
    }
    Text("어떤 소리든 작게 시작해서 점점 커져요.", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
}

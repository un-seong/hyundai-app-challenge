package com.chaon.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chaon.app.vehicle.DataSource
import com.chaon.app.vehicle.Gear
import com.chaon.app.vehicle.StopSafety
import com.chaon.app.vehicle.VehicleSnapshot

private val ButtonBorder = Color(0xFF2B3A48)

// ───────────── 버튼 ─────────────

/** 앰버 기본 버튼 (시안의 "10분 온쉼") */
@Composable
fun AmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sub: String? = null,
    minHeight: Int = 104,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ChaonColors.Amber,
            contentColor = ChaonColors.OnAmber,
            disabledContainerColor = ChaonColors.Amber.copy(alpha = 0.25f),
            disabledContentColor = ChaonColors.OnAmber.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.labelLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 테두리 버튼 (시안의 "직접 설정하고 시작") */
@Composable
fun LineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = ChaonColors.Text,
    minHeight: Int = 72,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, if (color == ChaonColors.Text) ButtonBorder else color.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 헤더의 작은 버튼 ("차량 모드", "SDK 진단") */
@Composable
fun SmallButton(text: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) ChaonColors.Amber else Color.Transparent)
            .border(1.dp, if (highlighted) ChaonColors.Amber else ButtonBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) ChaonColors.OnAmber else ChaonColors.Text,
        )
    }
}

/** "차량 | 데모" 두 칸 전환. 지금 쓰는 쪽이 채워져 보인다. */
@Composable
fun DataModeToggle(demoMode: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, ButtonBorder, RoundedCornerShape(10.dp)),
    ) {
        listOf(false to "차량", true to "데모").forEach { (isDemo, label) ->
            val selected = demoMode == isDemo
            Box(
                Modifier
                    .background(
                        when {
                            !selected -> Color.Transparent
                            isDemo -> ChaonColors.Yellow
                            else -> ChaonColors.Cyan
                        }
                    )
                    .clickable(enabled = !selected, onClick = onToggle)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) ChaonColors.Bg else ChaonColors.Muted,
                )
            }
        }
    }
}

/** 선택 칩 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ChaonColors.Amber else Color.Transparent)
            .border(1.5.dp, if (selected) ChaonColors.Amber else ButtonBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) ChaonColors.OnAmber else ChaonColors.Text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// ───────────── 카드 · 표시 ─────────────

@Composable
fun ChaonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = ChaonColors.Card,
        border = BorderStroke(1.dp, ChaonColors.CardBorder),
    ) {
        Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

enum class NoticeKind { OK, WARN, DANGER }

/** 색이 들어간 안내 상자 (시안의 초록 "정차가 확인됐어요") */
@Composable
fun NoticeBox(
    kind: NoticeKind,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val (bg, border) = when (kind) {
        NoticeKind.OK -> ChaonColors.GreenBg to ChaonColors.GreenBorder
        NoticeKind.WARN -> ChaonColors.YellowBg to ChaonColors.YellowBorder
        NoticeKind.DANGER -> ChaonColors.RedBg to ChaonColors.RedBorder
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** 작은 꼬리표 ("준비 중", "차량 데이터") */
@Composable
fun Tag(text: String, color: Color = ChaonColors.Muted, bg: Color = ChaonColors.Divider) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

@Composable
fun SourceTag(source: DataSource) = when (source) {
    DataSource.VEHICLE -> Tag("차량 데이터", ChaonColors.Cyan, ChaonColors.CyanBg)
    DataSource.DEMO -> Tag("데모 데이터", ChaonColors.Yellow, ChaonColors.YellowBg)
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun HorizontalLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ChaonColors.Divider))
}

@Composable
fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = ChaonColors.Amber)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
    }
}

// ───────────── 헤더 ─────────────

/** 시안 상단: 차온 로고 · 차량 상태 알약 · 모드/진단 버튼 */
@Composable
fun Header(
    vehicle: VehicleSnapshot,
    demoMode: Boolean,
    onToggleDemo: () -> Unit,
    onDiag: () -> Unit,
    onDemoGear: (Gear) -> Unit,
    sttAvailable: Boolean = false,
    listening: Boolean = false,
    onMic: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("차온", style = MaterialTheme.typography.headlineMedium, color = ChaonColors.Amber)
        Text("차 안의 온전한 쉼", style = MaterialTheme.typography.bodyMedium, color = ChaonColors.Muted)
        Spacer(Modifier.weight(1f))
        VehicleStatusPill(vehicle)
        if (demoMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Gear.PARK to "P", Gear.REVERSE to "R", Gear.NEUTRAL to "N", Gear.DRIVE to "D").forEach { (g, label) ->
                    SmallButton(label, onClick = { onDemoGear(g) }, highlighted = vehicle.gear == g)
                }
            }
        }
        if (sttAvailable) {
            SmallButton(if (listening) "듣는 중…" else "🎤 말로 하기", onClick = onMic, highlighted = listening)
        }
        DataModeToggle(demoMode = demoMode, onToggle = onToggleDemo)
        SmallButton("SDK 진단", onClick = onDiag)
    }
}

@Composable
fun VehicleStatusPill(v: VehicleSnapshot) {
    val (dotColor, safetyText) = when (v.safety) {
        StopSafety.PARKED -> ChaonColors.Green to "정차"
        StopSafety.MOVING -> ChaonColors.Red to "주행 중"
        StopSafety.UNKNOWN -> ChaonColors.Yellow to "정차 확인 불가"
    }
    val gear = when (v.gear) {
        Gear.PARK -> "P"
        Gear.REVERSE -> "R"
        Gear.NEUTRAL -> "N"
        Gear.DRIVE -> "D"
        Gear.UNKNOWN -> "-"
    }
    val (brakeText, brakeColor) = when (v.parkingBrakeOn) {
        true -> "체결" to ChaonColors.Green
        false -> "해제" to ChaonColors.Amber
        null -> "-" to ChaonColors.Muted
    }
    val soc = v.socPercent?.let { "%.0f%%".format(it) } ?: "-"

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(ChaonColors.Card)
            .border(1.dp, ChaonColors.CardBorder, RoundedCornerShape(50))
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(dotColor)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = dotColor, fontWeight = FontWeight.Bold)) { append(safetyText) }
                withStyle(SpanStyle(color = ChaonColors.Muted)) { append("  ·  ") }
                append("기어 $gear")
                withStyle(SpanStyle(color = ChaonColors.Muted)) { append("  ·  ") }
                append("주차브레이크 ")
                withStyle(SpanStyle(color = brakeColor, fontWeight = FontWeight.Bold)) { append(brakeText) }
                withStyle(SpanStyle(color = ChaonColors.Muted)) { append("  ·  ") }
                append("배터리 $soc")
            },
            style = MaterialTheme.typography.labelMedium,
            color = ChaonColors.Text,
        )
        SourceTag(v.source)
    }
}

// ───────────── 안내 배너 ─────────────

/** 하단 안내 배너 — 음성이 안 나와도 같은 내용을 화면으로 보여준다 (FR-05) */
@Composable
fun AnnounceBanner(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ChaonColors.Card)
                .border(1.dp, ChaonColors.Amber.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(ChaonColors.Amber, size = 12)
            Spacer(Modifier.width(16.dp))
            Text(text.orEmpty(), style = MaterialTheme.typography.titleLarge, color = ChaonColors.Text)
        }
    }
}

fun formatClock(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

package com.chaon.app.voice

import com.chaon.app.session.RestMode
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {

    private fun start(mode: RestMode, min: Int?) = VoiceCommand.Start(mode, min)

    @Test
    fun `숫자로 말한 시간과 모드를 알아듣는다`() {
        assertEquals(start(RestMode.ONSWIM, 20), VoiceCommandParser.parse("20분 쉬기"))
        assertEquals(start(RestMode.ONJAM, 15), VoiceCommandParser.parse("낮잠 15분 잘게"))
        assertEquals(start(RestMode.ONKKI, 30), VoiceCommandParser.parse("30분 동안 밥 먹을래"))
        assertEquals(start(RestMode.ONJEONG, 5), VoiceCommandParser.parse("5분 숨 고르기"))
    }

    @Test
    fun `한글로 말한 숫자도 알아듣는다`() {
        assertEquals(start(RestMode.ONSWIM, 20), VoiceCommandParser.parse("이십 분 쉬기"))
        assertEquals(start(RestMode.ONJAM, 15), VoiceCommandParser.parse("온잠 십오분"))
        assertEquals(start(RestMode.ONKKI, 60), VoiceCommandParser.parse("한 시간 식사"))
    }

    @Test
    fun `시간 없이 모드만 말하면 저장된 설정을 쓴다`() {
        assertEquals(start(RestMode.ONBAM, null), VoiceCommandParser.parse("오늘 여기서 차박할래"))
        assertEquals(start(RestMode.ONSWIM, null), VoiceCommandParser.parse("좀 쉬자"))
        assertEquals(start(RestMode.ONJAM, null), VoiceCommandParser.parse("낮잠 잘래"))
    }

    @Test
    fun `시간만 말하면 온쉼으로 본다`() {
        assertEquals(start(RestMode.ONSWIM, 10), VoiceCommandParser.parse("10분"))
    }

    @Test
    fun `더 자기, 일어났어, 그만을 구분한다`() {
        assertEquals(VoiceCommand.Snooze, VoiceCommandParser.parse("5분만 더"))
        assertEquals(VoiceCommand.Snooze, VoiceCommandParser.parse("조금 더 잘래"))
        assertEquals(VoiceCommand.WakeUp, VoiceCommandParser.parse("일어났어"))
        assertEquals(VoiceCommand.WakeUp, VoiceCommandParser.parse("확인했어"))
        assertEquals(VoiceCommand.WakeUp, VoiceCommandParser.parse("일어났어요"))
        assertEquals(VoiceCommand.WakeUp, VoiceCommandParser.parse("일어 났어"))
        assertEquals(VoiceCommand.WakeUp, VoiceCommandParser.parse("일어났어."))
        assertEquals(VoiceCommand.WakeUp, VoiceCommandParser.parse("깼어"))
        assertEquals(VoiceCommand.Snooze, VoiceCommandParser.parse("5분 만 더"))
        assertEquals(VoiceCommand.Stop, VoiceCommandParser.parse("그만할래"))
        assertEquals(VoiceCommand.Stop, VoiceCommandParser.parse("종료"))
    }

    @Test
    fun `모르는 말은 Unknown`() {
        assertEquals(VoiceCommand.Unknown, VoiceCommandParser.parse("오늘 날씨 어때"))
        assertEquals(VoiceCommand.Unknown, VoiceCommandParser.parse(""))
        assertEquals(VoiceCommand.Unknown, VoiceCommandParser.parse(null))
    }

    @Test
    fun `한자어 수 변환`() {
        assertEquals(10, VoiceCommandParser.sinoKorean("십"))
        assertEquals(25, VoiceCommandParser.sinoKorean("이십오"))
        assertEquals(3, VoiceCommandParser.sinoKorean("삼"))
        assertEquals(null, VoiceCommandParser.sinoKorean("백"))
    }
}

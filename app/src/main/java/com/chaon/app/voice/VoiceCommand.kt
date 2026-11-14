package com.chaon.app.voice

import com.chaon.app.session.RestMode

/** 음성 명령 해석 결과 */
sealed interface VoiceCommand {
    /** 모드 시작. minutes 가 null 이면 저장된 설정 사용 */
    data class Start(val mode: RestMode, val minutes: Int?) : VoiceCommand
    data object Stop : VoiceCommand
    data object Snooze : VoiceCommand
    data object WakeUp : VoiceCommand
    data object Unknown : VoiceCommand
}

/**
 * 한국어 음성 명령 해석기 (순수 Kotlin, 단위 테스트 대상).
 *
 * 예) "20분 쉬기", "온쉼 십오 분", "낮잠 20분", "밥 먹을래", "숨 고르기 5분", "차박할래",
 *     "그만", "5분만 더", "일어났어"
 */
object VoiceCommandParser {

    private val SNOOZE = listOf("만 더", "조금 더", "더 잘", "더 잘래", "더 자")
    // 띄어쓰기를 지운 문장에서 찾는다 ("일어 났어" → "일어났어")
    private val WAKE = listOf("일어났", "일어낫", "이러났", "일어나", "일어날", "깼", "기상", "확인", "알람꺼", "알람그만")
    private val STOP = listOf("그만", "끝내", "종료", "멈춰", "중지", "끝")

    private val MODE_WORDS: List<Pair<RestMode, List<String>>> = listOf(
        // "낮잠 잘래" 가 온밤으로 잡히지 않게 온잠을 먼저 본다
        RestMode.ONJAM to listOf("온잠", "낮잠", "잠깐 잘", "눈 좀 붙", "자고 싶"),
        RestMode.ONBAM to listOf("온밤", "차박", "밤새", "자고 갈", "잘래", "취침"),
        RestMode.ONKKI to listOf("온끼", "밥", "식사", "먹을", "점심", "저녁", "도시락"),
        RestMode.ONJEONG to listOf("온정", "호흡", "숨", "명상", "마음"),
        RestMode.ONSWIM to listOf("온쉼", "쉬기", "쉴래", "쉬자", "휴식", "쉬고"),
    )

    fun parse(raw: String?): VoiceCommand {
        val text = raw?.trim()?.replace(Regex("\\s+"), " ") ?: return VoiceCommand.Unknown
        if (text.isEmpty()) return VoiceCommand.Unknown

        val compact = text.replace(" ", "").replace(".", "").replace("?", "").replace("!", "")

        // "5분만 더" 는 숫자가 있어도 시작이 아니라 더 자기
        if (SNOOZE.any { compact.contains(it.replace(" ", "")) }) return VoiceCommand.Snooze
        if (WAKE.any { compact.contains(it) }) return VoiceCommand.WakeUp

        val minutes = parseMinutes(text)
        val mode = MODE_WORDS.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first

        if (mode != null) return VoiceCommand.Start(mode, minutes)
        if (STOP.any { text.contains(it) }) return VoiceCommand.Stop
        // "20분" 만 말하면 온쉼으로 본다
        if (minutes != null) return VoiceCommand.Start(RestMode.ONSWIM, minutes)
        return VoiceCommand.Unknown
    }

    /** "20분", "이십 분", "십오분", "한 시간", "1시간 반" → 분 */
    fun parseMinutes(text: String): Int? {
        Regex("(\\d+)\\s*시간\\s*반").find(text)?.let { return it.groupValues[1].toInt() * 60 + 30 }
        Regex("(\\d+)\\s*시간").find(text)?.let { return it.groupValues[1].toInt() * 60 }
        Regex("(\\d+)\\s*분").find(text)?.let { return it.groupValues[1].toInt() }

        if (text.contains("한 시간 반") || text.contains("한시간 반") || text.contains("한시간반")) return 90
        if (text.contains("한 시간") || text.contains("한시간")) return 60
        if (text.contains("반 시간")) return 30

        Regex("([일이삼사오육칠팔구십]+)\\s*분").find(text)?.let { m ->
            return sinoKorean(m.groupValues[1])
        }
        return null
    }

    /** 일~구십구 (한자어 수) */
    fun sinoKorean(word: String): Int? {
        val digits = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)
        if (word.isEmpty()) return null
        val tenIndex = word.indexOf('십')
        return if (tenIndex < 0) {
            if (word.length == 1) digits[word[0]] else null
        } else {
            val tensPart = word.substring(0, tenIndex)
            val onesPart = word.substring(tenIndex + 1)
            val tens = if (tensPart.isEmpty()) 1 else (if (tensPart.length == 1) digits[tensPart[0]] else null) ?: return null
            val ones = if (onesPart.isEmpty()) 0 else (if (onesPart.length == 1) digits[onesPart[0]] else null) ?: return null
            tens * 10 + ones
        }
    }
}

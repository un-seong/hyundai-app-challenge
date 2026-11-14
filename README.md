# 차온(CHAON) — 차 안의 온전한 쉼

> **정차한 차 안의 짧은 휴식을 정차 확인 · 사운드 · 종료 예고 · 복귀까지 하나의 세션으로 관리하는 Pleos Connect 차량용 앱**

2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 · 팀 **말랑복숭**

| 항목 | 내용 |
|---|---|
| 앱 이름 | 차온 (CHAON) |
| 팀명 | 말랑복숭 |
| 버전 | 0.6.1 |
| APK | [`apk/말랑복숭_차온_release.apk`](apk/) |
| 저장소 | https://github.com/un-seong/hyundai-app-challenge |

---

## 1. 무엇을 하는 앱인가요

휴게소·주차장에 차를 세우고 잠깐 쉴 때, 시간만 고르면 차온이 나머지를 챙깁니다.

| 모드 | 상황 | 핵심 기능 |
|---|---|---|
| **온쉼** | 눈 감고 잠깐 쉬기 | 10/20/30분 빠른 시작, 자체 생성 사운드, 종료 예고 |
| **온잠** | 짧은 낮잠 | 2분 전부터 소리로 천천히 깨우기, 점점 커지는 기상 알람(4종), "5분만 더" 최대 2번, 출발 전 체크 |
| **온끼** | 차 안 식사 | 식사 중간 환기 알림, 5분 전 정리 안내, 정리 체크 |
| **온정** | 업무 후 호흡 | 4-6 / 박스 호흡, 숨에 맞춰 움직이는 원, 마음 체크(저장 안 함) |
| **온밤** | 차박 취침 | SOC·용량·SOH(추정)·공조 수준으로 **기상 시각까지 배터리가 버티는지 예측**, 밤사이 배터리 감시, 기준 이하 시 깨움 |

공통 안전 원칙: **기어 D/R(주행)이 감지되면 어떤 세션이든 즉시 안전 종료**, 정차를 확인할 수 없으면 사용자가 직접 확인해야 시작합니다.

## 2. 실행 방법

### APK로 바로 실행 (심사용)
1. Pleos Connect Emulator v2.1.0을 실행합니다 (아래 테스트 환경 참고).
2. `apk/말랑복숭_차온_release.apk`를 에뮬레이터 화면에 끌어다 놓아 설치합니다.
   또는 `adb install "apk/말랑복숭_차온_release.apk"` (경로를 따옴표로 감싸 주세요. 한글 경로 문제가 생기면 끌어다 놓아 설치하거나 파일명을 영문으로 바꿔 설치해도 됩니다)
3. 화면 아래 독의 **⊞(앱 목록)**에서 **차온**(초승달 아이콘)을 눌러 실행합니다.

### 소스에서 빌드
1. 저장소를 **영문 경로**에 내려받습니다. 예: `D:\pleos\hyundai-app-challenge` (Windows에서 한글 경로는 빌드 오류)
2. Android Studio → **File → Open** → 저장소 폴더 → Gradle Sync
   - AGP 업그레이드 제안은 적용하지 않습니다 (공식 샘플과 같은 AGP 8.8.2 기준)
   - Sync 오류 시 Settings → Build Tools → Gradle → **Gradle JDK = 17**
3. 실행 대상 **app**, 기기 **Pleos Connect v2.1.0** 선택 후 ▶ Run
4. release APK: Build Variants에서 `release` 선택 → **Build → Build APK(s)** → `app/build/outputs/apk/release/app-release.apk`
   (시연·심사용으로 디버그 키로 서명되어 바로 설치됩니다)

## 3. 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (Android 14, API 34, x86_64) |
| 화면 규격 | **2560 × 1440 px, 160 dpi** (가로) |
| AVD 설정 | Boot option **Cold boot** (Quick boot에서는 VHAL 사용 불가), RAM 4GB |
| 개발 도구 | Android Studio, JDK 17, Gradle wrapper, AGP 8.8.2, Kotlin 2.0.0, Jetpack Compose |
| SDK 버전 | minSdk 28 / compileSdk 35 / targetSdk 35 |
| Pleos SDK | Vehicle 2.3.2, TextToSpeech 3.3.1, SpeechToText 3.3.1 (공개 Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`) |

**필요한 설정**
- **음성 명령을 시험하려면** 에뮬레이터에 PC 마이크를 연결해야 합니다 (에뮬레이터를 켤 때마다):
  에뮬레이터 `⋯` → Microphone → *Virtual microphone uses host audio input* 켜기, 또는 `adb emu avd hostmicon`
- **차량 상태를 바꿔 보려면** Pleos Connect Virtual Console에서 기어(P/D)·배터리 값을 조작합니다.
  Virtual Console 없이 시연하려면 앱 상단을 **"데모"**로 바꾸고 P/R/N/D 버튼을 사용합니다.
- 일반 폰 에뮬레이터에서도 실행되며, 이때는 자동으로 데모 데이터로 동작합니다.

## 4. 시연 시나리오 (약 3분)

1. 홈: 상단에 **정차 · 기어 P · 배터리 % · 차량 데이터** 표시 확인
2. **SDK 진단**: Pleos Vehicle SDK로 SOC 조회 성공, TTS·STT 연결, SOH 메서드 미제공 확인
3. 상단 **"데모"** 선택 → 🎤 **"낮잠 1분"** → 1분 뒤 기상 알람 → 🎤 **"일어났어"** → 출발 전 체크
4. 온쉼 시작 → 상단 **D** 누르기 → "주행이 감지되어 휴식을 안전하게 종료"
5. 온밤 설정: 기상 시각·공조 수준을 바꾸며 **충분/빠듯/부족** 예측 확인 → 시작 후 "배터리 −10%"로 경고·기상 흐름

## 5. 사용한 SDK / 차량 데이터

| 기능 | 연동 | 비고 |
|---|---|---|
| 기어 P/R/N/D, 주차 브레이크 | AAOS 표준 속성 `GEAR_SELECTION`, `PARKING_BRAKE_ON` (실시간 구독) | Pleos 공개 카탈로그에 Gear가 없어 표준 속성 사용. 권한 `android.car.permission.CAR_POWERTRAIN` |
| EV 배터리 SOC | **Pleos Vehicle SDK** `EvBattery` | 30초마다 갱신. 권한 `pleos.car.permission.CAR_ENERGY` |
| 주차 브레이크(보조) | **Pleos Vehicle SDK** `Brake` | AAOS 값이 없을 때만 사용. 권한 `pleos.car.permission.CAR_DRIVE` |
| 음성 안내 | **Pleos TextToSpeech SDK 3.3.1** (ON_DEVICE) | 실패 시 Android TTS로 자동 전환. 권한 `pleos.car.permission.TTS_SERVICE` |
| 음성 명령 | **Pleos SpeechToText SDK 3.3.1** (ON_DEVICE) | 버튼을 눌렀을 때와 기상 알람 사이에만 마이크 사용. 권한 `pleos.car.permission.STT_SERVICE` |
| 사운드 | 코드로 실시간 생성 (빗소리·파도·알람음 4종) | 음원 파일 없음 → 저작권 문제 없음 |

**에뮬레이터 실측 결과**: `getEvBatteryStateOfCharge` 조회 성공, 충전 상태·브레이크 페달 조회 성공, 전비(`…EvEfficiency`)는 에뮬레이터 미지원, **SOH 메서드 없음** → 온밤은 SOH를 보수적 추정값으로 계산하고 화면에 "추정"으로 표시.

- 차량 제어 권한(`CONTROL_CAR_*`)은 선언하지 않았습니다. 공조·조명·시트 제어는 App Market 승인 범위 확인 후 추가합니다.
- 모든 기록·설정은 기기에만 저장하고, 서버로 보내지 않습니다. 온정의 마음 체크는 저장하지 않습니다.

## 6. 화면 구성

| 화면 | 내용 |
|---|---|
| 홈 | 빠른 시작, 정차 확인, 오늘의 쉼, 쉼 모드 목록, 실제 SDK 연결 상태 |
| 모드 설정 | 5개 모드 탭, 길이, 예고/깨우기 방식, 알람음, 음성 안내 엔진 표시, 사운드·음량 |
| 세션 진행 | 남은 시간 링 / 온정 호흡 원 / 온밤 어두운 화면 |
| 기상 | 점점 커지는 알람, 음성 응답, 일어났어요 / 5분만 더 |
| 복귀 | 종료 사유별 안내, 모드별 체크리스트, 배터리 사용 요약(온밤) |
| SDK 진단 | Pleos SDK 호출 결과 (성공 / 미지원 / 오류 / 참고 구분) |

## 7. 코드 구조

```
app/src/main/java/com/chaon/app/
├─ MainActivity.kt            화면 전환
├─ session/RestSession.kt     세션 상태 머신 (순수 Kotlin, 단위 테스트)
├─ session/Breathing.kt       온정 호흡 패턴 계산 (단위 테스트)
├─ session/NightEnergy.kt     온밤 에너지 예측 모델 (단위 테스트)
├─ vehicle/                   정차 판단, AAOS 기어·주차 브레이크, Pleos Vehicle SDK, 데모 데이터
├─ audio/                     Pleos TTS(+대체), 자체 생성 사운드·알람음
├─ voice/                     Pleos STT 음성 입력, 한국어 명령 해석기 (단위 테스트)
├─ data/LocalStore.kt         설정·세션·오늘의 쉼 로컬 저장
└─ ui/                        Compose 화면, ViewModel, 테마
```

단위 테스트 29개: Android Studio에서 `app/src/test` 우클릭 → Run Tests

## 8. 알려진 한계

- 타이머·사운드는 앱이 실행 중일 때 동작합니다. 앱을 나갔다 돌아오면 남은 시간을 이어서 보여줍니다. 온밤처럼 긴 세션은 다음 버전에서 포그라운드 서비스로 옮길 예정입니다.
- 온밤의 공조·기본 소비전력은 가정값입니다(화면에 표시). 실차 측정으로 보정이 필요합니다.
- Windows 에뮬레이터에서 마이크를 켜면 소리가 가끔 끊길 수 있습니다(에뮬레이터 환경 문제).

## 변경 이력

- **v0.6.1** 기상 음성 응답 인식 개선("일어났어요", "일어 났어", "깼어" 등), 못 알아들으면 들은 말 표시
- **v0.6** 기상 알람음 4종 선택, 기상 알람 중 자동 음성 응답, "차량 | 데모" 전환 정리
- **v0.5** Pleos TTS·STT 연동, 한국어 음성 명령, 데모 모드 1분 세션, 소리 깨짐 완화
- **v0.4** 온밤 (배터리 기반 유지시간 예측, 밤사이 감시, SDK 진단 정리)
- **v0.3** 온끼, 온정
- **v0.2** 새 디자인, 온잠
- **v0.1** 온쉼, 정차 판단, Pleos Vehicle SDK·AAOS 연동

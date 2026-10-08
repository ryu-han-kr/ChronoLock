# ChronoLock

사용자가 정한 시간·횟수·재실행 간격으로 앱 사용을 조절하는 Android 앱입니다.
현재 범위는 오프라인 앱 차단 PoC이며, 제품 명세는 [PROJECT_SPEC.md](PROJECT_SPEC.md)입니다.

## 현재 구현

- 앱 선택, 일일 사용시간·실행 횟수·쿨다운 규칙 설정, 한국어 Compose 화면.
- 순수 Kotlin RuleEngine과 사용량 상태 머신, 1초 주기 재평가.
- 접근성 이벤트 감지, 차단 오버레이, Room 규칙·사용량·체크포인트 저장.
- 권한 설명, 시스템 설정·홈·전화 앱 제외, 로컬 데이터 삭제.
- 인터넷 권한 없이 동작하며 서버·AI·iOS는 아직 구현하지 않았습니다.

진행 상황과 검증 결과는 [docs/STATUS.md](docs/STATUS.md), 기기 검증 절차는
[docs/DEVICE_TEST_PLAN.md](docs/DEVICE_TEST_PLAN.md)에 기록합니다.

## 개발 환경

`android/`를 Gradle 프로젝트로 엽니다. JDK 17, Android SDK Platform 35,
Build Tools 34.0.0을 사용합니다. SDK 위치는 `ANDROID_HOME` 또는 Git에서
제외된 `android/local.properties`의 `sdk.dir=C:/path/to/Android/Sdk`로 지정합니다.
버전 조합은 [AGP 8.6 호환성 문서](https://developer.android.com/build/releases/agp-8-6-0-release-notes)를 따릅니다.

```powershell
cd android
.\gradlew.bat -PdomainOnly :domain:test
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

도메인 테스트는 Android SDK 없이 실행할 수 있습니다. 마지막 명령은 연결된 기기나
에뮬레이터가 필요합니다. macOS/Linux에서는 `./gradlew`를 사용합니다.
APK 경로는 `android/app/build/outputs/apk/debug/app-debug.apk`입니다.

GitHub Actions는 PR에서 빌드·Lint·단위 테스트와 Android 35 에뮬레이터의 Room 테스트를
실행하고 APK와 결과 보고서를 아티팩트로 보관합니다. 실제 앱 전환·차단 검증은 별도로 필요합니다.

## 구현 경계

`android/domain`은 Android 의존성 없는 판정·계측 모듈입니다. `android/app`의
`data`는 저장소, `blocking`은 OS 연동과 오버레이, `feature`는 ViewModel과 UI를 담당합니다.
설정 한도는 초 단위이며 계측은 소수 초 손실을 방지하기 위해 밀리초를 보존합니다.
기존 프로젝트의 이름과 패키지(`ChronoLock`, `com.chronolock`)를 유지합니다.

세션 한도·스케줄·집중 세션은 도메인 판정만 있고 저장·설정 화면은 아직 없습니다.
서비스 중단 중 실행 횟수와 이전 날짜 사용량은 복구하지 않으며, 제조사별 프로세스 관리와
이벤트 지연은 실기기 검증이 필요합니다. 권한 해제·강제 종료·제거를 방해하지 않습니다.

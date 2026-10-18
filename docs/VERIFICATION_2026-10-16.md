# 2026-10-16 로컬 검증

기준: `f466f08` 위의 미커밋 작업 트리. 이전 작업의 시간 입력·테마·언어·사용 기록 변경과
이번에 추가한 `ChronoLayoutTest`를 함께 검증했습니다.

## 결과

| 검사 | 결과 |
|---|---|
| Debug APK / 계측 APK | 빌드 성공 |
| Lint | 오류 0개, 경고 18개 |
| 도메인 단위 테스트 | 79개 통과 |
| 앱 단위 테스트 | 테마 팔레트 2개 통과 |
| 휴대폰 전체 계측 | 40개 통과, 59.166초 |
| 태블릿 레이아웃·집중 UI | 14개 통과, 37.421초 |
| 작은 화면·큰 글씨 레이아웃·집중 UI | 14개 통과, 51.686초 |

계측 테스트 40개는 HistoryRepository 5, LocalRepository 12, ThemePreferences 2,
ChronoLayout 3, FocusUi 11, HistoryUi 5, Localization 2개입니다.
태블릿과 큰 글씨 검사는 ChronoLayout·FocusUi의 같은 14개를 조건별로 재실행한 결과입니다.

환경: Windows, JDK 17, Gradle 8.7, Android 에뮬레이터 API 37 x86_64.
휴대폰은 1080×2400px / 420dpi, 태블릿은 1760×1280px / 256dpi(1100×800dp),
큰 글씨는 1080×2400px / 480dpi(폭 360dp) / font_scale 1.5입니다.

## 실행 명령

`android/`에서 SDK와 JDK를 지정하고 실행했습니다. 의존성은 사용자 Gradle 캐시에 준비되어 있습니다.

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
.\gradlew.bat :domain:test --rerun :app:testDebugUnitTest --rerun --offline --console=plain
```

기존 에뮬레이터 앱은 사용자 `.android/debug.keystore`로 서명되어 있어 같은 개발용 키를
`android.injected.signing.*` 빌드 인자로 지정해 두 APK를 다시 패키징했습니다.
키는 저장소에 포함하지 않았으며 기존 앱을 제거하지 않고 `adb install -r`로 업데이트했습니다.

이후 저장소 루트에서 다음 명령으로 전체 계측 테스트를 실행했습니다(`adb`는 SDK platform-tools 실행 파일).

```powershell
adb -e install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -e install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -e shell am instrument -w com.chronolock.test/androidx.test.runner.AndroidJUnitRunner
```

화면 크기와 밀도, 글씨 크기를 변경한 두 조건에서는 다음 테스트를 실행했습니다.
실행 후 크기·밀도는 reset하고 글씨 크기는 변경 전 값으로 복원했습니다.

```powershell
adb -e shell am instrument -w -e class com.chronolock.feature.ChronoLayoutTest,com.chronolock.feature.FocusUiTest com.chronolock.test/androidx.test.runner.AndroidJUnitRunner
```

## 검증 자료와 한계

로그: `.tools/verification-2026-10-16/{full-build,unit-tests,signing-build,instrumentation-phone,instrumentation-tablet,instrumentation-large-font}.log`.
캡처는 같은 디렉터리의 `phone/`, `tablet/`, `large-font/`에 있습니다. 모두 로컬 산출물이며 Git 제외입니다.
각 조건의 `layout-tab-*.png`와 시간 입력창 등 대표 캡처를 직접 열어 확인했습니다.
태블릿·큰 글씨 폴더의 히스토리 언어별 캡처는 최초 전체 실행에서 남은 파일입니다.
조건별 새 캡처는 레이아웃·FocusUi에서 생성하는 화면들입니다.

Lint 경고는 복수형 리소스 후보 13개와 UnusedAttribute, GradleDependency,
DataExtractionRules, KaptUsageInsteadOfKsp, SetTextI18n 각각 1개입니다.

실기기는 연결되지 않았습니다. 실제 접근성 이벤트·차단 지연·기기 재부팅·제조사 배터리 정책과
API 35의 CI 실행은 이번에 검증하지 않았습니다. 기기별 시나리오는 [기기 검사 계획](DEVICE_TEST_PLAN.md)에 따릅니다.

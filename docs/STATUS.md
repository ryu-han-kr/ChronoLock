# 작업 상태

갱신: 2026-10-10

## 이전 작업 확인

로컬 이전 대화 기록과 실제 파일을 대조했습니다. Android PoC 코드, 도메인 테스트,
Room 계측 테스트까지 작성되었으나 첫 커밋과 APK 빌드 검증은 완료되지 않았습니다.
명세의 초기 마일스톤은 아직 통과하지 않았습니다.

## 이번 작업

- 빈 원격 저장소에 명세·ignore 파일을 기준 커밋으로 등록했습니다.
- [PR #1](https://github.com/ryu-han-kr/ChronoLock/pull/1)에 기존 Android 구현을 초안으로 등록했습니다.
- Android/Kotlin/Hilt 플러그인을 루트에서 함께 선언해 `BaseVariant` 로딩 오류를 수정했습니다.
- Gradle 스크립트 줄바꿈·실행 권한과 GitHub Actions 빌드/검증을 구성했습니다.
- Room 테스트를 파일 DB 기반으로 실행하고 DB 재개방 후 복구 검증을 추가했습니다.
- README, 기기 검증 절차, 본 진행 기록을 추가했습니다.
- 사용자 준비 정보·권한·SDK 설정과 이후 API 준비 항목을 [USER_SETUP.md](USER_SETUP.md)에 정리했습니다.

## 검증

| 검사 | 결과 |
|---|---|
| 기존 도메인 테스트 재실행 | 32개 통과 (RuleEngine 18, UsageMonitor 14) |
| 로컬 Android 빌드 | 플러그인 오류 해결; Android SDK 미설치로 실행 불가 |
| GitHub Actions APK / Lint | 통과; Lint 오류 0개, 경고 19개 |
| Android 35 Room 계측 테스트 | 4개 통과; 파일 DB 재개방 복구 포함 |
| 실제 기기 A/B/C 차단, 오프라인, 재시작 | 미검증 |

2026-10-10: 갤럭시 탭 S9+ (SM-X810, Android 16/API 36)에 무선 ADB로 APK를 설치한 뒤
사용자가 정상 동작을 확인했습니다. 이 피드백은 초기 실기기 동작 확인이며,
A/B/C별 수치·지연·오프라인·재시작의 개별 검증 기록을 대체하지 않습니다.

## 기능·출시·운영 기획 (2026-10-10)

[보고서 원문](reports/CHRONOLOCK_BUSINESS_PLAN_2026-10-10.md),
[크롬 HTML](reports/CHRONOLOCK_BUSINESS_PLAN_2026-10-10.html),
[14쪽 PDF](reports/CHRONOLOCK_BUSINESS_PLAN_2026-10-10.pdf)를 추가했습니다.
추가 기능 10개, 선택형 AI, 구독 가격 가설, 운영비 산식, 일일 Discord/이메일 보고 설계를 포함합니다.
계산기 산식과 PDF 한글·페이지 구성을 검증했습니다. 서버·구독·AI·발송의 실제 활성화는 하지 않았습니다.
신규 Google Play 제출에 필요한 API 36 대응은 현재 targetSdk 35에서 별도 구현해야 합니다.

## 빌드 근거

빌드 검증 커밋: `d5fa237`.
[CI 실행 및 APK 아티팩트](https://github.com/ryu-han-kr/ChronoLock/actions/runs/37759871555).
로컬 APK: `.tools/artifacts/37759871555/apk/app-debug.apk` (Git 제외).
문서 갱신 커밋 `9de9fe8`의 [후속 CI](https://github.com/ryu-han-kr/ChronoLock/actions/runs/37761659602)도
빌드 및 저장소 테스트 모두 통과한 것을 2026-10-09에 확인했습니다.
Lint 경고에는 오래된 의존성/API 대상, 백업 추출 설정, 문자열 리소스 처리가 포함됩니다.
CI 액션의 Node 20 사용 중단 경고도 후속 유지보수 항목입니다.

## 다음 순서

1. 기기 검증 절차에 따라 60초 사용·4번째 실행 차단·30초 쿨다운을 확인합니다.
2. 초기 마일스톤을 통과한 뒤 세션 한도·스케줄·집중 세션의 저장과 UI를 구현합니다.
3. 백엔드·추천·AI·iOS는 초기 Android 차단 검증 이후 진행합니다.

## 기존 구현의 명세 차이와 남은 제한

- 임시 제품명 FocusGuard 대신 ChronoLock 및 `com.chronolock`을 사용합니다.
- PoC는 앱별 한 개의 규칙 집합을 저장합니다. UUID를 갖는 개별 BlockingRule,
  모드 및 전체 이벤트/세션 테이블은 아직 구현하지 않았습니다.
- 설정 단위는 초지만 측정값·체크포인트는 밀리초입니다. 명세 5장의 전체 초 저장 모델과 차이가 있습니다.
- 재시작 복구는 오늘의 미관측 사용시간을 보완하지만 미관측 실행 횟수는 보완하지 않습니다.
- 분할 화면/PiP, 제조사별 서비스 종료, 실제 오버레이 안전 접근은 미검증입니다.

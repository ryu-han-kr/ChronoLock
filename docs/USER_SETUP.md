# 사용자 준비 사항

작성: 2026-10-09

**2026-10-11 업데이트:** Pro 월 990원으로 시작합니다. 지금 할 일은 [월 990원 출시 실행표](LAUNCH_PLAN_990.md)의 계정 확인·문의 연락처 준비·실기기 검사·테스터 확보 순서입니다. 아래는 기존 환경 설정 참고 자료입니다.

**현재 제공해야 할 API 키는 없습니다.** 지금 구현된 Android PoC는 인터넷 권한 없이
기기 안에서 규칙 판정과 저장을 수행합니다. GitHub 연결과 PR 자동 검증도 이미 설정되어 있습니다.
우선 실제 기기에서 차단 동작을 확인하면 됩니다.

2026-10-10 기준 갤럭시 탭 S9+ / Android 16 / YouTube 테스트에서 사용자가 정상 동작을 확인했습니다.
출시·구독·AI 및 매일 비용 보고에 필요한 정보는
[기획 보고서의 사용자 준비 항목](reports/CHRONOLOCK_BUSINESS_PLAN_2026-10-10.md#11-실행-순서와-사용자-준비-정보)에 추가했습니다.
Discord·이메일 자동 발송은 아직 설정되지 않았습니다.

## 1. 지금 알려주실 정보

| 항목 | 알려주실 내용 | 용도 |
|---|---|---|
| 테스트 기기 | 제조사·모델, Android 버전 | 현재 앱의 최소 SDK는 28이며 기기별 차단 동작을 확인해야 합니다. |
| 테스트 대상 앱 | 예: Instagram, YouTube | 60초 제한·4번째 실행 차단·30초 쿨다운 검증 대상을 정합니다. |
| APK 실행 결과 | 설치 성공 여부, 권한 설정 성공 여부, 차단 시점과 오류 | 실제 기기에서 초기 마일스톤을 판정합니다. |
| 로컬 빌드 여부 | 이 PC에서 직접 빌드할지, 제공된 APK로 테스트할지 | 직접 빌드할 때만 SDK 경로 설정이 필요합니다. |

아래 양식을 채워 주시면 됩니다. API 키나 비밀번호를 적는 양식이 아닙니다.

```text
기기 모델:
Android 버전:
테스트할 앱:
테스트 방식: 제공 APK / 이 PC에서 빌드
APK 설치: 성공 / 실패 / 아직 안 함
사용 정보 접근 권한: 허용 / 미허용
ChronoLock 접근성 서비스: 켜짐 / 꺼짐
60초 제한 결과:
4번째 실행 차단 결과:
30초 쿨다운 결과:
문제 발생 시 증상:
```

## 2. 휴대전화에서 설정할 항목

1. 제공된 Debug APK를 설치합니다. 로컬 파일은
   `.tools/artifacts/37759871555/apk/app-debug.apk`에 있습니다.
   GitHub [검증 실행](https://github.com/ryu-han-kr/ChronoLock/actions/runs/37761659602)의
   `chronolock-debug` 아티팩트에서도 받을 수 있습니다. CI 보관 기간은 14일입니다.
2. 앱의 권한 안내를 확인하고 **사용 정보 접근**을 허용합니다.
3. 앱의 권한 안내를 확인하고 **ChronoLock 접근성 서비스**를 켭니다.
4. 앱으로 돌아와 감지 상태를 확인한 뒤 테스트 앱에 제한을 설정합니다.
5. [기기 검증 절차](DEVICE_TEST_PLAN.md)에 따라 세 가지 제한을 각각 검사합니다.

권한은 사용자가 기기에서 직접 설정합니다. USB 디버깅은 ADB 설치·로그 수집·자동 기기 테스트를
할 때만 필요하며, APK를 직접 설치해 수동 검사할 때는 필요하지 않습니다.
기기가 설치나 접근성 설정을 차단하면 기기 모델과 표시된 안내 문구를 알려 주세요.

## 3. 이 PC에서 직접 빌드할 경우에만

현재 프로젝트 설정은 JDK 17, Android SDK Platform 35, Build Tools 34.0.0입니다.
2026-10-09 확인 시 이 작업 환경에는 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 설정과
PATH의 `adb`가 확인되지 않았습니다. SDK를 이미 설치했다면 실제 SDK 폴더 경로를 알려 주세요.

SDK 설치 후 `android/local.properties`에 다음과 같이 실제 경로를 적습니다.
이 파일은 Git에서 제외됩니다.

```properties
# 예시 경로를 실제 SDK 설치 경로로 바꿉니다.
sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk
```

빌드와 테스트 명령은 [README](../README.md#개발-환경)를 참고합니다.
제공된 APK만 설치해 검사할 경우 PC의 SDK 설정은 필요하지 않습니다.

## 4. 로그인(Supabase+Google)·배너 광고(AdMob) 설정 — 2026-10-18 추가

앱 코드는 들어가 있습니다. 키가 없으면 로그인 카드는 숨겨지고, Debug 빌드는 Google 테스트 광고를,
Release 빌드는 광고 없이 동작합니다. 값은 `android/local.properties`(Git 제외) 또는 `-P` 빌드 인자로만 넣습니다.

```properties
chronolock.supabaseUrl=https://<프로젝트>.supabase.co
chronolock.supabaseAnonKey=<anon public key>   # service_role 키는 절대 넣지 않음
chronolock.googleWebClientId=<웹 애플리케이션 유형 OAuth 클라이언트 ID>
chronolock.admobAppId=ca-app-pub-...~...
chronolock.admobBannerId=ca-app-pub-.../...
```

1. Google Cloud: OAuth 동의 화면, **웹** 클라이언트 ID(Supabase용)와 **Android** 클라이언트 ID(패키지 `com.chronolock` + 서명 SHA-1)를 만듭니다.
2. Supabase: Authentication → Providers → Google 사용, 웹 클라이언트 ID/시크릿 입력.
3. Supabase SQL Editor에서 `supabase/migrations/20261018000000_delete_my_account.sql` 실행(앱 내 계정 삭제, Play 정책 요건).
4. AdMob: 앱 등록 후 앱 ID와 배너(적응형) 광고 단위 ID 발급. 유럽 사용자 대비 AdMob 개인정보 보호 메시지(GDPR) 게시.
5. Play Console 데이터 보안 양식: 광고 ID·기기 식별자(광고), 이메일(계정) 수집을 신고하고 개인정보처리방침에 반영합니다.
6. Play Console → 수익 창출 → 정기 결제에 상품 ID `ad_free_monthly`(기본 요금제 월 990원)를 만들고 활성화합니다.
   결제 프로필(판매자 계정)이 필요하며, 테스트는 라이선스 테스터 계정 + 내부 테스트 트랙 설치로 합니다(직접 설치한 APK는 상품을 못 불러옴).
7. 토스페이먼츠: 앱 안의 광고 제거 결제에는 Google Play 결제가 필수입니다. 토스를 앱에서 쓰려면 Google의 한국 사용자용
   대체 결제 프로그램에 등록하고 Play 결제와 **함께** 제공하며, 거래를 24시간 내 Google에 보고해야 합니다(수수료 4%p 감면뿐).
   권장: 토스는 웹사이트 결제에만 쓰고, 이후 서버가 생기면 "웹 구매 → 로그인 → 앱 광고 제거"를 연결합니다(앱 안에서 웹 결제로 유도하는 문구·링크는 금지).

## 5. 이후 단계에서 준비할 정보 — 지금 생성하거나 전달할 필요 없음

아래는 PROJECT_SPEC.md에 따른 후속 준비 항목입니다. 아직 해당 연동 코드나 환경변수 이름은
구현되지 않았습니다. 초기 Android 차단 검증을 통과하고 각 기능에 착수할 때 확정합니다.

| 단계 | 준비할 정보·설정 | 현재 상태 |
|---|---|---|
| 백엔드 / Supabase PostgreSQL | 사용할 프로젝트·리전, PostgreSQL 접속 호스트·포트·DB명·사용자 및 서버용 비밀번호/연결 설정 | 미구현. 현재 앱에 Supabase API 키를 넣을 곳은 없습니다. |
| 서버 배포 / AWS Lightsail | 배포 계정·리전, 인스턴스 접속 방식, API 도메인 및 DNS 관리 가능 여부 | 미구현. 서버 배포 방식 확정 후 준비합니다. |
| 기기 동기화 | 규칙 백업·동기화 범위 결정 | 로그인(Supabase+Google)은 4장 참고. 동기화는 미구현입니다. |
| AI 설명 기능 | 사용할 API 프로젝트, 서버용 API 키, 모델·사용 예산 | 먼저 결정적 추천 엔진을 구현합니다. LLM 도입 시에만 필요합니다. |
| 정식 Android 배포 | 최종 앱 이름·패키지 ID, 배포 계정, 릴리스 서명 관리 방식, 개인정보처리방침·문의 연락처 | 현재는 Debug APK입니다. 현재 패키지는 `com.chronolock`입니다. |
| 구독 / 결제 | Play Console 정기 결제 상품 `ad_free_monthly`(월 990원) | 앱 코드 구현됨(Play Billing 8). 상품 등록·서버 검증은 미완료. |

비밀번호·API 키·서명키는 채팅이나 PR 본문에 적지 않고, 연동 단계에서 정한 로컬 비공개 설정
또는 서버/CI 비밀 저장소에 등록합니다. 서버용 키를 Android 앱에 포함하지 않습니다.
루트 `.gitignore`는 `.env` 계열 파일, `local.properties`, `*.jks`, `*.keystore`를 제외합니다.
`.env.example`은 값이 없는 설정 예시를 공유할 때만 커밋할 수 있습니다.

## 6. 이미 완료된 설정

- GitHub 원격 저장소: `ryu-han-kr/ChronoLock`.
- [PR #1](https://github.com/ryu-han-kr/ChronoLock/pull/1): Android PoC와 빌드/검증 문서.
- CI: 단위 테스트, APK 빌드, Lint, Android 35 Room 계측 테스트.
- 현재 CI는 사용자 지정 API 키·배포 비밀 없이 실행됩니다.
- IDE 개인 설정인 `.idea/`는 루트 `.gitignore`에서 제외됩니다.

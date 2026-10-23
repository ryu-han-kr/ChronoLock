# Play Console 출시 가이드 (개인 계정)

작성: 2026-10-18. 대상 빌드: `0.2.0`(versionCode 2), targetSdk 36, 유료·광고·로그인 제외 Release AAB.

## 0. 이번 Release AAB

- 파일: `android/app/build/outputs/bundle/release/app-release.aab` (빌드 산출물, Git 제외)
- 만드는 명령: `cd android; .\gradlew.bat :app:bundleRelease`
- 서명: 업로드 키 `%USERPROFILE%\.chronolock\chronolock-upload.jks` (alias `upload`).
  비밀번호는 `android/local.properties`의 `chronolock.release*` 항목에 있습니다.
  **키 파일과 local.properties의 해당 4줄을 USB나 비밀번호 관리자에 함께 백업하세요.**
  업로드 키를 잃어버려도 Play 앱 서명을 쓰면 Console에서 재설정을 요청할 수 있습니다.
- 업로드 인증서 지문(공개 정보, OAuth 등록용):
  - SHA-1 `9F:21:96:2A:1B:FE:8C:20:F3:C5:AC:1B:88:C1:9C:C3:7C:F3:9A:33`
  - SHA-256 `F4:62:8E:A1:35:3B:5F:21:9E:7F:73:AF:39:BB:7E:61:95:B9:DB:79:B9:BB:6B:AD:63:0E:54:1D:A6:B0:F5:E5`
- 이 빌드에는 키가 들어가지 않아 **광고·로그인·광고 제거 결제 화면이 나오지 않습니다.**
  AdMob 키가 없으면 광고 ID 권한(AD_ID, ADSERVICES)도 매니페스트에서 제거됩니다.
  최종 권한: 사용 정보 접근, 부팅 완료 수신, 인터넷, 결제(BILLING, 결제 라이브러리 기본 포함), 네트워크 상태, WAKE_LOCK, FOREGROUND_SERVICE.

## 1. 지금 할 일 순서

| 순서 | 할 일 | 이유 |
|---|---|---|
| 1 | 테스터 15명 내외 모집 시작(Gmail 주소 수집) | 개인 계정은 12명 이상이 14일 연속 참여해야 프로덕션을 신청할 수 있습니다. 가장 오래 걸립니다 |
| 2 | 내부 테스트 트랙에 AAB 업로드 | 첫 업로드 때 Play 앱 서명이 설정되고, 접근성 신고 양식이 열립니다 |
| 3 | 개인정보처리방침 URL 준비 | 접근성·사용 정보 권한을 쓰는 앱은 필수입니다. 초안은 제가 작성할 수 있습니다 |
| 4 | 앱 콘텐츠 신고(아래 3장) | 비공개 테스트 출시 전에 모두 완료해야 합니다 |
| 5 | 접근성 시연 영상 녹화 → YouTube 일부 공개 | 접근성 API 신고에 필요합니다 |
| 6 | 스토어 등록정보(아래 4장) | 비공개 테스트 출시에도 필요합니다 |
| 7 | 비공개 테스트 트랙 출시 → 테스터 초대 → 14일 유지 | 끝나면 프로덕션 액세스를 신청합니다 |
| 8 | (나중) 외부 키 발급(아래 5장) → 광고·로그인·결제 빌드 | 유료·광고 버전을 넣을 때 진행합니다 |

## 2. 내부 테스트 업로드

1. Play Console → ChronoLock → 테스트 및 출시 → 테스트 → **내부 테스트** → 테스터 탭에서 이메일 목록을 만들고 본인 주소를 넣습니다.
2. 새 버전 만들기 → "Google Play 앱 서명" 사용(기본값)에 동의 → `app-release.aab` 업로드.
3. 버전 이름은 `0.2.0 (2)`, 출시 노트(ko-KR) 예: `첫 내부 테스트 버전입니다. 앱별 사용 제한, 집중 모드, 사용 기록을 확인해 주세요.`
4. 저장 → 검토 → 출시. 테스터 탭의 "웹에서 참여" 링크로 설치합니다.
5. 업로드 후 테스트 및 출시 → 설정 → **앱 무결성 → 앱 서명**에서 "앱 서명 키 인증서" SHA-1을 메모합니다(5장 OAuth용).

## 3. 앱 콘텐츠 신고 (정책 및 프로그램 → 앱 콘텐츠)

| 항목 | 이 빌드의 답변 |
|---|---|
| 개인정보처리방침 | 공개 URL 입력(HTML 페이지, PDF 불가). 접근성·사용 정보 이용 목적, 기기 내 저장, 30일 보관, 삭제 방법, 문의 이메일을 적습니다 |
| 광고 | **아니요, 광고가 포함되어 있지 않습니다** (광고 버전을 출시할 때 "예"로 바꿈) |
| 앱 액세스 권한 | 모든 기능을 로그인 없이 이용 가능 → "특별한 액세스 권한 없이 모든 기능 이용 가능" |
| 콘텐츠 등급 | 이메일 입력 → 카테고리 "기타 앱 유형(유틸리티·생산성 등)" → 폭력·성적 콘텐츠·도박·사용자 간 소통·위치 공유·디지털 상품 구매 모두 **아니요** → 대부분 전체이용가 |
| 타겟층 및 콘텐츠 | **13세 이상**(13–15, 16–17, 18세 이상) 선택, 13세 미만 제외. 13세 미만을 넣으면 가족 정책 심사가 추가됩니다. "어린이 관심 유도" 질문은 아니요 |
| 데이터 보안 | 이 빌드: 수집·공유하는 사용자 데이터 **없음**. 사용 기록과 규칙은 기기에만 저장되고 전송되지 않습니다. 암호화 전송·삭제 요청 질문은 "데이터 수집 없음"으로 건너뜁니다 |
| 정부 앱 / 금융 기능 / 건강 / 뉴스 | 모두 해당 없음 |
| 계정 삭제 | 이 빌드는 계정 생성 기능이 없음 → "계정 생성 불가". 로그인 버전을 내면 웹 삭제 안내 URL이 필요합니다 |
| 접근성 API | 아래 3-1 참고 |

### 3-1. 접근성 API 신고

AAB 업로드 후 앱 콘텐츠에 "접근성 API"가 나타납니다. `isAccessibilityTool`은 false입니다(장애인 보조 도구가 아님).

- **핵심 기능:** "사용자가 직접 정한 앱 사용 제한(하루 시간, 1회 시간, 실행 횟수, 재실행 대기, 시간대 예약, 앱 묶음, 집중 모드)에 도달하면, 접근성 서비스의 앱 전환 이벤트(TYPE_WINDOW_STATE_CHANGED)로 현재 실행 앱을 확인해 차단 화면을 표시합니다. 0.8.0부터 사용자가 추가한 웹사이트를 지원 브라우저에서 열면, 브라우저 주소창(view ID로 지정한 노드 하나)의 도메인을 확인해 차단 화면을 표시합니다."
- **수집·이용 데이터:** 실행 중인 앱의 패키지 이름, 웹사이트 차단을 쓸 때 지원 브라우저 주소창의 도메인만 사용합니다(`canRetrieveWindowContent=true`는 주소창 노드 조회에만 사용). 다른 화면 내용, 입력, 비밀번호는 읽지 않고, 주소·방문 기록은 저장하지 않습니다. 기록은 기기에만 저장하고 전송하지 않습니다.
- **명시적 고지·동의:** 앱 설정 → 권한 카드 → "접근성 권한 안내" 대화상자에서 목적을 고지하고 "동의하고 설정 열기"를 눌러야 시스템 설정으로 이동합니다.
- **시연 영상(30초~2분):** 앱 실행 → 고지 대화상자 → 동의 → Android 접근성 설정에서 ChronoLock 켜기 → 제한 설정 → 대상 앱 실행 시 차단 화면.
  0.8.0부터: 앱 제한 탭 "웹사이트 차단"에 youtube.com 추가 → Chrome에서 youtube.com 열기 → 차단 화면 장면을 추가합니다.
  화면 녹화 후 YouTube에 **일부 공개**로 올리고 링크를 입력합니다. 영상 필수 여부는 현재 양식에서 확인하세요.

## 4. 스토어 등록정보 (기본 스토어 등록정보, ko-KR)

- 앱 이름(30자 이내): `ChronoLock - 앱 사용 시간 제한`
- 간단한 설명(80자 이내): `내가 정한 기준으로 앱 사용 시간과 실행 횟수를 제한하고 집중 시간을 지키세요.`
- 자세한 설명(4000자 이내): 기능, 권한 사용 목적(접근성·사용 정보 접근), 데이터가 기기에만 저장된다는 점을 포함합니다. 초안 작성 가능합니다.
- 그래픽: 아이콘 512×512 PNG, 그래픽 이미지 1024×500, 휴대전화 스크린샷 2~8장(16:9 또는 9:16). 7·10인치 태블릿 스크린샷은 선택입니다.
- 카테고리: 앱 → **생산성**. 연락처 이메일(필수), 웹사이트(선택).

## 5. 외부 키 발급 (광고·로그인·결제 버전을 넣을 때)

### Google Cloud (OAuth)
1. console.cloud.google.com → 새 프로젝트 `ChronoLock`.
2. Google 인증 플랫폼 → 브랜딩: 앱 이름, 사용자 지원 이메일, 대상 **외부**, 개발자 연락처. 범위는 `openid`, `email`, `profile`(비민감, 별도 인증 심사 없음) → 게시 상태를 **프로덕션**으로.
3. 클라이언트 → **웹 애플리케이션**: 승인된 리디렉션 URI `https://<프로젝트>.supabase.co/auth/v1/callback` → 클라이언트 ID와 보안 비밀 복사.
4. 클라이언트 → **Android**(SHA-1마다 1개): 패키지 `com.chronolock`, SHA-1 = ① Play 앱 서명 키(2장 5번) ② 업로드 키(0장) ③ 디버그 키(`keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -storepass android`).

### Supabase
1. supabase.com → 새 프로젝트, 리전 **Northeast Asia (Seoul)**.
2. Authentication → Sign In / Providers → Google 사용: 웹 클라이언트 ID·보안 비밀 입력. Authorized Client IDs에 웹 클라이언트 ID 추가.
3. SQL Editor에서 `supabase/migrations/20261018000000_delete_my_account.sql` 실행.
4. Project Settings → API: Project URL과 **anon(public)** 키만 앱에 넣습니다. service_role 키는 절대 넣지 않습니다.

### AdMob
1. admob.google.com → 앱 추가 → Android → (Play 게시 전이면 "아니요"로 등록 후 나중에 연결) → 앱 ID `ca-app-pub-…~…`.
2. 광고 단위 → **배너** → 광고 단위 ID `ca-app-pub-…/…`.
3. 개인정보 보호 및 메시지 → **유럽 규정** 메시지 생성·게시. 결제 정보(지급 프로필)를 입력합니다.
4. app-ads.txt를 개발자 웹사이트에 게시합니다(웹사이트가 생기면).

### 앱에 넣는 곳 (`android/local.properties`, Git 제외)
```properties
chronolock.supabaseUrl=https://<프로젝트>.supabase.co
chronolock.supabaseAnonKey=<anon public key>
chronolock.googleWebClientId=<웹 클라이언트 ID>
chronolock.admobAppId=ca-app-pub-...~...
chronolock.admobBannerId=ca-app-pub-.../...
```
키를 넣고 출시하면 3장의 광고(예), 데이터 보안(광고 ID·이메일·구매 내역), 계정 삭제 URL을 갱신해야 합니다.
정기 결제 `ad_free_monthly`(월 990원)는 결제 프로필을 만든 뒤 수익 창출 → 정기 결제에서 등록합니다.

## 참고
- [대상 API 수준 요건](https://support.google.com/googleplay/android-developer/answer/11926878): 2026-08-31부터 신규 앱·업데이트는 API 36 이상.
- [접근성 API 정책](https://support.google.com/googleplay/android-developer/answer/10964491)
- [개인 계정 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465)

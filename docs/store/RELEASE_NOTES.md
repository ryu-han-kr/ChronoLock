# Play Console 출시 노트

각 항목은 Play Console "새로운 기능"(언어별 500자 이내)에 그대로 붙여 넣는다.

## 0.20.0 (versionCode 30) — 2026-10-30 업로드 (내부·테스터들)

AAB: `release/chronolock-0.20.0-vc30.aab`. Pro 구분은 꺼진 빌드. PR #131~#137. Play API로 업로드(commit 15146078389568591798), ko-KR·en-US 노트.
업로드 전 태블릿 기기 테스트 154/154 통과(무선 adb를 끊는 3건 제외). 개인정보처리방침 Google 문서에 '알림 모아 보기' 행·알림 접근 해제 문구 추가.

이번에 바뀐 것 (내부 기록):
- 대신 열 앱: 규칙 편집에서 앱 하나를 고르면(DataStore `rule_alternatives`) 그 앱의 차단 화면에 "대신 [앱] 열기" 버튼.
  누르면 그 앱을 열고 감시 상태를 비워 차단 화면이 물러남. 앱 이름은 대문자로 바꾸지 않음. 기기 테스트
  `DeviceBlockingTest.blockScreen_opensTheAppChosenInstead`(처음엔 차단 화면이 남아 실패 → 열고 나서 Foreground(null) 보내도록 고쳐 통과).
- 집중 기록: 끝까지 마친 집중(시간 모으기 정산과 같은 순간)과 중간에 끈 집중(실제 시작~종료)을 DataStore `focus_log`에 60일 보관.
  히스토리 '최근 7일 돌아보기'에 "이번 주 집중 X · 끝까지 마친 집중 N번", 결과 카드에 "이번 주 집중 X". `LocalRepository.stopFocus`가 시작 시각을 돌려줌.
- 첫 주 맞춤 한도: 기록이 최근 7일 중 3일 이상이면 오늘 탭에 규칙 없는 앱 중 하루 평균 20분 이상 쓴 상위 3개와 평균의 약 70%
  (5분 단위, 최소 15분) 하루 한도를 보여 주고 "한 번에 적용"(새 규칙이라 엄격 모드 대기 없음) 또는 "괜찮아요". 한 번 처리하면 다시 안 나옴(`starter_limits_done`).
- 알림 모아 보기(설정, 기본 꺼짐): 알림 접근(NotificationListenerService `NotificationBatcher`)을 허용하면 규칙이 있는 앱의 알림을
  다음 12·18·21시까지 snooze(원래 알림을 Android가 다시 보여 줌, 내용은 읽지 않음). 전화·알람·내비·교통 카테고리, 진행 중·지울 수 없는 알림,
  안전 앱(전화·문자·ChronoLock)은 바로 옴. 기기 테스트 `NotificationBatchDeviceTest`(셸이 보낸 알림이 켜면 사라지고 끄면 남음). 방침 HTML 항목 추가.
- 휴대폰 전체 한도(설정, 패밀리링크의 하루 한도·쉬는 시간을 자율형으로): 하루 전체 시간(1~6시간), 허용한 앱만 쓰는 시간대,
  항상 허용 앱. 규칙 없는 앱도 대상(`PhonePolicy`, 런처에 있는 앱 − 안전 앱·ChronoLock·항상 허용 앱). 감시 상태 머신이 정책이 덮는 앱도
  사용량을 재고 차단 사유 ALLOWED_ONLY·PHONE_BUDGET 추가. 앱의 '잠시 쉬기'로는 풀리지 않음. 엄격 모드·잠금 중엔 느슨하게 저장 불가.
  새로 설치한 앱은 처음 열 때 대상에 들어감. 첫날 전체 시간은 켠 뒤부터만 셈. 기기 테스트 작성(태블릿 연결 끊김으로 미실행).
- 주말 하루 한도: 규칙 편집에 '주말 하루 허용 시간'(비우면 평일과 같음). `AppRule.weekendLimitSeconds`, `dailyLimitOn(date)`로
  차단·남은 시간·연속 기록 계산에 반영, 주말 한도 올리기는 엄격 모드 대기 대상. DB 12→13(`MIGRATION_12_13`, 규칙·보류 테이블 열 추가),
  백업 파일에도 포함. MigrationTest 12→13 추가(태블릿 연결 끊김으로 미실행).

### ko
- 차단 화면에서 미리 골라 둔 다른 앱을 바로 열 수 있어요.
- 이번 주 집중한 시간과 끝까지 마친 횟수를 보여 드려요.
- 일주일 사용 기록으로 가장 많이 쓴 앱 3개의 한도를 한 번에 정할 수 있어요.
- 제한한 앱의 알림을 하루 세 번 모아서 받을 수 있어요(설정에서 켜기).
- 휴대폰 전체 하루 시간과, 정한 앱만 쓰는 시간대를 직접 정할 수 있어요.
- 주말에는 다른 하루 한도를 쓸 수 있어요.

### en
- Open an app you picked instead, right from the block screen.
- See how long you focused this week and how many focuses you finished.
- Set limits for your three most-used apps in one tap, based on your first week.
- Get limited apps' notifications together three times a day (Settings).
- Set a daily total for the whole phone and times when only chosen apps open.
- Give weekends their own daily limit.

## 0.19.0 (versionCode 29) — 2026-10-30 업로드 (내부·테스터들)

AAB: `release/chronolock-0.19.0-vc29.aab`. Pro 구분은 꺼진 빌드. PR #125~#129. Play API로 업로드(commit 10807039866235815958), ko-KR·en-US 노트.
업로드 전 태블릿 기기 테스트 148건 중 LocalizationTest 1건 실패(시간대 문구의 %1$02d를 검사가 숫자 자리로 못 알아봄) →
문구를 %1$s로 바꾸고 시간을 코드에서 두 자리로 맞춤 → LocalizationTest·LocalizedAppTest·RiskyHoursUiTest 재실행 통과 후 AAB 다시 빌드.

이번에 바뀐 것 (내부 기록):
- 책상 QR로만 집중 끄기(설정, 기본 꺼짐, #125): 켜면 기기에서 무작위 비밀값을 만들고(`desk_qr_secret`) QR 이미지를 보여 주며
  "QR 이미지 저장·인쇄"로 공유. 켜져 있으면 오늘 탭의 집중 종료는 Google Play 서비스 코드 스캐너(카메라 권한 불필요)로 QR을 찍어
  이 기기 코드와 같을 때만 끝냄. 빠른 설정 타일은 집중 중이면 앱을 엶. 집중 중에는 끄기·새 QR 만들기 불가.
  엄격 모드·잠금 규칙은 그대로 먼저 적용. 기기 테스트 `DeskQrUiTest`(그린 QR이 같은 값으로 읽힘, 집중 중 스위치 잠김). 실제 스캐너 화면은 미확인.
- 시험 D-day(설정): 날짜(달력, 오늘 이전 불가)와 이름(20자) 저장(DataStore `exam`). 오늘 탭 카드 "D-20 수능까지", 차단 화면 마지막 줄
  "수능까지 D-20". 남은 날 14일 이하면 "한도 줄이러 가기"로 앱 제한 탭 이동(자동으로 줄이지 않음). 시험 다음 날부터 표시 안 함.
- 목표 잠금 '친구 비밀번호': 조각 비밀번호와 같은 코드를 만들어 시작 직후 한 번만 공유 창으로 친구에게 보냄(화면에 번호를 보여 주지 않음).
  친구 코드로는 목표 전에도 풀 수 있고(`LockSetting.canUnlock`), 친구가 없어도 조각을 다 모으면 풀림. 엄격 모드 대기 로직은 건드리지 않음.
- 가장 많이 쓰는 시간대: 히스토리가 보관 중인 원시 이벤트(10일)로 지난 7일 앱별·시간별 사용을 계산(`hourlyUsage`),
  한 앱이 가장 많이 쓴 연속 2시간(합계 1시간 이상)을 '최근 7일 돌아보기'에 표시. "이 시간 막기"는 그 앱 편집기를 매일 그 시간 예약이
  켜진 상태로 엶(새 규칙이면 하루 한도 없이). 런처·ChronoLock 자신 제외.

### ko
- 책상에 붙인 QR을 찍어야만 집중을 끝낼 수 있게 할 수 있어요(설정에서 켜기).
- 시험 날짜를 정하면 오늘 화면과 차단 화면에 D-day를 보여 드려요.
- 목표 잠금 비밀번호를 친구에게 맡길 수 있어요.
- 가장 많이 쓰는 시간대를 찾아 한 번에 막을 수 있어요.

### en
- Optionally require scanning a QR on your desk to end a focus (Settings).
- Set an exam date to see a D-day countdown on Today and the block screen.
- Let a friend hold your goal lock code.
- See your busiest hours and block them in one tap.

## 0.18.0 (versionCode 28) — 2026-10-30 업로드 (내부·테스터들)

AAB: `release/chronolock-0.18.0-vc28.aab`. Pro 구분은 꺼진 빌드. PR #109~#121. Play API로 업로드(commit 00260744706696103753), ko-KR·en-US 노트.

이번에 바뀐 것 (내부 기록):
- 문자 앱(기본 SMS 앱, 삼성 메시지·Google 메시지·AOSP mms)을 전화처럼 안전 앱으로 처리. 제한 대상 목록에 나오지 않고,
  이미 걸린 규칙이 있어도 차단하지 않음. 사용자 요청: "전화와 메시지는 기본 기능이라 절대 차단하지 마".
- 접근성(앱 감지)을 사용자가 끄면 시각과 잠금·엄격 모드 여부를 기록하고, 오늘 탭에 "앱 감지가 꺼진 적이 있어요" 카드로
  꺼진 구간(또는 아직 꺼져 있음)을 보여 줌. "확인했어요"로 닫음. 서비스 해제 시 설정 목록에서 빠져 있을 때만 기록해
  프로세스 종료·재부팅은 기록하지 않음(QA 2026-10-23 개선안 7). 휴대폰에서 사용자가 카드 확인.
- 주간 자동 백업: 설정 → 백업과 복원에서 폴더를 고르면(SAF 트리, 영구 권한) 바로 한 번 쓰고, 이후 6시간 주기 작업이
  7일마다 `chronolock-backup-auto.json` 하나를 덮어씀. 마지막 자동 백업 시각 표시, "자동 백업 끄기"로 권한 반납
  (QA 2026-10-23 개선안 6). 실패(폴더 삭제·권한 회수)는 그 주만 건너뜀. 기기 미검증.
- 폴더를 고르지 않아도 자동 백업: Android 10 이상은 기본으로 휴대폰 `다운로드/ChronoLock/chronolock-backup-auto.json`에
  씀(MediaStore, 권한 불필요, 앱을 지워도 남음). 앱을 열 때도 밀린 백업을 씀. "지금 백업" 버튼, 고른 폴더는
  "다운로드 폴더로 되돌리기"로 해제. 재설치 뒤에는 이전 파일을 열 수 없어 새 파일을 추가함(이전 파일은 복원용으로 남음).
  사용자 요청: "백업은 반드시 남아 있어야 해, 핸드폰에". Android 9는 이전처럼 폴더를 골라야 함.
- 같이 도전 링크: '친구에게 추천하기' 링크에 내 첫 목표를 담음(`referrer=utm_source=invite&utm_content=SOCIAL.VIDEO`).
  친구가 그 링크로 설치하면 첫 화면에서 Install Referrer로 읽어 같은 목표를 미리 고르고 "친구가 보낸 링크" 안내를 보여 줌.
  첫 화면에 "어떤 상황이세요?"(공부·시험 준비 / 업무 집중 / 잠들기 전 휴대폰) 선택 추가 → 목표를 한 번에 채움.
  맞춤 스토어 등록정보 링크에 `utm_campaign=study|work|bedtime`을 붙이면 그 상황이 미리 골라짐. 기존 공유 링크(utm_source=share)는 목표 없음으로 처리.
- 차단 화면에 "왜 열려고 했나요?" 한 번 누르기(심심해서·습관처럼·알림 보고·꼭 필요해서). 앱 차단 화면에만 나오고
  사이트·숨 쉬기·계속 쓸까요 화면에는 없음. 같은 화면이 떠 있는 동안 한 번만 묻고, 누르면 "기록했어요" 문구.
  날짜별 횟수를 DataStore `open_reasons`에 28일 보관(기기에만). 히스토리 '최근 7일 돌아보기'에 이유별 비율과 짧은 팁.
  "꼭 필요해서"를 눌러도 열어 주지는 않음(엄격 모드·잠금과 충돌 방지, 필요하면 다음 단계에서 결정).
  태블릿 검증 중 탭 직후 화면이 다시 그려져 질문이 또 나오던 문제를 고침(같은 앱 답을 1분 유지, #119).
- 결과 카드 공유: 히스토리 '최근 7일 돌아보기'의 "결과 카드 공유"가 1080×1920 이미지(캐릭터·지난주 대비·연속 기록·이번 주 사용)를
  만들어 추천 링크 문구와 함께 공유. 앱 이름은 그리지 않음. 캐시 `share/` 폴더만 FileProvider로 공유.
- 미래의 나에게 한 줄: 규칙 편집에 60자 한 줄, 그 앱 차단 화면에 따옴표로 표시. DataStore `rule_notes`(기기에만, 백업 파일에는 미포함).
- 앱 아이콘 길게 누르기 메뉴: "집중 시작"(마지막으로 고른 시간, 제한 앱이 없으면 앱 제한 탭으로), "오늘 사용 보기"(히스토리 탭).
  동적 단축키라 앱 언어를 따르고 검증용 빌드에서도 동작.
- 충전하면 잘 시간 알림(설정, 기본 꺼짐): 접근성 서비스가 ACTION_POWER_CONNECTED를 받아 21:00~03:00에 하룻밤 한 번
  "잘 시간이에요" 알림. "아침까지 집중"을 누르면 다음 07:00까지 집중(`startFocusUntil`, 최대 12시간, 일반 집중은 계속 180분 제한).
  제한 앱이 없거나 이미 집중 중이면 묻지 않음. 위치 권한 불필요. 알림은 아침이 되면 사라짐.
- 시간 모으기: 25분 이상 집중을 중간에 끄지 않고 끝내면 10분 적립(최대 30분, DataStore `time_bank_minutes`).
  적립은 앱 열 때·집중 시작 직전·타일·밤 집중·6시간 주기 작업에서 정산(`TimeBankStore.settle`, FOCUS_STARTED 이벤트로 길이 확인).
  오늘 탭 "모은 시간" 카드에서 앱 하나를 10분 쉬게 함(기존 '잠시 쉬기'와 같음: 엄격 모드·잠금이면 대기 후 적용). 하루 한도 자체는 안 바뀜.
- Lint 정리(동작 변화 없음): UseKtx 6건(toUri·SharedPreferences.edit·createBitmap), 마스코트 깜박임 프레임을
  `LocalResources`로 읽음. MascotUiTest가 기분별 마스코트를 찾기만 하고 확인하지 않던 것을 테마당 1개씩 있는지 확인하도록 고침.

### ko
- 전화·문자 앱은 어떤 경우에도 차단하지 않아요.
- 앱 감지가 꺼졌던 시간을 오늘 화면에서 알려 드려요.
- 매주 다운로드 폴더에 자동 백업해요. 앱을 지워도 남아요.
- 친구 링크로 설치하면 친구의 목표가 미리 골라져요.
- 처음 시작할 때 공부·업무·잠들기 전 중 내 상황을 고를 수 있어요.
- 차단 화면에서 왜 열려 했는지 누르면 히스토리에 모아 보여 드려요.
- 이번 주 결과를 이미지 카드로 공유해요.
- 앱마다 나에게 한 줄을 적으면 차단 화면에 보여 드려요.
- 앱 아이콘을 길게 눌러 집중을 시작하거나 오늘 사용량을 봐요.
- 밤에 충전하면 아침까지 집중할지 물어봐요(설정에서 켜기).
- 25분 집중을 끝내면 10분을 모아 앱 하나를 잠깐 쉬게 할 수 있어요.

### en
- Calls and texts are never blocked.
- Today shows when app detection was off.
- Weekly backup to Download, kept after uninstall.
- Friend links start with their goals.
- Pick study, work or bedtime at first run.
- Tap why you opened a blocked app; History sums it up.
- Share your week as a picture.
- Notes to yourself on the block screen.
- Long-press the icon to focus or see usage.
- Charge at night to focus until morning (Settings).
- Finish 25-min focuses to bank pause time.

## 0.17.0 (versionCode 27) — 2026-10-28 업로드 (내부·테스터들)

AAB: `release/chronolock-0.17.0-vc27.aab`. Pro 구분은 꺼진 빌드.

이번에 바뀐 것 (내부 기록, QA 2026-10-26 미해결 항목):
- 사이트 차단을 브라우저마다 따로 기억. 분할 화면·팝업 창에서 옆의 다른 감시 앱(카카오톡, 다른 브라우저)이 확인되면
  차단 중인 사이트의 차단 화면이 풀리던 문제 수정. 화면에 보이는 브라우저 창에 차단 사이트가 있으면 계속 덮음.
  기기 테스트 `blockedSiteInAPopUpWindow_staysBlocked_whenAnotherBrowserIsOpened`: 수정 전 실패 → 수정 후 통과(태블릿).
- 통화 화면(삼성 incallui 등, InCallService UI 앱)을 홈·설정·전화처럼 '떠남'으로 처리해 차단 화면이 통화 위에 남지 않게 함.
  와이파이 태블릿이라 실통화 미검증.
- 구글에서 차단 도메인을 검색해도 차단되지 않음을 기기 테스트로 확인(변경 없음). 영어 문구 복수형 lint 경고 4건은 값이 1이
  될 수 없어 무시 처리.
- 확인할 것: 휴대폰에서 제한 앱 사용 중 전화가 올 때 통화 화면이 가려지지 않는지.

### ko
- 분할 화면이나 팝업 창에서 옆에 다른 앱을 열어도 차단한 사이트가 계속 차단돼요.
- 제한 중인 앱을 쓰다가 전화가 와도 차단 화면이 통화 화면을 가리지 않아요.

### en
- Blocked sites stay blocked in split screen or a pop-up window when another app opens beside them.
- An incoming call is no longer covered by the block screen.

## 0.16.0 (versionCode 26) — 2026-10-28 업로드 (내부·테스터들)

AAB: `release/chronolock-0.16.0-vc26.aab`. Pro 구분은 꺼진 빌드.

이번에 바뀐 것 (내부 기록):
- 앱에서 접근성·사용 정보 접근 설정을 열 때 설정을 별도 작업(NEW_TASK)으로 띄움. 이전에는 설정 화면이 ChronoLock 작업 위에 남아
  홈 아이콘으로 앱을 열면 앱 대신 접근성 설정이 다시 보였음(2026-10-28 태블릿에서 발견).
- 히스토리 캐릭터 기준 완화(사용자 요청 "너무 빡빡해"): 하루 평균 1시간/2.5시간/5시간 → **2시간/4시간/7시간**.
  2시간 미만 여유(성공 포즈), 4시간 미만 좋음(기본), 7시간 미만 놀람(경고), 그 이상 어지러움(한도). `moodFor`, `docs/MASCOT.md`.
- 확인할 것: 권한 설정을 연 뒤 홈 → 앱 아이콘으로 다시 열면 ChronoLock 화면이 바로 나오는지, 히스토리 캐릭터가 새 기준으로 바뀌는지.

### ko
- 권한 설정을 열었다가 돌아온 뒤 앱 아이콘을 누르면 설정 화면 대신 ChronoLock이 바로 열려요.
- 히스토리 캐릭터 기준을 넉넉하게 바꿨어요. 하루 평균 2시간 미만은 여유, 4시간 미만은 좋음, 7시간 미만은 놀람이에요.

### en
- After opening permission settings, tapping the app icon now opens ChronoLock instead of the settings screen.
- The History character is less strict: under 2 hours a day is relaxed, under 4 is fine, under 7 is surprised.

## 0.15.0 (versionCode 25) — 2026-10-27 업로드 (내부·테스터들)

AAB: `release/chronolock-0.15.0-vc25.aab`. Pro 구분은 꺼진 빌드.

이번에 바뀐 것 (내부 기록):
- PR #98: 히스토리 탭에 **최근 7일 vs 그 전 7일 막대 그래프** 추가. 요일별로 진한 막대(최근)·옅은 막대(일주일 전)를 나란히,
  위에 합계와 증감(%) 문구. 무료·프로 모두 보이고 기록이 없으면 숨김.
- PR #98: 엄격 모드로 대기 중인 앱·그룹·사이트 변경의 "적용 예정" 시각도 부팅 시계 기준으로 표시(휴대폰 시계를 앞당겨도 빨라 보이지 않음).
- PR #98: 설정의 "분할 화면과 화면 속 화면은 아직 지원하지 않습니다" 안내를 "분할 화면·화면 속 화면에 띄운 앱도 제한합니다"로 수정
  (기능은 이전 수정부터 이미 동작).
- 확인한 결과 변경 불필요: 홈 위젯은 화면이 켜져 있는 동안 1초마다 다시 그려져 잠금·집중 종료가 곧바로 반영됨.
- 확인할 것: 히스토리 그래프 모양(다크·라이트), 엄격 모드 대기 문구, 분할 화면에서 제한 앱 차단.

### ko
- 히스토리에서 최근 7일과 그 전 7일을 요일별 막대 그래프로 비교할 수 있어요.
- 엄격 모드 대기 중인 변경의 적용 시각이 휴대폰 시계를 바꿔도 정확하게 보여요.
- 분할 화면·화면 속 화면에 띄운 앱도 제한된다는 안내로 바꿨어요.

### en
- History compares the last 7 days with the 7 before in a day-by-day bar chart.
- Changes waiting under strict mode show the right apply time even if the phone clock changes.
- Settings now says apps in split screen and picture-in-picture are limited too.

## 0.14.0 (versionCode 24) — 2026-10-27 업로드 (내부·테스터들)

AAB: `release/chronolock-0.14.0-vc24.aab`. Pro 구분은 꺼진 빌드.

이번에 바뀐 것 (내부 기록):
- PR #95: 설정 맨 아래에 앱 버전 줄("ChronoLock 0.14.0") 추가.
- PR #95: 버전 줄을 7번 누르면 숨은 **프로 코드** 입력칸이 열림. 공용 코드 하나로 여러 명이 기기마다 프로를 열 수 있음
  (앱에는 코드 해시만 저장, 대소문자·공백·하이픈 무시). 코드로 연 프로는 프로 카드·광고 카드에 "코드로 프로를 사용하고 있어요."로 표시.
  지금은 Pro 구분이 꺼져 있어 체감 변화 없음. 숨은 기능이라 공개 노트에는 적지 않음.
- 확인할 것: 설정 맨 아래 버전 표시, 7번 탭 → 코드 칸, 틀린 코드면 "코드가 맞지 않습니다.", 맞는 코드면 안내 문구로 바뀌는지.

### ko
- 설정 맨 아래에서 지금 쓰는 앱 버전을 확인할 수 있어요.

### en
- The bottom of Settings now shows which app version you have.

## 0.13.0 (versionCode 23) — 2026-10-27 업로드 (내부·테스터들)

AAB: `release/chronolock-0.13.0-vc23.aab`. 0.11.0 이후 첫 출시(0.12.0은 올리지 못함): PR #85(알림 권한 안내), #89(집중 부팅 시계·위젯 상태 줄),
#90~#92(엄격 대기·잠시 쉬기·표시를 부팅 시계로), #93(상태바 아이콘). **Room v11→v12 마이그레이션** → 업데이트 설치 후 규칙 유지 확인. Pro 구분은 꺼진 빌드.

### ko
- 상태바에 흰 네모 대신 시계 아이콘이 보여요. 앱을 쓰는 동안엔 남은 시간이 링으로 줄어들어요.
- 앱을 쓰지 않을 땐 상태바에 아무것도 남지 않아요.
- 홈 위젯에 잠금·집중 상태가 보여요.
- 휴대폰 시계를 바꿔도 집중·잠금·엄격 모드 대기·잠시 쉬기가 앞당겨지지 않아요.
- 접근성 권한을 켤 때 알림 허용도 함께 물어봐요.

### en
- The status bar shows a clock instead of a white square, with a ring counting down while you use a limited app.
- Nothing stays in the status bar when you are not using one.
- The home widget shows lock and focus status.
- Changing the phone clock no longer cuts short focus, locks, strict-mode waits or pauses.
- Turning on accessibility now asks for notifications too.

## 0.12.0 (versionCode 22) — 올리지 않음(2026-10-27 403, 0.13.0에 포함)

AAB: `release/chronolock-0.12.0-vc22.aab`. 0.11.0 이후 PR #85(알림 권한 안내). Room v11 그대로. Pro 구분은 꺼진 빌드.

### ko
- 접근성 권한을 켤 때 알림 허용도 함께 물어봐요.
- 알림을 켜면 쓰고 있는 앱의 남은 시간과 한도 5분 전 알림을 받을 수 있어요.
- 처음 시작할 때 알림이 왜 필요한지 안내해요.

### en
- Turning on accessibility now asks for notifications too.
- With notifications on, see the time left in the app you are using and get a reminder 5 minutes before a limit.
- The first-run guide explains what notifications are for.

## 0.11.0 (versionCode 21) — 2026-10-27 API 업로드(internal·테스터들)

AAB: `release/chronolock-0.11.0-vc21.aab`. 0.10.0 이후 PR #74·#76·#78·#80(QA 수정, 리뷰 요청·추천, 애니메이션 절전, PiP). Room v11 그대로. Pro 구분은 꺼진 빌드.

### ko
- 화면을 껐다 켜서 같은 앱으로 돌아오면 새로 연 것으로 세지 않아요.
- 그룹에만 넣은 앱도 집중 모드에서 막혀요. 한글 주소 사이트도 차단돼요.
- 화면 속 화면(PiP)으로 띄운 앱이 제한에 걸려도 차단 화면에 갇히지 않아요.
- 캐릭터 움직임을 다듬어 배터리를 덜 써요.
- 친구에게 추천하기 버튼이 생겼어요.

### en
- Unlocking back into the same app no longer counts as a new open.
- Apps only in a group are blocked during focus, and non-Latin domains can be blocked.
- A limited app in picture-in-picture no longer traps you behind the block screen.
- The character's motion uses less battery.
- New: recommend ChronoLock to a friend.
## 0.10.0 (versionCode 20) — 2026-10-26 API 업로드

AAB: `release/chronolock-0.10.0-vc20.aab`. 0.9.0 이후 PR #63~#71(카카오톡·네이버 앱 내 브라우저 사이트 차단, 고지 문구, 캐릭터 잘림 복원, 숨쉬기·깜빡임). Room v11 그대로.
올리기 전에 개인정보처리방침 게시본 교체와 접근성 API 신고 문구 수정(`docs/POLICY_AND_PAYMENTS_2026-10-26.md` 1장)을 함께 한다.

### ko
- 카카오톡·네이버 앱 안에서 연 페이지도 웹사이트 차단에 걸려요.
- 잘려 보이던 캐릭터 그림을 다시 다듬었어요.
- 캐릭터가 숨 쉬고 눈을 깜빡이며 더 살아 있는 것처럼 움직여요. 설정의 '움직이는 효과 줄이기'로 멈출 수 있어요.
- 웹사이트 차단과 권한 안내를 실제 동작에 맞게 고쳤어요.

### en
- Website blocking now also covers pages opened inside KakaoTalk and NAVER.
- Character art that looked clipped has been redrawn.
- Your buddy breathes and blinks to feel more alive. Turn it off with Reduce motion in Settings.
- Website blocking and permission notes now match how they work.

### ja
- KakaoTalk・NAVERアプリ内で開いたページもウェブサイトのブロック対象になりました。
- 切れて見えていたキャラクターの絵を直しました。
- キャラクターが呼吸してまばたきし、より生き生きと動きます。設定の「動きを減らす」で止められます。
- ウェブサイトのブロックと権限の説明を実際の動作に合わせました。

### zh-CN
- 在 KakaoTalk 和 NAVER 应用内打开的页面现在也会被网站拦截。
- 修复了被裁切的角色图片。
- 角色会呼吸和眨眼，显得更生动。可在设置中用“减少动态效果”关闭。
- 网站拦截和权限说明已与实际行为一致。

### es
- El bloqueo de sitios ahora también cubre las páginas abiertas dentro de KakaoTalk y NAVER.
- Redibujamos el arte de los personajes que se veía recortado.
- Tu compañero respira y parpadea para sentirse más vivo. Desactívalo con Reducir movimiento en Ajustes.
- Las notas del bloqueo de sitios y de permisos ahora coinciden con su funcionamiento.

## 0.9.0 (versionCode 19) — 2026-10-24 업로드(사용자)

AAB: `release/chronolock-0.9.0-vc19.aab`. 0.8.5 이후 PR #46~#59(1년 기록·기록 부족 안내·히스토리에서 제한·그룹 시간대·사용 중 알림·위젯 캐릭터·움직임 줄이기·엄격 6/24시간·테마 이름·번역). Room v11.
0.8.4·0.8.5를 올리지 않았다면 0.9.0만 올리고, 500자 안에서 0.8.4(색상환·움직이는 테마) 한 줄을 더해 쓴다. 유료 구분(PRO_GATING)은 꺼진 빌드라 6·24시간 대기도 모두 쓸 수 있다.

### ko
- 함께할 캐릭터: 수달·흰너구리·햄스터·토끼·북극여우가 상황마다 다른 표정으로 오늘 화면·차단 화면·위젯에 나와요.
- 기록을 1년까지 모아요. 기록이 모자란 기간은 지어내지 않고 캐릭터가 며칠 모였는지 알려 줘요.
- 히스토리에서 바로 앱 제한을 걸 수 있어요.
- 앱 그룹 차단 시간대, 한 앱은 그룹 하나에만.
- 제한한 앱을 쓰는 동안 알림창 위쪽에 '사용 중'과 남은 한도가 보여요.
- 엄격 모드 6시간·24시간 대기, 움직이는 효과 줄이기.
- 테마 이름과 번역을 다듬었어요.

### en
- Pick a buddy (otter, white raccoon, hamster, rabbit or arctic fox) that reacts on Today, the block screen and the widget.
- History keeps a year. Missing days are never made up; your buddy says how many are in.
- Set a limit straight from History.
- Block hours for app groups; each app belongs to one group.
- A limited app in use shows "in use" and what is left in the notification.
- 6- and 24-hour strict waits, and Reduce motion.
- Polished theme names and translations.

### ja
- キャラクターを選べます：カワウソ・白いアライグマ・ハムスター・ウサギ・ホッキョクギツネが今日の画面・ブロック画面・ウィジェットで表情を変えます。
- 記録を1年間保存。足りない期間は作らず、何日分集まったかをキャラクターがお知らせします。
- 履歴からすぐにアプリを制限できます。
- アプリグループのブロック時間帯。1つのアプリは1つのグループに。
- 制限中のアプリを使っている間、通知の上の方に「使用中」と残りを表示します。
- 厳格モードの6時間・24時間の待ち時間、動きを減らす設定。
- テーマ名と翻訳を見直しました。

### zh-CN
- 选择陪伴角色：水獭、白浣熊、仓鼠、兔子、北极狐会在今日、拦截画面和小部件中变换表情。
- 记录最多保存1年。记录不足的时段不会编造，角色会告诉你已收集几天。
- 可直接在历史记录中限制应用。
- 应用组拦截时段；每个应用只能加入一个组。
- 使用受限应用时，通知栏上方显示“正在使用”和剩余上限。
- 严格模式新增6小时和24小时等待，以及“减少动态效果”。
- 优化了主题名称和翻译。

### es
- Elige un compañero (nutria, mapache blanco, hámster, conejo o zorro ártico) que reacciona en Hoy, el bloqueo y el widget.
- El historial guarda un año. Nunca inventamos días que faltan; tu compañero dice cuántos hay.
- Pon límites desde el Historial.
- Horas de bloqueo para grupos; cada app en un solo grupo.
- Al usar una app con límite, la notificación muestra "en uso" y lo que queda.
- Esperas estrictas de 6 y 24 horas, y Reducir movimiento.
- Nombres de temas y traducciones mejorados.

## 0.8.5 (versionCode 18) — 미업로드

AAB: `release/chronolock-0.8.5-vc18.aab`. 0.8.4 + 캐릭터 5종·그룹 한 곳 소속/그룹 차단 시간대·사용 중 알림(PR #44) + 번역 검수(PR #43). DB v10.
0.8.4를 아직 안 올렸다면 0.8.5만 올리고 0.8.4 노트를 합쳐 쓴다.

### ko
- 함께할 캐릭터를 골라요: 수달·흰너구리·햄스터·토끼·북극여우가 상황마다 다른 표정으로 함께해요.
- 앱 그룹에 차단 시간대를 정해 그룹 전체를 한 번에 막을 수 있어요.
- 한 앱은 그룹 하나에만 들어가도록 바뀌었어요.
- 제한한 앱을 쓰는 동안 알림창 위쪽에 '사용 중'과 남은 한도가 보여요.
- 영어·일본어·중국어·스페인어 번역을 다듬었어요.

### en
- Pick a buddy: an otter, white raccoon, hamster, rabbit or arctic fox reacts to how your day is going.
- App groups can have block hours that stop every app in the group at once.
- Each app now belongs to one group only.
- While you use a limited app, the notification shows it is in use and what is left, near the top.
- Polished the English, Japanese, Chinese and Spanish translations.

### ja
- 一緒にいるキャラクターを選べます：カワウソ・白いタヌキ・ハムスター・ウサギ・ホッキョクギツネが状況に合わせて表情を変えます。
- アプリグループにブロック時間帯を設定し、グループ全体をまとめて止められます。
- 1つのアプリは1つのグループにだけ入るようになりました。
- 制限中のアプリを使っている間、通知の上の方に「使用中」と残りの上限を表示します。
- 英語・日本語・中国語・スペイン語の翻訳を見直しました。

### zh-CN
- 选择陪伴你的角色：水獭、白浣熊、仓鼠、兔子、北极狐会随情况变换表情。
- 应用组可设置屏蔽时段，一次屏蔽组内所有应用。
- 每个应用现在只能加入一个组。
- 使用受限应用时，通知栏上方会显示“正在使用”和剩余上限。
- 优化了英文、日文、中文和西班牙文翻译。

### es
- Elige un compañero: una nutria, un mapache blanco, un hámster, un conejo o un zorro ártico reaccionan a tu día.
- Los grupos de apps pueden tener horas de bloqueo que detienen todas sus apps a la vez.
- Ahora cada app pertenece a un solo grupo.
- Mientras usas una app con límite, la notificación muestra arriba que está en uso y lo que queda.
- Se pulieron las traducciones al inglés, japonés, chino y español.

## 0.8.4 (versionCode 17) — 미업로드

AAB: `release/chronolock-0.8.4-vc17.aab`. 0.8.3 + 홈 이동 이벤트 누락 보정(PR #39) + 나만의 테마 색상환·효과 조합·움직이는 테마·1년 기록 월/요일. 0.8.3 검토가 끝나기 전에 올리면 0.8.3 노트와 합쳐 쓴다.

### ko
- 나만의 테마: 색상환을 끌어 색을 고르고, 효과를 여러 개 섞을 수 있어요.
- 무지개·오로라처럼 천천히 움직이는 테마를 추가했어요.
- 1년 기록에 월과 요일을 표시해요.
- 홈으로 나간 뒤에도 앱 사용 시간이 계속 늘어날 수 있던 문제를 고쳤어요.

### en
- My theme: drag on a color wheel and combine several effects.
- New moving themes such as rainbow and aurora.
- The year view now shows months and weekdays.
- Fixed usage time that could keep counting after going back to the home screen.

### ja
- マイテーマ：カラーホイールで色を選び、複数の効果を組み合わせられます。
- 虹やオーロラのようにゆっくり動くテーマを追加しました。
- 1年間の記録に月と曜日を表示します。
- ホームに戻った後も利用時間が増え続けることがある問題を修正しました。

### zh-CN
- 我的主题：拖动色轮选色，并可组合多种效果。
- 新增彩虹、极光等缓慢流动的动态主题。
- 一年记录现在显示月份和星期。
- 修复了返回主屏幕后使用时长仍可能继续累计的问题。

### es
- Mi tema: arrastra en la rueda de color y combina varios efectos.
- Nuevos temas en movimiento, como arcoíris y aurora.
- La vista anual ahora muestra los meses y los días de la semana.
- Se corrigió el tiempo de uso que podía seguir contando tras volver a la pantalla de inicio.

## 0.8.3 (versionCode 16) — 2026-10-23 내부·비공개 테스트 업로드(사용자)

AAB: `release/chronolock-0.8.3-vc16.aab`. 0.8.2 + 다른 AI 리뷰 반영(안정성·정책 리뷰 수정, 제품 리뷰 상위 3개). 0.8.2를 아직 안 올렸다면 0.8.3만 올리고 0.8.2 노트와 합쳐 쓴다.

### ko
- 열기 전 멈춤: 앱을 열 때 몇 초 숨 고르는 화면을 보여 줘요.
- 사용 중 확인: 정한 시간마다 계속 쓸지 물어봐요.
- 처음 시작할 때 줄이고 싶은 것을 고르면 맞춤 제한을 추천해요.
- 차단 화면이 더 부드러워지고 오늘 참은 횟수를 보여 줘요.
- 분할 화면·팝업 창으로 제한을 피하던 문제와 감시가 멈출 수 있던 문제를 고쳤어요.

### en
- Pause before opening: a short breathing screen when you open an app.
- Check-in while using: asks at set times whether to keep going.
- Pick what to cut down on at first run and get suggested limits.
- A kinder block screen that shows how often you held back today.
- Fixed bypassing limits with split screen or pop-up windows, and monitoring that could stop.

## 0.8.2 (versionCode 15) — 미업로드

AAB: `release/chronolock-0.8.2-vc15.aab`. 0.8.1 + 개선(주간 리포트·추천 행동·사이트 묶음·2열 설정·낮/밤 테마·테마 바로가기·랜덤).

### ko
- 기기 다크 모드에 맞춰 낮·밤 테마가 자동으로 바뀌어요.
- 테마 화면에 분류 바로가기와 랜덤 버튼을 넣었어요.
- 일요일 저녁에 이번 주 사용 리포트를 알려 드려요.
- 웹사이트 차단에 SNS·영상·쇼핑·커뮤니티 묶음 추가를 넣었어요.
- 차단 화면에서 대신 할 일을 추천해요. 큰 화면에서는 설정이 2열로 보여요.

### en
- Day and night themes follow the device's dark mode.
- Theme gallery shortcuts and a random pick.
- A weekly report every Sunday evening.
- Add site sets: social, video, shopping, communities.
- The block screen suggests something else to do; two-column settings on large screens.

## 0.8.1 (versionCode 14) — 2026-10-23 내부 테스트 업로드(사용자)

AAB: `release/chronolock-0.8.1-vc14.aab`. 0.8.0 기능 + QA 수정(docs/QA_REPORT_2026-10-23.md). 0.8.0을 아직 비공개 테스트에 안 올렸다면 0.8.1을 올리고 아래 0.8.0 노트와 합쳐 쓴다.

### ko
- 잠금 모드를 더 단단하게 고쳤어요(빠른 설정 타일·시계 변경에도 유지).
- 목표 잠금에서 앱을 쓰지 않은 날도 성공으로 세요.
- 오늘 화면에서 잠금 상태를 바로 볼 수 있어요.
- 백업 복원 오류와 알림이 남던 문제를 고쳤어요.

### en
- Lock mode is sturdier (quick settings tile, clock changes).
- Goal locks count days without using the apps as kept.
- The Today screen shows when a lock is on.
- Fixed a backup restore error and a notification that could stay.

## 0.8.0 (versionCode 13) — 2026-10-23 내부 테스트 업로드(사용자)

AAB: `release/chronolock-0.8.0-vc13.aab`. 업로드 전 접근성 API 신고(웹사이트 차단 추가)·시연 영상·개인정보처리방침 갱신 필요.

### ko
- 웹사이트 차단: Chrome·삼성 인터넷 등에서 정한 사이트를 열면 차단 화면을 띄워요.
- 잠금 모드: 정한 기간이나 목표를 이룰 때까지 제한을 풀 수 없게 잠가요. 조각 비밀번호도 있어요.
- 제한한 앱을 쓰는 동안 남은 시간·실행 횟수를 알림으로 보여 줘요.
- 기록을 3개월 보관하고 1년 달력을 보여 줘요. 날짜를 누르면 그날 앱별 사용이 나와요.
- 기록 삭제는 직접 정한 비밀번호로만 할 수 있어요.
- 한 앱을 여러 그룹에 넣고, 앱을 한 번에 선택할 수 있어요.
- 나만의 테마(색상 코드+효과)와 테마 찜을 추가했어요.
- 앱 안에서 바로 업데이트할 수 있어요.
- 앱 크기를 줄이고 더 빠르게 열리도록 최적화했어요.

### en
- Website blocking in Chrome, Samsung Internet and more.
- Lock mode: lock limits for a set time or until you reach a goal.
- A live notification shows time and opens left.
- 3 months of history and a yearly calendar; tap a day for its apps.
- Deleting history needs a password you set.
- Apps can join several groups; select apps all at once.
- Custom color themes and favorites.
- Update right inside the app.
- Smaller and faster to open.

## 0.7.0 (versionCode 12) — 2026-10-22

AAB: `release/chronolock-0.7.0-vc12.aab` (0.6.3을 올리지 않았다면 0.6.3 변경도 포함됨)
비공개 테스트 업로드: 2026-10-23 (사용자)

### ko
- 엄격 모드: 제한을 끄거나 느슨하게 바꾸면 정한 시간(10분~1시간) 뒤에 적용돼요.
- 잠시 쉬기: 10분·30분·1시간·오늘만 쉬고 자동으로 다시 켜져요.
- 예약을 앱마다 여러 구간(매일·평일·주말)으로 설정할 수 있어요.
- 앱 묶음: 여러 앱이 하루 시간과 실행 횟수를 함께 써요.
- 빠른 설정 타일로 바로 집중을 시작해요.
- 연속으로 한도를 지킨 날과 지난주 대비 사용량을 보여 줘요.
- 규칙과 설정을 파일로 백업하고 복원할 수 있어요.
- 새 제한을 만들 때 하루 허용 시간이 1시간으로 미리 채워져요.

### en
- Strict mode: turning off or relaxing a limit waits 10 min–1 h.
- Take a break: pause a limit for 10/30/60 min or today; it resumes by itself.
- Multiple schedule windows per app (every day, weekdays, weekends).
- App groups: several apps share one daily time and open count.
- Start a focus from a Quick Settings tile.
- See your streak within limits and the change from last week.
- Back up and restore your limits and settings with a file.
- New limits start with a 1-hour daily time.

## 0.6.3 (versionCode 11) — 2026-10-22

AAB: `release/chronolock-0.6.3-vc11.aab`

### ko
- 세부 기록이 없는 이전 날짜도 기기 사용 통계로 추정해 한 달 기록을 채워요(추정 시간은 따로 표시돼요).
- 접근성 서비스가 다시 시작될 때 연결 끊김으로 잘못 표시되던 문제를 고쳤어요.

### en
- History now fills older days of the month with estimates from your device's usage stats (marked as estimates).
- Fixed the accessibility service sometimes showing as disconnected after it restarted.

## 0.6.2 (versionCode 10) — 2026-10-22

AAB: `release/chronolock-0.6.2-vc10.aab`

### ko
- 최신 Android 개발 도구와 라이브러리로 업데이트해 안정성과 호환성을 높였어요.
- 앱 목록에서 정렬을 바꿀 때 다른 앱 아이콘이 잠깐 보이던 문제를 고쳤어요.

### en
- Updated to the latest Android build tools and libraries for better stability and compatibility.
- Fixed app icons briefly showing the wrong app after changing the list order.

## 0.6.1 (versionCode 9) — 2026-10-22

AAB: `release/chronolock-0.6.1-vc9.aab`

### ko
- 키보드나 권한 창을 띄워도 사용 시간이 끊기지 않도록 고쳤어요.
- 화면 잠금을 풀었을 때 보던 앱을 다시 정확히 인식해요.
- 히스토리 추천 앱 잠금이 레시피 값을 채운 채로 열려요.
- 오래 쓴 기록도 히스토리가 더 가볍게 열리고, 어두운 테마의 버튼 테두리가 더 잘 보여요.
- 광고 제거 구독자는 앱을 열 때 광고가 잠깐 보이던 문제를 고쳤어요.

### en
- Usage keeps counting while a keyboard or permission prompt is open.
- The app you were using is recognized again after unlocking.
- Suggested limits in History open with the recipe filled in.
- History opens faster with long records; button outlines are clearer in dark themes.
- Ad-free subscribers no longer see a brief banner at launch.

## 0.6.0 (versionCode 8) — 2026-10-22

AAB: `release/chronolock-0.6.0-vc8.aab`

### ko
- 앱 제한 목록을 이름순·사용량순·최근 사용순으로 정렬할 수 있어요. 최근 7일 사용 시간과 실행 횟수도 함께 보여요.
- 아이콘이 숨겨진 앱도 목록에서 아이콘이 보이도록 고쳤어요.
- 테마 110종과 테마 갤러리를 추가했어요. 파스텔·그라데이션·투톤 테마를 골라 보세요.
- 히스토리에 사용량에 반응하는 햄스터 마스코트와 추천 앱 잠금이 생겼어요. 많이 쓴 앱에 바로 제한을 걸 수 있어요.
- Pretendard 글꼴과 글자 체계를 정리해 화면이 더 또렷해졌어요.
- 설정의 결제 상태 표시 오류와 접근성 권한 안내를 개선했어요.

### en
- Sort the app limits list by name, usage or last used, with each app's 7-day usage and open count.
- Apps that hide their icon now show one in the list.
- 110 themes and a theme gallery: pastel, gradient and two-tone palettes.
- History gets a hamster mascot that reacts to your usage, plus suggested limits for your most-used apps.
- Pretendard typography for a crisper UI.
- Fixed the billing status message in Settings and clarified the accessibility setup guide.

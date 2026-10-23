# Play Console 출시 노트

각 항목은 Play Console "새로운 기능"(언어별 500자 이내)에 그대로 붙여 넣는다.

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
- Custom colour themes and favourites.
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

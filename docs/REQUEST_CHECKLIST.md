# 사용자 요청 이행 점검표 (2026-10-23)

판정: ✅ 구현·검증 완료 / 🟡 구현했으나 사용자 작업·결정 대기 / ⏳ 사용자 자료 대기.
근거의 테스트 이름은 `android/` 아래 테스트 클래스, "실기기"는 갤럭시 탭 S9+(Android 16) 확인.

| # | 요청 | 판정 | 근거 |
|---|---|---|---|
| 1 | 새 규칙 기본 하루 시간 1시간 | ✅ | RuleDraftTest.newRule_defaultsToOneHourDaily |
| 2 | 영어 복수형 정리 | ✅ | LocalizationTest.countedMessages_followPluralRules, lint PluralsCandidate 0 |
| 3 | PR 병합·배포 직전까지 자동 진행 | ✅ | PR #15~#31 병합 |
| 4 | 웹사이트 차단 | ✅ | SiteBlockTest, SiteBlockingDeviceTest(Chrome·삼성 인터넷, 실기기), R8 빌드 실기기 확인 |
| 5 | 테마 "+ 더보기"를 카드 오른쪽 위로 | ✅ | FocusUiTest.themePicker_switchesEveryTheme |
| 6 | 앱 안 업데이트 메시지 | ✅ | InAppUpdates + MainActivityLaunchTest(시작 크래시 수정 확인). 실제 업데이트 창은 Play 설치본 다음 버전부터 |
| 7 | 테마 이름·색 불일치 수정 | ✅ | ThemePaletteTest.trendNames_startWithThePageColour, 이름 중복·번역 검사 |
| 8 | 자주 쓰는 테마 찜 | ✅ | FocusUiTest.customTheme_..._andFavoritesCanBePinned, ThemePreferencesTest.favoriteThemes_toggleAndPersist |
| 9 | 나라별 이름(스토어) | ✅ | docs/store/STORE_LISTING_intl.md(en/ja/zh/es, 길이 확인) |
| 10 | 시연 영상(동의 장면 포함) | ✅ | docs/store/chronolock-a11y-demo-0.8.0-ko.mp4(실기기 녹화, 프레임 확인) |
| 11 | 일별 기록 날짜 누르면 그날 사용 | ✅ | HistoryUiTest.tappingADay_listsThatDaysApps |
| 12 | 테마 RGB 000000~FFFFFF + 효과 | ✅ | ThemePaletteTest.customThemes_stayReadable...(55색×10효과), FocusUiTest |
| 13 | 기록 3개월 보관·자동 삭제 | ✅ | HistoryRepositoryTest.dayRows_outliveTheirEvents_forNinetyDays... |
| 14 | 기록 삭제는 직접 정한 비밀번호로만 | ✅ | HistoryUiTest.deletingHistory_firstSetsAPassword..., LockPreferencesTest |
| 15 | 모든 규칙 초기화(기록 유지) | ✅ | LocalRepositoryTest.resetRules_removesRulesGroupsAndSites_butKeepsTodaysUsage |
| 16 | 1년 달력 | ✅ | HistoryUiTest(year-calendar), HistoryRepositoryTest(year 테이블), MigrationTest v7→8 |
| 17 | "묶음" → "그룹" | ✅ | 코드·한국어 리소스 일괄 치환, GroupEditorUiTest 문구 |
| 18 | 한 앱을 여러 그룹에 | ✅ | AppGroupTest.appInTwoGroups_isBlockedWhenEitherIsUsedUp, LocalRepositoryTest |
| 19 | 앱 일괄 선택 + 경고 | ✅ | GroupEditorUiTest.selectAll_warnsFirst_thenSelectsEverySearchResult |
| 20 | 그룹 이름 오류가 맨 아래 뜨는 버그 | ✅ | GroupEditorUiTest.savingWithoutName_showsTheErrorAtTheTop... |
| 21 | 제한 푸는 방법 상세 안내 | ✅ | 설정 "제한을 풀고 싶을 때" 8단계(HelpUi.kt), 5개 언어 라벨 대조 |
| 22 | 실시간 알림(남은 초·횟수·설정 정보) | ✅ | LiveLimitTest, SiteBlockingDeviceTest.limitedApp_showsALiveNotification(실기기) |
| 23 | 재설정 일정 시간 절대 불가(기간 잠금) | ✅ | LocalRepositoryTest.lock_refusesEveryLoosening..., LockClockTest, QA 수정(타일·시계) |
| 24 | 목표 달성 후 비밀번호로 해제 | ✅ | LockSettingTest, LockPreferencesTest.ownGoalPassword_canBeChanged_andThenUnlocks |
| 25 | 비밀번호 5회 실패 10분 정지 | ✅ | LockPreferencesTest.fiveWrongTries..., movingTheClockForward_doesNotLiftAPinBlock |
| 26 | 언제든 PIN 확인 후 재설정 | ✅ | LockPreferencesTest.passwordChanges_needTheCurrentOne |
| 27 | 조각 비밀번호(하루 한 자리) | ✅ | LockPreferencesTest.pieceCode_..., GroupEditorUiTest.lockCard_... |
| 28 | 유료/무료 구분 | 🟡 | 코드 완성·꺼짐(PRO_GATING). Play Console 상품 `chronolock_pro_lifetime` 등록 후 켬 |
| 29 | 토스페이먼츠·통신판매업 안내 | ✅ | docs/GROWTH_AND_MONETIZATION.md 1절 |
| 30 | 무료 홍보(ASO·커뮤니티·숏폼) | ✅/🟡 | STORE_LISTING_ko 제목·설명 반영, SHORTS_SCRIPTS.md. 촬영·게시는 사용자 |
| 31 | 캐릭터 10단계(기간별 기준) | ⏳ | docs/MASCOT.md 3절 제안표. 초안 PNG 대기 |
| 32 | DEX 최적화·사용자 환경·화면 비율 | ✅/🟡 | R8+profileinstaller(AAB 15.8→10.7MB), 가로/세로 실기기 확인. Play 경고 원문은 미확인 |
| 33 | 전문가 관점 QA·평가표·개선안 | ✅ | docs/QA_REPORT_2026-10-23.md, 개선안 4개 구현(PR #31) |
| 34 | 인터넷 없이 동작 여부 | ✅ | 답변: 핵심 기능 전부 오프라인. 광고·로그인·결제만 네트워크(릴리스에서 꺼짐) |
| 35 | 테스터에게 업데이트가 안 보임 | 🟡 | 원인·대처 안내. 태블릿 계정은 비공개 트랙 → 비공개 반영 후 확인 |
| 36 | 앱 캠페인 크레딧 확인 | 🟡 | 연결된 메일함에 관련 메일 없음. 배너 문구 전달 시 재확인 |
| 37 | 다른 것부터 진행(대기 중 작업) | ✅ | QA 개선안 4개(PR #31), 테마 개선(PR 이번) |
| 38 | UI·테마 개선 | ✅ | 낮/밤 테마(기기 다크 모드), 갤러리 바로가기·랜덤, 2열 설정 — ThemeUiTest(2), 실기기 화면 확인 |

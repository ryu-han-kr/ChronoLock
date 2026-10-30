# ChronoLock 제출 자료

## 시연 영상

- 파일: `chronolock-accessibility-demo-ko.mp4`
- 업로드 제목 초안: `ChronoLock — 접근성 API 사용 및 앱 제한 기능 시연`
- 공개 범위: YouTube 일부 공개. 2026-10-21 업로드·저장 완료.
- URL: https://www.youtube.com/watch?v=m6K2g4ciAqo (Shorts 링크: https://youtube.com/shorts/m6K2g4ciAqo)
- 설명 초안: `ChronoLock의 접근성 사용 고지와 동의 후 Android 설정 이동, 활성화된 접근성 서비스, Chrome 하루 1분 제한 규칙, 사용 한도 도달 시 차단 화면을 보여주는 시연입니다.`
- 약 154초, 1080×1920. 2026-10-21 전체 432프레임 순차 디코딩 성공.
- 0초: 앱 설정의 권한 상태.
- 약 30초: 접근성 사용 목적·데이터 처리 고지 및 동의 버튼.
- 약 56~62초: Android 접근성 목록과 켜진 ChronoLock 서비스 상세.
- 약 88~106초: 앱 목록과 Chrome 하루 1분 규칙.
- 약 119초 이후: Chrome 사용 한도 도달 차단 화면.
- 참고: 이미 켜진 서비스 상태를 보여준다. 꺼짐→켜짐 전환을 새로 수행한 영상은 아니다.
- 기존 `recovered-final-20261020.mp4`는 moov atom 누락으로 손상됐다. 사용하지 않는다.

## 스토어 스크린샷

현재 게시본(2026-10-30, 0.20 UI, 5개 언어 각 5장): `docs/store/screenshots-0.20/{ko,en,ja,zh,es}/`
1 오늘 · 2 첫 주 맞춤 한도·휴대폰 전체 한도 · 3 YouTube 차단 화면 · 4 히스토리 총 사용 시간 · 5 앱별 기록.
태블릿 SM-X810을 `wm size 1080x1920`로 바꿔 촬영, 상태바·내비바는 잘라냄(1080×1697). 게시: `play_api.py set-screenshots docs/store/screenshots-0.20`.
아래 8장 한국어 세트는 이전 게시본(0.6.1 UI)으로 기록용.

권장 순서(2026-10-22 0.6.1 UI로 전부 교체, 먹 테마, 에뮬레이터 1080×1920 실제 화면, 24비트 RGB PNG, 8장):
1. `screenshot-1-today-ko.png` — 오늘 사용량 1시간 7분, 허용 15회·차단 2회, 지금부터 집중
2. `screenshot-2-limits-ko.png` — 내 앱 제한 카드(Chrome·Photos 진행 바, 남은 시간, 제한 토글)
3. `screenshot-3-blocked-ko.png` — YouTube 일일 한도 도달 차단 화면
4. `screenshot-4-history-ko.png` — 히스토리 하루 요약과 햄스터 마스코트
5. `screenshot-5-suggest-ko.png` — 추천 앱 잠금(Gmail·Maps·Messages)과 앱별 사용 기록
6. `screenshot-6-editor-ko.png` — 추천에서 연 규칙 편집 시트(레시피 3종)
7. `screenshot-7-weekly-ko.png` — 최근 7일 돌아보기(지킨 날 점, 일별 막대, 제안)
8. `screenshot-8-themes-ko.png` — 테마 갤러리(110종)

캡처 방법: 에뮬레이터 emulator-5554(wm size 1080x1920), SystemUI demo 모드(9:30·배터리 100%), 비행기 모드로 광고 미로드.
데이터는 `.tools/shots/seed.py`로 앱 DB에 시드(스크린샷 전용 테스트 데이터). 히스토리는 앱 실행 시 원시 이벤트에서 재계산되므로
`history_events`에 RESUME/PAUSE 쌍을 넣는다. 테스트용 패키지(com.chronolock.verification 등)는 촬영 중 `pm disable-user`, 이후 `pm enable`.
검토 전송은 미실행. 최종 심사 적합성 확인 미완료.

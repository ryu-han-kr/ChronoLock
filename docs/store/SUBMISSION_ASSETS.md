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

권장 순서(2026-10-22 0.5.1 UI로 전부 교체, 에뮬레이터 1080×1920 실제 화면, 24비트 PNG):
1. `screenshot-today-ko.png` — 오늘 사용량 1시간 7분, 허용 15회·차단 2회, 집중 시작 버튼
2. `screenshot-limits-ko.png` — 내 앱 제한 카드(Chrome·Photos 진행 바, 남은 시간, 제한 토글)
3. `screenshot-blocked-ko.png` — YouTube 일일 한도 도달 차단 화면
4. `screenshot-editor-ko.png` — 규칙 편집 시트(집중 레시피 3종, 하루 허용 시간 입력)
5. `screenshot-weekly-ko.png` — 최근 7일 돌아보기(지킨 날 점, 일별 막대, 제안)
6. `screenshot-apps-ko.png` — 앱 제한 탭 전체 앱 목록(실제 앱 아이콘)

캡처 방법: `.tools/avd/ChronoLock_Test`(wm size 1080x1920 override), 상태 표시줄은 SystemUI demo 모드(9:30·배터리 100%),
광고 배너는 비행기 모드에서 앱을 재시작해 미로드, 규칙·7일 사용 데이터는 `databases/chronolock.db`에 시드(스크린샷 전용 테스트 데이터).
2026-10-21 Play Console 기본 스토어 등록정보에는 이전 0.2.0 UI 4장(today/blocked/limits/apps)이 저장돼 있음 → 위 6장으로 교체 필요(미수행).
검토 전송은 미실행. 최종 심사 적합성 확인 미완료.

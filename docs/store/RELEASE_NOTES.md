# Play Console 출시 노트

각 항목은 Play Console "새로운 기능"(언어별 500자 이내)에 그대로 붙여 넣는다.

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

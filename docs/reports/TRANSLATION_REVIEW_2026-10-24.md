# 번역 검토 보고서 (2026-10-24)

대상: `android/app/src/main/res/values-{en,ja,zh-rCN,es}/ui_strings.xml`(원문 `values/ui_strings.xml`, 키 약 630개),
`docs/store/STORE_LISTING_intl.md`, `docs/store/RELEASE_NOTES.md`. 한국어 원문·키·Kotlin은 바꾸지 않았다.

## 요약

| 언어 | 누락 키 | 자리표시자 불일치 | 앱 문자열 수정 | 스토어 문서 수정 |
|---|---|---|---|---|
| en | 0 | 0 | 10 | 등록정보 3곳(보관 기간, 잠금 모드·실시간 알림 절 추가), 출시 노트 영국식 철자 2곳 |
| ja | 0 | 0 | 10 | 등록정보 5곳(再起動 오역, 용어 통일, 보관 기간, 절 추가), 0.8.4 출시 노트 신규 |
| zh-CN | 0 | 0 | 11 | 등록정보 4곳(용어 통일, 보관 기간, 절 추가), 0.8.4 출시 노트 신규 |
| es | 0 | 0 | 16 | 등록정보 5곳(용어·지역어, 보관 기간, 절 추가), 0.8.4 출시 노트 신규 |

- 스크립트로 모든 언어의 키 집합, `%1$d`/`%1$s` 자리표시자, plurals 구조를 원문과 비교했다. 누락·불일치는 없었다.
- 스토어 글자 수 제한: 제목(최대 27자)·간단한 설명(최대 77자)·자세한 설명(각 3000자 미만)·0.8.4 출시 노트(각 500자 미만) 모두 범위 안이다.

## 주요 수정

| 언어 | 키/위치 | 이전 → 이후 | 이유 |
|---|---|---|---|
| ja | `recipe_short_hint`, `cooldown_seconds`, `overlay_cooldown`, 등록정보 | 再起動まで / 再起動待機 → 再び開くまで / 再度開くまでの待機 | 再起動은 '기기·앱 재부팅'이라는 뜻이라 '다시 열기 전 대기'를 잘못 전달함 |
| 전체 | 등록정보 개인정보 절 | History is kept for up to 30 days → 앱별 3개월·하루 합계 1년 | 한국어 등록정보·앱 안내(90일)와 사실이 맞지 않음 |
| 전체 | 등록정보 | 잠금 모드·실시간 알림 절 추가 | 한국어 등록정보(0.8.0)에 있는 기능이 번역본에 빠져 있었음 |
| ja/zh | `hours_input` | 時 / 时 → 時間 / 小时 | 입력칸 라벨에서 '시'는 시각(○시)으로 읽힘 |
| en/es | `count_times` | Count: %1$d / %1$d veces → %1$d× | 'Count:'는 기계적인 표현이고, es는 1일 때 '1 veces'로 문법이 틀림 |
| es | `fr_block_resisted`, `overlay_retry`, `focus_confirm` | %1$d veces / segundos / minutos → 숫자 단위 축약·구조 변경 | 1일 때 복수형 오류(plurals가 아닌 일반 문자열이라 문장 구조로 해결) |
| es | 집중 관련 6개 키 | Enfoque → Concentración | '집중' 번역어가 enfoque와 concentración으로 섞여 있어 다수 쪽으로 통일 |
| es | `site_preset_title` | Añadir un grupo → Añadir en bloque | '그룹'(앱 그룹) 용어와 혼동됨 |
| es | `weekly_report_first`, `mascot_surprised_2`, `site_preset_video`, `theme_word_beige`, 등록정보 | móvil / cachetes / Vídeo / Beis → teléfono / mejillas / Video / Beige | 스페인·중남미 한쪽에만 쓰는 지역어를 중립 스페인어로 변경 |
| zh | `pause_rule`, `unlock_step_1`, 등록정보 | 暂时休息 → 暂停一下 | 직역투. '잠시 쉬기'(제한 일시 정지) 의미를 버튼 길이에 맞게 표현 |
| zh | `ads_free`, `accessibility_disclosure` | 您 → 你 | 앱 전체가 你를 쓰는데 두 곳만 존댓말 |
| zh | `limit_channel`, `notify_permission_hint`, `pro_note` | 额度 → 上限, 账号 → 账户 | 앱 안의 다른 용어(上限·账户)와 통일 |
| zh | `recipe_sleep`, `live_title` | 遵守就寝时间 / 正受限制 → 按时睡觉 / 限制中 | 딱딱한 직역을 자연스러운 표현으로 변경 |
| en/ja/zh/es | `recommend_hint` | recipe / レシピ / 方案 / receta → preset / プリセット / 预设 / preajuste | 같은 기능의 화면 제목(Focus presets 등)과 용어가 달랐음 |
| ja | `theme_word_count` | テーマ %1$d 件 → テーマ%1$d種 | 件은 테마 수를 셀 때 어색함 |
| ja | `theme_word_selected`, `block_suggestion`, `focus_custom`, `limit_channel` | 반각 콜론 / どう？ / 指定 / 上限前 → 전각 콜론 / どうですか？ / カスタム / 上限間近 | 문장부호 통일, 존댓말 통일, 자연스러운 표현 |
| en | `no_records`, `overlay_retry`, `fr_checkin_hint`, `fr_pause_detail`, `weekly_report_title`, `mascot_surprised` | 'No record', 'Seconds until you can reopen: %1$d.' 등 | 문법 오류와 기계 번역투 정리 |
| en | `theme_custom_code`, `restricted_settings_hint`, 출시 노트 | Colour / greyed / favourites → Color / grayed / favorites | 앱이 쓰는 미국식 철자(gray, Favorites)로 통일 |

## 원어민 확인이 필요한 부분

1. **es '잠금 모드' = Modo bloqueo**: 앱 차단(bloqueo)과 같은 단어라 헷갈릴 수 있다. 'Modo candado', 'Bloqueo de cambios' 같은 다른 이름을 검토해 볼 만하다(키가 많아 이번에는 그대로 두었다).
2. **es 지역성**: Ajustes, Añadir, Melocotón, apunta 같은 스페인식 표현이 여전히 많다. 중남미 사용자가 많다면 Configuración, Agregar, Durazno 등으로 바꾸거나 `values-es-rUS`를 따로 두는 방안을 검토해야 한다.
3. **es 복수형**: `enabled_count`, `focus_active`, `app_count`, `usage_opens`, `limit_opens` 등은 일반 문자열이라 값이 1이면 '1 apps', '1 aperturas'로 나온다. 고치려면 Kotlin에서 plurals를 쓰도록 바꿔야 한다(이번 범위 밖).
4. **es `pause_rule`(Tomar un descanso)**와 `focus_start`/`focus_stop`(Iniciar/Terminar concentración)은 작은 화면에서 버튼이 길 수 있다. 실기기에서 줄바꿈되는지 확인이 필요하다.
5. **ja `ChronoLock プロ`**: 일본 앱은 보통 'Pro'를 영어 그대로 쓴다. 'ChronoLock Pro'로 바꿀지 확인이 필요하다.
6. **zh `暂停一下`**: 버튼과 안내문 용어를 통일했다. 일시 정지 기능을 가리키는 자연스러운 표현인지 확인이 필요하다.
7. **원문 불일치(번역 아님)**: `local_data_hint`(30일)와 `history_retention`/`usage_disclosure`(3개월)의 보관 기간이 서로 다르다. 한국어 원문부터 맞춰야 한다.
8. **출시 노트**: 0.8.3 이하 버전은 ko/en만 있다. ja/zh/es 노트는 다음 업로드인 0.8.4에만 추가했다.

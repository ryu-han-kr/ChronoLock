# 캐릭터(마스코트) 가이드

2026-10-24: 사용자가 그린 초안 5종(`photo/chronolock/*.png`, 원본 시트)을 잘라 앱에 넣었다.
캐릭터 5종 × 포즈 10개 = 이미지 50장, `android/app/src/main/res/drawable-nodpi/mascot_<캐릭터>_<포즈>.webp` (320×320, 투명 배경, 약 1.3MB).

## 1. 캐릭터와 포즈
| 캐릭터 (`MascotKind`) | 이름 | 원본 시트 |
|---|---|---|
| `OTTER` | 수달 | `40a9803d-….png` |
| `RACCOON` | 흰너구리 | `7beaede0-….png` |
| `HAMSTER` (기본값) | 햄스터 | `aba9bf22-….png` |
| `RABBIT` | 토끼 | `de5be1d4-….png` |
| `FOX` | 북극여우 | `eeea5e15-….png` |

포즈 (`Pose`, 시트 순서): 기본 `default` · 잠금 `lock` · 해제 `unlock` · 집중 `focus` · 타이머 `timer` ·
사용 제한 `limit` · 휴식 `rest` · 취침 `sleep` · 경고 `warning` · 성공 `success`.

## 2. 어디에 나오나
| 화면 | 포즈 |
|---|---|
| 첫 실행 환영 화면 | 기본 + 캐릭터 고르기 |
| 오늘 탭 요약 카드 (`todayMood`) | 잠금 → 잠금 / 집중 중 → 집중 / 한도 다 씀 → 사용 제한 / 10분 이내 → 경고 / 잠시 쉬기 → 휴식 / 23~6시 → 취침 / 제한 없음 → 기본 / 그 밖 → 성공, 한 줄 멘트 포함 |
| 지금부터 집중 카드 | 집중 중 → 집중, 아니면 타이머 |
| 잠금 모드 카드 | 잠금 중 → 잠금, 아니면 해제 |
| 히스토리 | 기록 없음 → 취침, 1시간 미만 → 성공, 2.5시간 미만 → 기본, 5시간 미만 → 경고, 그 이상 → 사용 제한 |
| 차단 화면 | 집중 → 집중, 예약 시간대 → 취침, 대기(쿨다운) → 타이머, 그 밖 → 사용 제한, 사이트 → 사용 제한, 숨 고르기 → 휴식, 사용 중 확인 → 경고 |
| 사용 중 알림 (큰 아이콘) | 남은 시간 5분 미만 → 경고, 아니면 타이머 |
| 설정 › 캐릭터 | 5종 고르기 + 고른 캐릭터의 포즈 10개 미리보기 |
| 홈 화면 위젯 | 제한 없음 → 기본, 한도 다 씀 → 사용 제한, 10분 이내 → 경고, 그 밖 → 성공 |

선택은 `Preferences.mascot`(DataStore `mascot`)에 저장되고, 화면에는 `LocalMascot`으로 전달된다.
설정의 "움직이는 효과 줄이기"(`Preferences.reduceMotion`, `LocalReduceMotion`)를 켜면 캐릭터 숨쉬기와 움직이는 테마가 멈춘다.

## 3. 이미지 다시 자르기 / 교체
- 원본 시트를 바꾸면 `python -I scripts/cut_mascots.py photo/chronolock android/app/src/main/res/drawable-nodpi`로 다시 자른다:
  배경(가장자리와 이어진 흰색)을 투명하게 → 시트를 5열×2행 칸으로 → 칸마다 라벨 줄 위까지만 → 320px 정사각형 webp.
- 파일 이름 규칙은 `mascot_<kind 소문자>_<pose 소문자>`. 새 캐릭터는 `data/MascotKind.kt`의 enum과 `ART` 표에 추가한다
  (이름으로 찾지 않고 R.drawable로 직접 참조해야 리소스 축소에서 빠지지 않는다).
- 이름은 `TranslationKeys.kt`의 `mascot_*` 키와 5개 언어 `ui_strings.xml`에 있다.

## 4. 멘트 설정
- 히스토리 멘트: `feature/Mascot.kt`의 `MascotLines`(표정별, 날짜에 따라 돌아감).
- 오늘 탭 멘트: `feature/Mascot.kt`의 `todayMood()`.
- 번역: `TranslationKeys.kt`에 한국어 문장 → `R.string.…`를 추가하고 5개 언어 `ui_strings.xml`에 값을 넣는다.

## 5. 남은 결정
- 유료화 시 캐릭터를 Pro 혜택으로 둘지(조사 문서 `docs/MONETIZATION_LOCALIZATION_2026-10-24.md`의 제안: 무료 1종·포즈 일부, Pro 전체). 지금은 모두 무료다.
- 원본 시트의 칸 경계에서 잘린 소품(쿠션·이불 끝)이 일부 있다. 더 깔끔하게 하려면 포즈별 단독 PNG를 받는 편이 좋다.

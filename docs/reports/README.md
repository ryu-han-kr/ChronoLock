# 기능·출시·운영 보고서

- [크롬용 HTML](CHRONOLOCK_BUSINESS_PLAN_2026-10-10.html): PDF 다운로드 버튼 및 가정 조절 계산기.
- [PDF](CHRONOLOCK_BUSINESS_PLAN_2026-10-10.pdf): 저장·공유용 고정본.
- [Markdown 원본](CHRONOLOCK_BUSINESS_PLAN_2026-10-10.md): 기능 10개, 구독안, 비용 산식, 일일 보고 설계 및 공식 출처.

HTML 파일을 크롬으로 열면 됩니다. `Ctrl+P`의 대상에서 'PDF로 저장'을 선택하면
계산기에 입력한 현재 값으로 다시 저장할 수 있습니다. 실제 서비스 키나 청구 정보가 없는 기획 자료입니다.

## 다시 생성하기

저장소 루트에서:

```powershell
python -m pip install --target .tools/report-deps markdown==3.9
python scripts/build_report.py
```

PDF는 생성한 HTML을 크롬에서 인쇄해 저장합니다. A4, 여백 없음, 배경 그래픽 포함,
머리글/바닥글 제외를 권장합니다. 공급자 가격·정책을 바꾸면 문서 날짜와 산식도 함께 갱신하세요.
현재 기준 모델 비용 산식은 `scripts/report_calculator.js`에 있습니다.

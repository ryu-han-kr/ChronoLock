"""Generate the standalone Chrome report; PDF printing is a separate Chrome step.

Install Markdown 3.9 into .tools/report-deps, then run this script from any cwd.
No application or external service is run by this document builder.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.tools' / 'report-deps'))
import markdown

OUT = ROOT / 'docs' / 'reports'
STEM = 'CHRONOLOCK_BUSINESS_PLAN_2026-10-10'
source = (OUT / f'{STEM}.md').read_text(encoding='utf-8')
parts = source.split('<!-- page -->')

css = """
:root{--ink:#18283b;--muted:#56677a;--accent:#166b66;--line:#d9e3e8;--soft:#f0f6f6}
*{box-sizing:border-box}body{margin:0;background:#e6ecf0;color:var(--ink);font-family:'Malgun Gothic','맑은 고딕',sans-serif}
.toolbar{position:sticky;top:0;z-index:5;background:#142a3b;color:white;padding:14px 24px;display:flex;align-items:center;gap:14px;flex-wrap:wrap;font-size:14px}
.toolbar span{flex:1}.toolbar a,.toolbar button{border:0;border-radius:6px;background:#dbf2df;color:#142a3b;padding:10px 16px;text-decoration:none;font:inherit;cursor:pointer}
.sheet{width:210mm;height:297mm;background:white;margin:20px auto;padding:14mm 16mm 18mm;position:relative;box-shadow:0 6px 24px #142a3b18;font-size:9.4pt;line-height:1.65;break-after:page;page-break-after:always}
.sheet:last-child{break-after:auto;page-break-after:auto}
.kicker{font-size:8pt;letter-spacing:2px;color:var(--accent);border-bottom:2px solid var(--accent);padding-bottom:8px;margin-bottom:18px}
h1{font-size:42pt;letter-spacing:-2px;line-height:1.15;margin:15px 0 8px}h2{font-size:20pt;line-height:1.3;margin:8px 0 20px;letter-spacing:-.7px}h3{font-size:12pt;color:var(--accent);margin:19px 0 7px}
p{margin:8px 0 12px}strong{font-weight:700}a{color:var(--accent);text-decoration:none;overflow-wrap:anywhere}
table{width:100%;border-collapse:collapse;font-size:8.5pt;line-height:1.55;margin:12px 0 16px;table-layout:auto}
th{background:#203d4e;color:white;text-align:left;font-weight:600;padding:8px}td{vertical-align:top;padding:7px 8px;border-bottom:1px solid var(--line)}tr:nth-child(even) td{background:#f5f8fa}
thead{display:table-header-group}tr{break-inside:avoid}ul,ol{padding-left:20px;margin:7px 0 13px}li{margin:4px 0}
pre{padding:14px;background:var(--soft);border-left:3px solid var(--accent);font-family:inherit;font-size:9pt;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}
code{font-family:inherit;background:#eff3f6;padding:1px 3px;border-radius:3px}pre code{padding:0;background:none}
footer{position:absolute;bottom:9mm;left:16mm;right:16mm;border-top:1px solid var(--line);padding-top:5px;display:flex;justify-content:space-between;font-size:7.5pt;color:var(--muted)}
.cover{border-top:5mm solid var(--accent);padding-top:12mm}.cover h2{color:var(--accent);font-size:19pt}.cover .kicker{border:0}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px 18px}.form-grid label{display:flex;justify-content:space-between;align-items:center;font-size:9pt}.form-grid input{width:105px;padding:6px;border:1px solid #9ab3bf;border-radius:4px;color:var(--ink);font:inherit}
.results{display:grid;grid-template-columns:1fr 1fr 1fr;gap:9px;margin-top:16px}.result{background:var(--soft);padding:11px;border-radius:6px;font-size:8pt}.result b{display:block;font-size:15pt;color:var(--accent);margin-top:4px}
#calc-error{color:#a12323;min-height:15px}.note{color:var(--muted);font-size:8.5pt}
@page{size:A4;margin:0} @media print{body{background:white}.toolbar{display:none}.sheet{margin:0;box-shadow:none}*{-webkit-print-color-adjust:exact;print-color-adjust:exact}a{color:var(--accent)}input{border:0!important;background:#f0f6f6}}
@media screen and (max-width:850px){.sheet{width:100%;height:auto;min-height:297mm;margin:0 auto 12px;padding:24px 20px 64px}.toolbar{position:static}h1{font-size:34pt}table{font-size:8pt}footer{left:20px;right:20px}.form-grid{grid-template-columns:1fr}.results{grid-template-columns:1fr 1fr}}
"""

calc = """
<h2>부록 · 운영비와 구독 계산기</h2>
<p>크롬에서 값을 바꾸면 즉시 계산됩니다. PDF는 생성 당시 값의 고정본입니다.
아래 기본값은 보고서의 월간 Pro 예시이며, 연간 결제는 월 인식 가격으로 바꿔 입력하세요.</p>
<form id="cost-form" class="form-grid">
<label>월 활성 사용자 수<input name="mau" type="number" min="0" value="1000"></label>
<label>Pro 전환율 (%)<input name="conversion" type="number" min="0" max="100" step="0.1" value="3"></label>
<label>Pro 월 소비자가 (원)<input name="price" type="number" min="0" value="3900"></label>
<label>기본 운영 예산 (USD/월)<input name="fixedUsd" type="number" min="0" step="0.01" value="45"></label>
<label>계산용 환율 (원/USD)<input name="fx" type="number" min="1" value="1400"></label>
<label>스토어 수수료 (%)<input name="fee" type="number" min="0" max="99.9" step="0.1" value="15"></label>
<label>가격에 포함된 세금 (%)<input name="tax" type="number" min="0" step="0.1" value="10"></label>
<label>AI 옵션 이용자 수<input name="aiUsers" type="number" min="0" value="0"></label>
<label>AI 추가 소비자가 (원/월)<input name="addonPrice" type="number" min="0" value="2900"></label>
<label>1인 월 AI 호출 횟수<input name="calls" type="number" min="0" value="12"></label>
</form><p id="calc-error" role="alert"></p>
<div class="results">
<div class="result">Pro 월간 이용자<b id="out-paid"></b></div>
<div class="result">AI 옵션 이용자<b id="out-aiUsers"></b></div>
<div class="result">소비자 결제 총액 / 월<b id="out-gross"></b></div>
<div class="result">세금·수수료 반영 정산 가정<b id="out-net"></b></div>
<div class="result">AI 월 모델 예산<b id="out-aiUsd"></b></div>
<div class="result">운영 예산 + AI / 월<b id="out-operating"></b></div>
<div class="result">운영비 일평균 (30일)<b id="out-daily"></b></div>
<div class="result">추가 비용 차감 전 운영 잔액<b id="out-remaining"></b></div>
<div class="result">고정비 회수 Pro 인원 (AI 제외)<b id="out-breakEven"></b></div>
</div>
<h3>계산 기준</h3>
<p class="note">유료 인원 = MAU × 전환율의 소수점 버림. AI 인원은 유료 인원 이내로 제한합니다.
정산 = 소비자 결제액 ÷ (1 + 세금률) × (1 − 수수료율).
AI 비용은 회당 입력 3,000 / 청구 출력 800토큰, 입력 $0.75 / 출력 $4.50 per 1M tokens,
20% 여유를 포함합니다. 운영 잔액은 순이익이 아닙니다. 인건비·마케팅·환불·추가 세금·초과 인프라는 별도입니다.</p>
<p class="note">월 기본 운영 예산은 용량 보장이 아닙니다. 가격·전환율·세금·환율은 직접 정한 가정이고,
실제 정산은 계정 명세로 확인해야 합니다. 가격을 0으로 설정하면 손익분기 인원은 계산하지 않습니다.</p>
"""

rendered = []
for part in parts:
    part = part.replace('<div id="calculator"></div>', '<p>가정을 바꾸는 계산기는 이 보고서 마지막 부록에서 이용할 수 있습니다.</p>')
    rendered.append(markdown.markdown(part.strip(), extensions=['tables', 'fenced_code']))
rendered.append(calc)
pages = []
for index, content in enumerate(rendered, start=1):
    klass = 'sheet cover' if index == 1 else 'sheet'
    pages.append(f'<section class="{klass}"><div class="kicker">CHRONOLOCK / PRODUCT & BUSINESS PLAN</div>{content}<footer><span>2026.10.10 · 제안 / 실제 청구·출시 확정 아님</span><span>{index:02d} / {len(rendered):02d}</span></footer></section>')
js = (ROOT / 'scripts' / 'report_calculator.js').read_text(encoding='utf-8')
html = f'''<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ChronoLock · 기능·출시·구독·운영비 제안서</title><style>{css}</style></head>
<body><nav class="toolbar"><span>ChronoLock · 2026.10.10 기획 보고서</span>
<a href="{STEM}.pdf" download>PDF 다운로드</a><button onclick="window.print()">현재 값으로 인쇄 / PDF 저장</button></nav>
<main>{''.join(pages)}</main><script>{js}</script></body></html>'''
(OUT / f'{STEM}.html').write_text(html, encoding='utf-8')
print(f'Generated {len(pages)} report pages: {OUT / (STEM + ".html")}')

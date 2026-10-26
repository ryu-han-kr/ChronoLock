"""Daily cost/revenue report for ChronoLock, posted to a Discord webhook.

Inputs (all outside git, in key-backup/):
  key-backup/<service-account>.json   Play service account (needs "View financial data" for revenue)
  key-backup/report-config.json       {"webhook": "https://discord.com/api/webhooks/...",
                                       "bucket": "pubsite_prod_rev_XXXXXXXX",
                                       "fixed_costs": [{"name": "...", "krw_per_month": 0}]}
Usage: python -I scripts/daily_report.py [--dry-run]
"""
import base64, csv, datetime as dt, glob, io, json, os, sys, time, zipfile
import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEYS = os.path.join(ROOT, "key-backup")
PKG = "com.chronolock.app"
KST = dt.timezone(dt.timedelta(hours=9))
SCOPES = "https://www.googleapis.com/auth/androidpublisher https://www.googleapis.com/auth/devstorage.read_only"


def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def token(key):
    now = int(time.time())
    head = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    body = b64(json.dumps({"iss": key["client_email"], "scope": SCOPES, "aud": key["token_uri"],
                           "iat": now, "exp": now + 3600}).encode())
    pk = serialization.load_pem_private_key(key["private_key"].encode(), password=None)
    sig = b64(pk.sign(f"{head}.{body}".encode(), padding.PKCS1v15(), hashes.SHA256()))
    r = requests.post(key["token_uri"], data={"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                              "assertion": f"{head}.{body}.{sig}"}, timeout=30)
    r.raise_for_status()
    return r.json()["access_token"]


def sales(session, bucket, month):
    """Sum of the month's Play sales report (KRW of 'Charged Amount' per currency), or None if not readable."""
    name = f"sales/salesreport_{month}.zip"
    r = session.get(f"https://storage.googleapis.com/storage/v1/b/{bucket}/o/{requests.utils.quote(name, safe='')}",
                    params={"alt": "media"}, timeout=60)
    if r.status_code == 404:
        return {}
    if not r.ok:
        return None
    totals, orders = {}, 0
    with zipfile.ZipFile(io.BytesIO(r.content)) as z:
        for n in z.namelist():
            for row in csv.DictReader(io.TextIOWrapper(z.open(n), encoding="utf-8-sig")):
                if row.get("Product ID", PKG) != PKG:
                    continue
                cur = row.get("Currency of Sale") or row.get("Buyer Currency") or "?"
                amt = float((row.get("Charged Amount") or "0").replace(",", ""))
                totals[cur] = totals.get(cur, 0) + amt
                orders += 1
    totals["_orders"] = orders
    return totals


def release_status(session):
    api = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
    edit = session.post(f"{api}/edits", timeout=30).json()["id"]
    tracks = session.get(f"{api}/edits/{edit}/tracks", timeout=30).json().get("tracks", [])
    session.delete(f"{api}/edits/{edit}", timeout=30)
    return ", ".join(f"{t['track']}={r.get('name')}" for t in tracks for r in t.get("releases", [])[:1])


def main():
    cfg = json.load(open(os.path.join(KEYS, "report-config.json"), encoding="utf-8"))
    key_path = next(p for p in glob.glob(os.path.join(KEYS, "*.json")) if json.load(open(p, encoding="utf-8")).get("type") == "service_account")
    s = requests.Session()
    s.headers["Authorization"] = "Bearer " + token(json.load(open(key_path, encoding="utf-8")))
    today = dt.datetime.now(KST).date()
    month = today.strftime("%Y%m")

    fixed = cfg.get("fixed_costs", [])
    monthly_cost = sum(c.get("krw_per_month", 0) for c in fixed)
    lines = [f"**ChronoLock 일일 보고 {today.isoformat()}**"]
    try:
        lines.append(f"출시: {release_status(s)}")
    except Exception as e:  # noqa: BLE001 - report must still go out
        lines.append(f"출시 상태 조회 실패: {type(e).__name__}")
    lines.append(f"고정 비용: 월 {monthly_cost:,}원 (" + ", ".join(f"{c['name']} {c.get('krw_per_month', 0):,}원" for c in fixed) + ")")
    lines.append(f"오늘까지 이번 달 비용(일할): 약 {round(monthly_cost * today.day / 30):,}원")
    rev = sales(s, cfg["bucket"], month) if cfg.get("bucket") else None
    if rev is None:
        lines.append("수입: 조회 불가 (서비스 계정에 '재무 데이터 보기' 권한과 버킷 ID 필요)")
    else:
        orders = rev.pop("_orders", 0)
        total = ", ".join(f"{v:,.0f} {k}" for k, v in rev.items()) or "0원"
        lines.append(f"수입(이번 달 Play 판매, 수수료 차감 전): {total} · 주문 {orders}건")
    msg = "\n".join(lines)
    if "--dry-run" in sys.argv:
        print(msg)
        return
    requests.post(cfg["webhook"], json={"content": msg}, timeout=30).raise_for_status()
    print("sent")


main()

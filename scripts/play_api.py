"""Google Play Developer API helper (service account key in key-backup/, never committed).

  python -I scripts/play_api.py tracks
  python -I scripts/play_api.py listings                 # prints every store listing as JSON
  python -I scripts/play_api.py set-listings FILE.json   # {"ko-KR": {"title","shortDescription","fullDescription"}, ...}
  python -I scripts/play_api.py upload AAB TRACKS NOTES.json   # TRACKS comma-separated; NOTES {"name", "notes": {lang: text}}
"""
import base64, glob, json, os, sys, time
import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = "com.chronolock.app"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}"


def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def session():
    path = next(p for p in glob.glob(os.path.join(ROOT, "key-backup", "*.json"))
                if json.load(open(p, encoding="utf-8")).get("type") == "service_account")
    key = json.load(open(path, encoding="utf-8"))
    now = int(time.time())
    head = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    body = b64(json.dumps({"iss": key["client_email"], "scope": "https://www.googleapis.com/auth/androidpublisher",
                           "aud": key["token_uri"], "iat": now, "exp": now + 3600}).encode())
    pk = serialization.load_pem_private_key(key["private_key"].encode(), password=None)
    sig = b64(pk.sign(f"{head}.{body}".encode(), padding.PKCS1v15(), hashes.SHA256()))
    r = requests.post(key["token_uri"], data={"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                              "assertion": f"{head}.{body}.{sig}"}, timeout=30)
    r.raise_for_status()
    s = requests.Session()
    s.headers["Authorization"] = "Bearer " + r.json()["access_token"]
    return s


def check(r):
    if not r.ok:
        print("HTTP", r.status_code, r.text[:2000])
        sys.exit(1)
    return r.json() if r.text else {}


def main():
    cmd = sys.argv[1]
    s = session()
    edit = check(s.post(f"{API}/edits"))["id"]
    if cmd == "tracks":
        for t in check(s.get(f"{API}/edits/{edit}/tracks")).get("tracks", []):
            for rel in t.get("releases", []):
                print(t["track"], rel.get("status"), rel.get("versionCodes"), rel.get("name"))
    elif cmd == "listings":
        print(json.dumps(check(s.get(f"{API}/edits/{edit}/listings")).get("listings", []), ensure_ascii=False, indent=1))
    elif cmd == "set-listings":
        for lang, listing in json.load(open(sys.argv[2], encoding="utf-8")).items():
            check(s.put(f"{API}/edits/{edit}/listings/{lang}", json={"language": lang, **listing}))
            print("listing set", lang)
        print("committed", check(s.post(f"{API}/edits/{edit}:commit")).get("id"))
        return
    elif cmd == "upload":
        aab, tracks, notes = sys.argv[2], sys.argv[3], json.load(open(sys.argv[4], encoding="utf-8"))
        with open(aab, "rb") as f:
            vc = check(s.post(f"{UPLOAD}/edits/{edit}/bundles", params={"uploadType": "media"}, data=f,
                              headers={"Content-Type": "application/octet-stream"}, timeout=600))["versionCode"]
        for track in tracks.split(","):
            check(s.put(f"{API}/edits/{edit}/tracks/{track}", json={"track": track, "releases": [{
                "name": notes["name"], "versionCodes": [str(vc)], "status": "completed",
                "releaseNotes": [{"language": l, "text": t} for l, t in notes["notes"].items()]}]}))
            print("track set", track, vc)
        print("committed", check(s.post(f"{API}/edits/{edit}:commit")).get("id"))
        return
    s.delete(f"{API}/edits/{edit}")


main()

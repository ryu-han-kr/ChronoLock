"""Render Play Store icon (512x512) and feature graphic (1024x500) from the launcher vector."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
BG = (16, 20, 31)
LIME = (184, 242, 126)
MUTED = (160, 170, 190)
SS = 4  # supersampling


def clock(draw, cx, cy, r, w):
    """Ring plus hands, matching ic_chronolock.xml (viewport 48, r=17, stroke 3)."""
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=LIME, width=w)
    s = r / 17
    top = (cx, cy - 11 * s)
    mid = (cx, cy)
    end = (cx + 8 * s, cy + 5 * s)
    draw.line([top, mid, end], fill=LIME, width=w, joint="curve")
    for p in (top, end):
        draw.ellipse((p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2), fill=LIME)
    draw.ellipse((cx - w / 2, cy - w / 2, cx + w / 2, cy + w / 2), fill=LIME)


def icon():
    size = 512 * SS
    img = Image.new("RGB", (size, size), BG)
    d = ImageDraw.Draw(img)
    clock(d, size / 2, size / 2, 0.30 * size, int(0.055 * size))
    img.resize((512, 512), Image.LANCZOS).save(OUT / "icon-512.png")


def feature():
    W, H = 1024 * SS, 500 * SS
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    clock(d, 220 * SS, H / 2, 110 * SS, 18 * SS)
    bold = ImageFont.truetype("C:/Windows/Fonts/malgunbd.ttf", 76 * SS)
    reg = ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 34 * SS)
    x = 400 * SS
    small = ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 28 * SS)
    d.text((x, 150 * SS), "크로노락", font=bold, fill=(240, 244, 250))
    d.text((x, 250 * SS), "앱 사용 시간, 내가 정한 만큼만", font=reg, fill=LIME)
    d.text((x, 312 * SS), "시간·횟수 제한 · 집중 모드 · 사용 기록", font=small, fill=MUTED)
    img.resize((1024, 500), Image.LANCZOS).save(OUT / "feature-1024x500.png")


icon()
feature()
print("ok")

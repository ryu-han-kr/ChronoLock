# Makes a closed-eye frame for every mascot pose whose eyes are open, so the app can blink the character.
# Usage: python -I scripts/make_blinks.py <drawable dir>   (reads mascot_*.webp, writes mascot_*_blink.webp next to them)
#
# The open eyes are the large dark round shapes in the face; thin outlines are removed first so only irises remain.
# Each eye is painted over with the surrounding fur (inpainting) and a soft closed-eye curve is drawn in its place.
# Poses whose eyes are already closed have no such pair and get no blink frame.
import cv2, numpy as np, sys, os
from PIL import Image

# Poses whose eyes are already closed or hidden (glasses), or where a blink frame did not look right.
SKIP = {'fox_focus', 'fox_timer', 'otter_default', 'otter_rest', 'otter_success', 'otter_warning', 'rabbit_success'}

def eyes(rgb, alpha):
    h, w = alpha.shape
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    best = None
    # Irises vary from near black to warm brown, so a few darkness levels are tried.
    for level in (95, 115):
        dark = ((gray < level) & (alpha > 200)).astype(np.uint8)
        dark = cv2.morphologyEx(dark, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
        n, lab, st, cen = cv2.connectedComponentsWithStats(dark)
        blobs = []
        for i in range(1, n):
            x, y, bw, bh, area = st[i]
            r, g, _ = rgb[lab == i].mean(0)
            # Round, solid, in the face, and not the red of an open mouth.
            if 120 <= area <= 2500 and .6 <= bw / bh <= 1.6 and .12 * h <= cen[i][1] <= .7 * h \
                    and area >= .42 * bw * bh and r - g < 50:
                blobs.append((i, x, y, bw, bh, area, *cen[i]))
        # blob = (label, x, y, width, height, area, centre x, centre y); eyes are two alike blobs side by side.
        for a in blobs:
            for b in blobs:
                if a[0] >= b[0]: continue
                dy, dx = abs(a[7] - b[7]), abs(a[6] - b[6])
                alike = .7 < a[3] / b[3] < 1.4 and .7 < a[4] / b[4] < 1.4 and .6 < a[5] / b[5] < 1.7
                if alike and dy < .1 * h and .1 * w < dx < .45 * w:
                    score = a[5] + b[5] - 10 * dy
                    if best is None or score > best[0]: best = (score, a, b)
        if best: break
    return None if best is None else [best[1], best[2]]

def blink(path, out):
    im = np.asarray(Image.open(path).convert('RGBA'))
    rgb, alpha = im[:, :, :3].copy(), im[:, :, 3]
    found = eyes(rgb, alpha)
    if not found: return False
    ink = np.median(rgb[(cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY) < 80) & (alpha > 200)], axis=0)
    mask = np.zeros(alpha.shape, np.uint8)
    for _, x, y, bw, bh, _, cx, cy in found:
        cv2.ellipse(mask, (int(cx), int(cy)), (int(bw * .78), int(bh * .82)), 0, 0, 360, 255, -1)
    mask = cv2.dilate(mask, np.ones((3, 3), np.uint8))
    filled = cv2.inpaint(rgb, mask, 7, cv2.INPAINT_TELEA)
    # Closed lids: a gentle downward curve across each eye, drawn large and scaled down for smooth edges.
    k = 4
    lids = np.zeros((alpha.shape[0] * k, alpha.shape[1] * k), np.uint8)
    for _, x, y, bw, bh, _, cx, cy in found:
        cv2.ellipse(lids, (int(cx * k), int((cy + bh * .05) * k)), (int(bw * .62 * k), int(bh * .28 * k)), 0, 15, 165,
                    255, max(2, int(bw * .09 * k)), cv2.LINE_AA)
    lids = cv2.resize(lids, alpha.shape[::-1], interpolation=cv2.INTER_AREA).astype(np.float32)[:, :, None] / 255
    filled = (filled * (1 - lids) + ink * lids).astype(np.uint8)
    Image.fromarray(np.dstack([filled, alpha])).save(out, 'WEBP', quality=88, method=6)
    return True

d = sys.argv[1]
for f in sorted(os.listdir(d)):
    if f.startswith('mascot_') and f.endswith('.webp') and not f.endswith('_blink.webp') and f[7:-5] not in SKIP:
        made = blink(os.path.join(d, f), os.path.join(d, f[:-5] + '_blink.webp'))
        print(('blink ' if made else 'none  ') + f)

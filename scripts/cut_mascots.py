# Cuts the mascot sheets in photo/chronolock (5 columns x 2 rows of poses, a label under each) into transparent 320px webp files.
# Usage: python -I scripts/cut_mascots.py <sheet dir> <output dir>
#
# Poses are not cut at fixed column lines: decorations (hearts, "Zzz", "!") reach past them and neighbouring poses touch
# (rest and sleep share a cushion). Every shape is kept whole and given to its row and column; where two poses touch they
# are parted along a seam that follows the drawing's dark outlines, so the cut runs where one drawing overlaps the other.
import cv2, numpy as np, sys, os
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
SHEETS = {  # file stem -> (character, top row bottom, bottom row bottom); the labels start just below each row bottom
 '40a9803d':('otter',466,904),
 '7beaede0':('raccoon',482,931),
 'aba9bf22':('hamster',477,928),
 'de5be1d4':('rabbit',489,946),
 'eeea5e15':('fox',481,939),
}
POSES=['default','lock','unlock','focus','timer','limit','rest','sleep','warning','success']
# (sheet, row, seam between column i and i+1) -> a point the seam must pass, where the outlines alone mislead it:
# the hamster's nightcap tassel rests on the cushion of the pose beside it.
WAYPOINTS = {('aba9bf22', 1, 1): (566, 742)}
# (sheet, x, y inside the decoration) -> its pose, for decorations as near to a neighbour as to their own pose.
OWNERS = {
 ('40a9803d', 838, 329): 'unlock',   # a ray of the opened lock, beside the laptop
 ('de5be1d4', 1192, 607): 'success',  # confetti drifting over the warning
 ('de5be1d4', 1175, 657): 'success',
 ('7beaede0', 273, 637): 'limit',     # the alarm rays of the blocked phone, by the rest cushion
 ('7beaede0', 282, 668): 'limit',
 ('7beaede0', 278, 700): 'limit',
}
SIZE, MARGIN = 320, 0.05
DECORATION = 2500  # shapes smaller than this (hearts, "Zzz", confetti) go whole to the nearest pose

def seam(cost, x0, x1, via=None):
    """The cheapest top-to-bottom path within columns [x0, x1), moving at most one column per row, through `via` if given."""
    c = cost[:, x0:x1].astype(np.float64)
    if via is not None:
        vx, vy = via; c[vy] = np.inf; c[vy, vx - x0] = 0
    h, w = c.shape
    acc = c.copy(); back = np.zeros((h, w), np.int8)
    for y in range(1, h):
        prev = acc[y - 1]
        options = np.stack([np.r_[np.inf, prev[:-1]], prev, np.r_[prev[1:], np.inf]])
        pick = options.argmin(0); back[y] = pick - 1
        acc[y] += options[pick, np.arange(w)]
    path = np.zeros(h, int); path[-1] = int(acc[-1].argmin())
    for y in range(h - 1, 0, -1): path[y - 1] = path[y] + back[y, path[y]]
    return path + x0

os.makedirs(out, exist_ok=True)
for stem, (name, top_end, bottom_end) in SHEETS.items():
    img = cv2.imread(os.path.join(src, [f for f in os.listdir(src) if f.startswith(stem)][0]))
    h, w = img.shape[:2]
    bg = img[5, 5].astype(int)
    fg = (np.abs(img.astype(int) - bg).max(2) > 16).astype(np.uint8)
    # Background is the near-background area connected to the border; white fur inside an outline stays opaque.
    near = (1 - fg).astype(np.uint8)
    _, lab = cv2.connectedComponents(near, connectivity=4)
    border = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
    obj = (~(np.isin(lab, border) & (near == 1))).astype(np.uint8)
    n, comp, stats, cent = cv2.connectedComponentsWithStats(obj, connectivity=8)
    row_of = np.full(n, -1)
    for i in range(1, n):
        x, y, cw, ch, area = stats[i]
        if area < 40: continue
        if ch < 75 and (top_end - 5 <= y and y + ch <= top_end + 80 or y >= bottom_end - 5): continue  # a label
        row_of[i] = 0 if cent[i][1] < top_end + 40 else 1
    rows = [(row_of[comp] == r).astype(np.uint8) for r in (0, 1)]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float64) / 255
    for r, mask in enumerate(rows):
        ys = np.nonzero(mask.any(1))[0]; y0, y1 = ys.min(), ys.max() + 1
        # Crossing background is free and crossing a dark outline is cheap, so seams pass between or along drawings.
        cost = mask[y0:y1] * (0.15 + gray[y0:y1] ** 2)
        centers = [(c + 0.5) * w / 5 for c in range(5)]
        seams = []
        for k, (a, b) in enumerate(zip(centers, centers[1:])):
            via = WAYPOINTS.get((stem, r, k))
            seams.append(seam(cost, int(a + 0.3 * (b - a)), int(b - 0.3 * (b - a)), via and (via[0], via[1] - y0)))
        cols = np.arange(w)[None, :]
        parts = []
        for c in range(5):
            part = np.zeros_like(mask)
            left = seams[c - 1][:, None] if c > 0 else np.full((y1 - y0, 1), -1)
            right = seams[c][:, None] if c < 4 else np.full((y1 - y0, 1), w)
            part[y0:y1] = mask[y0:y1] * ((cols > left) & (cols <= right))
            # Drop slivers the seam shaved off a neighbour.
            pn, plab, pst, _ = cv2.connectedComponentsWithStats(part, connectivity=8)
            parts.append(np.isin(plab, [i for i in range(1, pn) if pst[i, 4] >= 60]).astype(np.uint8))
        # A seam through open background can fall on either side of a decoration; give each one whole to the pose whose
        # body (its largest shape) comes nearest, unless the sheet names its owner.
        reach = []
        for part in parts:
            pn, plab, pst, _ = cv2.connectedComponentsWithStats(part, connectivity=8)
            body = (plab == 1 + pst[1:, 4].argmax()).astype(np.uint8)
            reach.append(cv2.distanceTransform(1 - body, cv2.DIST_L2, 3))
        for i in np.nonzero((row_of == r) & (stats[:, 4] < DECORATION))[0]:
            x, y, cw, ch, _ = stats[i]
            box = comp[y:y + ch, x:x + cw] == i
            gaps = [d[y:y + ch, x:x + cw][box].min() for d in reach]
            owner = next((pose for (s, px, py), pose in OWNERS.items() if s == stem and x <= px < x + cw and y <= py < y + ch),
                          None)
            owner = POSES.index(owner) - r * 5 if owner else int(np.argmin(gaps))
            if os.environ.get('CUT_DEBUG') and sorted(gaps)[1] < 1.6 * sorted(gaps)[0]:
                print(f'close call {name} row {r} at ({x},{y}) {cw}x{ch} -> {POSES[r * 5 + owner]}', [round(g) for g in gaps])
            for c, part in enumerate(parts): part[y:y + ch, x:x + cw][box] = int(c == owner)
        for c, part in enumerate(parts):
            py, px = np.nonzero(part)
            bx0, bx1, by0, by1 = px.min(), px.max() + 1, py.min(), py.max() + 1
            rgba = np.dstack([img[by0:by1, bx0:bx1, ::-1], cv2.GaussianBlur(part[by0:by1, bx0:bx1] * 255, (3, 3), 0)])
            side = int(max(bx1 - bx0, by1 - by0) * (1 + 2 * MARGIN))
            canvas = np.zeros((side, side, 4), np.uint8)
            ox = (side - (bx1 - bx0)) // 2; oy = side - (by1 - by0) - int(side * MARGIN)
            canvas[oy:oy + by1 - by0, ox:ox + bx1 - bx0] = rgba
            Image.fromarray(canvas).resize((SIZE, SIZE), Image.LANCZOS).save(
                os.path.join(out, f'mascot_{name}_{POSES[r * 5 + c]}.webp'), 'WEBP', quality=88, method=6)
print('ok')

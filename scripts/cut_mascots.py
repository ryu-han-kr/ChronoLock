# Cuts the mascot sheets in photo/chronolock (5 columns x 2 rows of poses) into transparent 320px webp files.
# Usage: python -I scripts/cut_mascots.py <sheet dir> <output dir>
import cv2, numpy as np, sys, os
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
SHEETS = {  # file stem -> (character, top band, bottom band) ; band = (y0, y1 incl. label)
 '40a9803d':('otter',(140,466),(556,904)),
 '7beaede0':('raccoon',(130,482),(560,931)),
 'aba9bf22':('hamster',(135,477),(570,928)),
 'de5be1d4':('rabbit',(95,489),(556,946)),
 'eeea5e15':('fox',(95,481),(578,939)),
}
POSES=['default','lock','unlock','focus','timer','limit','rest','sleep','warning','success']
os.makedirs(out,exist_ok=True)
for stem,(name,top,bot) in SHEETS.items():
    img=cv2.imread(os.path.join(src,[f for f in os.listdir(src) if f.startswith(stem)][0]))
    h,w=img.shape[:2]
    bg=img[5,5].astype(int)
    diff=np.abs(img.astype(int)-bg).max(2)
    fg=(diff>16).astype(np.uint8)
    # background = near-bg pixels connected to the border
    nb=(1-fg).astype(np.uint8)
    n,lab=cv2.connectedComponents(nb,connectivity=4)
    border=set(np.unique(np.concatenate([lab[0],lab[-1],lab[:,0],lab[:,-1]])))
    bgmask=np.isin(lab,list(border)) & (nb==1)
    obj=(~bgmask).astype(np.uint8)
    for r,(y0,y1) in enumerate([top,bot]):
        cells=[]
        for c in range(5):
            m=np.zeros_like(obj); xa,xb=int(c*w/5),int((c+1)*w/5)
            m[y0:y1,xa:xb]=obj[y0:y1,xa:xb]
            n,lab,stats,_=cv2.connectedComponentsWithStats(m,connectivity=8)
            keep=[i for i in range(1,n) if stats[i,4]>=40 and not ((stats[i,0]<=xa+1 or stats[i,0]+stats[i,2]>=xb-1) and stats[i,2]<45)]
            cells.append(np.isin(lab,keep).astype(np.uint8))
        for c,m in enumerate(cells):
            ys,xs=np.nonzero(m)
            x0,x1,yy0,yy1=xs.min(),xs.max()+1,ys.min(),ys.max()+1
            crop=img[yy0:yy1,x0:x1]; a=m[yy0:yy1,x0:x1]*255
            a=cv2.GaussianBlur(a,(3,3),0)
            rgba=np.dstack([crop[:,:,::-1],a])
            side=max(x1-x0,yy1-yy0)+12
            canvas=np.zeros((side,side,4),np.uint8)
            ox=(side-(x1-x0))//2; oy=side-(yy1-yy0)-6
            canvas[oy:oy+yy1-yy0,ox:ox+x1-x0]=rgba
            im=Image.fromarray(canvas).resize((320,320),Image.LANCZOS)
            im.save(os.path.join(out,f'mascot_{name}_{POSES[r*5+c]}.webp'),'WEBP',quality=88,method=6)
print('ok')

# -*- coding: utf-8 -*-
# 逐帧程序化渲染「搭配记忆 v4.3.5 更新宣传片」
# 关键：真实截图原样嵌入、文字由代码绘制 —— 零 AI 重绘、零文字扭曲
import math
import numpy as np
import imageio.v2 as imageio
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1080, 1920
FPS = 30
DUR = [2.2, 3.2, 3.2, 3.2, 2.6]           # 各场景时长（秒）
BOUNDS = [0.0]
for d in DUR:
    BOUNDS.append(BOUNDS[-1] + d)
TOTAL = BOUNDS[-1]
XFADE = 0.4                                 # 交叉淡化时长

FB = "C:/Windows/Fonts/msyhbd.ttc"
FR = "C:/Windows/Fonts/msyh.ttc"
f_kick  = ImageFont.truetype(FR, 34)
f_big   = ImageFont.truetype(FB, 96)
f_title = ImageFont.truetype(FB, 58)
f_sub   = ImageFont.truetype(FR, 36)
f_point = ImageFont.truetype(FR, 32)
f_small = ImageFont.truetype(FR, 28)

# ---- 背景：深蓝渐变（一次生成）+ 可移动光晕（预生成）----
def make_bg():
    top, bottom = (10, 20, 40), (24, 40, 70)
    bg = Image.new('RGB', (W, H))
    d = ImageDraw.Draw(bg)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=(
            int(top[0] + (bottom[0]-top[0])*t),
            int(top[1] + (bottom[1]-top[1])*t),
            int(top[2] + (bottom[2]-top[2])*t)))
    return bg
BASE_BG = make_bg()

glow = Image.new('RGBA', (900, 480), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([100, 60, 800, 420], fill=(46, 84, 140, 95))
glow = glow.filter(ImageFilter.GaussianBlur(80))

def paste_glow(frame, t):
    gy = 120 + math.sin(t * 0.9) * 55
    frame.paste(glow, (int(W/2 - 450), int(gy)), glow)

def ctext(d, y, text, font, fill):
    w = d.textlength(text, font=font)
    d.text(((W - w) / 2, y), text, font=font, fill=fill)

def load_shot(path, width):
    img = Image.open(path).convert('RGB')
    r = width / img.width
    img = img.resize((width, int(img.height * r)), Image.LANCZOS)
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]], radius=34, fill=255)
    return img, mask

SHOT_W = 660
SHOTS = {}
for key, path in [("learn", "D:/AndroidProjects/CollocationMemory/shot_learn.png"),
                  ("preview", "D:/AndroidProjects/CollocationMemory/shot_preview.png"),
                  ("image", "D:/AndroidProjects/CollocationMemory/shot_image.png")]:
    SHOTS[key] = load_shot(path, SHOT_W)

def draw_shot(frame, key, cy, alpha, yoff=0.0):
    """截图卡片：圆角 + 白描边 + 入场位移/透明度"""
    img, mask = SHOTS[key]
    iw, ih = img.size
    x = int((W - iw) / 2)
    y = int(cy - ih / 2 + yoff)
    a = max(0, min(1, alpha))
    m = mask.point(lambda p: int(p * a))
    # 白描边底板
    if a > 0.05:
        edge = Image.new('RGBA', (iw + 8, ih + 8), (0, 0, 0, 0))
        ImageDraw.Draw(edge).rounded_rectangle([0, 0, iw + 8, ih + 8], radius=38,
                                               outline=(255, 255, 255, int(200 * a)), width=2)
        frame.paste(edge, (x - 4, y - 4), edge)
    frame.paste(img, (x, y), m)

def ease_out(p):
    return 1 - (1 - p) ** 3

def enter(local, dur=0.55, slide=46.0):
    """入场：返回 (alpha, yoff)"""
    if local >= dur:
        return 1.0, 0.0
    p = max(0.0, local / dur)
    return p, (1 - ease_out(p)) * slide

def float_y(local, base_phase=0.0):
    return math.sin(local * 2 * math.pi / 2.6 + base_phase) * 5.0

def dots(d, page, total=3):
    cx = W / 2
    for i in range(total):
        x = cx - 40 + i * 40
        if i == page:
            d.ellipse([x - 8, 1836 - 8, x + 8, 1836 + 8], fill=(127, 179, 255))
        else:
            d.ellipse([x - 6, 1836 - 6, x + 6, 1836 + 6], outline=(120, 140, 175), width=2)

# ---- 场景渲染 ----
def scene0(local, d):
    a1, y1 = enter(local)
    a2, _ = enter(local - 0.35)
    al = int(255 * a1)
    if a1 > 0.01:
        ctext(d, 660 + y1, "UPDATE · v4.3.5", f_kick, (127, 179, 255, al) if False else (127, 179, 255))
        ctext(d, 720 + y1, "搭配记忆", f_big, (255, 255, 255))
    if a2 > 0.01:
        ctext(d, 880, "这次更新了什么", f_sub, (168, 186, 214))
        w = d.textlength("这次更新了什么", font=f_sub)
        d.line([(W/2 - 60, 950), (W/2 + 60, 950)], fill=(127, 179, 255), width=3)
    a3, _ = enter(local - 0.9)
    if a3 > 0.01:
        ctext(d, 1690, "学习记录不受影响 · 直接覆盖安装", f_small, (130, 148, 178))

def scene_shot(local, d, key, title, sub, page):
    a_t, y_t = enter(local)
    ctext(d, 96 + y_t, title, f_title, (255, 255, 255))
    ctext(d, 178 + y_t, sub, f_sub, (150, 190, 245))
    a_s, _ = enter(local - 0.25)
    fy = float_y(local) if local > 0.55 else 0.0
    if a_s > 0.01:
        draw_shot(frame, key, 280 + SHOT_W * 2400/1080 / 2 - 10, a_s, fy)
    dots(d, page)
    return None

def scene4(local, d):
    a1, y1 = enter(local)
    ctext(d, 620 + y1, "立即更新", f_big, (255, 255, 255))
    a2, _ = enter(local - 0.3)
    if a2 > 0.01:
        ctext(d, 770, "学习记录不受影响 · 直接覆盖安装", f_sub, (150, 190, 245))
    items = ["语音缺失只提醒一次", "词库更新至 556 条", "新学配图陆续补充中"]
    for i, text in enumerate(items):
        a3, _ = enter(local - 0.6 - i * 0.18)
        if a3 > 0.01:
            y = 950 + i * 84
            d.text((W/2 - 190, y), "＋", font=f_point, fill=(127, 179, 255))
            d.text((W/2 - 130, y), text, font=f_point, fill=(225, 233, 245))

SCENES = [
    lambda lo, d, fr: scene0(lo, d),
    lambda lo, d, fr: (ctext(d, 96, "学习页焕新", f_title, (255,255,255)),
                   ctext(d, 178, "看答案后，释义按词性拆成选项卡", f_sub, (150,190,245)),
                   draw_shot(fr, "learn", 280 + SHOTS["learn"][1].size[1]/2 - 10,
                             enter(lo - 0.25)[0], float_y(lo) if lo > 0.55 else 0.0),
                   dots(d, 0)),
    lambda lo, d, fr: (ctext(d, 96, "新增词库预览", f_title, (255,255,255)),
                   ctext(d, 178, "没学先翻一遍 · 搜索支持跨词本", f_sub, (150,190,245)),
                   draw_shot(fr, "preview", 280 + SHOTS["preview"][1].size[1]/2 - 10,
                             enter(lo - 0.25)[0], float_y(lo) if lo > 0.55 else 0.0),
                   dots(d, 1)),
    lambda lo, d, fr: (ctext(d, 96, "新学页配图", f_title, (255,255,255)),
                   ctext(d, 178, "看答案配一张示意图，帮记忆", f_sub, (150,190,245)),
                   draw_shot(fr, "image", 280 + SHOTS["image"][1].size[1]/2 - 10,
                             enter(lo - 0.25)[0], float_y(lo) if lo > 0.55 else 0.0),
                   dots(d, 2)),
    lambda lo, d, fr: scene4(lo, d),
]

def render_scene(i, local):
    frame = BASE_BG.copy()
    paste_glow(frame, BOUNDS[i] + local)
    d = ImageDraw.Draw(frame)
    SCENES[i](local, d, frame)
    return frame

def render_global(t):
    i = 0
    for k in range(len(BOUNDS) - 1):
        if BOUNDS[k] <= t < BOUNDS[k + 1]:
            i = k
            break
    else:
        i = len(BOUNDS) - 2
    local = t - BOUNDS[i]
    if i < len(SCENES) - 1 and t > BOUNDS[i + 1] - XFADE:
        k = (t - (BOUNDS[i + 1] - XFADE)) / XFADE
        a = render_scene(i, local)
        b = render_scene(i + 1, t - BOUNDS[i + 1])
        return Image.blend(a, b, min(1.0, k))
    return render_scene(i, local)

out = "D:/AndroidProjects/CollocationMemory/更新宣传片-v4.3.5.mp4"
writer = imageio.get_writer(out, fps=FPS, codec='libx264',
                            macro_block_size=8, pixelformat='yuv420p', quality=8)
n = int(TOTAL * FPS)
for f in range(n):
    t = f / FPS
    cur = render_global(t)
    writer.append_data(np.asarray(cur))
    if f % 60 == 0:
        print(f"frame {f}/{n}", flush=True)
writer.close()
print("done:", out)

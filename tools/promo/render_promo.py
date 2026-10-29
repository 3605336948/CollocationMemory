# -*- coding: utf-8 -*-
"""
搭配记忆 宣传片渲染器。

画面：1920x1080 横屏
  左侧 —— 手机里的真实界面动效（首页 / 新学 / 熟 / 复习 / 拼写 / 笔记本 / 统计 / 更新）
  右侧 —— 项目真实源码（语法高亮 + 打字机出现 + 焦点行高亮）
  底部 —— 一句面向家长的文案

只依赖 Pillow（渲染）与 imageio-ffmpeg（编码），不需要浏览器。
用法：python render_promo.py
"""

import os
import re
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

from promo_content import CHAPTERS, OUTRO

# ============================================================ 画布

W, H = 1920, 1080
FPS = 30
CHAPTER_SEC = 6.5
OUTRO_SEC = 5.0

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_MP4 = os.path.join(OUT_DIR, "搭配记忆-宣传片-v4.2.mp4")

# ============================================================ 配色（与 App 主题一致）

BG_TOP = (11, 16, 28)
BG_BOTTOM = (20, 28, 48)
PANEL = (24, 32, 52)
PANEL_EDGE = (58, 72, 108)
AMBER = (255, 179, 71)
AMBER_SOFT = (255, 200, 122)
WHITE = (255, 255, 255)
DIM = (176, 188, 214)
CODE_BG = (17, 22, 36)
GREEN = (94, 214, 138)
BLUE = (110, 176, 255)

KEYWORDS = {
    "fun", "val", "var", "return", "if", "else", "private", "const", "object",
    "class", "data", "suspend", "override", "for", "while", "when", "is", "in",
    "true", "false", "null", "this", "companion", "import", "package", "internal",
}
TYPES = {
    "String", "Boolean", "Int", "Long", "Float", "Double", "File", "Flow",
    "List", "TextToSpeech", "AppUpdateInfo", "NotebookEntry", "AppDatabase",
    "Collocation", "Unit",
}

# ============================================================ 字体

F_REG = "C:/Windows/Fonts/msyh.ttc"
F_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
F_MONO = "C:/Windows/Fonts/consola.ttf"
F_MONO_B = "C:/Windows/Fonts/consolab.ttf"

_font_cache = {}


def font(path, size):
    key = (path, size)
    if key not in _font_cache:
        _font_cache[key] = ImageFont.truetype(path, size)
    return _font_cache[key]


def zh(size, bold=False):
    return font(F_BOLD if bold else F_REG, size)


def mono(size, bold=False):
    return font(F_MONO_B if bold else F_MONO, size)


# ============================================================ 小工具

def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1.0 - (1.0 - t) ** 3


def clamp01(t):
    return max(0.0, min(1.0, t))


def rr(d, box, radius, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def text_w(d, s, f):
    return d.textlength(s, font=f)


def draw_text(d, xy, s, f, fill, anchor=None):
    d.text(xy, s, font=f, fill=fill, anchor=anchor)


def center_text(d, cx, y, s, f, fill):
    d.text((cx, y), s, font=f, fill=fill, anchor="ma")


# ============================================================ 静态背景（只画一次）

def build_static():
    img = Image.new("RGB", (W, H), BG_TOP)
    d = ImageDraw.Draw(img)

    # 竖向渐变
    grad = np.linspace(0, 1, H).reshape(H, 1)
    top = np.array(BG_TOP, dtype=np.float64)
    bottom = np.array(BG_BOTTOM, dtype=np.float64)
    colors = top + (bottom - top) * grad
    arr = np.repeat(colors[:, None, :], W, axis=1).astype(np.uint8)
    img = Image.fromarray(arr)
    d = ImageDraw.Draw(img)

    # 右侧琥珀光晕
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((1180, -260, 2180, 780), fill=(92, 58, 22))
    gd.ellipse((-320, 620, 720, 1420), fill=(26, 44, 78))
    glow = glow.filter(ImageFilter.GaussianBlur(180))
    img = Image.blend(img, Image.blend(img, glow, 1.0), 0.55)

    d = ImageDraw.Draw(img)

    # 顶部标题条
    rr(d, (0, 0, W, 104), 0, fill=(16, 22, 38))
    d.line([(0, 104), (W, 104)], fill=(40, 52, 80), width=2)
    rr(d, (60, 32, 104, 72), 12, fill=AMBER)
    draw_text(d, (74, 52), "C", mono(30, True), (30, 20, 8), anchor="mm")
    draw_text(d, (124, 52), "搭配记忆", zh(30, True), WHITE, anchor="lm")
    draw_text(d, (252, 54), "CollocationMemory", mono(19), DIM, anchor="lm")
    draw_text(
        d, (W - 60, 52),
        "固定搭配 · 间隔重复 · 拼写巩固 · 学情统计",
        zh(20), DIM, anchor="rm",
    )

    # 手机外框
    PH_X, PH_Y, PH_W, PH_H = 120, 138, 470, 920
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (PH_X + 8, PH_Y + 14, PH_X + PH_W + 8, PH_Y + PH_H + 14), radius=54, fill=(0, 0, 0, 150)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(26))
    img = Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB")
    d = ImageDraw.Draw(img)
    rr(d, (PH_X, PH_Y, PH_X + PH_W, PH_Y + PH_H), 52, fill=(14, 18, 30), outline=(70, 84, 118), width=3)
    rr(d, (PH_X + 14, PH_Y + 14, PH_X + PH_W - 14, PH_Y + PH_H - 14), 42, fill=(9, 12, 22))
    # 听筒
    rr(d, (PH_X + PH_W // 2 - 42, PH_Y + 30, PH_X + PH_W // 2 + 42, PH_Y + 38), 4, fill=(38, 46, 68))

    # 代码面板（底）
    CP_X, CP_Y, CP_W, CP_H = 680, 150, 1170, 760
    rr(d, (CP_X, CP_Y, CP_X + CP_W, CP_Y + CP_H), 22, fill=PANEL, outline=PANEL_EDGE, width=2)
    rr(d, (CP_X, CP_Y, CP_X + CP_W, CP_Y + 54), 22, fill=(30, 39, 62))
    d.rectangle((CP_X, CP_Y + 40, CP_X + CP_W, CP_Y + 54), fill=(30, 39, 62))
    rr(d, (CP_X + 26, CP_Y + 62, CP_X + CP_W - 26, CP_Y + CP_H - 22), 14, fill=CODE_BG)

    # 字幕条（底）
    SB_X, SB_Y, SB_W, SB_H = 680, 946, 1170, 92
    rr(d, (SB_X, SB_Y, SB_X + SB_W, SB_Y + SB_H), 18, fill=PANEL, outline=PANEL_EDGE, width=2)

    return img


# ============================================================ 代码高亮

TOKEN_RE = re.compile(r'("(?:[^"\\]|\\.)*?"|//.*|@\w+|\b\d+(?:\.\d+)?\b|[A-Za-z_][A-Za-z0-9_]*|\s+|.)')


def tokenize(line):
    """把一行切成 token + 颜色，供逐段绘制。"""
    out = []
    for m in TOKEN_RE.finditer(line):
        tk = m.group(0)
        if tk.startswith("//"):
            color = (106, 153, 85)          # 注释：绿
        elif tk.startswith('"'):
            color = (206, 145, 120)         # 字符串：橙
        elif tk.startswith("@"):
            color = (200, 200, 140)
        elif tk[0].isdigit():
            color = (181, 206, 168)
        elif tk in KEYWORDS:
            color = (197, 134, 192)         # 关键字：紫
        elif tk in TYPES or (tk[0].isupper() and tk.isalpha()):
            color = (78, 201, 176)          # 类型：青
        elif tk.strip() == "":
            color = None
        else:
            color = (212, 212, 212)         # 默认：浅灰
        out.append((tk, color))
    return out


def draw_code(d, chapter, t):
    CP_X, CP_Y, CP_W = 680, 150, 1170
    line_h = 38
    max_rows = 16
    # 代码行数各章不同：整体垂直居中，避免面板下半部大片留白
    voff = max(0, (max_rows - len(chapter["code"])) * line_h // 2)
    code_x = CP_X + 46
    code_y = CP_Y + 110 + voff
    f = mono(24)

    def token_font(tk):
        # Consolas 没有中文字形，含 CJK 的片段换雅黑，否则会渲染成方块
        if any(ord(c) > 0x2E7F for c in tk):
            return zh(21)
        return f

    lines = chapter["code"]
    total_chars = sum(len(l) + 1 for l in lines)
    appear = clamp01(t / 0.55)
    shown = int(total_chars * ease_out(appear))

    # 文件名
    draw_text(d, (CP_X + 26, CP_Y + 27), chapter["file"], mono(19), AMBER, anchor="lm")
    rr(d, (CP_X + CP_W - 150, CP_Y + 14, CP_X + CP_W - 26, CP_Y + 40), 13, fill=(45, 58, 88))
    draw_text(d, (CP_X + CP_W - 88, CP_Y + 27), "Kotlin", mono(17), DIM, anchor="mm")

    # 焦点行高亮（打字机完成后才亮，模拟「视线落在这里」）
    focus = chapter["focus_line"]
    if t > 0.5:
        a = clamp01((t - 0.5) / 0.2)
        hy = code_y + focus * line_h
        rr(
            d, (CP_X + 30, hy - 4, CP_X + CP_W - 30, hy + line_h - 8),
            8, fill=(int(255 * 0.10 * a), int(179 * 0.10 * a), int(71 * 0.10 * a)),
        )
        d.rectangle((CP_X + 30, hy - 4, CP_X + 34, hy + line_h - 8), fill=AMBER)

    # 逐行绘制
    used = 0
    for i, line in enumerate(lines):
        y = code_y + i * line_h
        if used >= shown:
            break
        remain = shown - used
        seg = line[:remain]
        x = code_x
        for tk, color in tokenize(seg):
            if color is None:
                x += text_w(d, tk, f)
                continue
            tf = token_font(tk)
            d.text((x, y + (line_h - 30) // 2 - 2), tk, font=tf, fill=color)
            x += text_w(d, tk, tf)
        used += len(line) + 1

    # 光标
    if appear < 1.0:
        li = 0
        acc = 0
        for i, line in enumerate(lines):
            if acc + len(line) + 1 > shown:
                li = i
                break
            acc += len(line) + 1
        seg = lines[li][: max(0, shown - acc)]
        cursor_x = code_x + text_w(d, seg, f)
        d.rectangle((cursor_x, code_y + li * line_h, cursor_x + 2, code_y + li * line_h + 26), fill=AMBER)


# ============================================================ 手机屏幕

SX, SY, SW, SH = 136, 152, 438, 892


def ph_status_bar(d):
    draw_text(d, (SX + 26, SY + 22), "10:13", mono(15), DIM, anchor="lm")
    for i in range(4):
        x = SX + SW - 30 - i * 12
        rr(d, (x, SY + 16, x + 8, SY + 28), 2, fill=DIM)
    rr(d, (SX + SW - 92, SY + 18, SX + SW - 74, SY + 26), 3, fill=DIM)


def ph_top_bar(d, title, actions=None, y=None):
    """App 顶栏：返回箭头 + 标题 + 右侧胶囊按钮"""
    y = y or SY + 58
    d.line([(SX + 28, y), (SX + 40, y - 10), (SX + 40, y + 10)], fill=WHITE, width=3)
    d.line([(SX + 28, y), (SX + 40, y - 10)], fill=WHITE, width=3)
    draw_text(d, (SX + 60, y), title, zh(23, True), WHITE, anchor="lm")
    if actions:
        x = SX + SW - 28
        for label, highlight, scale in reversed(actions):
            w = 46 * scale
            rr(
                d, (x - w, y - 17 * scale, x, y + 17 * scale), 10,
                fill=AMBER if highlight else (255, 255, 255, 40),
                outline=AMBER if highlight else (255, 255, 255, 140), width=2,
            )
            draw_text(d, (x - w / 2, y), label, zh(17, True), (28, 18, 6) if highlight else WHITE, anchor="mm")
            x -= w + 12


def ph_card(d, top, english, chinese, note=None, chip=False, height=250):
    """大卡片：英文 + 释义（内容在卡内略偏中上，与真实 App 一致）"""
    rr(d, (SX + 24, top, SX + SW - 24, top + height), 20, fill=(28, 36, 58), outline=(62, 78, 116), width=2)
    center_text(d, SX + SW // 2, top + 74, english, zh(33, True), WHITE)
    center_text(d, SX + SW // 2, top + 128, chinese, zh(21), AMBER_SOFT)
    if note:
        center_text(d, SX + SW // 2, top + 174, note, zh(17), DIM)
    if chip:
        cx = SX + SW - 46
        rr(d, (cx - 26, top + 16, cx + 26, top + 48), 12, fill=(70, 190, 120))
        draw_text(d, (cx, top + 32), "记", zh(17, True), (12, 30, 20), anchor="mm")


def ph_button(d, top, label, amber=True, height=54, alpha=1.0):
    col = AMBER if amber else (52, 66, 100)
    fg = (28, 18, 6) if amber else WHITE
    if alpha < 1.0:
        col = tuple(int(c * alpha) for c in col)
        fg = tuple(int(c * alpha) for c in fg)
    rr(d, (SX + 24, top, SX + SW - 24, top + height), 16, fill=col)
    center_text(d, SX + SW // 2, top + height // 2, label, zh(22, True), fg)


def ph_bottom_nav(d):
    y = SY + SH - 46
    labels = ["首页", "统计", "词本", "设置"]
    for i, lb in enumerate(labels):
        cx = SX + 60 + i * ((SW - 120) // 3)
        col = AMBER if i == 0 else (130, 145, 176)
        rr(d, (cx - 22, y - 16, cx + 22, y - 2), 6, fill=col if i == 0 else (70, 84, 116))
        draw_text(d, (cx, y + 12), lb, zh(14), col, anchor="mm")


def ph_sound_waves(d, cx, cy, t):
    """朗读声波：三圈同心弧向外扩散"""
    for k in range(3):
        ph = (t * 1.6 + k * 0.33) % 1.0
        r = 26 + ph * 62
        a = int(200 * (1 - ph))
        d.arc((cx - r, cy - r, cx + r, cy + r), -50, 50, fill=(255, 179, 71, a), width=4)
        d.arc((cx - r, cy - r, cx + r, cy + r), 130, 230, fill=(255, 179, 71, a), width=4)


# ---------------------------------------------------------------- 场景

def scene_home(d, t):
    ph_status_bar(d)
    title_y = SY + 84
    center_text(d, SX + SW // 2, title_y, "搭配记忆", zh(27, True), WHITE)
    center_text(d, SX + SW // 2, title_y + 32, "10月13日 · 已连续打卡 9 天", zh(15), DIM)

    # 今日概览玻璃卡
    cx0, cy0 = SX + 24, SY + 142
    rr(d, (cx0, cy0, cx0 + SW - 48, cy0 + 252), 20, fill=(34, 44, 70), outline=(72, 90, 132), width=2)
    draw_text(d, (cx0 + 24, cy0 + 18), "今日概览", zh(18, True), WHITE, anchor="lm")
    draw_text(d, (cx0 + SW - 72, cy0 + 20), "查看明细 ›", zh(13), DIM, anchor="lm")

    stats = [("学习&复习", 104, ""), ("累计学习", 403, ""), ("今日时长", 28, "分"), ("累计时长", 1260, "分")]
    for i, (lb, val, unit) in enumerate(stats):
        col = i % 2
        row = i // 2
        x = cx0 + 30 + col * ((SW - 100) // 2)
        y = cy0 + 56 + row * 82
        v = int(val * ease_out(clamp01((t - 0.05 - i * 0.06) / 0.5)))
        vs = str(v)
        vw = text_w(d, vs, zh(28, True))
        center_text(d, x + 78, y, vs, zh(28, True), AMBER)
        if unit:
            draw_text(d, (x + 78 + vw / 2 + 5, y + 4), unit, zh(13), DIM, anchor="lm")
        center_text(d, x + 78, y + 36, lb, zh(13), DIM)

    draw_text(d, (cx0 + 24, cy0 + 214), "蓝哥固定搭配单词本 ▾", zh(14, True), WHITE, anchor="lm")

    # 新学 / 复习
    by = SY + 424
    rr(d, (SX + 24, by, SX + SW // 2 - 8, by + 118), 18, fill=(40, 52, 82))
    center_text(d, (SX + 24 + SX + SW // 2 - 8) / 2, by + 42, "新学", zh(25, True), WHITE)
    center_text(d, (SX + 24 + SX + SW // 2 - 8) / 2, by + 82, "还有 186 条没学", zh(14), DIM)
    rr(d, (SX + SW // 2 + 8, by, SX + SW - 24, by + 118), 18, fill=(40, 52, 82))
    center_text(d, (SX + SW // 2 + 8 + SX + SW - 24) / 2, by + 42, "复习", zh(25, True), WHITE)
    center_text(d, (SX + SW // 2 + 8 + SX + SW - 24) / 2, by + 82, "今天该复习 41 条", zh(14), DIM)

    # 生词本 / 已掌握 / 明日
    ty = SY + 570
    cells = [("生词本", "37 条"), ("已掌握", "268 条"), ("明日复习", "38 条")]
    for i, (lb, val) in enumerate(cells):
        x = SX + 24 + i * ((SW - 48) // 3)
        w = (SW - 48) // 3 - 10
        rr(d, (x, ty, x + w, ty + 86), 14, fill=(34, 44, 70), outline=(72, 90, 132), width=2)
        center_text(d, x + w / 2, ty + 30, val, zh(18, True), AMBER)
        center_text(d, x + w / 2, ty + 60, lb, zh(13), DIM)

    rr(d, (SX + 24, SY + 682, SX + SW - 24, SY + 740), 16, fill=(40, 52, 82))
    draw_text(d, (SX + 44, SY + 711), "今日概览 · 明细点这里", zh(16), WHITE, anchor="lm")
    ph_bottom_nav(d)


def scene_learn(d, t):
    ph_status_bar(d)
    ph_top_bar(d, "新学", actions=[("熟", False, 1.0), ("英", False, 1.0)])
    prog = 3
    center_text(d, SX + SW // 2, SY + 116, f"{prog} / 20", zh(17), DIM)
    px0, px1 = SX + 40, SX + SW - 40
    rr(d, (px0, SY + 146, px1, SY + 154), 4, fill=(52, 66, 100))
    rr(d, (px0, SY + 146, px0 + (px1 - px0) * (prog / 20), SY + 154), 4, fill=AMBER)

    top = SY + 186
    ph_card(d, top, "take part in", "参加；参与", chip=True)
    # 朗读声波：卡片右上角「记」签左侧，小弧不压字
    if t > 0.05:
        ph_sound_waves(d, SX + SW - 118, top + 32, t * 3)
    draw_text(d, (SX + SW // 2, top + 232), "自动朗读中", zh(15), AMBER_SOFT, anchor="mm")
    ph_button(d, SY + 470, "看答案", amber=False)
    draw_text(d, (SX + SW // 2, SY + 560), "点顶栏「英」可以再听一遍", zh(15), DIM, anchor="mm")
    ph_bottom_nav(d)


def scene_familiar(d, t):
    ph_status_bar(d)
    pulse = 1.0 if (t * 4 % 2) < 1.0 else 1.12
    ph_top_bar(d, "新学", actions=[("熟", True, pulse), ("英", False, 1.0)])
    center_text(d, SX + SW // 2, SY + 116, "4 / 20", zh(17), DIM)
    px0, px1 = SX + 40, SX + SW - 40
    rr(d, (px0, SY + 146, px1, SY + 154), 4, fill=(52, 66, 100))
    rr(d, (px0, SY + 146, px0 + (px1 - px0) * 0.2, SY + 154), 4, fill=AMBER)

    # 卡片随点击上浮淡出（t>0.45 后）
    lift = int(ease_out(clamp01((t - 0.45) / 0.5)) * 40)
    top = SY + 186 - lift
    ph_card(d, top, "take part in", "参加；参与", note="已标记为「熟」→ 跳过拼写", chip=True)
    if t > 0.45:
        rr(d, (SX + 24, SY + 480, SX + SW - 24, SY + 540), 14, fill=(70, 190, 120))
        center_text(d, SX + SW // 2, SY + 510, "直接进入复习，不再拼写", zh(18, True), (12, 30, 20))
    else:
        ph_button(d, SY + 470, "看答案", amber=False)
    ph_bottom_nav(d)


def scene_review(d, t):
    ph_status_bar(d)
    ph_top_bar(d, "复习", actions=[("英", False, 1.0)])
    center_text(d, SX + SW // 2, SY + 116, "7 / 41", zh(17), DIM)
    px0, px1 = SX + 40, SX + SW - 40
    rr(d, (px0, SY + 146, px1, SY + 154), 4, fill=(52, 66, 100))
    rr(d, (px0, SY + 146, px0 + (px1 - px0) * (7 / 41), SY + 154), 4, fill=AMBER)

    top = SY + 186
    revealed = t > 0.5
    flip = ease_out(clamp01((t - 0.5) / 0.35)) if revealed else 0.0
    if not revealed:
        ph_card(d, top, "make up for", "弥补；补偿", chip=True)
        ph_button(d, SY + 470, "看答案", amber=True)
    else:
        # 翻面：英文缩小，中文释义与例句浮出
        rr(d, (SX + 24, top, SX + SW - 24, top + 250), 20, fill=(28, 36, 58), outline=(62, 78, 116), width=2)
        center_text(d, SX + SW // 2, top + 68, "make up for", zh(27, True), WHITE)
        a = int(255 * flip)
        center_text(d, SX + SW // 2, top + 124, "弥补；补偿", zh(24, True), (255, 179, 71, a))
        center_text(
            d, SX + SW // 2, top + 166,
            "Nothing can make up for the lost time.", zh(15), (176, 188, 214, a)
        )
        center_text(d, SX + SW // 2, top + 206, "★ 下次复习：3 天后", zh(15), (94, 214, 138, a))
        # 记住 / 忘记
        rr(d, (SX + 24, SY + 470, SX + SW // 2 - 8, SY + 524), 14, fill=(70, 190, 120))
        center_text(d, (SX + 24 + SX + SW // 2 - 8) / 2, SY + 497, "记住", zh(20, True), (12, 30, 20))
        rr(d, (SX + SW // 2 + 8, SY + 470, SX + SW - 24, SY + 524), 14, fill=(214, 96, 96))
        center_text(d, (SX + SW // 2 + 8 + SX + SW - 24) / 2, SY + 497, "忘记", zh(20, True), WHITE)
    ph_bottom_nav(d)


def scene_spell(d, t):
    ph_status_bar(d)
    ph_top_bar(d, "拼写巩固")
    center_text(d, SX + SW // 2, SY + 116, "2 / 12", zh(17), DIM)

    rr(d, (SX + 24, SY + 156, SX + SW - 24, SY + 232), 18, fill=(28, 36, 58), outline=(62, 78, 116), width=2)
    center_text(d, SX + SW // 2, SY + 176, "弥补；补偿", zh(22, True), WHITE)
    center_text(d, SX + SW // 2, SY + 208, "首字母 m · 3 个单词", zh(14), DIM)

    target = "make up for"
    typed_n = int(len(target) * ease_out(clamp01(t / 0.5)))
    typed = target[:typed_n]
    rr(d, (SX + 24, SY + 252, SX + SW - 24, SY + 320), 14, fill=(18, 24, 40), outline=(70, 88, 130), width=2)
    draw_text(d, (SX + 44, SY + 286), typed, mono(24), WHITE, anchor="lm")
    if t < 0.5:
        cx = SX + 44 + text_w(d, typed, mono(24))
        d.rectangle((cx, SY + 266, cx + 3, SY + 306), fill=AMBER)

    # 键盘（示意）
    rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    ky = SY + 344
    for ri, row in enumerate(rows):
        kw = (SW - 60) // len(row) - 4
        for ci, ch in enumerate(row):
            x = SX + 30 + ci * (kw + 4)
            hot = ch in typed and t < 0.55
            rr(d, (x, ky + ri * 46, x + kw, ky + ri * 46 + 38), 8,
               fill=AMBER if hot else (40, 52, 82))
            draw_text(d, (x + kw / 2, ky + ri * 46 + 19), ch, mono(17),
                      (28, 18, 6) if hot else WHITE, anchor="mm")

    if t > 0.62:
        a = clamp01((t - 0.62) / 0.2)
        rr(d, (SX + 24, SY + 500, SX + SW - 24, SY + 560), 14,
           fill=(int(70 * a), int(190 * a), int(120 * a)))
        center_text(d, SX + SW // 2, SY + 530, "√ 正确 · 冠词写漏了也不算错", zh(18, True),
                    (int(12 * a), int(30 * a), int(20 * a)))
    ph_bottom_nav(d)


def scene_notebook(d, t):
    ph_status_bar(d)
    ph_top_bar(d, "笔记本")
    center_text(d, SX + SW // 2, SY + 104, "共 12 条 · 仅保存在本机", zh(15), DIM)

    entries = [
        ("take part in", "参加；参与", "写作常用，接 activities"),
        ("make up for", "弥补；补偿", "完型填空高频"),
        ("pay attention to", "注意；重视", ""),
    ]
    y = SY + 136
    for i, (en, cn, note) in enumerate(entries):
        appear = clamp01((t - 0.1 - i * 0.16) / 0.3)
        if appear <= 0:
            continue
        h = 118
        off = int((1 - ease_out(appear)) * 24)
        rr(d, (SX + 24, y + off, SX + SW - 24, y + h + off), 16, fill=(28, 36, 58), outline=(62, 78, 116), width=2)
        draw_text(d, (SX + 44, y + 26 + off), en, zh(21, True), WHITE, anchor="lm")
        draw_text(d, (SX + 44, y + 58 + off), cn, zh(16), AMBER_SOFT, anchor="lm")
        if i == 0:
            note_txt = note[: int(len(note) * ease_out(clamp01((t - 0.45) / 0.35)))]
            draw_text(d, (SX + 44, y + 90 + off), "备注：" + note_txt, zh(15), DIM, anchor="lm")
            if t > 0.45:
                rr(d, (SX + SW - 92, y + 82 + off, SX + SW - 40, y + 104 + off), 10, fill=(70, 190, 120))
                draw_text(d, (SX + SW - 66, y + 93 + off), "保存", zh(14, True), (12, 30, 20), anchor="mm")
        elif note:
            draw_text(d, (SX + 44, y + 90 + off), "备注：" + note, zh(15), DIM, anchor="lm")
        y += h + 14
    ph_bottom_nav(d)


def scene_stats(d, t):
    ph_status_bar(d)
    ph_top_bar(d, "统计")
    center_text(d, SX + SW // 2, SY + 104, "最近 7 天", zh(15), DIM)

    # 柱状图
    gx0, gy0, gw, gh = SX + 40, SY + 150, SW - 80, 190
    vals = [42, 68, 55, 96, 34, 78, 104]
    days = ["一", "二", "三", "四", "五", "六", "日"]
    rr(d, (gx0, gy0, gx0 + gw, gy0 + gh), 14, fill=(28, 36, 58), outline=(62, 78, 116), width=2)
    bw = (gw - 40) // 7 - 10
    for i, v in enumerate(vals):
        grow = ease_out(clamp01((t - 0.08 - i * 0.05) / 0.45))
        bh = int((v / 110) * (gh - 50) * grow)
        x = gx0 + 20 + i * (bw + 10)
        rr(d, (x, gy0 + gh - 30 - bh, x + bw, gy0 + gh - 30), 5, fill=AMBER if i == 6 else (92, 128, 196))
        draw_text(d, (x + bw / 2, gy0 + gh - 12), days[i], zh(13), DIM, anchor="mm")

    # 三个数字卡
    cards = [("累计学习", "403 条"), ("连续打卡", "9 天"), ("累计时长", "1260 分")]
    y = SY + 380
    for i, (lb, val) in enumerate(cards):
        x = SX + 24 + i * ((SW - 48) // 3)
        w = (SW - 48) // 3 - 10
        rr(d, (x, y, x + w, y + 96), 14, fill=(34, 44, 70), outline=(72, 90, 132), width=2)
        center_text(d, x + w / 2, y + 38, val, zh(20, True), AMBER)
        center_text(d, x + w / 2, y + 70, lb, zh(14), DIM)

    rr(d, (SX + 24, SY + 506, SX + SW - 24, SY + 566), 14, fill=(34, 44, 70))
    draw_text(d, (SX + 44, SY + 536), "已掌握 268 条 · 生词本 37 条", zh(16), WHITE, anchor="lm")
    ph_bottom_nav(d)


def scene_update(d, t):
    ph_status_bar(d)
    # 背景首页（虚化示意）
    center_text(d, SX + SW // 2, SY + 110, "搭配记忆", zh(26, True), (120, 132, 158))
    rr(d, (SX + 24, SY + 160, SX + SW - 24, SY + 370), 20, fill=(30, 38, 58))
    ph_bottom_nav(d)

    # 遮罩
    d.rectangle((SX, SY, SX + SW, SY + SH), fill=(6, 9, 16, 170))
    # 弹窗
    dx0, dy0 = SX + 20, SY + 196
    dw, dh = SW - 40, 344
    rr(d, (dx0, dy0, dx0 + dw, dy0 + dh), 22, fill=(32, 41, 64), outline=(80, 98, 142), width=2)
    center_text(d, dx0 + dw / 2, dy0 + 40, "发现新版本 4.2", zh(23, True), WHITE)
    center_text(d, dx0 + dw / 2, dy0 + 72, "当前 v4.1.1 · 更新包 20.8 MB", zh(15), DIM)

    rr(d, (dx0 + 22, dy0 + 96, dx0 + dw - 22, dy0 + 190), 12, fill=(20, 27, 44))
    notes = [
        "新增单词朗读：翻到卡片自动念一遍",
        "顶栏「英」按钮随时重听",
        "设置页可关闭自动朗读",
    ]
    for i, s in enumerate(notes):
        a = clamp01((t - 0.1 - i * 0.08) / 0.25)
        draw_text(d, (dx0 + 40, dy0 + 114 + i * 26), "· " + s, zh(14), (int(200 * a), int(210 * a), int(226 * a)), anchor="lm")

    # 进度条
    prog = ease_out(clamp01((t - 0.35) / 0.45))
    px0, px1 = dx0 + 24, dx0 + dw - 24
    rr(d, (px0, dy0 + 214, px1, dy0 + 228), 7, fill=(52, 66, 100))
    rr(d, (px0, dy0 + 214, px0 + (px1 - px0) * prog, dy0 + 228), 7, fill=AMBER)
    label = "校验中（大小 / SHA-256 / 签名）" if prog < 1 else "校验通过，点击安装"
    center_text(d, dx0 + dw / 2, dy0 + 250, label, zh(14), AMBER_SOFT)

    col = AMBER if prog >= 1 else (52, 66, 100)
    fg = (28, 18, 6) if prog >= 1 else DIM
    rr(d, (dx0 + 24, dy0 + 280, dx0 + dw - 24, dy0 + 324), 14, fill=col)
    center_text(d, dx0 + dw / 2, dy0 + 302, "立即更新", zh(19, True), fg)


SCENES = {
    "home": scene_home,
    "learn": scene_learn,
    "familiar": scene_familiar,
    "review": scene_review,
    "spell": scene_spell,
    "notebook": scene_notebook,
    "stats": scene_stats,
    "update": scene_update,
}


# ============================================================ 字幕 / 进度

def draw_caption(d, text, t):
    SB_X, SB_Y, SB_W, SB_H = 680, 946, 1170, 92
    a = clamp01(t / 0.16)
    slide = int((1 - ease_out(a)) * 16)
    rr(d, (SB_X + 18, SB_Y + 22 + slide, SB_X + 24, SB_Y + SB_H - 22 + slide), 3, fill=AMBER)
    draw_text(
        d, (SB_X + 46, SB_Y + SB_H // 2 + slide), text, zh(31, True),
        (int(255 * a), int(255 * a), int(255 * a)), anchor="lm",
    )


def draw_progress(d, index):
    """章节进度点：放在字幕条右端（顶栏右侧被标语占了）"""
    n = len(CHAPTERS)
    total_w = n * 26
    SB_X, SB_Y, SB_W, SB_H = 680, 946, 1170, 92
    x0 = SB_X + SB_W - 40 - total_w
    cy = SB_Y + SB_H // 2
    for i in range(n):
        cx = x0 + i * 26 + 7
        if i == index:
            rr(d, (cx - 7, cy - 7, cx + 7, cy + 7), 7, fill=AMBER)
        else:
            rr(d, (cx - 5, cy - 5, cx + 5, cy + 5), 5, fill=(80, 96, 132))


def draw_outro(d, t):
    # 先用背景色把手机与面板区域盖掉，避免空框分散注意力
    a0 = clamp01(t / 0.25)
    veil = (int(11 * a0), int(16 * a0), int(28 * a0))
    alpha = int(255 * a0)
    rr(d, (100, 124, 1870, 1058), 40, fill=veil + (alpha,))

    # 中央大标题
    a1 = clamp01(t / 0.25)
    center_text(d, W // 2, 380, OUTRO["title"], zh(84, True),
                (int(255 * a1), int(255 * a1), int(255 * a1)))
    a2 = clamp01((t - 0.2) / 0.3)
    center_text(d, W // 2, 486, OUTRO["subtitle"], zh(34),
                (int(255 * a2), int(200 * a2), int(122 * a2)))
    d.line([(W // 2 - 120, 552), (W // 2 + 120, 552)], fill=AMBER, width=3)
    for i, b in enumerate(OUTRO["bullets"]):
        a = clamp01((t - 0.35 - i * 0.14) / 0.3)
        if a <= 0:
            continue
        off = int((1 - ease_out(a)) * 18)
        center_text(d, W // 2, 596 + i * 54 + off, "· " + b, zh(24),
                    (int(210 * a), int(220 * a), int(238 * a)))
    a3 = clamp01((t - 0.8) / 0.2)
    center_text(d, W // 2, 812, OUTRO["version"], zh(26, True),
                (int(255 * a3), int(179 * a3), int(71 * a3)))


# ============================================================ 主流程

def render_frame(static, fi, chapter_frames):
    """渲染单帧（供主流程与预览共用）"""
    img = static.copy()
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if fi < chapter_frames * len(CHAPTERS):
        ci = fi // chapter_frames
        local = (fi % chapter_frames) / chapter_frames
        ch = CHAPTERS[ci]
        draw_progress(d, ci)
        SCENES[ch["scene"]](d, local)
        draw_code(d, ch, local)
        draw_caption(d, ch["caption"], local)
    else:
        t = (fi - chapter_frames * len(CHAPTERS)) / int(OUTRO_SEC * FPS)
        draw_outro(d, t)
    return Image.alpha_composite(img, layer).convert("RGB")


def preview():
    """冒烟测试：每章取一个中间帧存 PNG，人工核对排版"""
    static = build_static().convert("RGBA")
    cf = int(CHAPTER_SEC * FPS)
    picks = [cf * i + int(cf * 0.75) for i in range(len(CHAPTERS))]
    picks.append(cf * len(CHAPTERS) + int(OUTRO_SEC * FPS * 0.9))
    for i, fi in enumerate(picks):
        img = render_frame(static, fi, cf)
        p = os.path.join(OUT_DIR, "frames", f"preview_{i:02d}.png")
        img.save(p)
        print("保存", p)
    print("预览帧已生成")


def main():
    print("渲染静态背景…")
    static = build_static().convert("RGBA")

    chapter_frames = int(CHAPTER_SEC * FPS)
    outro_frames = int(OUTRO_SEC * FPS)
    total = chapter_frames * len(CHAPTERS) + outro_frames
    print(f"总帧数 {total}（约 {total / FPS:.1f} 秒）")

    # 编码
    try:
        import imageio_ffmpeg
        writer = imageio_ffmpeg.write_frames(
            OUT_MP4, (W, H), fps=FPS, codec="libx264", quality=None,
            output_params=["-crf", "18", "-pix_fmt", "yuv420p", "-preset", "medium"],
            macro_block_size=1,
        )
        writer.send(None)
        use_imageio = True
        print("编码器：H.264 (libx264)")
    except Exception as e:
        import cv2
        writer = cv2.VideoWriter(OUT_MP4, cv2.VideoWriter_fourcc(*"mp4v"), FPS, (W, H))
        use_imageio = False
        print(f"编码器回退 mp4v（{e}）")

    for fi in range(total):
        img = static.copy()
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)

        if fi < chapter_frames * len(CHAPTERS):
            ci = fi // chapter_frames
            local = (fi % chapter_frames) / chapter_frames
            ch = CHAPTERS[ci]
            draw_progress(d, ci)
            SCENES[ch["scene"]](d, local)
            draw_code(d, ch, local)
            draw_caption(d, ch["caption"], local)
        else:
            t = (fi - chapter_frames * len(CHAPTERS)) / outro_frames
            draw_outro(d, t)

        out = Image.alpha_composite(img, layer).convert("RGB")
        frame = np.asarray(out)[:, :, ::-1]  # RGB -> BGR
        if use_imageio:
            writer.send(np.asarray(out).tobytes())
        else:
            writer.write(frame)

        if fi % 150 == 0:
            print(f"  帧 {fi}/{total}")

    if use_imageio:
        writer.close()
    else:
        writer.release()
    print("完成：", OUT_MP4)


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview()
    else:
        main()

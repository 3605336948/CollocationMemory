# -*- coding: utf-8 -*-
"""把各章代表帧缩拼成一张对照图，便于一眼找出遮挡/偏移。"""

import os
from PIL import Image, ImageDraw, ImageFont

import render_promo as R

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "frames", "contact_sheet.png")


def main():
    static = R.build_static().convert("RGBA")
    cf = int(R.CHAPTER_SEC * R.FPS)

    picks = []
    for i, ch in enumerate(R.CHAPTERS):
        for frac in (0.35, 0.9):
            picks.append((f"{i+1}·{ch['scene']}", cf * i + int(cf * frac)))
    picks.append(("结尾", cf * len(R.CHAPTERS) + int(R.OUTRO_SEC * R.FPS * 0.9)))

    TW, TH = 480, 270
    cols = 4
    rows = (len(picks) + cols - 1) // cols
    sheet = Image.new("RGB", (TW * cols, TH * rows + 30 * rows), (8, 10, 16))
    sd = ImageDraw.Draw(sheet)
    f = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 18)

    for idx, (label, fi) in enumerate(picks):
        img = R.render_frame(static, fi, cf).resize((TW, TH), Image.LANCZOS)
        cx = (idx % cols) * TW
        cy = (idx // cols) * (TH + 30)
        sheet.paste(img, (cx, cy + 30))
        sd.text((cx + 8, cy + 6), label, font=f, fill=(255, 179, 71))
        sd.rectangle((cx, cy + 30, cx + TW - 1, cy + 30 + TH - 1), outline=(60, 74, 104))

    sheet.save(OUT)
    print("保存", OUT)


if __name__ == "__main__":
    main()

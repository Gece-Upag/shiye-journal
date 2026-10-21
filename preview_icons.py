# -*- coding: utf-8 -*-
"""预览：读实际生成的 mipmap 资源，渲染三种启动器遮罩下的样子。

产物写到 docs/launcher_icon_preview.png（README 用的就是这张图）。
用法：python preview_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
RES = ROOT / "app" / "src" / "main" / "res"
OUT = ROOT / "docs" / "launcher_icon_preview.png"

fg = Image.open(RES / "mipmap-xxxhdpi" / "ic_launcher_foreground.webp").convert("RGBA")
SIZE = 300
CANVAS = 108.0
VIEW = 66.0


def shaped(mask_draw_fn):
    canvas = Image.new("RGBA", (SIZE, SIZE), (0xFC, 0xF8, 0xED, 255))
    inner = round(SIZE * VIEW / CANVAS)
    scaled = fg.resize((inner, inner), Image.LANCZOS)
    canvas.paste(scaled, ((SIZE - inner) // 2, (SIZE - inner) // 2), scaled)
    mask = Image.new("L", (SIZE, SIZE), 0)
    mask_draw_fn(ImageDraw.Draw(mask))
    result = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    result.paste(canvas, (0, 0), mask)
    return result


def circle(d):
    d.ellipse((0, 0, SIZE, SIZE), fill=255)


def rounded(d):
    d.rounded_rectangle((0, 0, SIZE, SIZE), radius=int(SIZE * 0.22), fill=255)


def squircle(d):
    d.rounded_rectangle((0, 0, SIZE, SIZE), radius=int(SIZE * 0.42), fill=255)


board = Image.new("RGBA", (SIZE * 3 + 80, SIZE + 60), (0x2A, 0x2E, 0x2A, 255))
for i, fn in enumerate([circle, rounded, squircle]):
    img = shaped(fn)
    board.paste(img, (20 + i * (SIZE + 20), 30), img)

board.save(OUT)
print("saved", OUT)

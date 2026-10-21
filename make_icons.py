# -*- coding: utf-8 -*-
"""把一张图片生成为 Android 启动器图标的全部资源。

生成三套东西：
1. legacy 方形 ic_launcher.webp（不支持自适应图标的极老启动器兜底用）；
2. legacy 圆形 ic_launcher_round.webp（圆形遮罩）；
3. 自适应图标的前景（108dp 画布铺满）+ 纯色背景层（取卡片米色）。

用法：
    python make_icons.py [原图路径]

不传参数时默认读仓库根目录的 `绿色叶语笔记本图标.png`。
注意：CROP 是为「白底 + 居中米色圆角卡片」这版原图调的，换图后需要自己调。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "绿色叶语笔记本图标.png"
RES = ROOT / "app" / "src" / "main" / "res"

src = Image.open(SRC).convert("RGB")

# 裁掉外围白边（原图 = 白底 + 居中的米色圆角卡片），
# 只留卡片本身：铺满画布后遮罩内才是完整的一张“卡片”，而不是浮在小图上
w0, h0 = src.size
CROP = 0.105
src = src.crop((round(w0 * CROP), round(h0 * CROP), round(w0 * (1 - CROP)), round(h0 * (1 - CROP))))

# 取卡片内部米色当背景层颜色。采样点取 5% 高度：
# 再往下就碰到书本的绿色顶边了（0.125 处已经是绿）
w, h = src.size
bg = src.getpixel((w // 2, int(h * 0.05)))
print("背景色: #%02X%02X%02X" % bg)

DPI_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

for dpi, size in DPI_SIZES.items():
    folder = RES / f"mipmap-{dpi}"
    img = src.resize((size, size), Image.LANCZOS)
    img.save(folder / "ic_launcher.webp", "WEBP", lossless=True)

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    round_img = img.copy()
    round_img.putalpha(mask)
    round_img.save(folder / "ic_launcher_round.webp", "WEBP", lossless=True)

for dpi, size in FG_SIZES.items():
    folder = RES / f"mipmap-{dpi}"
    # 前景铺满 108dp 画布：书本主体占裁后图片约 60%，落在中间 66dp 安全区内
    canvas = src.resize((size, size), Image.LANCZOS).convert("RGBA")
    canvas.save(folder / "ic_launcher_foreground.webp", "WEBP", lossless=True)

print("done")

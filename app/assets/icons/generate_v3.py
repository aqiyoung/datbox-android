#!/usr/bin/env python3
"""OpenList APP 图标 v3 (极致扁平 + Teal 品牌色 + 拉开间距)

SensenNova v2 评估:
- 48px 圆盘粘连 → 缩小半径 + 拉开间距
- 缺 Teal-200 → 1 个圆用 teal-200, 2 个用纯白
- 去掉高光 + 白边 → 纯扁平
- 跟 OpenList 官方 res.oplist.org/logo.svg (sky-400 + teal-200 双色扁平) 风格贴
"""
import os
import math
from PIL import Image, ImageDraw

# OpenList 官方色
SKY_400 = (56, 189, 248)        # #38BDF8 (主背景)
SKY_500 = (14, 165, 233)        # #0EA5E9 (背景渐变深色)
TEAL_200 = (153, 246, 228)      # #99F6E4 (品牌强调色, 1 个圆盘用)
WHITE = (255, 255, 255)

SIZES = {
    'mdpi': 48, 'hdpi': 72, 'xhdpi': 96,
    'xxhdpi': 144, 'xxxhdpi': 192,
}
BASE_SIZE = 432


def radial_bg(size):
    """sky-400 → sky-500 渐变"""
    img = Image.new('RGB', (size, size), SKY_500)
    pixels = img.load()
    cx, cy = size * 0.5, size * 0.35
    max_dist = math.hypot(size, size) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx, y - cy) / max_dist
            d = min(1.0, d)
            t = d ** 1.2
            r = int(SKY_400[0] * (1 - t) + SKY_500[0] * t)
            g = int(SKY_400[1] * (1 - t) + SKY_500[1] * t)
            b = int(SKY_400[2] * (1 - t) + SKY_500[2] * t)
            pixels[x, y] = (r, g, b)
    return img


def compose_icon(size):
    """极致扁平: 3 圆盘 (1 teal + 2 白), 拉开间距, 圆形无椭圆压扁 (SensenNova 反而说椭圆压扁更好, 我保留这版圆形)"""
    bg = radial_bg(size).convert('RGBA')
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(bg)

    cx, cy = size * 0.5, size * 0.52
    # 缩小半径 + 加大间距 (SensenNova 关键建议)
    r = size * 0.16  # v2 是 0.20, 现在 0.16 (缩 20%)

    # Venn 三角 (3 个圆, 中心距比 v2 大 1.5x)
    # top 圆
    canvas_top = (cx, cy - size * 0.22)
    # right 圆
    canvas_right = (cx + size * 0.22, cy + size * 0.13)
    # left 圆
    canvas_left = (cx - size * 0.22, cy + size * 0.13)

    draw = ImageDraw.Draw(canvas)
    # 1 个 teal (top, 品牌强调色)
    draw.ellipse(
        [canvas_top[0] - r, canvas_top[1] - r, canvas_top[0] + r, canvas_top[1] + r],
        fill=TEAL_200
    )
    # 2 个白 (right, left)
    for cc in [canvas_right, canvas_left]:
        draw.ellipse(
            [cc[0] - r, cc[1] - r, cc[0] + r, cc[1] + r],
            fill=WHITE
        )

    return canvas


def main():
    out_dir = 'app/assets/icons'
    os.makedirs(out_dir, exist_ok=True)
    for name, size in SIZES.items():
        big = compose_icon(BASE_SIZE)
        img = big.resize((size, size), Image.LANCZOS)
        img.save(f'{out_dir}/openlist_icon_v3_{name}.png', 'PNG', optimize=True)
        mipmap_path = f'app/src/main/res/mipmap-{name}/ic_launcher.png'
        os.makedirs(os.path.dirname(mipmap_path), exist_ok=True)
        img.save(mipmap_path, 'PNG', optimize=True)
        print(f'  ✅ {name} {size}px')
    # round launcher
    for name, size in SIZES.items():
        big = compose_icon(BASE_SIZE)
        mask = Image.new('L', (BASE_SIZE, BASE_SIZE), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, BASE_SIZE, BASE_SIZE], fill=255)
        bg_layer = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (0, 0, 0, 0))
        bg_layer.paste(big, (0, 0), big)
        out = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (0, 0, 0, 0))
        out.paste(bg_layer, (0, 0), mask)
        out = out.resize((size, size), Image.LANCZOS)
        out.save(
            f'app/src/main/res/mipmap-{name}/ic_launcher_round.png',
            'PNG', optimize=True
        )
    print('\n✅ OpenList v3 极致扁平 + Teal 品牌色')


if __name__ == '__main__':
    main()

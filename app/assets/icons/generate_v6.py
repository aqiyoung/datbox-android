#!/usr/bin/env python3
"""OpenList APP 图标 v6: 跟 OpenList 官方 WEB 端完全一致 (白底)

老板 6/14 10:31 反馈:
'LOGO 你还是换成官方一致的, 它不是白色底吗? 咱这个你自己弄的那差一笔。'

- 跟官方保持一致: 白底 (官方 SVG 透明背景, 在白底上展示)
- 22% 圆角 (跟 v5 一样, 跟 Android adaptive icon 标准)
- sky-400 蓝 + teal-200 浅蓝绿
- cairosvg 渲染官方 SVG
- 5 mipmap 尺寸 + round launcher
"""
import os
import cairosvg
from PIL import Image, ImageDraw
from io import BytesIO

SVG_PATH = 'app/src/main/assets/openlist-logo.svg'

SIZES = {
    'mdpi': 48, 'hdpi': 72, 'xhdpi': 96,
    'xxhdpi': 144, 'xxxhdpi': 192,
}
BASE_SIZE = 1024

# 老板 6/14: 跟官方 web 端保持一致 - 白底
BG_COLOR = (255, 255, 255)  # #FFFFFF 白
CORNER_PCT = 0.22


def render_svg_png(size):
    return cairosvg.svg2png(url=SVG_PATH, output_width=size, output_height=size)


def make_mask_circle(size):
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size, size], fill=255)
    return mask


def make_mask_rounded(size, corner_pct=CORNER_PCT):
    mask = Image.new('L', (size, size), 0)
    corner = int(size * corner_pct)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1],
        radius=corner,
        fill=255,
    )
    return mask


def main():
    out_dir = 'app/assets/icons'
    os.makedirs(out_dir, exist_ok=True)
    # 预渲染 base size 一次
    big_bytes = render_svg_png(BASE_SIZE)
    big = Image.open(BytesIO(big_bytes)).convert('RGBA')
    # 老板 6/14 10:31: 跟官方 web 保持一致 - 白底
    bg = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), BG_COLOR + (255,))
    big_with_bg = Image.alpha_composite(bg, big)
    # 圆角 mask
    rounded_mask = make_mask_rounded(BASE_SIZE)
    for name, size in SIZES.items():
        final = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (0, 0, 0, 0))
        final.paste(big_with_bg, (0, 0), rounded_mask)
        img = final.resize((size, size), Image.LANCZOS)
        img.save(f'{out_dir}/openlist_icon_v6_{name}.png', 'PNG', optimize=True)
        mipmap_path = f'app/src/main/res/mipmap-{name}/ic_launcher.png'
        os.makedirs(os.path.dirname(mipmap_path), exist_ok=True)
        img.save(mipmap_path, 'PNG', optimize=True)
        print(f'  ✅ {name} {size}px (rounded square, 白底)')
    # round launcher (圆形)
    circle_mask = make_mask_circle(BASE_SIZE)
    for name, size in SIZES.items():
        final = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (0, 0, 0, 0))
        final.paste(big_with_bg, (0, 0), circle_mask)
        out = final.resize((size, size), Image.LANCZOS)
        out.save(
            f'app/src/main/res/mipmap-{name}/ic_launcher_round.png',
            'PNG', optimize=True
        )
    print('\n✅ OpenList v6 官方 logo (白底 + 22% 圆角) - 跟官方 web 端一致')


if __name__ == '__main__':
    main()

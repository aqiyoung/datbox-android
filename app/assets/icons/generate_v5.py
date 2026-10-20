#!/usr/bin/env python3
"""OpenList APP 图标 v5: 用官方 logo SVG (cairosvg 矢量渲染)

老板 13:35: "你直接用官方的 logo 不就行了吗？"
- 用 app/src/main/assets/openlist-logo.svg (1024x1024 viewBox)
- sky-400 #38BDF8 + teal-200 #99F6E4
- 矢量无失真
- cairosvg 渲染

改进: v4 SensenNova 6.5/10 (teal-200 在白底看不见)
→ v5 改: 背景 sky-400 蓝 (跟官方 logo 主色一致), teal-200 在蓝底上对比清晰
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

# 背景色: sky-400 (跟 logo 主色一致)
BG_COLOR = (56, 189, 248)  # #38BDF8 sky-400
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
    # 蓝底 + logo
    bg = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), BG_COLOR + (255,))
    big_with_bg = Image.alpha_composite(bg, big)
    # 圆角 mask
    rounded_mask = make_mask_rounded(BASE_SIZE)
    for name, size in SIZES.items():
        final = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (0, 0, 0, 0))
        final.paste(big_with_bg, (0, 0), rounded_mask)
        img = final.resize((size, size), Image.LANCZOS)
        img.save(f'{out_dir}/openlist_icon_v5_{name}.png', 'PNG', optimize=True)
        mipmap_path = f'app/src/main/res/mipmap-{name}/ic_launcher.png'
        os.makedirs(os.path.dirname(mipmap_path), exist_ok=True)
        img.save(mipmap_path, 'PNG', optimize=True)
        print(f'  ✅ {name} {size}px (rounded square)')
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
    print('\n✅ OpenList v5 官方 logo (sky-400 蓝底 + 22% 圆角)')


if __name__ == '__main__':
    main()

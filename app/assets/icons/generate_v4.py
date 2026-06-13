#!/usr/bin/env python3
"""OpenList APP 图标 v4: 用官方 logo SVG 直接渲染 (cairosvg)

老板 13:35: "你直接用官方的 logo 不就行了吗？"
官方 logo: app/src/main/assets/openlist-logo.svg (1024x1024 viewBox)
- sky-400 #38BDF8 主体 (左下大弧)
- teal-200 #99F6E4 主体 (右上大圆弧, 跟 sky-400 组成两个交织弧)
- 矢量, 无失真
"""
import os
import cairosvg
from PIL import Image, ImageDraw

SVG_PATH = '~/dev-projects/openlist-android/app/src/main/assets/openlist-logo.svg'

SIZES = {
    'mdpi': 48, 'hdpi': 72, 'xhdpi': 96,
    'xxhdpi': 144, 'xxxhdpi': 192,
}
BASE_SIZE = 1024


def main():
    out_dir = '~/dev-projects/openlist-android/app/assets/icons'
    os.makedirs(out_dir, exist_ok=True)
    # 渲染 SVG 到 PNG (4x 超采样)
    # v5: SensenNova 建议背景改为 sky-500 深 (不是白), 解决 teal-200 在白底不可见
    # 但官方 logo 是 "深色背景设计", 我们用 sky-500 #0EA5E9 当背景
    png_bytes = cairosvg.svg2png(
        url=SVG_PATH,
        output_width=BASE_SIZE,
        output_height=BASE_SIZE,
    )
    from io import BytesIO
    big = Image.open(BytesIO(png_bytes)).convert('RGBA')
    # 深 sky-500 背景 (官方 logo 设计是透明 + sky-500 背景)
    bg = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (14, 165, 233, 255))  # sky-500
    big_with_bg = Image.alpha_composite(bg, big)
    # 22% 圆角 mask
    mask = Image.new('L', (BASE_SIZE, BASE_SIZE), 0)
    corner = int(BASE_SIZE * 0.22)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, BASE_SIZE - 1, BASE_SIZE - 1],
        radius=corner,
        fill=255
    )
    final = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (0, 0, 0, 0))
    final.paste(big_with_bg, (0, 0), mask)
    img = final.resize((size, size), Image.LANCZOS)
        img.save(f'{out_dir}/openlist_icon_v4_{name}.png', 'PNG', optimize=True)
        mipmap_path = f'~/dev-projects/openlist-android/app/src/main/res/mipmap-{name}/ic_launcher.png'
        os.makedirs(os.path.dirname(mipmap_path), exist_ok=True)
        img.save(mipmap_path, 'PNG', optimize=True)
        print(f'  ✅ {name} {size}px')
    # round launcher (用白色背景 + 圆形 mask)
    for name, size in SIZES.items():
        png_bytes = cairosvg.svg2png(url=SVG_PATH, output_width=BASE_SIZE, output_height=BASE_SIZE)
        from io import BytesIO
        big = Image.open(BytesIO(png_bytes)).convert('RGBA')
        bg = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (255, 255, 255, 255))
        big_with_bg = Image.alpha_composite(bg, big)
        mask = Image.new('L', (BASE_SIZE, BASE_SIZE), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, BASE_SIZE, BASE_SIZE], fill=255)
        out = Image.new('RGBA', (BASE_SIZE, BASE_SIZE), (0, 0, 0, 0))
        out.paste(big_with_bg, (0, 0), mask)
        out = out.resize((size, size), Image.LANCZOS)
        out.save(
            f'~/dev-projects/openlist-android/app/src/main/res/mipmap-{name}/ic_launcher_round.png',
            'PNG', optimize=True
        )
    print('\n✅ OpenList v4 官方 logo 图标生成完毕 (cairosvg 矢量渲染)')


if __name__ == '__main__':
    main()

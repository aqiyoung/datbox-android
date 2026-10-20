#!/usr/bin/env python3
"""OpenList APP 图标 v2 (扁平化, 贴 OpenList 官方 res.oplist.org/logo/logo.svg 风格)

改进 (按 SensenNova v1 评估):
- 背景: 简化为 sky-400 → sky-500 径向 (提明度, 减少复杂度)
- 3 圆盘: 纯白实心 + 1.5px 白边 + 单点高光 (无暖色 rim, 无阴影)
- 去连线: 让圆盘更清晰, 三角 Venn 本身就是隐喻
- 整体扁平, 符合 OpenList 官方 Flat 风格
"""
import os
import math
from PIL import Image, ImageDraw, ImageFilter

# OpenList 官方色
SKY_400 = (56, 189, 248)        # #38BDF8
SKY_500 = (14, 165, 233)        # #0EA5E9 (提高明度, 比 v1 浅)
SKY_300 = (125, 211, 252)       # #7DD3FC
TEAL_500 = (20, 184, 166)       # #14B8A6 (备用)
WHITE = (255, 255, 255)

SIZES = {
    'mdpi': 48, 'hdpi': 72, 'xhdpi': 96,
    'xxhdpi': 144, 'xxxhdpi': 192,
}
BASE_SIZE = 432


def radial_bg(size):
    """简化的 sky-400 → sky-500 渐变 (提高明度)"""
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


def glass_disk_v2(canvas, cx, cy, r):
    """v2 简化玻璃圆盘: 纯白填充 + 1.5px 边 + 单点高光"""
    # 主体: 纯白实心 (无半透明, 无阴影, 无 rim)
    disk = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    dd = ImageDraw.Draw(disk)
    # 微微椭圆压扁 (SensenNova 建议: 压扁更稳固)
    dd.ellipse(
        [cx - r, cy - r * 0.95, cx + r, cy + r * 0.95],
        fill=(255, 255, 255, 245),  # 95% opacity (几乎纯白)
    )
    # 描边: 半透明白, 更清晰
    dd.ellipse(
        [cx - r, cy - r * 0.95, cx + r, cy + r * 0.95],
        outline=(255, 255, 255, 180),
        width=max(2, int(r * 0.05))
    )
    canvas.alpha_composite(disk)
    # 顶部单点小高光 (冷色, 不要暖色)
    hl = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.ellipse(
        [cx - r * 0.5, cy - r * 0.85, cx + r * 0.2, cy - r * 0.45],
        fill=(255, 255, 255, 200)
    )
    hl = hl.filter(ImageFilter.GaussianBlur(radius=r * 0.06))
    canvas.alpha_composite(hl)


def top_highlight(size):
    """顶部 sky-300 高光带 (轻微)"""
    hl = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(hl)
    for i in range(20, 0, -1):
        alpha = int(255 * (i / 20) * 0.12)  # 比 v1 弱
        inset = (size * 0.5) * (1 - i / 20) * 0.4
        box = (
            int(-size * 0.3 + inset),
            int(-size * 0.5 + inset * 0.6),
            int(size * 1.3 - inset),
            int(size * 0.55 - inset * 0.4),
        )
        draw.ellipse(box, fill=(125, 211, 252, alpha))
    hl = hl.filter(ImageFilter.GaussianBlur(radius=size * 0.04))
    return hl


def compose_icon(size):
    bg = radial_bg(size).convert('RGBA')
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(bg)
    canvas.alpha_composite(top_highlight(size))

    # 3 个白色圆盘 (Venn 风格, 紧凑三角)
    cx, cy = size * 0.5, size * 0.55
    # 比 v1 略大更紧凑 (SensenNova: 压扁椭圆, 紧凑三角)
    r = size * 0.20
    # 中心三角 + 各自偏移
    glass_disk_v2(canvas, cx + size * 0.02, cy - size * 0.18, r * 0.85)  # top
    glass_disk_v2(canvas, cx + size * 0.16, cy + size * 0.08, r * 0.95)  # right
    glass_disk_v2(canvas, cx - size * 0.16, cy + size * 0.08, r * 1.0)   # left (largest, foreground)

    return canvas


def main():
    out_dir = 'app/assets/icons'
    os.makedirs(out_dir, exist_ok=True)
    for name, size in SIZES.items():
        big = compose_icon(BASE_SIZE)
        img = big.resize((size, size), Image.LANCZOS)
        img.save(f'{out_dir}/openlist_icon_v2_{name}.png', 'PNG', optimize=True)
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
    print('\n✅ OpenList v2 扁平图标生成完毕')


if __name__ == '__main__':
    main()

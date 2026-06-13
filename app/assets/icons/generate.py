#!/usr/bin/env python3
"""生成 OpenList 液态玻璃 APP 图标（沿用 OpenList 官方 sky-400 + teal-200 配色）。

设计（3 透明圆盘 + 径向渐变 + 玻璃高光）：
- 背景：sky-400 (top center) → teal-500 (corners) 径向渐变
- 中心：3 个半透明白色玻璃圆盘（Venn diagram 风格），代表多云盘聚合
  - 前景：最大，左前
  - 中：右后
  - 背景：最小，顶后
- 每个圆盘：白色高光顶部 + 微弱暖色底部 rim
- 连线：白色细线（数据同步隐喻）
- 顶部：cyan 高光带

输出 5 个 mipmap 尺寸 + round launcher。
"""
import os
import math
from PIL import Image, ImageDraw, ImageFilter, ImageChops

# OpenList 官方配色
SKY_400 = (56, 189, 248)        # #38BDF8
SKY_300 = (125, 211, 252)       # #7DD3FC (高光辅助)
TEAL_500 = (20, 184, 166)       # #14B8A6
TEAL_700 = (15, 118, 110)       # #0F766E (边角深)
WHITE = (255, 255, 255)
WARM_RIM = (255, 220, 200)      # 微弱暖色 rim

SIZES = {
    'mdpi': 48, 'hdpi': 72, 'xhdpi': 96,
    'xxhdpi': 144, 'xxxhdpi': 192,
}
BASE_SIZE = 432  # 4x 超采样


def radial_bg(size):
    """径向渐变背景：sky-400 (top center) → teal-700 (corners)"""
    img = Image.new('RGB', (size, size), TEAL_700)
    pixels = img.load()
    cx, cy = size * 0.5, size * 0.32
    max_dist = math.hypot(size, size) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx, y - cy) / max_dist
            d = min(1.0, d)
            t = d ** 1.3
            # 中心 sky_400 (light), 边缘 teal_700 (dark)
            if t < 0.5:
                # sky_400 → teal_500
                t2 = t * 2
                r = int(SKY_400[0] * (1 - t2) + TEAL_500[0] * t2)
                g = int(SKY_400[1] * (1 - t2) + TEAL_500[1] * t2)
                b = int(SKY_400[2] * (1 - t2) + TEAL_500[2] * t2)
            else:
                # teal_500 → teal_700
                t2 = (t - 0.5) * 2
                r = int(TEAL_500[0] * (1 - t2) + TEAL_700[0] * t2)
                g = int(TEAL_500[1] * (1 - t2) + TEAL_700[1] * t2)
                b = int(TEAL_500[2] * (1 - t2) + TEAL_700[2] * t2)
            pixels[x, y] = (r, g, b)
    return img


def glass_disk(canvas, cx, cy, r, tilt_deg=0):
    """画一个玻璃圆盘（半透明白 + 高光 + rim）

    - 主体：白色 30% 透明度，圆
    - 顶部高光：白色小弧 (冷色)
    - 底部 rim：暖色 (微弱)
    - 阴影：圆下方偏移
    - 倾斜 ~10°（用 affine 旋转遮罩）
    """
    # 阴影
    shadow = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.ellipse(
        [cx - r, cy - r + r * 0.15, cx + r, cy + r + r * 0.15],
        fill=(0, 0, 0, 80)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=r * 0.15))
    canvas.alpha_composite(shadow)

    # 主体 - 旋转圆 (用更简单的 ellipse 近似)
    disk = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    dd = ImageDraw.Draw(disk)
    # 倾斜（用椭圆近似）
    dd.ellipse(
        [cx - r, cy - r * 0.85, cx + r, cy + r * 0.85],
        fill=(255, 255, 255, 75)  # 30% opacity approx (76/255)
    )
    canvas.alpha_composite(disk)

    # rim (底部暖色)
    rim = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    rd = ImageDraw.Draw(rim)
    rd.ellipse(
        [cx - r * 0.95, cy + r * 0.2, cx + r * 0.95, cy + r * 0.9],
        fill=(255, 220, 200, 100)
    )
    rim = rim.filter(ImageFilter.GaussianBlur(radius=r * 0.1))
    canvas.alpha_composite(rim)

    # 顶部高光
    hl = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.ellipse(
        [cx - r * 0.6, cy - r * 0.95, cx + r * 0.3, cy - r * 0.4],
        fill=(255, 255, 255, 200)
    )
    hl = hl.filter(ImageFilter.GaussianBlur(radius=r * 0.08))
    canvas.alpha_composite(hl)

    # rim 描边 (冷色，更清晰边缘)
    edge = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ed = ImageDraw.Draw(edge)
    ed.ellipse(
        [cx - r, cy - r * 0.85, cx + r, cy + r * 0.85],
        outline=(255, 255, 255, 140),
        width=max(1, int(r * 0.04))
    )
    canvas.alpha_composite(edge)


def draw_filament(canvas, x1, y1, x2, y2, w):
    """白色细线 (数据同步) + 微发光"""
    glow = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.line([(x1, y1), (x2, y2)], fill=(255, 255, 255, 90), width=int(w * 3))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=w * 1.5))
    canvas.alpha_composite(glow)
    line = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(line)
    ld.line([(x1, y1), (x2, y2)], fill=(255, 255, 255, 200), width=int(w))
    canvas.alpha_composite(line)


def top_highlight(size):
    """顶部 cyan 高光带"""
    hl = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(hl)
    for i in range(20, 0, -1):
        alpha = int(255 * (i / 20) * 0.18)
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

    # 3 个玻璃圆盘（Venn 风格）
    # 中心三角 + 各自轻微偏移
    cx, cy = size * 0.5, size * 0.52
    r_big = size * 0.18
    r_mid = size * 0.155
    r_small = size * 0.13

    # 背景小盘（top-back）
    glass_disk(canvas, cx + size * 0.05, cy - size * 0.20, r_small)
    # 中盘（right-back）
    glass_disk(canvas, cx + size * 0.18, cy + size * 0.05, r_mid)
    # 前景大盘（left-front, 略微盖住中盘）
    glass_disk(canvas, cx - size * 0.18, cy + size * 0.05, r_big)

    # 连线 (3 圆盘中心)
    centers = [
        (cx + size * 0.05, cy - size * 0.20),
        (cx + size * 0.18, cy + size * 0.05),
        (cx - size * 0.18, cy + size * 0.05),
    ]
    w = size * 0.02
    for i in range(3):
        a, b = centers[i], centers[(i + 1) % 3]
        draw_filament(canvas, a[0], a[1], b[0], b[1], w)

    return canvas


def main():
    out_dir = '~/dev-projects/openlist-android/app/assets/icons'
    os.makedirs(out_dir, exist_ok=True)
    for name, size in SIZES.items():
        big = compose_icon(BASE_SIZE)
        img = big.resize((size, size), Image.LANCZOS)
        asset_path = f'{out_dir}/openlist_icon_{name}.png'
        img.save(asset_path, 'PNG', optimize=True)
        mipmap_path = f'~/dev-projects/openlist-android/app/src/main/res/mipmap-{name}/ic_launcher.png'
        os.makedirs(os.path.dirname(mipmap_path), exist_ok=True)
        img.save(mipmap_path, 'PNG', optimize=True)
        print(f'  ✅ {name} {size}px -> {mipmap_path}')
    # round launcher (圆形 mask)
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
            f'~/dev-projects/openlist-android/app/src/main/res/mipmap-{name}/ic_launcher_round.png',
            'PNG', optimize=True
        )
    print('\n✅ OpenList 液态玻璃图标生成完毕')


if __name__ == '__main__':
    main()

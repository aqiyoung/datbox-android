package com.datbox.app.ui.component

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Dock 玻璃的 backdrop 折射管线。
 *
 * 形态参照 aqiyoung/FlClash@dev 的 shaders/liquid_glass.frag (GPL-3.0):
 * 它的玻璃之所以"完美", 是 Flutter 引擎把 widget 背后的画面作为 uTexture
 * 交给 fragment shader, shader 在药丸 SDF 的边缘法线上做折射偏移采样。
 * 我们在 Android 侧用等价管线还原:
 *
 *   页面内容 Modifier.drawWithContent → GraphicsLayer.record 录制 (取景层)
 *   取景层 GraphicsLayer.renderEffect = createRuntimeShaderEffect(shader, "uTexture")
 *   → shader 的 uTexture 就是被绘制的 layer 内容 = 真实 backdrop;
 *     只有 Dock 的裁剪绘制经过该层, 主内容不挂 effect —— 任何 shader 异常
 *     (编译失败 / uniforms 异常) 的影响面收敛在 dock 药丸区, 不可能拖垮整屏;
 *     构造与挂载全部 runCatching 兜底, 失败即回落 v3 平玻璃。
 *   RenderEffect 以 layer 局部坐标执行, uOrigin/uPill 按纹理像素表达药丸几何。
 *
 * shader 内 (对照 liquid_glass.frag 的公式重新实现, 未复制其代码):
 * 1. 圆角矩形 SDF → 内缩距离 inset + 外法线 normal
 * 2. bevel 折射: rise = 1-inset/bevel, depth 沿法线偏移采样点 (边缘挤压/放大)
 * 3. 滑镜 raised-lens 折射: 仅上下缘、向两端渐收 (lens mode)
 * 4. 双级毛玻璃: 主模糊 uBlur + 远模糊 uHazeBlur, 按 uHaze 混合 (FlClash pill: blur 0 + haze 0.72)
 * 5. 色散: R/B 通道沿弯曲方向错开采样
 * 6. 饱和度 → 滑镜端部变暗 → 内容自适应增厚 → 平涂染色 → 顶/底 rim 高光 → iOS 暗边线
 *
 * 降级: 仅 API 33+ (AGSL); 以下回落 v3 平玻璃 (不挂 effect)。
 */
internal object LiquidGlassBackdrop {

    @RequiresApi(33)
    const val SHADER: String = """
        uniform float2 uTexSize;
        uniform float2 uOrigin;
        uniform float2 uPill;
        uniform float uRadius;
        uniform float uBevel;
        uniform float uRefraction;
        uniform float uBlur;
        uniform float uHaze;
        uniform float uHazeBlur;
        uniform float uRim;
        uniform float uDispersion;
        uniform float uSaturation;
        uniform float uContrast;
        uniform float uLine;
        uniform float uEndShade;
        uniform float uEndShadeReach;
        uniform float uLensX;
        uniform float uLensW;
        uniform float uLensBevel;
        uniform float uLensDepth;
        uniform float4 uTint;
        uniform shader uTexture;

        // 圆角矩形外法线 (p 相对药丸中心; q 角落区取径向, 否则取主轴)
        float2 pillNormal(float2 p, float2 halfSize, float r) {
            float2 q = abs(p) - halfSize + float2(r);
            float2 n;
            if (q.x > 0.0 && q.y > 0.0) {
                n = normalize(max(q, float2(0.0001)));
            } else if (q.x > q.y) {
                n = float2(1.0, 0.0);
            } else {
                n = float2(0.0, 1.0);
            }
            return n * float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
        }

        // texture 像素坐标取色 (夹到像素中心, 避免采到纹理外)
        float4 texel(float2 pixel) {
            return sample(uTexture, clamp(pixel, float2(0.5), uTexSize - 0.5) / uTexSize);
        }

        // Vogel 盘 (黄金角) 13 tap 磨砂; radius<=0 退化为单点采样 (dock 条体 uBlur = 0)
        float4 frost(float2 pixel, float radius) {
            float4 sum = texel(pixel);
            if (radius <= 0.0) {
                return sum;
            }
            float total = 1.0;
            for (int i = 1; i < 13; i++) {
                float ang = float(i) * 2.3999632;
                float rad = sqrt((float(i) + 0.5) / 13.0) * radius;
                sum += texel(pixel + float2(cos(ang), sin(ang)) * rad);
                total += 1.0;
            }
            return sum / total;
        }

        // 双级毛玻璃: 主模糊 uBlur + 远模糊 uHazeBlur, 按 uHaze 混合。
        // FlClash 的 dock 条体是 blur 0 + haze 0.72@15.6dp —— 毛玻璃感来自 haze 而非主模糊。
        float4 glassAt(float2 pixel) {
            float4 sharp = frost(pixel, uBlur);
            if (uHaze <= 0.0) {
                return sharp;
            }
            return mix(sharp, frost(pixel, uHazeBlur), uHaze);
        }

        half4 main(float2 coord) {
            // uniforms 未初始化/纹理尺寸非法 → 返回透明 (不覆盖下层内容)。
            // 注意不能在这里 sample: uTexSize<=0 时 coord/uTexSize = Inf → 采样未定义。
            if (uTexSize.x <= 0.0 || uTexSize.y <= 0.0 || uPill.x <= 0.0 || uPill.y <= 0.0) {
                return half4(0.0);
            }
            float2 local = coord - uOrigin;
            float2 halfSize = uPill * 0.5;
            float2 p = local - halfSize;
            float r = min(uRadius, min(halfSize.x, halfSize.y));
            float2 q = abs(p) - halfSize + float2(r);
            float d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
            // 药丸外 (含圆角外的角落方区) → 透传, 保证主内容层全屏画面逐像素不变
            if (d > 0.0) {
                return sample(uTexture, coord / uTexSize);
            }
            float inset = -d;
            float2 normal = pillNormal(p, halfSize, r);

            // bevel 折射 (FlClash bevel profile: 平进陡出, 边缘内容被挤压放大)
            float minor = min(halfSize.x, halfSize.y);
            float bevel = max(min(uBevel, minor * 0.5), 0.5);
            float rise = 1.0 - clamp(inset / bevel, 0.0, 1.0);
            float depth = min(uRefraction, minor) * (1.0 - sqrt(max(1.0 - rise * rise, 0.0)));
            float2 shift = -normal * depth;

            // 滑镜 raised lens: 仅上下缘折射 (|n.y| 门控), 向两端按 (1-across^2) 渐收。
            // 几何在分支外声明, 供下方 endShade 使用 (需要滑镜自身的 inset / normal)。
            float2 lensNormal = float2(0.0);
            float lensInset = 0.0;
            if (uLensX >= 0.0) {
                float2 lp = float2(local.x - uLensX - uLensW * 0.5, p.y);
                float2 lHalf = float2(uLensW * 0.5, halfSize.y);
                float lr = min(lHalf.x, lHalf.y);
                float2 lq = abs(lp) - lHalf + float2(lr);
                float ld = length(max(lq, float2(0.0))) + min(max(lq.x, lq.y), 0.0) - lr;
                lensInset = max(-ld, 0.0);
                lensNormal = pillNormal(lp, lHalf, lr);
                float lBevel = max(min(uLensBevel, min(lHalf.x, lHalf.y) * 0.5), 0.5);
                float lRise = 1.0 - clamp(lensInset / lBevel, 0.0, 1.0);
                float lDepth = min(uLensDepth, lBevel * lRise / sqrt(max(1.0 - lRise * lRise, 0.0001)));
                float across = lp.x / max(lHalf.x, 0.0001);
                shift += lensNormal * lDepth * smoothstep(0.85, 1.0, abs(lensNormal.y))
                    * (1.0 - across * across);
            }

            // 磨砂 + 色散: 三次采样都走 glassAt, 保证 R/B 错开的那两次与主采样处于同一毛玻璃层级
            float2 tex = coord + shift;
            float4 bent = glassAt(tex);
            float2 disp = shift * uDispersion;
            float4 rTap = glassAt(tex - disp);
            float4 bTap = glassAt(tex + disp);

            float alpha = max(bent.a, 0.0001);
            float3 color = bent.rgb / alpha;
            color.r = rTap.r / max(rTap.a, 0.0001);
            color.b = bTap.b / max(bTap.a, 0.0001);

            float3 kLuma = float3(0.2126, 0.7152, 0.0722);

            // 饱和度 (FlClash LiquidGlassStyle.pill: 2.1): 纯白底上恒等, 内容从玻璃下经过时才显色
            color = mix(float3(dot(color, kLuma)), color, uSaturation);

            // 滑镜端部变暗 (FlClash LiquidGlassStyle.lens.endShade 0.045, 衰减 3dp)
            if (uLensX >= 0.0 && uEndShade > 0.0) {
                color *= 1.0 - uEndShade * abs(lensNormal.x)
                    * exp(-lensInset / max(uEndShadeReach, 0.0001));
            }

            // 内容自适应增厚 (FlClash contrast 0.4): 采药丸内 5 点估背后平均亮度, 与染色亮度
            // 冲突时增厚染色, 让玻璃在深浅内容上都保住可读性。注: 纯白底上 tint 亮度 0.957 与
            // 底色 1.0 只差 0.04 < 阈值 0.3 → clash 恒为 0, 本项只在深色内容/图片从玻璃下经过时触发。
            float4 behind = texel(uOrigin + uPill * float2(0.5, 0.5))
                + texel(uOrigin + uPill * float2(0.2, 0.3))
                + texel(uOrigin + uPill * float2(0.8, 0.3))
                + texel(uOrigin + uPill * float2(0.2, 0.7))
                + texel(uOrigin + uPill * float2(0.8, 0.7));
            float behindLuma = dot(behind.rgb / max(behind.a, 0.0001), kLuma);
            float clash = clamp((sign(uContrast) * (dot(uTint.rgb, kLuma) - behindLuma) - 0.3) / 0.5, 0.0, 1.0);

            // 平涂染色 (内容在玻璃下可见, 染色只是压一层中性色)
            float cover = uTint.a + (1.0 - uTint.a) * clash * abs(uContrast);
            float3 glass = color * (1.0 - cover) + uTint.rgb * cover;

            // rim 高光 (iOS Liquid Glass): 上下缘主光带 + 全周细 rim, 均在边线内侧 —
            // 边线附近一圈提亮而非穹顶聚光, 玻璃感的主要来源
            float within = max(inset - uLine, 0.0);
            float cap = clamp(1.0 - within / 12.0, 0.0, 1.0);
            float band = smoothstep(0.55, 0.95, abs(normal.y));            // 上下缘主带
            float rim = smoothstep(0.45, 1.0, band + 0.55 * abs(normal.x)); // 混入左右细边
            float shine = uRim * rim * cap;
            glass += (1.0 - glass) * min(shine, 1.0);

            // iOS 暗边线: 最外 ~1dp 一圈压暗 (对照 FlClash kLine/kLineCap 的取值)
            float line = clamp((uLine - inset) + 0.5, 0.0, 1.0);
            float3 dark = max(color - 0.298, 0.22 * color);
            glass = mix(glass, mix(color, dark, 0.8), line);

            return half4(clamp(glass, float3(0.0), float3(1.0)), 1.0);
        }
    """

    @RequiresApi(33)
    fun createGlassEffect(shader: RuntimeShader): RenderEffect =
        android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "uTexture")
            .asComposeRenderEffect()
}

package com.datbox.app.ui.component

import android.graphics.RuntimeShader
import android.os.Build
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
 * 4. Vogel 盘 13 tap 磨砂模糊
 * 5. 色散: R/B 通道沿弯曲方向错开采样
 * 6. 平涂染色 → 顶/底 rim 高光 → iOS 暗边线
 *
 * 降级: 仅 API 33+ (AGSL); 以下回落 v3 平玻璃 (不挂 effect)。
 */
internal object LiquidGlassBackdrop {

    /**
     * AGSL (RuntimeShader) 源码。坐标均为 backdrop 纹理像素 (与 layer 同一坐标系)。
     * uniform:
     * - uTexSize: 纹理尺寸 (px)
     * - uOrigin: 药丸左上角在纹理内的位置 (px)
     * - uPill: 药丸尺寸 (px, <=0 表示未初始化 → 全透传)
     * - uRadius/uBevel/uRefraction/uBlur/uRim/uDispersion/uLine: 光学参数 (px / 无量纲)
     * - uLensX/uLensW/uLensBevel/uLensDepth: 滑镜几何 (uLensX<0 时关闭)
     * - uTint: 平涂染色 (straight alpha)
     */
    @RequiresApi(33)
    const val SHADER: String = """
        uniform float2 uTexSize;
        uniform float2 uOrigin;
        uniform float2 uPill;
        uniform float uRadius;
        uniform float uBevel;
        uniform float uRefraction;
        uniform float uBlur;
        uniform float uRim;
        uniform float uDispersion;
        uniform float uLine;
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

            // 滑镜 raised lens: 仅上下缘折射 (|n.y| 门控), 向两端按 (1-across^2) 渐收
            if (uLensX >= 0.0) {
                float2 lp = float2(local.x - uLensX - uLensW * 0.5, p.y);
                float2 lHalf = float2(uLensW * 0.5, halfSize.y);
                float lr = min(lHalf.x, lHalf.y);
                float2 lq = abs(lp) - lHalf + float2(lr);
                float ld = length(max(lq, float2(0.0))) + min(max(lq.x, lq.y), 0.0) - lr;
                float lInset = max(-ld, 0.0);
                float2 lNormal = pillNormal(lp, lHalf, lr);
                float lBevel = max(min(uLensBevel, min(lHalf.x, lHalf.y) * 0.5), 0.5);
                float lRise = 1.0 - clamp(lInset / lBevel, 0.0, 1.0);
                float lDepth = min(uLensDepth, lBevel * lRise / sqrt(max(1.0 - lRise * lRise, 0.0001)));
                float across = lp.x / max(lHalf.x, 0.0001);
                shift += lNormal * lDepth * smoothstep(0.85, 1.0, abs(lNormal.y))
                    * (1.0 - across * across);
            }

            // 磨砂: Vogel 盘 (黄金角) 均匀采样, 半径 uBlur px
            float2 base = clamp((coord + shift) / uTexSize, float2(0.0), float2(1.0));
            float4 sum = sample(uTexture, base);
            float total = 1.0;
            for (int i = 1; i < 13; i++) {
                float ang = float(i) * 2.3999632;
                float rad = sqrt((float(i) + 0.5) / 13.0) * uBlur;
                float2 off = float2(cos(ang), sin(ang)) * rad / uTexSize;
                sum += sample(uTexture, clamp(base + off, float2(0.0), float2(1.0)));
                total += 1.0;
            }
            float4 bent = sum / total;

            // 色散: R/B 通道沿弯曲方向错开
            float2 disp = shift * uDispersion;
            float4 rTap = sample(uTexture, clamp((coord + shift - disp) / uTexSize, float2(0.0), float2(1.0)));
            float4 bTap = sample(uTexture, clamp((coord + shift + disp) / uTexSize, float2(0.0), float2(1.0)));

            float alpha = max(bent.a, 0.0001);
            float3 color = bent.rgb / alpha;
            color.r = rTap.r / max(rTap.a, 0.0001);
            color.b = bTap.b / max(bTap.a, 0.0001);

            // 平涂染色 (内容在玻璃下可见, 染色只是压一层中性色)
            float cover = uTint.a;
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

    /** 全效果: shader (折射 + 磨砂 + 色散 + 边线)。uTexture = 被绘制的 layer 内容。 */
    @RequiresApi(33)
    fun createGlassEffect(shader: RuntimeShader): RenderEffect =
        android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "uTexture")
            .asComposeRenderEffect()
}

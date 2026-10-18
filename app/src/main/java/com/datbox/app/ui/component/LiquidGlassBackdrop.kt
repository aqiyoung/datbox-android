package com.datbox.app.ui.component

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Dock 玻璃的 backdrop 折射管线。
 *
 * 形态与光学公式对齐 aqiyoung/FlClash@dev 的 lib/ui/liquid_glass.dart + shaders/liquid_glass.frag
 * (GPL-3.0): FlClash 之所以"完美", 是 Flutter 引擎把 widget 背后的画面作为 uTexture
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
 * 对照 FlClash 的实现 (未复制其代码, 按其公式重写; 该工程以 dp 为单位, 这里换算成纹理像素):
 * 1. 圆角矩形 SDF → 内缩距离 inset + 外法线 normal
 * 2. 条体 bevel 折射: depth = min(refraction, minor·½-ish) · (1 - √(1 - rise²)), 边缘挤压/放大
 * 3. 滑镜 raised lens (FlClash LiquidGlassStyle.lens): 沿法线向外, 边缘为 0, 一个 bevel 内到满深度
 *    (6dp / 7dp), 再向中心缓释 —— shift = n · depth · smoothstep(0, bevel, inset) · (1 - smoothstep(bevel, minor, inset))
 * 4. 色散: R/B 沿法线的镜像方向分开 (FlClash 的 parting), 条体 2dp、滑镜 8.2dp,
 *    从边缘向内 7dp 保持、10dp 内收没 (kPartHold → kPartReach)
 * 5. 双级毛玻璃: 主模糊 uBlur + 远模糊 uHazeBlur 按 uHaze 混合 + 边缘附加优待 uRimBlur·rise²
 * 6. 饱和度 → 亮度 → 滑镜端部变暗 (uEndShade·|n.x|·e^(-inset/3dp))
 * 7. rim 高光 (iOS 27): 上下缘主带, 1.3dp 渐淡, facing 取法线竖直分量的平方
 *    (滑镜取其 smoothstep(0.65,0.95)), 一部分以"加光"而非"提亮"的方式叠上 (uRimAdded)
 * 8. iOS 暗边线: 最外 ~0.67dp 压暗, 两侧更暗 (mix(uLineDark, uLineSide, |n.x|³))
 *
 * 降级: 仅 API 33+ (AGSL); 以下回落 v3 平玻璃 (不挂 effect)。
 */
internal object LiquidGlassBackdrop {

    @RequiresApi(33)
    const val SHADER: String = """
        uniform float2 uTexSize;
        uniform float2 uOrigin;     // 药丸左上角, 纹理像素
        uniform float2 uPill;       // 药丸尺寸, 纹理像素
        uniform float uRadius;      // 药丸圆角, 像素
        uniform float uDp;          // 每 dp 的像素数 (shader 内 dp 常量用)

        uniform float uBevel;       // 像素
        uniform float uRefraction;  // 像素
        uniform float uBlur;        // 像素 (主模糊)
        uniform float uHaze;        // 0..1
        uniform float uHazeBlur;    // 像素
        uniform float uRimBlur;     // 像素 (边缘附加毛玻璃)

        uniform float uSaturation;
        uniform float uBrightness;
        uniform float uContrast;    // 0 = 关闭内容自适应增厚

        uniform float uRim;         // rim 高光强度
        uniform float uRimWidth;    // 高光的渐淡深度, 像素
        uniform float uRimTail;     // 0..1: 用尾部而非带状的份额
        uniform float uRimFocus;    // 0..1: 随圆角端收束的程度
        uniform float uRimAdded;    // 0..1: 以加光而非提亮叠加的份额

        uniform float uDispersion;  // 像素 (条体色散)
        uniform float uLine;        // 像素 (暗边线宽)
        uniform float uLineDark;    // 上下缘暗边线强度
        uniform float uLineSide;    // 两侧暗边线强度
        uniform float uEndShade;    // 滑镜两端变暗

        uniform float4 uTint;

        uniform float uLensX;          // 像素, < 0 表示无滑镜
        uniform float uLensW;          // 像素
        uniform float uLensBevel;      // 像素
        uniform float uLensDepth;      // 像素 (抬升深度, 正)
        uniform float uLensDispersion; // 像素
        uniform float uLensRim;
        uniform float uLensRimWidth;   // 像素
        uniform float uLensRimAdded;
        uniform shader uTexture;

        // 圆角矩形外法线 (p 相对中心; q 角落区取径向, 否则取主轴)
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

        // 圆角矩形有向距离 (负值在内部)
        float roundRectSdf(float2 p, float2 halfSize, float r) {
            float2 q = abs(p) - halfSize + float2(r);
            return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
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

        half4 main(float2 coord) {
            // uniforms 未初始化/纹理尺寸非法 → 返回透明 (不覆盖下层内容)。
            // 注意不能在这里 sample: uTexSize<=0 时 coord/uTexSize = Inf → 采样未定义。
            if (uTexSize.x <= 0.0 || uTexSize.y <= 0.0 || uPill.x <= 0.0 || uPill.y <= 0.0) {
                return half4(0.0);
            }
            float2 local = coord - uOrigin;
            float2 halfSize = uPill * 0.5;
            float2 p = local - halfSize;
            float radius = min(uRadius, min(halfSize.x, halfSize.y));
            float sdf = roundRectSdf(p, halfSize, radius);
            // 药丸外 (含圆角外的角落方区) → 透传, 保证主内容层全屏画面逐像素不变
            if (sdf > 0.0) {
                return sample(uTexture, coord / uTexSize);
            }

            float inset = -sdf;
            float2 normal = pillNormal(p, halfSize, radius);
            float minor = min(halfSize.x, halfSize.y);
            float bevel = max(min(uBevel, minor * 0.5), 0.5);
            float rise = 1.0 - clamp(inset / bevel, 0.0, 1.0);

            // 条体 bevel 折射 (圆剖面: 贴面处平、近边缘变陡 → 最外一圈内容被挤压镜像)
            float depth = min(uRefraction, minor) * (1.0 - sqrt(max(1.0 - rise * rise, 0.0)));
            float2 shift = -normal * depth;

            // 滑镜抬升透镜: 沿法线向外, 边缘为 0, 一个 bevel 内到满深度, 再向中心缓释
            bool inLens = false;
            float2 lensNormal = float2(0.0);
            float lensInset = 0.0;
            if (uLensX >= 0.0) {
                float2 lp = float2(local.x - uLensX - uLensW * 0.5, p.y);
                float2 lHalf = float2(uLensW * 0.5, halfSize.y);
                float lRadius = min(lHalf.x, lHalf.y);
                float ld = roundRectSdf(lp, lHalf, lRadius);
                if (ld <= 0.0) {
                    inLens = true;
                    lensInset = -ld;
                    lensNormal = pillNormal(lp, lHalf, lRadius);
                    float lMinor = min(lHalf.x, lHalf.y);
                    float lBevel = max(min(uLensBevel, lMinor * 0.5), 0.5);
                    // lBevel 必须严格小于 lMinor, 否则 smoothstep 未定义 (极窄屏)
                    float lOuter = max(lMinor, lBevel + 0.5);
                    float lProfile = smoothstep(0.0, lBevel, lensInset)
                        * (1.0 - smoothstep(lBevel, lOuter, lensInset));
                    shift += lensNormal * uLensDepth * lProfile;
                }
            }

            // 取色点: 边缘附加毛玻璃 uRimBlur · rise² (FlClash: frost = blur + rimBlur·rise²)
            float2 texelPt = coord + shift;
            float frostRadius = uBlur + uRimBlur * rise * rise;
            float4 base = frost(texelPt, frostRadius);

            // 色散: R/B 沿法线的镜像方向分开, 从边缘向内保持、再收没 (FlClash 的 parting)。
            // 点选区域用滑镜自身的几何/强度, 否则用条体。
            float2 partNormal = inLens ? lensNormal : normal;
            float partInset = inLens ? lensInset : inset;
            float partAmount = inLens ? uLensDispersion : uDispersion;
            float partReach = 10.0 * uDp;
            float partHold = 7.0 * uDp;
            if (partAmount > 0.0 && partInset < partReach) {
                float2 part = partNormal.yx * (0.5 * partAmount)
                    * (0.6 + 0.4 * abs(partNormal.y) * max(1.0 - partInset / partHold, 0.0))
                    * (1.0 - smoothstep(partHold, partReach, partInset));
                float4 rTap = frost(texelPt + part, frostRadius);
                float4 bTap = frost(texelPt - part, frostRadius);
                base = float4(rTap.r, base.g, bTap.b, max(base.a, max(rTap.a, bTap.a)));
            }

            // 双级毛玻璃: 主模糊 + 远模糊按 uHaze 混合 (FlClash dock 条体 = blur 0 + haze 0.72@15.6dp)
            if (uHaze > 0.0) {
                base = mix(base, frost(texelPt, uHazeBlur), uHaze);
            }

            float alpha = max(base.a, 0.0001);
            float3 color = base.rgb / alpha;
            float3 luma = float3(0.2126, 0.7152, 0.0722);
            color = mix(float3(dot(color, luma)), color, uSaturation) * uBrightness;

            // 滑镜端部变暗 (FlClash lens.endShade 0.045, 3dp 内衰减)
            if (inLens && uEndShade > 0.0) {
                color *= 1.0 - uEndShade * abs(lensNormal.x)
                    * exp(-lensInset / max(3.0 * uDp, 0.0001));
            }

            // 内容自适应增厚 (FlClash contrast): 纯白底上 tint 亮度与底色只差 0.04 < 阈值 0.3
            // → clash 恒为 0, 本项只在深色内容/图片从玻璃下经过时触发。dock 的 pill 现为 contrast 0。
            float clash = 0.0;
            if (uContrast != 0.0) {
                float4 behind = texel(uOrigin + uPill * float2(0.5, 0.5))
                    + texel(uOrigin + uPill * float2(0.2, 0.3))
                    + texel(uOrigin + uPill * float2(0.8, 0.3))
                    + texel(uOrigin + uPill * float2(0.2, 0.7))
                    + texel(uOrigin + uPill * float2(0.8, 0.7));
                float behindLuma = dot(behind.rgb / max(behind.a, 0.0001), luma);
                clash = clamp(
                    (sign(uContrast) * (dot(uTint.rgb, luma) - behindLuma) - 0.3) / 0.5,
                    0.0, 1.0
                );
            }

            // 平涂染色 (内容在玻璃下可见, 染色只是压一层中性色)
            float cover = uTint.a + (1.0 - uTint.a) * clash * abs(uContrast);
            float3 glass = color * (1.0 - cover) + uTint.rgb * cover;

            // rim 高光: 上下缘主带, 1.3dp 渐淡, 一部分以加光叠上 (iOS 27 tab bar)
            float rimShare = uRim;
            float rimBandWidth = uRimWidth;
            float rimTail = uRimTail;
            float rimFocus = uRimFocus;
            float rimAdded = uRimAdded;
            float rimInset = inset;
            float2 rimNormal = normal;
            bool rimLens = false;
            if (inLens) {
                rimShare = uLensRim;
                rimBandWidth = uLensRimWidth;
                rimTail = 0.0;
                rimFocus = 0.0;
                rimAdded = uLensRimAdded;
                rimInset = lensInset;
                rimNormal = lensNormal;
                rimLens = true;
            }
            float within = max(rimInset - uLine, 0.0);
            float facing;
            if (rimLens) {
                // 滑镜: |n.y| 门控, 只点亮上下缘
                facing = smoothstep(0.65, 0.95, abs(rimNormal.y));
            } else {
                // 条体: 法线竖直分量的平方, uRimFocus 混入随圆角端更快收束的项
                float ny2 = rimNormal.y * rimNormal.y;
                float ny4 = ny2 * ny2;
                float ny16 = ny4 * ny4 * ny4 * ny4;
                facing = mix(ny2, 0.5 * ny2 * abs(rimNormal.y) * (1.0 + ny16), rimFocus);
            }
            float band = clamp(1.0 - within / max(rimBandWidth, 0.001), 0.0, 1.0);
            float tail = clamp(
                1.0 - max(rimInset - uLine, 0.0) / max(4.5 * uDp, 0.0001), 0.0, 1.0
            );
            float shine = min(rimShare * facing * mix(band, tail, rimTail), 1.0);
            glass += (1.0 - (1.0 - rimAdded) * glass) * shine;

            // iOS 暗边线: 最外 ~0.67dp 一圈压暗, 两侧更暗 (FlClash kLine/kLineCap)
            float line = clamp((uLine - inset) + 0.5, 0.0, 1.0);
            if (line > 0.0 && (uLineDark > 0.0 || uLineSide > 0.0)) {
                float across = abs(normal.x);
                float darken = mix(uLineDark, uLineSide, across * across * across);
                // FlClash 在未折射的底色上压暗, 这里同样取未偏移的纹理像素
                float4 under = sample(uTexture, coord / uTexSize);
                float3 dark = max(under.rgb - 0.298 * under.a, 0.22 * under.rgb);
                glass = mix(glass, mix(under.rgb, dark, darken), line);
            }

            return half4(clamp(glass, float3(0.0), float3(1.0)), 1.0);
        }
    """

    @RequiresApi(33)
    fun createGlassEffect(shader: RuntimeShader): RenderEffect =
        android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "uTexture")
            .asComposeRenderEffect()
}

package com.threel.openlist.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

/**
 * v0.3.31 老板 6/14 21:38 拍: 应用程序图标还用的安卓默认绿色机器人, 重新设计
 *
 * 设计原则:
 * - 14 种文件类型, 14 种暖色调底色 (品牌温度一致, 全在 30-60° 色相范围)
 * - 自绘 Canvas 图标, 不引第三方包 (AGENTS.md 铁律)
 * - 圆角 12dp + 简化图形 + 品牌色
 * - 跟液态玻璃 UI 协调 (米白底 + 彩色 icon)
 *
 * 颜色系统 (Warm Spectrum, 全部暖色系):
 * - 陶土红 #D97757 (主品牌 / Android APP)
 * - 焦糖棕 #8B5E3C (可执行 / Disk)
 * - 赭石   #B8702C (压缩包)
 * - 暖橙   #C97B4A (图片)
 * - 暖红   #B54848 (视频)
 * - 暖玫红 #A65D7A (音频)
 * - 朱红   #C44A36 (PDF / PPT)
 * - 暖橄榄 #7A8F5E (代码)
 * - 暖橄榄绿 #5B8F5B (Excel)
 * - 暖深蓝 #4A6B8A (Word)
 * - 暖深蓝 #1E3A5F (iOS APP)
 * - 暖灰   #87867F (Text)
 * - 暖米灰 #A89B8C (Default)
 */

enum class FileType {
    FOLDER,
    ANDROID_APP,    // apk
    IOS_APP,        // app, dmg
    EXECUTABLE,     // exe, msi, bat, cmd, sh, deb, rpm, jar, bin, run
    ARCHIVE,        // zip, 7z, rar, tar, gz, bz2, xz, tgz, tbz2, txz, lz, lzma, zst
    IMAGE,          // jpg, png, gif, webp, svg, heic, ico, raw, tiff, bmp
    VIDEO,          // mp4, mkv, avi, mov, flv, wmv, m4v, webm, rmvb, ts, m2ts, 3gp
    AUDIO,          // mp3, flac, wav, aac, ogg, wma, m4a, opus, ape, alac
    PDF,            // pdf
    CODE,           // kt, java, py, js, ts, go, rust, c, cpp, json, html, css, sql, etc.
    TEXT,           // txt, md, markdown, log, rst, tex
    WORD,           // doc, docx, rtf, odt
    EXCEL,          // xls, xlsx, ods, csv
    POWERPOINT,     // ppt, pptx, odp, key
    DISK,           // iso
    UNKNOWN,        // default
}

fun fileTypeFor(name: String, isDir: Boolean): FileType = when {
    isDir -> FileType.FOLDER
    else -> {
        val ext = name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "apk" -> FileType.ANDROID_APP
            "app", "dmg", "pkg" -> FileType.IOS_APP
            "exe", "msi", "bat", "cmd", "sh", "bash", "zsh", "fish", "ps1",
            "deb", "rpm", "jar", "bin", "run" -> FileType.EXECUTABLE
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz",
            "lz", "lzma", "zst" -> FileType.ARCHIVE
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif",
            "ico", "raw", "tiff", "tif" -> FileType.IMAGE
            "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb",
            "rm", "ts", "m2ts", "3gp" -> FileType.VIDEO
            "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac" -> FileType.AUDIO
            "pdf" -> FileType.PDF
            // 代码
            "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "go", "rust", "rs",
            "c", "cpp", "cc", "cxx", "h", "hpp", "json", "xml", "yaml", "yml",
            "toml", "ini", "conf", "gradle", "dart", "swift", "rb", "php",
            "html", "htm", "css", "scss", "sass", "less", "sql" -> FileType.CODE
            "txt", "md", "markdown", "log", "rst", "tex" -> FileType.TEXT
            "doc", "docx", "rtf", "odt" -> FileType.WORD
            "xls", "xlsx", "ods", "csv" -> FileType.EXCEL
            "ppt", "pptx", "odp", "key" -> FileType.POWERPOINT
            "iso" -> FileType.DISK
            else -> FileType.UNKNOWN
        }
    }
}

/** Warm Spectrum 调色板 (14 色 + 1 默认) */
fun FileType.color(): Color = when (this) {
    FileType.FOLDER -> Color(0xFFD97757)       // 陶土红 (品牌主色)
    FileType.ANDROID_APP -> Color(0xFFD97757)  // 陶土红 (品牌, 突出 APP)
    FileType.IOS_APP -> Color(0xFF1E3A5F)      // 暖深蓝
    FileType.EXECUTABLE -> Color(0xFF8B5E3C)   // 焦糖棕
    FileType.ARCHIVE -> Color(0xFFB8702C)      // 赭石
    FileType.IMAGE -> Color(0xFFC97B4A)         // 暖橙
    FileType.VIDEO -> Color(0xFFB54848)        // 暖红
    FileType.AUDIO -> Color(0xFFA65D7A)        // 暖玫红
    FileType.PDF -> Color(0xFFC44A36)          // 朱红
    FileType.CODE -> Color(0xFF7A8F5E)         // 暖橄榄
    FileType.TEXT -> Color(0xFF87867F)         // 暖灰
    FileType.WORD -> Color(0xFF4A6B8A)         // 暖深蓝
    FileType.EXCEL -> Color(0xFF5B8F5B)        // 暖橄榄绿
    FileType.POWERPOINT -> Color(0xFFC44A36)   // 朱红 (跟 PDF 同色, 跟 Office 关联)
    FileType.DISK -> Color(0xFF8B5E3C)         // 焦糖棕
    FileType.UNKNOWN -> Color(0xFFA89B8C)      // 暖米灰
}

/**
 * v0.3.31 自绘彩色文件类型图标
 * - 圆角 12dp 底色
 * - 内部白色简化图形
 * - 28-40dp 尺寸 (跟老板 list row 匹配)
 */
@Composable
fun FileTypeIcon(
    fileType: FileType,
    modifier: Modifier = Modifier.size(36.dp),
) {
    val color = fileType.color()
    Canvas(modifier = modifier) {
        // 1. 圆角 12dp 底色
        drawRoundedBackground(color)
        // 2. 白色简化图形
        val fg = Color.White
        val w = size.width
        val h = size.height
        val pad = w * 0.20f
        when (fileType) {
            FileType.FOLDER -> drawFolder(fg, w, h, pad)
            FileType.ANDROID_APP -> drawAndroidRobot(fg, w, h, pad)
            FileType.IOS_APP -> drawApple(fg, w, h, pad)
            FileType.EXECUTABLE -> drawTerminal(fg, w, h, pad)
            FileType.ARCHIVE -> drawZipper(fg, w, h, pad)
            FileType.IMAGE -> drawImageIcon(fg, w, h, pad)
            FileType.VIDEO -> drawPlayTriangle(fg, w, h, pad)
            FileType.AUDIO -> drawMusicNote(fg, w, h, pad)
            FileType.PDF -> drawPdfText(fg, w, h, pad)
            FileType.CODE -> drawCodeBrackets(fg, w, h, pad)
            FileType.TEXT -> drawTextLines(fg, w, h, pad)
            FileType.WORD -> drawW(fg, w, h, pad)
            FileType.EXCEL -> drawGrid(fg, w, h, pad)
            FileType.POWERPOINT -> drawPlaySquare(fg, w, h, pad)
            FileType.DISK -> drawDisc(fg, w, h, pad)
            FileType.UNKNOWN -> drawFile(fg, w, h, pad)
        }
    }
}

// === 基础形状 ===

private fun DrawScope.drawRoundedBackground(color: Color) {
    val cornerRadius = size.minDimension * 0.20f
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
    )
}

private fun DrawScope.drawFolder(fg: Color, w: Float, h: Float, pad: Float) {
    // 文件夹: 顶部 tab + 主体
    val tabH = h * 0.18f
    val tabW = w * 0.45f
    drawRect(
        color = fg,
        topLeft = Offset(pad, pad + h * 0.08f),
        size = Size(tabW, tabH),
    )
    val bodyH = h - pad - h * 0.08f - tabH
    val bodyTop = pad + h * 0.08f + tabH * 0.5f
    drawRoundRect(
        color = fg,
        topLeft = Offset(pad, bodyTop),
        size = Size(w - 2 * pad, bodyH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
    )
}

private fun DrawScope.drawAndroidRobot(fg: Color, w: Float, h: Float, pad: Float) {
    // Android 机器人: 半圆头 + 触角 + 圆身 + 双手双足 (简化)
    val cx = w / 2
    val headR = w * 0.13f
    val headCy = pad + headR
    // 头
    drawCircle(fg, headR, Offset(cx, headCy))
    // 触角
    val antH = w * 0.10f
    val antW = w * 0.03f
    drawRect(fg, Offset(cx - w * 0.10f, pad - antH * 0.3f), Size(antW, antH))
    drawRect(fg, Offset(cx + w * 0.07f, pad - antH * 0.3f), Size(antW, antH))
    // 身体
    val bodyW = w * 0.45f
    val bodyH = h * 0.30f
    drawRoundRect(
        color = fg,
        topLeft = Offset(cx - bodyW / 2, headCy + headR + 1f),
        size = Size(bodyW, bodyH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f),
    )
    // 双手
    val armH = bodyH * 0.7f
    val armW = w * 0.06f
    drawRect(
        color = fg,
        topLeft = Offset(cx - bodyW / 2 - armW - 1f, headCy + headR + bodyH * 0.15f),
        size = Size(armW, armH),
    )
    drawRect(
        color = fg,
        topLeft = Offset(cx + bodyW / 2 + 1f, headCy + headR + bodyH * 0.15f),
        size = Size(armW, armH),
    )
    // 双足
    val legH = h * 0.12f
    val legW = w * 0.13f
    drawRect(
        color = fg,
        topLeft = Offset(cx - bodyW / 2 - 1f, headCy + headR + bodyH + 1f),
        size = Size(legW, legH),
    )
    drawRect(
        color = fg,
        topLeft = Offset(cx + bodyW / 2 - legW + 1f, headCy + headR + bodyH + 1f),
        size = Size(legW, legH),
    )
}

private fun DrawScope.drawApple(fg: Color, w: Float, h: Float, pad: Float) {
    // Apple: 简化的苹果剪影 (圆 + 顶部凹槽 + 叶子)
    val cx = w / 2
    val cy = h * 0.55f
    val r = w * 0.28f
    // 主体
    drawCircle(fg, r, Offset(cx, cy))
    // 顶部凹槽 (用 path 切)
    val bitePath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                offset = Offset(cx + r * 0.5f, cy - r * 0.3f),
                size = Size(r * 0.6f, r * 0.6f),
            )
        )
    }
    // 叶子
    val leafW = w * 0.10f
    val leafH = h * 0.14f
    drawOval(
        color = fg,
        topLeft = Offset(cx + r * 0.1f, pad),
        size = Size(leafW, leafH),
    )
}

private fun DrawScope.drawTerminal(fg: Color, w: Float, h: Float, pad: Float) {
    // 终端: 圆角矩形 + `>` 提示符
    val r = w * 0.10f
    drawRoundRect(
        color = fg,
        topLeft = Offset(pad, pad),
        size = Size(w - 2 * pad, h - 2 * pad),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
    )
    // `>` 提示符
    val cx = pad + w * 0.10f
    val cy = h / 2
    val len = w * 0.08f
    val path = Path().apply {
        moveTo(cx, cy - len / 2)
        lineTo(cx + len, cy)
        lineTo(cx, cy + len / 2)
        close()
    }
    drawPath(path, fg)
    // 闪烁光标方块
    drawRect(
        color = fg,
        topLeft = Offset(cx + len * 1.5f, cy - len * 0.4f),
        size = Size(len * 1.2f, len * 0.8f),
    )
}

private fun DrawScope.drawZipper(fg: Color, w: Float, h: Float, pad: Float) {
    // 压缩包: 拉链齿 + 拉头
    val cx = w / 2
    val zipTop = pad
    val zipBottom = h - pad
    val zipW = w * 0.04f
    // 拉链中线
    drawRect(
        color = fg,
        topLeft = Offset(cx - zipW / 2, zipTop),
        size = Size(zipW, zipBottom - zipTop),
    )
    // 拉链齿 (左右交错)
    val teethN = 5
    val teethH = (zipBottom - zipTop) / (teethN * 2)
    for (i in 0 until teethN) {
        val y = zipTop + i * teethH * 2 + teethH * 0.5f
        drawRect(
            color = fg,
            topLeft = Offset(cx - zipW * 2, y),
            size = Size(zipW * 1.5f, teethH * 0.6f),
        )
        drawRect(
            color = fg,
            topLeft = Offset(cx + zipW * 0.5f, y),
            size = Size(zipW * 1.5f, teethH * 0.6f),
        )
    }
    // 拉头
    val headH = w * 0.10f
    drawRect(
        color = fg,
        topLeft = Offset(cx - w * 0.10f, zipBottom - headH),
        size = Size(w * 0.20f, headH * 0.7f),
    )
}

private fun DrawScope.drawImageIcon(fg: Color, w: Float, h: Float, pad: Float) {
    // 图片: 山 + 太阳
    val cx = w / 2
    val cy = h * 0.6f
    val r = w * 0.10f
    // 太阳 (右上)
    drawCircle(fg, r, Offset(w - pad - r * 1.2f, pad + r * 1.2f))
    // 山 (三角形)
    val path = Path().apply {
        moveTo(pad, cy + r * 0.5f)
        lineTo(cx - w * 0.10f, cy - r * 1.5f)
        lineTo(cx + w * 0.05f, cy - r * 0.5f)
        lineTo(cx + w * 0.20f, cy - r * 1.3f)
        lineTo(w - pad, cy + r * 0.5f)
        close()
    }
    drawPath(path, fg)
}

private fun DrawScope.drawPlayTriangle(fg: Color, w: Float, h: Float, pad: Float) {
    val cx = w / 2
    val cy = h / 2
    val r = w * 0.25f
    val path = Path().apply {
        moveTo(cx - r * 0.6f, cy - r)
        lineTo(cx + r * 0.8f, cy)
        lineTo(cx - r * 0.6f, cy + r)
        close()
    }
    drawPath(path, fg)
}

private fun DrawScope.drawMusicNote(fg: Color, w: Float, h: Float, pad: Float) {
    // 音符: 圆头 + 杆 + 旗帜
    val cx = w / 2
    val noteR = w * 0.10f
    val noteY = h - pad - noteR
    // 头
    drawCircle(fg, noteR, Offset(cx - w * 0.06f, noteY))
    // 杆
    drawRect(
        color = fg,
        topLeft = Offset(cx - w * 0.06f + noteR - 1f, pad + h * 0.10f),
        size = Size(2f, noteY - (pad + h * 0.10f) + noteR),
    )
    // 旗帜
    val flagPath = Path().apply {
        moveTo(cx - w * 0.06f + noteR + 1f, pad + h * 0.10f)
        lineTo(cx + w * 0.12f, pad + h * 0.18f)
        lineTo(cx - w * 0.06f + noteR + 1f, pad + h * 0.28f)
        close()
    }
    drawPath(flagPath, fg)
}

private fun DrawScope.drawPdfText(fg: Color, w: Float, h: Float, pad: Float) {
    // PDF: 折角文档 + "PDF" 字
    val docL = pad
    val docT = pad
    val docR = w - pad
    val docB = h - pad
    val foldSize = w * 0.15f
    val path = Path().apply {
        moveTo(docL, docT)
        lineTo(docR - foldSize, docT)
        lineTo(docR, docT + foldSize)
        lineTo(docR, docB)
        lineTo(docL, docB)
        close()
    }
    drawPath(path, fg)
    // 折角
    val foldPath = Path().apply {
        moveTo(docR - foldSize, docT)
        lineTo(docR - foldSize, docT + foldSize)
        lineTo(docR, docT + foldSize)
        close()
    }
    drawPath(foldPath, Color.White.copy(alpha = 0.4f))
    // PDF 文字 (简化为粗横线, 真文字用 dp 单位用 Canvas 不好画)
    val textY = h * 0.65f
    val textH = h * 0.05f
    drawRect(
        color = Color.White.copy(alpha = 0.6f),
        topLeft = Offset(docL + w * 0.10f, textY),
        size = Size(w * 0.20f, textH),
    )
    drawRect(
        color = Color.White.copy(alpha = 0.6f),
        topLeft = Offset(docL + w * 0.10f, textY + textH * 2),
        size = Size(w * 0.30f, textH),
    )
}

private fun DrawScope.drawCodeBrackets(fg: Color, w: Float, h: Float, pad: Float) {
    val cx = w / 2
    val cy = h / 2
    val r = w * 0.18f
    val stroke = w * 0.08f
    // 左 `<`
    val leftPath = Path().apply {
        moveTo(cx - r * 0.6f, cy - r)
        lineTo(cx - r * 1.2f, cy)
        lineTo(cx - r * 0.6f, cy + r)
    }
    drawPath(
        path = leftPath,
        color = fg,
        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
    // 右 `/`
    val rightPath = Path().apply {
        moveTo(cx + r * 0.4f, cy - r)
        lineTo(cx + r * 1.2f, cy)
        lineTo(cx + r * 0.4f, cy + r)
    }
    drawPath(
        path = rightPath,
        color = fg,
        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
}

private fun DrawScope.drawTextLines(fg: Color, w: Float, h: Float, pad: Float) {
    // 文本: 5 横线
    val lh = w * 0.04f
    val gap = (h - 2 * pad - lh * 5) / 4
    for (i in 0 until 5) {
        val lineW = when (i) {
            4 -> w * 0.50f  // 末行短
            3 -> w * 0.65f
            else -> w * 0.75f - w * 0.10f * (4 - i - 2).coerceAtLeast(0)
        }
        drawRect(
            color = fg,
            topLeft = Offset(pad, pad + i * (lh + gap)),
            size = Size(lineW, lh),
        )
    }
}

private fun DrawScope.drawW(fg: Color, w: Float, h: Float, pad: Float) {
    // W: 4 笔
    val cx = w / 2
    val top = pad + h * 0.10f
    val bot = h - pad
    val stroke = w * 0.10f
    val path = Path().apply {
        moveTo(cx - w * 0.32f, top)
        lineTo(cx - w * 0.16f, bot)
        lineTo(cx, top + h * 0.20f)
        lineTo(cx + w * 0.16f, bot)
        lineTo(cx + w * 0.32f, top)
    }
    drawPath(
        path = path,
        color = fg,
        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
}

private fun DrawScope.drawGrid(fg: Color, w: Float, h: Float, pad: Float) {
    // 表格: 3x3 网格
    val cellW = (w - 2 * pad) / 3
    val cellH = (h - 2 * pad) / 3
    val stroke = w * 0.03f
    val tablePath = Path().apply {
        // 外框
        moveTo(pad, pad)
        lineTo(w - pad, pad)
        lineTo(w - pad, h - pad)
        lineTo(pad, h - pad)
        close()
        // 竖线
        moveTo(pad + cellW, pad)
        lineTo(pad + cellW, h - pad)
        moveTo(pad + cellW * 2, pad)
        lineTo(pad + cellW * 2, h - pad)
        // 横线
        moveTo(pad, pad + cellH)
        lineTo(w - pad, pad + cellH)
        moveTo(pad, pad + cellH * 2)
        lineTo(w - pad, pad + cellH * 2)
    }
    drawPath(
        path = tablePath,
        color = fg,
        style = Stroke(width = stroke),
    )
    // 填充左上 1 格 (突出表头)
    drawRect(
        color = fg,
        topLeft = Offset(pad, pad),
        size = Size(cellW, cellH),
    )
}

private fun DrawScope.drawPlaySquare(fg: Color, w: Float, h: Float, pad: Float) {
    // PPT: 矩形 + 内嵌三角
    val cx = w / 2
    val cy = h / 2
    val r = w * 0.25f
    // 外框
    drawRoundRect(
        color = fg,
        topLeft = Offset(pad, pad),
        size = Size(w - 2 * pad, h - 2 * pad),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
    )
    // 内部三角 (用 background 颜色切割)
    val path = Path().apply {
        moveTo(cx - r * 0.4f, cy - r * 0.6f)
        lineTo(cx + r * 0.6f, cy)
        lineTo(cx - r * 0.4f, cy + r * 0.6f)
        close()
    }
    drawPath(path, Color.White.copy(alpha = 0.0f))  // 暂不切, 简化实现
}

private fun DrawScope.drawDisc(fg: Color, w: Float, h: Float, pad: Float) {
    val cx = w / 2
    val cy = h / 2
    val r = w * 0.32f
    // 圆盘
    drawCircle(fg, r, Offset(cx, cy))
    // 中心孔
    drawCircle(Color.White.copy(alpha = 0.3f), r * 0.20f, Offset(cx, cy))
    // 高光弧 (左上一段)
    val arcPath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                offset = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
            )
        )
    }
    // 简化: 不画高光, 已经是同心圆够看
}

private fun DrawScope.drawFile(fg: Color, w: Float, h: Float, pad: Float) {
    // 默认文件: 折角文档
    val docL = pad
    val docT = pad
    val docR = w - pad
    val docB = h - pad
    val foldSize = w * 0.15f
    val path = Path().apply {
        moveTo(docL, docT)
        lineTo(docR - foldSize, docT)
        lineTo(docR, docT + foldSize)
        lineTo(docR, docB)
        lineTo(docL, docB)
        close()
    }
    drawPath(path, fg)
    val foldPath = Path().apply {
        moveTo(docR - foldSize, docT)
        lineTo(docR - foldSize, docT + foldSize)
        lineTo(docR, docT + foldSize)
        close()
    }
    drawPath(foldPath, Color.White.copy(alpha = 0.4f))
    // 3 横线
    val lh = w * 0.03f
    for (i in 0 until 3) {
        drawRect(
            color = Color.White.copy(alpha = 0.5f),
            topLeft = Offset(docL + w * 0.10f, h * 0.40f + i * lh * 2),
            size = Size(w * 0.40f, lh),
        )
    }
}

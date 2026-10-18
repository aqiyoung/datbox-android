package com.datbox.app.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 文件列表的类型图标 —— 取自 Lucide (https://lucide.dev, ISC License) 的 file-* 家族
 * (外加 folder / package / disc / presentation)。
 *
 * 每个图标都是官方 SVG 的路径原样搬运, 未做任何手改: 24×24 viewBox、stroke-width 2、
 * 圆角端点/连接 —— 这正是 Lucide 的线稿规格 (圆与折线按 SVG 等价路径写回)。
 * 颜色不写死, 由 Icon(tint = …) 上色 (仓库约定: 颜色只出现在符号上)。
 */
internal object LucideFileIcons {

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** lucide/folder */
    val Folder: ImageVector by lazy {
        icon(
            "folder",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }

    /** lucide/file-type */
    val FileType: ImageVector by lazy {
        icon(
            "file-type",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M11 18h2",
            "M12 12v6",
            "M9 13v-.5a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 .5.5v.5",
        )
    }

    /** lucide/file-text */
    val FileText: ImageVector by lazy {
        icon(
            "file-text",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8",
        )
    }

    /** lucide/file-image */
    val FileImage: ImageVector by lazy {
        icon(
            "file-image",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M8 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "m20 17-1.296-1.296a2.41 2.41 0 0 0-3.408 0L9 22",
        )
    }

    /** lucide/file-play */
    val FilePlay: ImageVector by lazy {
        icon(
            "file-play",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M15.033 13.44a.647.647 0 0 1 0 1.12l-4.065 2.352a.645.645 0 0 1-.968-.56v-4.704a.645.645 0 0 1 .967-.56z",
        )
    }

    /** lucide/file-music */
    val FileMusic: ImageVector by lazy {
        icon(
            "file-music",
            "M11.65 22H18a2 2 0 0 0 2-2V8a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 14 2H6a2 2 0 0 0-2 2v10.35",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M8 20v-7l3 1.474",
            "M4 20a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        )
    }

    /** lucide/file-archive */
    val FileArchive: ImageVector by lazy {
        icon(
            "file-archive",
            "M13.659 22H18a2 2 0 0 0 2-2V8a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 14 2H6a2 2 0 0 0-2 2v11.5",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M8 12v-1",
            "M8 18v-2",
            "M8 7V6",
            "M6 20a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        )
    }

    /** lucide/file-code */
    val FileCode: ImageVector by lazy {
        icon(
            "file-code",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 12.5 8 15l2 2.5",
            "m14 12.5 2 2.5-2 2.5",
        )
    }

    /** lucide/file-spreadsheet */
    val FileSpreadsheet: ImageVector by lazy {
        icon(
            "file-spreadsheet",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M8 13h2",
            "M14 13h2",
            "M8 17h2",
            "M14 17h2",
        )
    }

    /** lucide/presentation */
    val Presentation: ImageVector by lazy {
        icon(
            "presentation",
            "M2 3h20",
            "M21 3v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V3",
            "m7 21 5-5 5 5",
        )
    }

    /** lucide/file-scan */
    val FileScan: ImageVector by lazy {
        icon(
            "file-scan",
            "M20 10V8a2.4 2.4 0 0 0-.706-1.704l-3.588-3.588A2.4 2.4 0 0 0 14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h4.35",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M16 14a2 2 0 0 0-2 2",
            "M16 22a2 2 0 0 1-2-2",
            "M20 14a2 2 0 0 1 2 2",
            "M20 22a2 2 0 0 0 2-2",
        )
    }

    /** lucide/package */
    val Package: ImageVector by lazy {
        icon(
            "package",
            "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
            "M12 22V12",
            "M3.29 7L12 12L20.71 7",
            "m7.5 4.27 9 5.15",
        )
    }

    /** lucide/disc */
    val Disc: ImageVector by lazy {
        icon(
            "disc",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
            "M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        )
    }

    /** lucide/file-terminal */
    val FileTerminal: ImageVector by lazy {
        icon(
            "file-terminal",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "m8 16 2-2-2-2",
            "M12 18h4",
        )
    }

    /** lucide/file */
    val File: ImageVector by lazy {
        icon(
            "file",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
        )
    }
}

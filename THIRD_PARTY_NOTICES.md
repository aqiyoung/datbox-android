# 第三方资源声明

## 文件类型图标 — 自绘

`app/src/main/res/drawable/ic_file_type_*.xml` 为本项目自绘，未取自第三方图标库。

风格：黄色文件夹 + 白色描边 + 底部厚度（立体感），页面类图标统一为「彩色纸页 + 右上折角 + 白色符号」。
所有图标使用同一份 32×32 画布、同一套描边宽度（1.9）与厚度偏移（1.2），保证列表里并排时不打架。

### 图层结构

每个 drawable 由以下图层自下而上叠成：

| 图层 | 作用 |
|---|---|
| shadow | 轮廓下移 0.75、黑色 10% —— 白边在浅色列表上也能立起来 |
| depth | 同轮廓下移 1.2、填充色加深 28% —— 底部厚度 |
| body | 正轮廓，填充 + 1.9 白色描边 |
| fold | 右上折角，填充色提亮 |
| glyph | 白色符号（文字、三角、音符等） |

偏移使用 `<group android:translateY>`，不改写 pathData。

### 与列表的配合

图标在 `FileBrowserScreen.kt` 的 `FileTypeIconBox` 里以 28dp 绘制（外层 46dp 占位），
用 `Icon(painter = ..., tint = Color.Unspecified)` 关闭 `LocalContentColor` 着色，
颜色来自 drawable 自身。

### 图标对照

| drawable | FileKind | 主色 |
|---|---|---|
| `ic_file_type_folder.xml` | FOLDER | `#ffc94d` |
| `ic_file_type_doc.xml` | DOC | `#2b7cd3` |
| `ic_file_type_text.xml` | TEXT | `#3fa9f5` |
| `ic_file_type_image.xml` | IMAGE | `#17a2a2` |
| `ic_file_type_video.xml` | VIDEO | `#f0813a` |
| `ic_file_type_audio.xml` | AUDIO | `#e8557d` |
| `ic_file_type_archive.xml` | ARCHIVE | `#9bae2e` |
| `ic_file_type_code.xml` | CODE | `#7c5ce0` |
| `ic_file_type_sheet.xml` | SHEET | `#157347` |
| `ic_file_type_slide.xml` | SLIDE | `#e0562f` |
| `ic_file_type_pdf.xml` | PDF | `#c62828` |
| `ic_file_type_apk.xml` | APK | `#34a853` |
| `ic_file_type_disk.xml` | DISK | `#5b8fb9` |
| `ic_file_type_exec.xml` | EXEC | `#3e5c76` |
| `ic_file_type_unknown.xml` | UNKNOWN | `#9aa5b1` |

# 第三方资源声明

## 文件类型图标 — Material Icon Theme 几何 + 自绘效果层

`app/src/main/res/drawable/ic_file_type_*.xml` 的**几何形状（pathData）**取自
[Material Icon Theme](https://github.com/material-extensions/vscode-material-icon-theme)，
MIT License，Copyright (c) 2024 Material Extensions。

在原始几何之上，本项目自行叠加了三层效果，用于实现「黄色文件夹 + 白边 + 立体感」：

| 图层 | 做法 |
|---|---|
| 投影 | 轮廓下移 0.9（32 视口基准）、黑色 7.5%，带同等白边宽度 |
| 白边 | 每个 path 填白 + 白色描边（半宽 1.35），形成一圈外轮廓 |
| 渐变 | 每个 path 用上浅下深的线性渐变（提亮 32% → 原色 → 加深 78%），代替平涂 |

偏移使用 `<group android:translateY>`，**不改写 pathData**。
渐变用 `<aapt:attr name="android:fillColor">` 内嵌 `<gradient>`（minSdk 26 已支持）。

### 上游文件对照

- 项目：https://github.com/material-extensions/material-icon-theme
- 取得方式：`https://api.iconify.design/material-icon-theme/<name>.svg`
  （Iconify 上缺少 default folder / default file 两个基础图标，这两个取自上游
  `https://unpkg.com/material-icon-theme/icons/`）

| drawable | 上游文件 | FileKind |
|---|---|---|
| `ic_file_type_folder.xml` | `folder.svg` | FOLDER |
| `ic_file_type_doc.xml` | `word.svg` | DOC |
| `ic_file_type_text.xml` | `document.svg` | TEXT |
| `ic_file_type_image.xml` | `image.svg` | IMAGE |
| `ic_file_type_video.xml` | `video.svg` | VIDEO |
| `ic_file_type_audio.xml` | `audio.svg` | AUDIO |
| `ic_file_type_archive.xml` | `zip.svg` | ARCHIVE |
| `ic_file_type_code.xml` | `xml.svg` | CODE |
| `ic_file_type_sheet.xml` | `table.svg` | SHEET |
| `ic_file_type_slide.xml` | `powerpoint.svg` | SLIDE |
| `ic_file_type_pdf.xml` | `pdf.svg` | PDF |
| `ic_file_type_apk.xml` | `android.svg` | APK |
| `ic_file_type_disk.xml` | `disc.svg` | DISK |
| `ic_file_type_exec.xml` | `console.svg` | EXEC |
| `ic_file_type_unknown.xml` | `file.svg` | UNKNOWN |

### 主色

上游调色板里 `audio`/`pdf` 同为 `#ef5350`、`code`/`sheet` 同为 `#8bc34a`，
在列表里区分度不够，因此几何保留、主色替换为下列一套（每类不同色相）：

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

`UNKNOWN` 的几何改为实心空白页（去掉 `doc` 的 W 字形）。上游的空白页是描边式、
内部镂空，叠白边后会被填成一整块白，无法辨认。

### 与列表的配合

图标在 `FileBrowserScreen.kt` 的 `FileTypeIconBox` 里以 28dp 绘制（外层 46dp 占位），
用 `Icon(painter = ..., tint = Color.Unspecified)` 关闭 `LocalContentColor` 着色，
颜色来自 drawable 自身。

### MIT License

The MIT License (MIT)
Copyright (c) 2025 Material Extensions

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

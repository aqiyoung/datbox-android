# 第三方资源声明

## 文件类型图标 — Material Symbols Rounded 字形 + 自绘纸张

`app/src/main/res/drawable/ic_file_type_*.xml` 的**字形路径（pathData）**取自
[Material Symbols](https://fonts.google.com/icons) 的 Rounded 字重（filled），
Apache License 2.0，Copyright (c) Google LLC。

在此之上，本项目自行绘制了「白纸 + 折角 + 落影」的外壳，以及琥珀色文件夹，
统一成 iOS Files 那种「白纸 + 彩色字形」的观感：

| 图层 | 做法 |
|---|---|
| 纸张 | 24 视口内的圆角竖版纸（4.2,1.8)-(19.8,22.2)，r2.4，上白下近白的线性渐变 |
| 折角 | 右上切 5.2 的斜角 + 折下来的纸背三角（自行绘制）；切角与纸边交点做了 0.8 倒圆 |
| 落影 | 纸张轮廓下移 0.7 / 0.75，黑色 11% / 14%，带同等模糊宽度的描边 |
| 发丝边 | 纸张外轮廓 0.35 描边（`#E3DFD2`），否则白纸压在象牙底色上看不见边界 |
| 字形 | 上游字形按实际 alpha 包围盒等比缩放到 10.4 见方，居中于 (12, 14.6)，按类型上色 |
| 文件夹 | 单形状（背板折角 + 前片），`#FFD46E → #F0A93C` 线性渐变 + 落影 |

偏移使用 `<group android:translateY>`，缩放用 `<group>` 的 scale，
**不改写 pathData**；字形坐标要额外加 `translateY 960`（Material Symbols 的
SVG 视口是 `0 -960 960 960`）。
渐变用 `<aapt:attr name="android:fillColor">` 内嵌 `<gradient>`（minSdk 26 已支持）。

### 上游文件对照

- 图标库：https://fonts.google.com/icons （Material Symbols Rounded）
- 取得方式：`https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/<name>/fill1/24px.svg`

| drawable | 字形 | FileKind | 颜色 |
|---|---|---|---|
| `ic_file_type_folder.xml` | （自绘） | FOLDER | `#FFD46E → #F0A93C` |
| `ic_file_type_doc.xml` | `subject` | DOC | `#2E7BE0` |
| `ic_file_type_text.xml` | `notes` | TEXT | `#6B7785` |
| `ic_file_type_sheet.xml` | `grid_on` | SHEET | `#22A06B` |
| `ic_file_type_slide.xml` | `slideshow` | SLIDE | `#F28B30` |
| `ic_file_type_pdf.xml` | `picture_as_pdf` | PDF | `#E3494E` |
| `ic_file_type_code.xml` | `code` | CODE | `#7A5CF0` |
| `ic_file_type_archive.xml` | `folder_zip` | ARCHIVE | `#A9762F` |
| `ic_file_type_image.xml` | `image` | IMAGE | `#D6559B` |
| `ic_file_type_video.xml` | `movie` | VIDEO | `#17A2C4` |
| `ic_file_type_audio.xml` | `music_note` | AUDIO | `#C24BD6` |
| `ic_file_type_apk.xml` | `android` | APK | `#1E9E8C` |
| `ic_file_type_disk.xml` | `hard_drive` | DISK | `#5A7A9B` |
| `ic_file_type_exec.xml` | `terminal` | EXEC | `#4A4F57` |
| `ic_file_type_unknown.xml` | `help` | UNKNOWN | `#9AA0A6` |

每类用不同色相，是因为白纸上只剩字形承担区分度；Material Symbols 默认的
单色方案在列表里会让 `doc`/`text`、`image`/`video` 难以一眼分开。

### 与列表的配合

图标在 `FileBrowserScreen.kt` 的 `FileTypeIconBox` 里以 28dp 绘制（外层 46dp 占位），
用 `Icon(painter = ..., tint = Color.Unspecified)` 关闭 `LocalContentColor` 着色，
颜色来自 drawable 自身。

### Apache License 2.0

Material Symbols 由 Google LLC 以 Apache License 2.0 发布：
https://www.apache.org/licenses/LICENSE-2.0

```
Copyright (c) Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

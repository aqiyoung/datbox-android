# 第三方资源声明

## 文件类型图标 — Material Icon Theme

`app/src/main/res/drawable/ic_file_type_*.xml` 由 **Material Icon Theme** 的 SVG 转换而来
（原 SVG 转成 Android VectorDrawable，几何路径未作修改；唯一一处填充色改动见文末「本地改动」）。

- 项目：https://github.com/material-extensions/material-icon-theme
- 取得方式：`https://api.iconify.design/material-icon-theme/<name>.svg`
  （Iconify 上缺少 default folder / default file 两个基础图标，这两个取自上游
  `https://unpkg.com/material-icon-theme/icons/`）
- 许可：MIT

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

### 本地改动

`ic_file_type_folder.xml` 的填充色由上游的 `#90a4ae`（灰蓝）改为 `#ffb74d`（暖黄）。
原因：文件夹在列表里占绝大多数，上游默认的灰蓝文件夹会让整屏看起来偏灰；改成文件管理器
常见的暖黄后与其余彩色图标协调。仅改填充色，几何路径与其余图标均与上游一致。

### MIT License

The MIT License (MIT)
Copyright (c) 2025 Material Extensions

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

# 三页云盘 (OpenList) - 版本迭代记录

## v0.3.4 (build 14) - 2026-06-14
### 🐛 修复 (老板 6/14 反馈: 上传下载报错)
- **下载 401 根因**: `/d/<path>` 路由只查 `?sign=` 不看 Authorization。改用先 `POST /api/fs/get` 拿 sign，再 `/d/<path>?sign=xxx` 跳 302。
- **上传 400 'storage not found' 根因**: `FsForm` 源码是读 `File-Path` header，不读 query。改用 `PUT /api/fs/form` + `File-Path: <完整路径>` + `As-Task: true` + `Overwrite: true`。
- 干掉 `OpenListApi.upload` (Retrofit 幻觉端点)，上传改 OkHttp 直发。

### 💧 UI 升级: 液态玻璃弹窗
- 长按文件从 `AlertDialog.Builder` (灰底老气) 改为 Material 3 `Dialog` + `Surface` 半透明白 (alpha 0.85) + 24dp 圆角 + 12dp 阴影。
- 下载/分享两项独立行：圆角小背景 + icon 圆 + 标题 + 副标题三段式。
- 取消按钮独立一行（玻璃感分层）。

## v0.1.1-beta (build 3) - 2026-06-13
### 🆕 新增
- **APP 名称变更**：`OpenList` → `三页云盘`（中文优先 + 老板家品牌）
- **构建类型 (Build Types)**：
  - `debug` - applicationId `com.threel.openlist.debug`，versionName 后缀 `-debug`
  - `beta` - applicationId `com.threel.openlist.beta`，versionName 后缀 `-beta`（老板手机直装）
  - `release` - applicationId `com.threel.openlist`，isMinifyEnabled true（待签名）
- **APP 图标升级 (icon v5)**：cairosvg 矢量渲染 res.oplist.org 官方 logo
  - sky-400 #38BDF8 + teal-200 #99F6E4
  - 22% 圆角 + 5 mipmap 尺寸 + round launcher
  - 修复 cairosvg 不解析 CSS 变量 → 字面色展开
- **字符串资源扩展**：`app_name_en` / `app_subtitle` / `app_version_format` / `changelog_url` / `update_check_url`

### 🔧 技术债务
- 拆分 `data/api/` 包 (AuthInterceptor / NetworkModule / OpenListApi / OpenListRepository / TokenStore)
- Compose BOM 2024.06 + Material 3 + Hilt 2.51.1 + Retrofit 2.11 + DataStore 1.1.1
- minSdk 26 (Android 8.0), targetSdk 34, compileSdk 34

## v0.1.0-beta.1 (build 1) - 2026-06-13
- 初始 GitHub Release（ci-build）
- 临时 applicationId `com.threel.openlist`
- 临时图标 = Synapse 神经突触 v3（占位）

## v0.1.0 (build 1) - 2026-06-13
- 初始本地 APK
- Compose + Hilt + Retrofit + DataStore 架构落地
- 登录页 + 文件浏览页 + TokenStore + AuthInterceptor
- TokenStore 默认 server: `https://<your-server>`

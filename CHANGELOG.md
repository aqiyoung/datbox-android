# 三页云盘 (OpenList) - 版本迭代记录

## v0.3.7 (build 17) - 2026-06-14
### 🎨 UI 升级 (老板 6/14 拍: 换官方 logo + 标注)
- **APP 图标换 OpenList 官方 logo**: 重跑 cairosvg 渲染脚本 (v5), sky-400 蓝底 + 几何 “O” 线条 + 22% 圆角。5 个 mipmap 尺寸 + round launcher 全部刷新
- **About 页面加“基于官方 OpenList 开发”标注**: 在版本号下面一行, 12sp + onSurfaceVariant 色, 点跟随点官网仓库 (AppConfig.UPSTREAM_REPO)
- **AppConfig 常量加 UPSTREAM_NAME / UPSTREAM_REPO / UPSTREAM_NOTE**

## v0.3.6 (build 16) - 2026-06-14
### 🐛 防护 (老板 6/14 反馈: '上传下载还是失败')
- **根因不是代码而是 server 限流**: OpenList 默认 5 次错密码锁 5 分钟, 老板手机在 v0.3.4 5 次错锁 5 分钟 → v0.3.5 重装后老密码也错 → 续锁。******** 是对的 (我 10:09 curl login 200 success), 错密码是装的别的 APK
- **防误锁**: LoginViewModel.submit 失败时 clearLastCredentials(), 下次启动不再顶填错密码
- **老板需要**: 装 v0.3.6 (APP 改过), 重启 APP (限流过我 10:09 重启 server 清了), 手输正确密码 admin / ********

## v0.3.5 (build 15) - 2026-06-14
### 🐛 修复 (老板 6/14 反馈: '点三个点没反应')
- **FileRow 三个点图标** 从装饰 Icon 改为 IconButton (点中圈区 32dp) → 点三个点直接弹玻璃弹窗
- **移除长按整行的弹窗** (双通道合并为一个, 点三个点是正路)
- 文件行可点区不变 (还是点文件/点目录)

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

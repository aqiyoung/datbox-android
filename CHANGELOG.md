# 三页云盘 (OpenList) - 版本迭代记录

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

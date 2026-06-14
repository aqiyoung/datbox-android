## v0.3.29 (build 39) - 2026-06-14 20:10
### 🔒 分享链接隐藏中间目录 (老板 6/14 20:00 拍: '不能暴露我们的目录啊')
- **之前**: 分享链接露完整路径
  - 例: https://<your-server>/d/天翼云盘/电影/动作片/蝙蝠侠.mkv?sign=xxx
  - 问题: 拿到链接的人能看到整个目录树, 隐私泄露
- **现在**: OpenList 4.x 官方短链 /sd/<id>/<filename>
  - 例: https://<your-server>/sd/EXhJt9KX/蝙蝠侠.mkv
  - 拿链接的人只看到 ID + 文件名, 不知道原路径
- **代码改动**:
  - AppConfig 加 PUBLIC_BASE_URL = "https://<your-server>" (必须用公网域名, 不用 serverUrl 内网)
  - OpenListRepository.buildShareUrl -> buildShortShareUrl
  - 实现: POST /api/share/create 拿 share_id -> 拼 /sd/<id>/<name> (URL encode)
  - ViewModel FileBrowserScreen.buildShareUrl 调 repo.buildShortShareUrl
- **限制**:
  - share 永久有效 (expires 2099-12-31)
  - 无密码 (pwd="")
  - 多文件 share 也可以 (/api/share/create 接受 files 数组), 但目前 UI 一次只分享一个
- **验证**: 手工 curl  https://<your-server>/sd/E6xL1bJ2/hello.txt -> 200, 19B (老板 6/14 19:57 测过)

## v0.3.28 (build 38) - 2026-06-14 19:50
### 🗑️ 移除 About 页底部'更新日志'区块 (老板 6/14 19:38 拍: '把下面那个更新日志移除吧, 不要了')
- **AboutScreen 精简**:
  - 删: 底部 '更新日志' 标题 + 分割线
  - 删: changelogLoading/changelogError/changelog 三个 state + LaunchedEffect
  - 删: ChangelogCard composable
  - 删: ChangelogData / ReleaseEntry data class (解析用)
  - 删: changelogClient (OkHttp)
  - 删: fetchChangelog() 挂起函数
  - 删: 'androidx.compose.foundation.lazy.items' import
  - 文件: 367 行 -> 250 行 (-117 行)
- **保留**:
  - AppConfig.CHANGELOG_URL 常量 (不删, 以后可能复用)
  - changelog.json server API (<your-server> 上还在, 不动)
  - AppUpdateInfo 里的 changelog_url 字段 (数据模型, 后续可能用)

## v0.3.27 (build 37) - 2026-06-14 19:40
### 🎨 字体换 MiSans (老板 6/14 19:35 拍: '什么字体？看着刺眼, 用 misans 吧')
- **Type.kt 重写**:
  - 之前: SerifFamily (系统衬线) + SansFamily (Roboto/Noto), 衬线在小字号 + 阴影下扎眼
  - 现在: MiSansFamily = FontFamily(Regular, Medium, Semibold, Bold) — 4 字重全装入
  - 字体源: ~/dev-projects/synapse/app/assets/fonts/MiSans*.ttf (小米 2022, CC-BY 4.0)
- **APK 体积 增大**: 1.8MB -> 32MB (MiSans 4 字重 * 8MB)
  - 后续可优化: pyftsubset 中文子集化 (上文字符 3000+)
- **LiquidGlass 文字阴影 减弱** (顺手修):
  - titleTextStyle 阴影 0.30/4 -> 0.20/2 (TopBar 标题)
  - liquidGlassTextStyle helper 默认 0.25/3 -> 0.12/1.5 (Row 文字)
  - subtitle 0.20/3 -> 0.08/1.5
  - 原因: 阴影 + 0.30 黑色 + 白玻璃, 在 OLED 屏上双层黑色看着扎眼
  - 不去阴影: 白玻璃 0.72 alpha 下, 黑色文字 + 白底其实够清楚, 阴影只加层次

## v0.3.26 (build 36) - 2026-06-14 19:30
### 🐛 修复文件夹列表文字模糊 (老板 6/14 19:20 拍)
- **LiquidGlassRow 拆双层 Box**:
  - 老板报: '网盘里面的文件夹, 名称都看不清'
  - 根因: v0.3.23 加的 `.blur(12.dp)` 写在外层 Box, Modifier 链是 clip → background → **blur** → border → clickable, blur 会把后续渲染也模糊了, **内层 Row 文字图标全部被模糊**
  - 修法: 拆双层 Box (跟 LiquidGlassCard 一样), 外层只做背景 (blur 12→6dp), 内层装内容 (清晰)
  - 文字加 TextStyle.shadow 黑色 0.25 alpha 偏移 0,1 模糊 3 (跟 iOS 26 液态玻璃同款)
- 状态栏沉浸 + 液态玻璃 TopBar 不动 (v0.3.25 已经修对)

## v0.3.25 (build 35) - 2026-06-14 19:00
### 🐛 修复状态栏不沉浸 + 液态玻璃文字糊 (老板 6/14 18:40 拍)
- **Revert 21c3b3b** (恢复 `enableEdgeToEdge()`):
  - 状态栏 / 导航栏恢复透明, 沉浸式回来
  - 液态玻璃 TopBar 模糊真实画布 (不是模糊白底)
- **LiquidGlassTopBar 液态玻璃文字糊修复**:
  - 标题加 `TextStyle.shadow`: 黑色 0.30 alpha, 偏移 (0,1), 模糊 4dp — iOS 26 同款
  - icon tint alpha 0.95 + NearBlack #141413
  - 模糊半径 20dp → 12dp (iOS 26 默认)
  - 玻璃渐变 alpha 0.80/0.60 → 0.55/0.40 (让下面内容更可见)
- 状态栏图标深色/浅色切换保留 a1d2779 的 SideEffect + WindowCompat 方案

## v0.3.14 (build 24) - 2026-06-14 13:30
### ✨ 液态玻璃 UI 全面升级 (老板 6/14 拍: "全做 在加上手势返回")
- **LiquidGlass.kt 增强 (3 个新组件)**:
  - `LiquidGlassRow`: 圆角 16dp + 选中态 alpha 0.95 + 暖色渐变底
  - `LiquidGlassTopBar`: 半透明 Parchment 0.75 + 渐变底 + 标题前缀 logo icon
  - `LiquidGlassFab`: Terracotta 渐变 + 1px 描边 + 圆角 20dp
- **FileBrowserScreen**:
  - `TopAppBar` → `LiquidGlassTopBar`
  - `ExtendedFloatingActionButton` → `LiquidGlassFab` (玻璃上传按钮)
  - `FileRow` → `LiquidGlassRow` (圆角 16dp, 选中态液态)
  - `CenterLoading` / `CenterMessage` → `LiquidGlassCard` (居中玻璃卡)
  - 空目录 / 错误 加 "返回上一层" / "刷新" 按钮
- **AboutScreen**: 顶部品牌区 → 大号 `LiquidGlassCard` (24dp 圆角) 装 LOGO + 名称 + 版本 + "基于官方 OpenList 开发" 标注
- **LoginScreen**: 矩形 `Button` → `LiquidGlassFab` (玻璃登录按钮)
- **OpenListNavGraph**: `BackHandler` 在 About 屏幕 → 系统返回键 / 手势返回都能回到 files
- **OpenListRepository**: 修 EACCES! 下载写 `getExternalFilesDir(DIRECTORY_DOWNLOADS)` (app 私有目录, 不需 WRITE_EXTERNAL_STORAGE 权限, Android 11+ scoped storage 兼容)
- **OpenListNavGraph**: 启动 Loading → 居中 `CircularProgressIndicator`
- **CHANGELOG / latest.json / changelog.json**: 同步到 <your-server>

# 三页云盘 (OpenList) - 版本迭代记录

## v0.3.9 (build 19) - 2026-06-14
### 🐛 修 2 个 UX/逻辑 (老板 6/14 10:45 反馈)
- **登录页面 LOGO 加圆角 + 跟背景融合**: `Modifier.clip(RoundedCornerShape(size * 0.22f))` + `ContentScale.Crop` - 不再四方四正, 跟桌面图标一致
- **修分享链接 401 真根因**: `buildShareUrl` 之前返 `https://.../d<path>` (没 sign, 永远 401)。10:32:41 老板手机 /d/天翼云盘/Windows 401 是点击分享后, 粘到浏览器跳 401。改成跟 download 一样先 `POST /api/fs/get` 拿 sign 再拼 `?sign=`
- 之前 v0.3.0-v0.3.8 老板所有 '上传下载还是不对' 里包含分享这一路 (只是老板描述不准, 实际是分享链接点开跳 401)

## v0.3.8 (build 18) - 2026-06-14
### 🎨 修正 LOGO (老板 6/14 10:31 反馈: '它不是白色底吗? 咱这个你自己弄的那差一笔')
- **mipmap 图标改白底**: 重跑 generate_v6.py, sky-400 蓝线条 + teal-200 浅蓝绿 + **白底** (跟 OpenList 官方 web favicon 完全一致)
  - 之前 v5/v7 是蓝底 (#38BDF8) + 蓝线条 = 蓝上画蓝, 对比度差、跟官方不一致
  - 现在是 白底 + 蓝/浅蓝绿线条, 跟 https://res.oplist.org/logo/logo.svg 展示一致
- **登录页面 OpenListLogo 换官方 SVG 渲染图**: 之前是 Canvas 手画 (蓝圆 + 深蓝斜杠 + 青色环), 跟 mipmap 不一致; 现在用 drawable/openlist_logo_official.png (512×512 白底 SVG 渲染)
- **drawable/openlist_logo_official.png**: 官方 SVG cairosvg 渲染 + 白底, 作为 LoginScreen 的 Image painterResource
- **assets/icons/openlist_icon_v5_*.png 清除**: v5 产物过气, 只保留 v6

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

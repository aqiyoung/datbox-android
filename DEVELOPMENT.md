# 开发说明

给要改这个 App 的人：怎么搭、怎么发版、关键实现放在哪。

## 环境

| 项 | 版本 |
| --- | --- |
| JDK | 17 |
| Kotlin | 1.9.24（compose compiler 1.5.14） |
| Compose | BOM 2025.01.00，Material 3 固定 1.2.0 |
| Android SDK | compileSdk / targetSdk 34，minSdk 26 |

## 目录

```
app/src/main/java/com/datbox/app/
├── data/
│   ├── api/        Retrofit 接口、TokenStore、动态服务器拦截器
│   ├── download/   下载队列 + MediaStore 落盘 + 完成通知
│   ├── model/      FsItem / Mount / Share / User 等
│   └── update/     GitHub Release 检查更新
├── ui/
│   ├── component/  液态玻璃组件（LiquidGlass.kt）、下拉刷新
│   ├── screen/     登录 / 文件浏览 / 预览 / 管理 / 关于
│   └── theme/      颜色、字体（MiSans）
└── util/           AppConfig、本地日志
```

## 构建

```bash
./gradlew assembleDebug   # 调试包
./gradlew test            # 单元测试
```

## CI 与发版

| 工作流 | 触发 | 内容 |
| --- | --- | --- |
| `ci.yml` | push / PR | 单元测试 + `assembleDebug`，产物留 artifact |
| `build-apk.yml` | push 到 `main` / `beta` | 签名 release APK、打 tag、建 Release、推 Telegram |

- `main` → 正式版，tag `v{versionName}`
- `beta` 分支 → 预发布，tag `v{versionName}-beta.N`，同版本自动递增
- 签名走 secrets：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
- Release 产物名 `datbox-v{tag}.apk`

发版步骤：

1. 改 `app/build.gradle.kts` 的 `versionCode`（+1）和 `versionName`
2. commit，改动写进 commit message（CHANGELOG 已停更）
3. push 到 `main`，等两条流水线通过
4. `gh release view v{versionName}` 确认产物

## 关键实现

### 服务器地址

App 不内置任何后端。登录页填 `host[:port]`，HTTPS / HTTP 开关决定协议，存 DataStore。
Retrofit 的 baseUrl 是占位 host `dynamic.openlist.local`，`DynamicServerInterceptor` 在每次
请求时替换成用户实际地址，所以换服务器不用重建 Retrofit。

### 液态玻璃

`ui/component/LiquidGlass.kt`：`FrostedGlass` 用 RenderEffect 做背景模糊（Android 12+），
低版本降级成半透明底色。列表行 `LiquidGlassRow`、菜单 `GlassMenu*`、面板 `LiquidGlassSurface`。

### 文件列表

`FileBrowserScreen.kt`：`FileBrowserViewModel` 管路径 / 排序 / 搜索，列表是玻璃卡片。
下拉刷新用 Material 的 `pullRefresh`（封装在 `GlassPullRefresh`），刷新时只替换 items，
不切 loading；失败只弹提示，保留原列表。

### 下载

`AppDownloadManager`：前台服务排队下载，落到 `Download/Datbox`（Android 10+ 走 MediaStore，
低版本走 WRITE_EXTERNAL_STORAGE），完成后发通知，点开走 FileProvider。

### 日志

`TelemetryLog` 只写 Logcat 和 `Android/data/com.datbox.app/files/logs/local.log`，不上传。

## 约定

- 正式包一律由 CI 产出
- 每版必须同步升 `versionCode` / `versionName`
- UI 文案用中文，代码注释保持现状

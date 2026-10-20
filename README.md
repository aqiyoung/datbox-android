# Datbox Android

Datbox — 原生 Android 客户端，用来连接自己的 OpenList / Alist 后端。

## 功能

- **登录**：服务器地址直接填在登录框里（域名或 `IP:端口`），旁边是 HTTPS / HTTP 开关；粘贴完整 URL 会自动拆出协议。可选记住账号密码。
- **文件浏览**：多级目录导航、排序、返回上一层；卡片式列表，支持下拉刷新。
- **搜索**：优先走服务端搜索 API，走不通时回退到本地逐层遍历。
- **下载**：文件存到公共 `Download/Datbox` 目录，完成后可从通知栏直接打开。
- **上传**：从系统文件选择器选文件，上传到当前目录。
- **分享**：生成 OpenList 分享链接，可复制或调起系统分享。
- **预览**：常见图片等文件在应用内预览。
- **管理**：用户 / 挂载 / 分享 / 设置四个 Tab，含存储概览、当前服务器地址、退出登录。

## 技术栈

| 项 | 说明 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 液态玻璃 | 自定义 Compose Modifier + RenderEffect（Android 12+） |
| 架构 | MVVM + Hilt |
| 网络 | Retrofit + OkHttp + kotlinx.serialization |
| 本地存储 | DataStore |
| 最低 API | 26（Android 8.0） |
| 目标 API | 34（Android 14） |
| 包名 | `com.datbox.app` |

## 下载

从 [Releases](https://github.com/aqiyoung/datbox-android/releases) 下载最新的 `datbox-vX.Y.Z.apk`。

## 构建

正式 APK 全部由 GitHub Actions 产出，推到 `main` 会触发两条流水线：

| 工作流 | 内容 |
| --- | --- |
| `CI Build Check` | 单元测试 + `assembleDebug` |
| `Build & Release Android APK` | 签名 release APK，打 tag `vX.Y.Z` 并发布到 Releases |

本地编译（可选）：

```bash
./gradlew assembleDebug
```

发版规则：每次改动都要同步升 `app/build.gradle.kts` 里的 `versionCode` / `versionName`，改动内容写在 commit message 里。

## 目录

```
app/src/main/java/com/datbox/app/
├── data/     API、模型、下载、更新
├── ui/       屏幕、组件、主题、导航
└── util/     配置与工具
```

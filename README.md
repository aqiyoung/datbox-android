# Datbox Android Client
Datbox — 原生 Android 客户端，连接 OpenList / Alist 后端（v4.2.2）。

## 技术栈
- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **液态玻璃**（iOS 26 风格）：自定义 Compose Modifier + RenderEffect（API 31+）
- **网络**：Retrofit + OkHttp + kotlinx.serialization
- **架构**：MVVM + Hilt DI + Room cache
- **最低 API**：Android 8.0 (API 26)
- **目标 API**：Android 14 (API 34)

## 包名
`com.datbox.app`

## 后端
- 本地 OpenList v4.2.2 systemd 服务（端口 5244）
- 历史 web admin UI: http://<internal-ip>:5244/

## MVP 功能（首版）
1. 登录（admin / guest）
2. 文件浏览（按 path 树形导航）
3. 下载文件
4. 分享链接生成
5. 搜索

## 二期（暂缓）
- 视频/图片预览
- 上传
- 后台任务（offline download）
- 系统文件提供器集成

## 启动
```bash
# Android Studio Hedgehog+ 打开此目录
# 或命令行构建
./gradlew assembleDebug
```

## 设计语言（沿用 Synapse）
- 暖色 Terracotta #c96442（主色）
- 衬线标题（Roboto Slab / Source Serif Pro）
- 液态玻璃：半透明白 + 暖色光晕 + 玻璃反光
- 神经突触/多盘聚合 icon 隐喻

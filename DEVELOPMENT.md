# OpenList APP 开发文档 (v0.3.3)

> 老板问"整理一下开发文档"——这是 6/13-6/14 通宵开发的完整记录
> 涉及：APP 开发 / 后端部署 / nginx 反代 / OpenList 4.x 集成

---

## 项目概况

| 项 | 值 |
|---|---|
| **APP 名称** | 三页云盘 (com.threel.openlist) |
| **后端** | OpenList 4.2.2 (systemd: openlist.service) |
| **域名** | https://<your-server> |
| **GitHub** | https://github.com/aqiyoung/openlist-android |
| **数据目录** | ~/openlist/ |
| **systemd** | /etc/systemd/system/openlist.service |
| **nginx** | /etc/nginx/conf.d/<your-server>.conf |
| **老板账号** | admin / ******** (role=0 admin, base_path=/) |

---

## 6/14 通宵 7 个版本（v0.2.1 ~ v0.3.3）

| 版本 | 主要内容 |
|---|---|
| **v0.2.1** | 初始发布 - 登录 + 文件列表 (空目录) |
| **v0.2.2** | 修登录后不跳转 (onLoginSuccess 空 stub → setLoggedIn) |
| **v0.2.3** | 修文件列表空 (FsItem.isDir 加 @SerialName("is_dir")) + 记住密码 (DataStore) |
| **v0.2.4** | About in-app 渲染 changelog + 检查更新弹窗 |
| **v0.3.0** | 加 upload/download/share (FAB + 长按菜单 + share Intent) |
| **v0.3.1** | About 极简化 (只留 2 按钮: 更新+仓库) |
| **v0.3.2** | About 整页可滑动 (LazyColumn) |
| **v0.3.3** | 修下载 (去 "Bearer " 前缀) + 修上传 (根目录提示进 storage) + GitHub 猫猫 logo + 复制链接 |

---

## v0.3.3 完整功能

### 1. 登录 (LoginScreen)
- 液态玻璃登录卡 (alpha 0.5 毛玻璃 + 暖色径向渐变)
- **OpenListLogo** Canvas 绘制 (天蓝圆 + LB 蓝斜杠 + RT 青圆环)
- 密码小眼睛切换
- **记住账号密码** (DataStore 持久化, init 自动预填)
- v0.2.2 修了 onLoginSuccess 空 stub bug

### 2. 文件浏览器 (FileBrowserScreen)
- 路径面包屑 (TopAppBar 副标题)
- **FAB 右下角** (ExtendedFloatingActionButton "上传")
- 文件/文件夹不同图标 (Folders: 暖橘 + Outlined.Folder)
- **长按文件** → 弹菜单 (AlertDialog) → 下载/分享
- 目录: /天翼云盘 + /天翼云盘飞牛备份 (2 个 storage)

### 3. 上传 (FAB)
```kotlin
// OpenListRepository.upload
PUT /api/fs/form?path={remotePath}&override=true
- multipart/form-data
- Authorization: {token}  // raw token, 不加 Bearer (OpenList 4.x bug)
- root 目录上传会抛 error: "请先进 storage 目录再上传"
```

### 4. 下载 (长按文件 → 下载到本地)
```kotlin
// OpenListRepository.download
GET /d/{path}?sign=xxx  // 或无 sign
- Authorization: {token}  // raw token
- 写到 Environment.getExternalStoragePublicDirectory(DOWNLOADS)
- readTimeout 120s (2 分钟大文件)
```

### 5. 分享 (长按文件 → 系统分享)
```kotlin
fun buildShareUrl(remotePath: String) = "$serverUrl/d$remotePath"
// Intent.ACTION_SEND + text/plain
```

### 6. About (极简)
- 顶部: 品牌 + 版本号
- 中间: 2 按钮 (更新 + 仓库) **这是用户拍板的极简方案**
- 仓库: **GitHub 猫猫 logo** (drawable/ic_github_cat.xml 官方 Octocat 矢量图)
- 分割线
- "更新日志" + v0.2.1/2/2.3 changelog 卡片 (整页 LazyColumn 可滑)
- 仓库按钮: 跳 https://github.com/aqiyoung/openlist-android
- 更新按钮: AppUpdateManager.checkForUpdate() + 弹 3 按钮对话框 (下载/复制链接/稍后)

---

## 关键 bug 修复时间线

### 1. 文件列表空 (v0.2.3 修)
**根因**: `FsItem.isDir` 字段没加 `@SerialName("is_dir")` —— OpenList 4.x API 返 snake_case
**修**: `@SerialName("is_dir") val isDir: Boolean = false`
**教训**: OpenList 4.x 全部 JSON 字段是 snake_case

### 2. 登录后不跳转 (v0.2.2 修)
**根因**: `OpenListNavGraph` 里 `onLoginSuccess` 是空 stub, 注释里都写了 `/* 不工作 */`
**修**: RootViewModel 加 `setLoggedIn()` + lambda 改 `vm.setLoggedIn()`

### 3. 下载 HTTP 401 invalidated (v0.3.3 修)
**根因**: OpenListRepository.download 手动加了 `"Bearer $token"` 前缀
- OpenList 4.x 中间件: 拿 `c.GetHeader("Authorization")` 当 token
- 但 GenerateToken Set validTokenCache 的 key 是 `eyJ...` (不带 Bearer)
- ParseToken Get 时拿到 `Bearer eyJ...` 找不到 cache → 401 invalidated
- 跟 Retrofit AuthInterceptor 不一致 (AuthInterceptor 已经传 raw)
**修**: 去掉 "Bearer " 前缀
**教训**: OpenList 4.x 客户端不能传 "Bearer " 前缀 (跟 OAuth 标准不一样)

### 4. 上传 "storage not found" (v0.3.3 修)
**根因**: 用户在根 `/` 触发上传, 但 storage 都在 `/天翼云盘/...`
- `PUT /api/fs/form?path=/test.txt` 在根目录 → OpenList 返 400 "storage not found"
**修**: `remoteDir == "/"` 抛 error: "请先进 storage 目录再上传"
**教训**: 根目录只是 storage 的容器, 不能直接写文件

### 5. HTTP 413 (nginx 修复 - 凌晨 00:40 修)
**根因**: `/etc/nginx/conf.d/<your-server>.conf` 的 `location /` 漏了 `client_max_body_size`
- nginx 默认 1M
- 老板上传文件 ≥1M 就 413
**修**: 在 `proxy_pass http://127.0.0.1:5244/;` 下面加 `client_max_body_size 0;`
**备份**: `/etc/nginx/conf.d/<your-server>.conf.bak.20260614_004000`
**重启**: `sudo systemctl restart nginx` (systemd 守护, reload 不一定生效)

---

## 老板 4 次反馈 (v0.3.x 设计)

### 第 1 次 (v0.3.0) - "上传下载失败" + "关于页面增加的东西有点多"
**老板拍板**:
- A. 上传/下载/分享 全加 (v0.3.0 一气做完)
- B. About 砍 4 个按钮, 只留核心

### 第 2 次 (v0.3.1) - "就留一个更新和仓库" + "下面的更新日志可以留着, 日志多可以滑动"
**老板拍板**:
- About 只 2 按钮: 更新 + 仓库
- 底下 in-app 渲染 changelog
- **可滑动** (v0.3.2 加 LazyColumn)

### 第 3 次 (v0.3.3) - "GitHub 关于那个页面, 给他加上汉堡的标识, 猫猫那个标识挺好看的"
**老板拍板**:
- About "仓库" 按钮用 **GitHub 官方 Octocat 猫猫 logo** (drawable/ic_github_cat.xml)
- 不是浏览器 icon
- **drawable 矢量图, fillColor #24292F (GitHub 深色), 不用 ?attr/colorPrimary (theme attr 在 drawable 里不能用)**

### 第 4 次 (凌晨) - "上传和下载都不行, 上传失败 HTTP 413"
**根因**: nginx 漏了 client_max_body_size
**修**: 加 `client_max_body_size 0;` 在 location / 段 + nginx restart

---

## OpenList 4.x API 字段命名 (snake_case 重要!)

```json
{
  "name": "Deepin",
  "size": 0,
  "is_dir": true,          // 不是 isDir
  "modified": "2025-12-30T12:22:12+08:00",
  "created": "2025-12-22T11:49:11+08:00",
  "sign": "",
  "thumb": "",
  "type": 1,               // 0=file 1=folder
  "hashinfo": "null",
  "hash_info": null
}
```

**Kotlin DTO 必须用 `@SerialName`**:
```kotlin
@Serializable
data class FsItem(
    val name: String,
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
    val modified: String = "",
    val sign: String = "",
    val thumb: String = "",
    @SerialName("type") val type: Int = 0,
)
```

---

## OpenList 4.x API 完整端点

| 端点 | 方法 | 用途 |
|---|---|---|
| `/api/auth/login` | POST | 登录拿 JWT (48h 过期) |
| `/api/me` | POST | 当前用户 (base_path, role, permission) |
| `/api/admin/user/info` | POST | 同 /api/me (兼容旧版) |
| `/api/fs/list` | POST | 列出目录 (path, page, per_page, refresh) |
| `/api/fs/get` | POST | 单个文件元数据 |
| `/api/fs/form` | PUT | multipart 上传 (?path=xxx&override=true) |
| `/api/fs/stream` | PUT | 流式上传 (大文件) |
| `/d/{path}` | GET | 直接下载 (raw token, 不加 Bearer) |
| `/p/{path}` | GET | 预览 |
| `/api/fs/remove` | POST | 删除 |
| `/api/fs/mkdir` | POST | 新建文件夹 |
| `/api/fs/rename` | POST | 重命名 |
| `/api/fs/move` | POST | 移动 |
| `/api/fs/copy` | POST | 复制 |
| `/api/admin/user/*` | POST | 管理员管用户 |
| `/api/admin/storage/*` | POST | 管理员管存储 |
| `/api/admin/log` | GET | 服务器日志 (admin) |

---

## OpenList 4.x 已知的几个 bug/坑

### 1. LoginCache 限流 (5 次错锁 5 分钟)
```go
var DefaultMaxAuthRetries = 5
var DefaultLockDuration = time.Minute * 5
// 按 c.ClientIP() 计数
// 锁了只能等 5 分钟 或 重启 openlist
```

### 2. validTokenCache 重启清空
```go
var validTokenCache = cache.NewMemCache[bool]()
// GenerateToken Set, 重启就清
// 重启后所有旧 token 立刻 401 "token is invalidated"
// 需要重新登录
```

### 3. Authorization header 不认 "Bearer " 前缀
```go
// 中间件: c.GetHeader("Authorization") 直接当 token
// GenerateToken Set validTokenCache.Set(tokenString, true)  // tokenString 不含 "Bearer "
// ParseToken Get 时拿到 "Bearer eyJ..." 找不到 cache → 401
// **客户端必须传 raw token, 不加 Bearer**
```

### 4. `openlist admin set` 命令有 bug
```bash
$ ./bin/openlist admin set admin '********'
admin user has been updated:
username: admin       # 实际改了 admin 用户, 跟 set 的 username 无关
password: admin       # 实际 password 是 "admin" (子命令冲突)
// 看起来改了, 实际是 admin 用户改 password='admin'
// admin 用户的 password 字段确实改成了 'admin' (乱码), 但 pwd_hash 没动
```

### 5. pwd_ts 改了所有老 token 立即失效
```go
// middleware 验证: userClaims.PwdTS != user.PwdTS → 401
// pwd_ts 存在 JWT 里 + db x_users.pwd_ts
// 改 db pwd_ts → 老 token 全部失效 (这个是 feature, 不是 bug)
```

---

## nginx 配置 (<your-server>)

```nginx
server {
    listen 80;
    server_name <your-server>;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name <your-server>;

    ssl_certificate /usr/trim/var/trim_connect/ssls/<your-server>/1778243825/fullchain.crt;
    ssl_certificate_key /usr/trim/var/trim_connect/ssls/<your-server>/1778243825/<your-server>.key;

    # OpenList Android 更新 API - 静态 JSON
    location /api/openlist-android/ {
        alias ~/dev-projects/ricoui-astro-starter/dist/api/openlist-android/;
        add_header Cache-Control "no-cache" always;
        add_header Access-Control-Allow-Origin "*" always;
        try_files $uri $uri/ =404;
    }

    # OpenList 网盘反代
    location / {
        proxy_pass http://127.0.0.1:5244/;
        client_max_body_size 0;  # 老板 6/14 拍: OpenList 大文件上传不限制
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }
}
```

---

## GitHub Actions (openlist-android)

```yaml
name: Build & Release Android APK
on:
  push:
    branches: [master]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17' }
      - uses: actions/cache@v4  # 缓存 ~/.gradle + ANDROID_HOME
        with: { ... }
      # 不用 reactivecircus/android-sdk (没 root)
      # 改用: 系统 ubuntu + openjdk-17 + curl gradle 8.10.2 zip + ANDROID_HOME/sdkmanager
      - run: ./gradlew assembleRelease
      - uses: softprops/action-gh-release@v1
        with:
          files: app/build/outputs/apk/release/sanyun-v${{ ... }}.apk
```

**注意**:
- gradle wrapper 8.10.2 (AGP 8.5 要求 8.7+)
- compose-bom 2024.06.00
- compileSdk 34, minSdk 24, targetSdk 34
- 第一次 build 8-12 分钟 (gradle + AGP + Kotlin + Compose 全下)
- 后续 build 2-3 分钟 (cache 命中)

---

## 老板 4 个未拍板项

### 1. "经常更新有没有必要" (v0.3.3 提了, 老板没回)
**现状**: 每次 commit push 都出 APK
**建议方案**: 改 workflow → `on.push.tags: 'v*'` → 日常 commit 跑 build-debug, 要发版本时 `git tag v0.4.0 && git push --tags`
**好处**: 老板手机上只看到大版本, 不被小修复骚扰
**是否改**: ⏳ 待老板拍

### 2. Hermes 不响应 (跨 6+ 梦境 stale)
2026-05-31 老板反馈 Hermes (@thinkpa_bot) 不回复消息
- 跨 6+ 个梦境未排查
- 如果老板未再提及 → 下次移除

### 3. Wiki 排序 bug
手写文章同步后跑到最后面 (sort by created_at)
- 需要修复 sql ORDER BY

### 4. "About 加 GitHub 标识" 备选方案未拍
老板选了 B (Octocat 猫猫), 还有 A (star+fork) / C (last commit) 没做
- 是不是要再加 star 数?

---

## 老板明天可以做的事

1. **装 v0.3.3 测试** - 验证 nginx 修复后大文件上传
2. **拍 workflow 改不改** - 频繁出 APK vs tag push
3. **下一个功能** - UI 全面重做? 加文件夹管理 (新建/删除/重命名)?
4. **给 Hermes 排查** - 跨 7 梦境 stale, 老板再确认是不是要修

---

## 关键文件路径速查

| 用途 | 路径 |
|---|---|
| OpenList 二进制 | ~/openlist/bin/openlist |
| OpenList 配置 | ~/openlist/data/config.json |
| OpenList db | ~/openlist/data/data.db |
| OpenList log | ~/openlist/data/log/log.log |
| OpenList systemd | /etc/systemd/system/openlist.service |
| nginx conf | /etc/nginx/conf.d/<your-server>.conf |
| nginx backup (6/14) | /etc/nginx/conf.d/<your-server>.conf.bak.20260614_004000 |
| APP 项目 | ~/dev-projects/openlist-android/ |
| APP FsItem | app/src/main/java/com/threel/openlist/data/model/Models.kt |
| APP Repository | app/src/main/java/com/threel/openlist/data/api/OpenListRepository.kt |
| APP FileBrowser | app/src/main/java/com/threel/openlist/ui/screen/FileBrowserScreen.kt |
| APP AboutScreen | app/src/main/java/com/threel/openlist/ui/screen/AboutScreen.kt |
| APP ic_github_cat | app/src/main/res/drawable/ic_github_cat.xml |
| APP build.gradle | app/build.gradle.kts |
| 更新 JSON | ~/dev-projects/ricoui-astro-starter/dist/api/openlist-android/ |

---

## 版本号对照

| versionName | versionCode | 内容 |
|---|---|---|
| v0.2.1 | 6 | 初始发布 |
| v0.2.2 | 7 | 修登录跳转 |
| v0.2.3 | 8 | 修文件列表 + 记住密码 |
| v0.2.4 | 9 | About in-app changelog |
| v0.3.0 | 10 | upload/download/share |
| v0.3.1 | 11 | About 极简 (2 按钮) |
| v0.3.2 | 12 | About 可滑动 |
| v0.3.3 | 13 | 修下载/上传 + 猫猫 + 复制链接 |

---

## 老板下次回来重点

1. **v0.3.3 大文件上传测试** (nginx 修后)
2. **决定 workflow 改不改** (tag push)
3. **下一版本方向**: UI 重做 / 新功能 / 修 stale

# OpenList Android 客户端重构方案

> 版本: v1.0 | 日期: 2026-06-28 | 状态: 待开发

## 1. 现状分析

### 1.1 当前架构
```
data/api/    → OpenListApi, OpenListRepository, TokenStore, AuthInterceptor
data/model/ → FsItem, UserInfo, LoginResponse, FsUploadResponse
data/update/ → AppUpdateManager, AppUpdateService, AppUpdateLauncher
ui/screen/   → LoginScreen, FileBrowserScreen, AboutScreen
ui/component/→ LiquidGlassCard/Row/TopBar/Fab, TvFocusCapWrap
ui/theme/    → Color, Theme, Type
ui/          → OpenListNavGraph, RootViewModel
util/        → AppConfig, TelemetryLog
```

### 1.2 现有功能
- ✅ 登录（液态玻璃 UI，自动填账号，密码可见切换）
- ✅ 文件浏览（LazyColumn，Material Icons 分类图标）
- ✅ 上传（OkHttp 直发，File-Path header）
- ✅ 下载（OkHttp + sign 流程，app 私有目录）
- ✅ 分享链接（OpenList 4.x 短链 /sd/<id>/<name>）
- ✅ 退出登录

### 1.3 缺失功能（网页端有但 APP 没有）
- ❌ 服务器地址可配置（写死为 <your-server>）
- ❌ 新建文件夹
- ❌ 删除/重命名/移动文件
- ❌ 用户管理（增删改查）
- ❌ 挂载管理（增删改/启用停用）
- ❌ 分享管理（查看/删除）
- ❌ 系统设置/仪表盘

### 1.4 技术债务
- compose-bom 需要升级到 2024.12（为了 navigation 动画等）
- 没有下拉刷新
- 没有搜索/排序

---

## 2. 重构架构

### 2.1 目标架构
在现有 MVVM + Hilt 架构上**扩展** Management 模块，**不破坏**原有浏览/上传逻辑。

```
data/api/
  ├── OpenListApi.kt          (原有: 浏览/上传/下载/分享)
  ├── ManagementApi.kt       (新增: 用户/挂载/分享管理/设置/仪表盘)
  ├── OpenListRepository.kt   (原有: 浏览/上传/下载/分享)
  └── ManagementRepository.kt (新增: 管理功能)

data/model/
  ├── FsItem.kt               (原有)
  ├── UserInfo.kt             (原有)
  └── ManagementModels.kt     (新增: User, Mount, Share, Option, Overview)

ui/screen/
  ├── LoginScreen.kt          (改造: 加服务器地址配置)
  ├── FileBrowserScreen.kt    (保持不变)
  ├── AboutScreen.kt          (保持不变)
  └── ManagementScreen.kt     (新增: TabLayout 容器)

ui/component/
  ├── LiquidGlass*.kt         (保持不变)
  └── ManagementComponents.kt (新增: UserEditDialog, MountEditDialog, StatCard)

ui/viewmodel/
  ├── FileBrowserViewModel.kt (保持不变)
  └── ManagementViewModel.kt   (新增)

ui/
  └── OpenListNavGraph.kt     (改造: 加 management 路由 + 动画)

util/
  ├── AppConfig.kt            (保持不变)
  └── TelemetryLog.kt         (保持不变)
```

### 2.2 依赖升级
```kotlin
// 升级 compose-bom 到 2024.12.01（支持 enterTransition/exitTransition）
implementation(platform("androidx.compose:compose-bom:2024.12.01"))

// 新增 coil（图片预览）
implementation("io.coil-kt:coil-compose:2.7.0")
```

---

## 3. API 设计（OpenList v4.x）

### 3.1 认证（保持不变）
```
POST /api/auth/login          {username, password} → {token}
GET  /api/user/info           {Authorization}     → {id, username, role, permission, ...}
```

### 3.2 文件管理（保持不变）
```
POST /api/fs/list             {path, password, page, perPage, refresh} → {content: [...]}
POST /api/fs/get              {path}                                    → {name, size, modified, sign, ...}
GET  /d/{path}?sign={hmac}                                              → 302 → 文件流
PUT  /api/fs/form             {File-Path, As-Task, Overwrite}           → {code, message}
```

### 3.3 用户管理（新增）
```
GET    /api/user/list         → {users: [{id, username, role, disabled, ...}]}
POST   /api/user/create       {username, password, role, permission}   → {id, ...}
PUT    /api/user/update       {id, username, role, permission}       → {id, ...}
DELETE /api/user/delete       {id}                                     → {code, message}
```

### 3.4 挂载管理（新增）
```
GET    /api/storage/list      → {storages: [{id, name, driver, path, status, ...}]}
POST   /api/storage/add       {driver, path, name, ...}               → {id, ...}
PUT    /api/storage/update    {id, driver, path, name, status, ...}    → {id, ...}
DELETE /api/storage/remove    {id}                                      → {code, message}
```

### 3.5 分享管理（新增）
```
GET    /api/share/list         → {shares: [{id, path, name, expires, ...}]}
POST   /api/share/create      {files, expires, password}              → {id, ...}
DELETE /api/share/delete      {id}                                      → {code, message}
```

### 3.6 系统设置/仪表盘（新增）
```
GET    /api/overview          → {storage, user, share, ...}
GET    /api/option/list       → {options: [{key, value, ...}]}
PUT    /api/option/update     {key, value}                             → {code, message}
```

### 3.7 文件操作（新增）
```
POST   /api/fs/mkdir          {path, name}                             → {code, message}
POST   /api/fs/rename         {path, new_name}                         → {code, message}
POST   /api/fs/move           {path, new_path}                         → {code, message}
DELETE /api/fs/delete         {path}                                    → {code, message}
```

---

## 4. UI 设计

### 4.1 登录页（改造）

```
┌─────────────────────────────────┐
│  [OpenList Logo]                │
│  聚合你的云盘                    │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 🌐 服务器地址              │  │
│  │ [<your-server>________]  │  │
│  │ [测试连接]                  │  │
│  ├───────────────────────────┤  │
│  │ 👤 用户名                  │  │
│  │ [admin______________]    │  │
│  ├───────────────────────────┤  │
│  │ 🔒 密码                    │  │
│  │ [••••••••] [显示/隐藏]     │  │
│  └───────────────────────────┘  │
│                                 │
│  [登录]                         │
│                                 │
│  v0.2.1 · 三页札记              │
└─────────────────────────────────┘
```

- 服务器地址输入框（必填，支持 http/https）
- "测试连接" 按钮（验证服务器可达 + 版本匹配）
- 用户名/密码不变
- 默认服务器地址：`<your-server>`

### 4.2 主页（不变 + 右上角齿轮）

```
┌─────────────────────────────────┐
│  [≡ 三页云盘 · /]    [🔍] [⚙️] │
│                                 │
│  上次观看 · CCTV1               │
│  [继续观看] [×]                  │
│  ─────────────                  │
│  频道分类 · 500+ 国内频道        │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │央视 │ │卫视 │ │地方 │       │
│  │ CCTV│ │ 36 │ │ 地方│       │
│  └─────┘ └─────┘ └─────┘       │
│  ┌─────┐ ┌─────┐               │
│  │新闻 │ │影视 │               │
│  └─────┘ └─────┘               │
│                                 │
│  [➕ FAB]                       │
└─────────────────────────────────┘
```

### 4.3 设置页（新）- Tab 结构

```
┌─────────────────────────────────┐
│  [← 设置]                       │
│  ┌──────┬──────┬──────┬──────┐ │
│  │ 用户 │ 挂载 │ 分享 │ 设置 │ │
│  └──────┴──────┴──────┴──────┘ │
│  ═══════════════════════════   │
│                                 │
│  [当前 Tab 内容区域]             │
│                                 │
└─────────────────────────────────┘
```

#### Tab 1: 用户管理
```
┌─────────────────────────────────┐
│  用户管理                        │
│  ┌─────────────────────────┐    │
│  │ 👤 admin   管理员 [编辑]│   │
│  ├─────────────────────────┤    │
│  │ 👤 guest    游客  [编辑]│   │
│  └─────────────────────────┘    │
│  [➕ 添加用户]                  │
└─────────────────────────────────┘

编辑弹窗:
┌─────────────────────────────┐
│  编辑用户                    │
│  用户名: [admin____]        │
│  密码:   [________]          │
│  角色:   [管理员 ▼]           │
│  权限:   [全选 ▼]             │
│  [取消] [保存]                │
└─────────────────────────────┘
```

#### Tab 2: 挂载管理
```
┌─────────────────────────────────┐
│  挂载管理                        │
│  ┌─────────────────────────┐   │
│  │ 📁 天翼云盘  本地 ✅启用  │   │
│  │    /天翼云盘             │   │
│  │    [编辑] [停用] [删除]  │   │
│  ├─────────────────────────┤   │
│  │ 📁 本地存储  本地 ✅启用  │   │
│  │    /本地存储             │   │
│  │    [编辑] [停用] [删除]  │   │
│  └─────────────────────────┘   │
│  [➕ 添加挂载]                 │
└─────────────────────────────────┘

添加弹窗:
┌─────────────────────────────┐
│  添加挂载                    │
│  名称:   [________]          │
│  驱动:   [Local ▼]           │
│  路径:   [________]          │
│  [取消] [保存]               │
└─────────────────────────────┘
```

#### Tab 3: 分享管理
```
┌─────────────────────────────────┐
│  分享管理                        │
│  ┌─────────────────────────┐   │
│  │ 🔗 *.mkv  永久  [复制]  │   │
│  │    创建于 2026-06-14    │   │
│  │    [删除]                │   │
│  ├─────────────────────────┤   │
│  │ 🔗 *.zip  永久  [复制]  │   │
│  │    创建于 2026-06-13    │   │
│  │    [删除]                │   │
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

#### Tab 4: 设置
```
┌─────────────────────────────────┐
│  系统设置                        │
│                                 │
│  服务器信息                      │
│  ┌─────────────────────────┐   │
│  │ 地址: <your-server>    │   │
│  │ 版本: OpenList v4.2.2   │   │
│  │ 存储: 183 schema_migrations│  │
│  └─────────────────────────┘   │
│                                 │
│  账户信息                        │
│  ┌─────────────────────────┐   │
│  │ 用户名: admin          │   │
│  │ 角色:   管理员           │   │
│  └─────────────────────────┘   │
│                                 │
│  关于                            │
│  ┌─────────────────────────┐   │
│  │ 三页云盘 v0.3.36        │   │
│  │ 基于官方 OpenList 开发   │   │
│  │ [检查更新]               │   │
│  └─────────────────────────┘   │
│                                 │
│  [退出登录]                     │
└─────────────────────────────────┘
```

---

## 5. 数据模型

### 5.1 新增 Models

```kotlin
// User.kt
@Serializable
data class User(
    val id: Int,
    val username: String,
    val password: String = "",
    val role: Int,           // 1=guest, 2=admin
    val disabled: Boolean = false,
    val permission: Int = 0,  // bitmask
    val createdAt: String = "",
    val updatedAt: String = ""
)

// Mount.kt (Storage)
@Serializable
data class Mount(
    val id: Int,
    val name: String,
    val driver: String,      // "Local", "S3", etc.
    val path: String,
    val status: Int,         // 0=disabled, 1=enabled
    val order: Int = 0,
    val cacheExpiration: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
)

// Share.kt
@Serializable
data class Share(
    val id: String,
    val path: String,
    val name: String,
    val password: String = "",
    val expires: String = "",
    val downloadCount: Int = 0,
    val viewerId: Int? = null,
    val creatorId: Int? = null,
    val createdAt: String = ""
)

// Option.kt (System settings)
@Serializable
data class Option(
    val key: String,
    val value: String,
    val type: String = "string",
    val description: String = ""
)

// Overview.kt (Dashboard)
@Serializable
data class Overview(
    val userCount: Int,
    val mountCount: Int,
    val shareCount: Int,
    val storageUsed: Long,
    val storageTotal: Long,
    val schemaMigrations: Int
)
```

---

## 6. 实施计划

### Phase 1: 基础改造（必做）

#### Task 1: 项目准备
- [ ] 升级 compose-bom 到 2024.12.01
- [ ] 升级 navigation-compose 到 2.7.9
- [ ] 添加 coil-compose 依赖
- [ ] 创建 docs/ 目录
- [ ] 创建 data/model/ManagementModels.kt（空文件）

#### Task 2: 服务器地址配置 + Login 改造
- [ ] 新增 `ServerConfig` DataStore（持久化服务器地址）
- [ ] LoginScreen 加服务器地址输入框
- [ ] LoginScreen 加"测试连接"按钮
- [ ] LoginScreen 测试连接逻辑（GET /api/overview）
- [ ] Login 成功后保存服务器地址到 DataStore
- [ ] 默认值 `https://<your-server>`

#### Task 3: ManagementApi + ManagementRepository
- [ ] 新增 ManagementApi（接口定义）
- [ ] 新增 ManagementRepository（OkHttp 直发，复用 TokenStore）
- [ ] 新增错误处理（统一错误码）

#### Task 4: ManagementScreen + NavGraph
- [ ] 新增 ManagementScreen（TabLayout: 用户/挂载/分享/设置）
- [ ] OpenListNavGraph 加 /management 路由
- [ ] Home 页右上角加齿轮图标 → 跳转 /management
- [ ] 页面动画（fadeIn + slideInHoriz）

### Phase 2: 用户管理

#### Task 5: 用户管理 UI
- [ ] Tab 1 内容：用户列表
- [ ] 添加用户弹窗
- [ ] 编辑用户弹窗
- [ ] 删除用户确认弹框
- [ ] ManagementViewModel.loadUsers()

### Phase 3: 挂载管理

#### Task 6: 挂载管理 UI
- [ ] Tab 2 内容：挂载列表
- [ ] 添加/编辑挂载弹窗
- [ ] 启用/停用/删除功能
- [ ] ManagementViewModel.loadMounts()

### Phase 4: 分享管理

#### Task 7: 分享管理 UI
- [ ] Tab 3 内容：分享列表
- [ ] 删除分享
- [ ] 复制链接

### Phase 5: 设置 Tab + 仪表盘

#### Task 8: 设置 Tab
- [ ] Tab 4 内容：系统信息
- [ ] Overview API 调用 + 展示
- [ ] 用户信息展示
- [ ] 检查更新
- [ ] 退出登录

### Phase 6: 文件操作增强

#### Task 9: 文件操作 API
- [ ] mkdir / rename / move / delete API
- [ ] FileBrowserScreen 建目录按钮
- [ ] FileBrowserScreen 长按菜单加删除/重命名
- [ ] 下拉刷新
- [ ] 搜索栏 + 排序

---

## 7. 测试策略

### 单元测试
- `ManagementRepository` 各 API 测试
- `ManagementViewModel` 状态测试

### 集成测试
- Login → 测试连接 → 登录成功 → 跳转 Home
- Home → 点齿轮 → 设置页 → 用户管理 → 添加/编辑/删除
- 下拉刷新 / 搜索 / 排序

### 手动测试清单
- [ ] 首次安装 → 默认服务器地址正确
- [ ] 输入错误服务器地址 → 测试连接失败提示
- [ ] 输入正确服务器地址 → 测试连接成功 → 登录
- [ ] 文件浏览/上传/下载/分享（原有功能不变）
- [ ] 用户管理（增删改）
- [ ] 挂载管理（增删改/启用停用）
- [ ] 分享管理（查看/删除/复制链接）
- [ ] 设置页（仪表盘数据正确）
- [ ] 退出登录 → 回到登录页

---

## 8. 里程碑

| 里程碑 | 内容 | 预计完成 |
|--------|------|---------|
| M1 | Login + 服务器配置 + Management 框架 | Week 1 |
| M2 | 用户管理 + 挂载管理 | Week 1 |
| M3 | 分享管理 + 设置 Tab | Week 1 |
| M4 | 文件操作增强 + 搜索/排序 | Week 2 |
| M5 | UX 优化（动画/暗色/细节） | Week 2 |
| M6 | 测试 + 文档 + Release | Week 3 |

---

## 9. 风险与 Mitigation

| 风险 | Mitigation |
|------|-----------|
| OpenList API 不匹配 | 先发 /api/overview 探测版本，不匹配提示 |
| CI 构建失败 | 每个 commit 独立可构建 |
| 服务器地址输入错误 | 测试连接按钮前置验证 |
| 文件操作 API 变化 | 封装 ManagementRepository，集中处理差异 |
| Token 过期 | AuthInterceptor 自动刷新（保留原有逻辑） |

---

## 10. 附录

### 10.1 OpenList API 快速参考
- Base URL: `http://127.0.0.1:5244` 或 `https://<your-server>`
- 认证: `Authorization: Bearer <token>`
- Content-Type: `application/json`
- 错误码: 200=成功, 401=未登录, 403=无权限, 404=不存在, 500=服务器错误

### 10.2 Material 3 组件参考
- TabRow + Tab → 设置页 Tab
- AlertDialog → 编辑弹窗
- Snackbar → 提示
- ProgressIndicator → 加载状态
- DropdownMenu → 角色/权限选择

# Audio Player

一个基于 Java 的 Android 音乐播放器，支持本地音乐播放与 WebDAV 云音乐播放。采用 Material 3 设计语言，搭载 ExoPlayer / Media3 播放引擎，提供后台播放、通知栏控制和沉浸式播放界面。

---

## 功能特性

### 本地音乐
- 通过 `MediaStore` 扫描设备上的音频文件，过滤 `IS_MUSIC = 1` 确保只展示音乐文件
- 按艺术家 + 专辑升序排列，列表清晰有序
- 每首歌曲展示标题、艺术家和圆形裁剪的专辑封面（使用 Glide 加载）
- 点击歌曲即进入播放器界面并开始播放
- 后台线程加载歌曲列表，主线程更新 UI，避免列表卡顿

### 云音乐（WebDAV）
- 通过 WebDAV 协议（PROPFIND）连接远程媒体服务器
- 兼容 [Alist](https://alist.nn.ma/) 及其他标准 WebDAV 服务
- 输入服务器地址、用户名、密码即可连接，支持 HTTP / HTTPS
- 连接测试：先尝试 OPTIONS 方法，失败后降级为原始 Socket PROPFIND
- 文件浏览：目录与音频文件混排，目录排在前面，按名称字母排序
- 支持目录导航（含返回上级 ".." 功能）
- 流式播放：WebDAV 音频文件通过 URL 直接传输到 ExoPlayer，无需下载到本地
- 认证凭证通过 SharedPreferences 持久化，下次自动登录
- 支持自签名 HTTPS 证书（信任所有证书，适用于内网场景）

### 播放器
- **波形进度条** — 使用 `WaveformSeekBar` 控件，显示伪波形（随机生成 50 条柱状），支持拖拽跳进
- **播放控制** — 播放/暂停、上一首、下一首（循环列表）
- **专辑封面** — Glide 加载圆形封面，同时作为毛玻璃模糊背景（`BlurTransformation`，模糊半径 25，采样 3 倍）
- **时间显示** — 实时更新当前播放位置和总时长，格式 `MM:SS`
- **错误处理** — 播放出错时 Toast 提示错误信息
- 歌曲结束后自动跳转到下一首

### 迷你播放条（Mini Player）
- MainActivity 底部常驻迷你播放条，显示专辑封面（圆形）、歌曲标题、艺术家和播放进度条
- 每秒更新一次进度，与 `NowPlayingStore` 中的状态同步
- 点击迷你播放条跳转到 PlayerActivity 全屏播放界面
- 播放结束时自动隐藏

### 后台播放
- 基于 Media3 `MediaSessionService` 实现，独立于 Activity 生命周期
- 使用 ExoPlayer 作为底层播放引擎
- 智能 DataSource 路由：根据 URI scheme 自动选择 `OkHttpDataSource`（HTTP/HTTPS）或 `FileDataSource`（本地文件）
- 云音乐播放时通过 OkHttp 拦截器注入 WebDAV 认证请求头（Basic Auth）
- `NowPlayingStore` 单例跨组件共享当前播放状态

### 通知栏控制
- 播放时显示前台服务通知，带媒体样式控制（`MediaStyleNotificationHelper`）
- 通知关联 PendingIntent 点击回到播放器界面
- 通知重要性设为 `IMPORTANCE_LOW`，不显示角标
- Android 13+ 动态请求 `POST_NOTIFICATIONS` 权限

### 账户系统
- 本地注册/登录，邮箱 + 密码存储于 SharedPreferences
- 密码最少 6 位，防重复注册
- 登录状态管理（登录/登出切换 UI）

### 音量控制
- 系统媒体音量实时调节（`AudioManager.STREAM_MUSIC`）
- SeekBar 滑块显示当前音量等级（如 "15/30"）

---

## 截图

| 音乐列表 | 播放界面 | 云音乐 | 设置 |
|---------|---------|-------|-----|
| 本地歌曲列表，展示歌曲名、艺术家、专辑封面（圆形） | 毛玻璃背景+专辑封面居中、波形进度条、播放/暂停/上下曲控制、时间显示 | WebDAV 登录表单 + 文件浏览器，目录/音频文件列表，可见路径导航 | 登录/注册表单、已登录状态与登出、音量滑块 |

---

## 技术栈

| 组件 | 方案 |
|------|------|
| 语言 | Java 17 |
| 最低 SDK | 24 (Android 7.0 Nougat) |
| 目标 SDK | 35 (Android 15) |
| 构建系统 | Gradle + Android Gradle Plugin 8.9.x |
| NDK | 27.0.12077973 |
| 播放引擎 | ExoPlayer 1.x (via Media3) |
| 媒体会话 | Media3 `MediaSession` + `MediaSessionService` |
| 云协议 | WebDAV (PROPFIND + Basic Auth)，基于原始 Socket 实现 |
| 图片加载 | Glide + `glide-transformations` (毛玻璃效果) |
| 波形控件 | `WaveformSeekBar` (com.frolo:waveformseekbar) |
| HTTP | OkHttp (播放流媒体与 WebDAV 认证拦截器) |
| UI | Material 3 (Material Components), ConstraintLayout, ViewBinding |
| 导航 | BottomNavigationView 三 Tab 切换 Fragment |
| 状态共享 | 全局单例 `NowPlayingStore` |

### 依赖项

```gradle
// AndroidX
androidx.appcompat:appcompat
com.google.android.material:material
androidx.activity:activity
androidx.constraintlayout:constraintlayout
androidx.palette:palette
androidx.media:media

// 图片加载
com.github.bumptech.glide:glide
jp.wasabeef:glide-transformations

// 播放引擎 (Media3 / ExoPlayer)
androidx.media3:media3-exoplayer
androidx.media3:media3-ui
androidx.media3:media3-session
androidx.media3:media3-datasource-okhttp

// 网络
com.squareup.okhttp3:okhttp

// 第三方控件
com.frolo:waveformseekbar

// 测试
junit:junit
androidx.test.ext:junit
androidx.test.espresso:espresso-core
```

---

## 项目结构

```
app/src/main/java/com/shahadot/android_music_player/
├── MainActivity.java              # 主入口，底部导航栏（音乐/云/设置）+ 迷你播放条
├── MusicFragment.java             # 本地音乐列表页，MediaStore 查询 + 权限处理
├── CloudFragment.java             # 云音乐页，WebDAV 登录/浏览/播放
├── SettingsFragment.java          # 设置页，本地账户登录注册 + 音量控制
├── PlayerActivity.java            # 全屏播放器，波形进度条 + 播放控制 + 毛玻璃背景
├── MusicService.java              # 后台播放服务 (MediaSessionService)，ExoPlayer 管理
├── Song.java                      # 歌曲数据模型 (Parcelable，支持 Intent 传递)
├── SongAdapter.java               # 本地歌曲 RecyclerView 适配器
├── CloudSongAdapter.java          # 云文件列表适配器（目录/音频区分）
├── WebDavClient.java              # WebDAV 客户端（原始 Socket HTTP 引擎 + XML 解析）
└── NowPlayingStore.java           # 全局单例，跨组件共享播放状态

app/src/main/res/
├── layout/
│   ├── activity_main.xml          # 主界面布局（FragmentContainer + 迷你播放条 + 底部导航）
│   ├── activity_player.xml        # 播放器布局（毛玻璃背景 + 专辑封面 + 波形条 + 控制按钮）
│   ├── fragment_music.xml         # 本地音乐列表
│   ├── fragment_cloud.xml         # 云音乐（登录表单 + 文件浏览器）
│   ├── fragment_settings.xml      # 设置页（账户 + 音量）
│   └── item_song.xml              # 歌曲列表项布局
├── drawable/                      # 图标、背景渐变、形状资源
├── values/                        # 颜色、字符串、主题
└── menu/
    └── bottom_nav_menu.xml        # 底部导航菜单定义
```

---

## 架构与设计要点

### 组件协作流程

```
User taps song
      │
      ▼
MusicFragment ──Intent──► PlayerActivity
                              │
                    MediaController (Media3 API)
                              │
                              ▼
                        MusicService (MediaSessionService)
                              │
                        ExoPlayer (播放引擎)
                              │
                    ┌─────────┴─────────┐
                    ▼                    ▼
              FileDataSource      OkHttpDataSource
           (本地 .mp3/.flac 等)    (WebDAV 流媒体 + Auth 拦截器)
```

### 状态共享：NowPlayingStore

全局单例，持有当前播放歌曲、播放状态、进度、播放列表和 WebDAV 认证请求头。MainActivity 的迷你播放条和 PlayerActivity 通过此单例同步 UI。

### WebDAV 实现细节

`WebDavClient` 使用**原始 Socket** 发送 HTTP 请求（而非 `HttpURLConnection`），因为 `HttpURLConnection` 不支持 `PROPFIND` 方法。核心流程：

1. **连接测试** — 先 OPTIONS（HttpURLConnection），再 PROPFIND（原始 Socket）
2. **文件列表** — PROPFIND 请求获取目录 XML，XmlPullParser 解析 `DASL` 响应
3. **路径处理** — 将服务器返回的绝对路径转换为相对于挂载点的路径
4. **认证** — Basic Auth 通过 OkHttp 拦截器注入到 ExoPlayer 的流媒体请求中
5. **HTTPS** — 信任所有证书，适配内网自签名场景

---

## 云音乐配置

应用支持通过 WebDAV 协议连接远程音乐服务器，推荐使用 [Alist](https://alist.nn.ma/)。

**连接地址格式：**
```
http://服务器IP:端口/dav
```

例如 Alist 默认 WebDAV 路径为 `http://192.168.1.100:5244/dav`。

**支持的音频格式：**
```
mp3, wav, flac, ogg, aac, wma, m4a, opus
```

**认证提示：**
- 使用 Alist 时，用户名和密码使用 Alist 的登录账号
- 认证凭证保存至本地 SharedPreferences，下次自动连接
- 支持 HTTPS（包括自签名证书）

---

## 构建

### 前置要求
- JDK 17+
- Android SDK 35
- Android Studio (推荐最新版本)
- Gradle (项目包含 Gradle Wrapper)

### 构建命令

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# 安装到已连接设备
./gradlew installDebug
```

### 构建产物
APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 权限说明

| 权限 | 用途 | 适用版本 |
|------|------|---------|
| `READ_MEDIA_AUDIO` | 读取设备上的本地音频文件 | Android 13+ (API 33+) |
| `READ_EXTERNAL_STORAGE` | 读取本地音频文件（旧版本兼容） | Android 12 及以下 (maxSdkVersion=32) |
| `INTERNET` | WebDAV 连接和云音乐流媒体播放 | 全部版本 |
| `FOREGROUND_SERVICE` | 启动前台服务进行后台播放 | Android 9+ |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 声明媒体播放类型前台服务 | Android 14+ |
| `POST_NOTIFICATIONS` | 显示播放控制通知 | Android 13+ (动态请求) |

---

## 开发相关

### 版本信息
- 版本号: 1.0.0
- `applicationId`: `com.shahadot.android_music_player`
- `compileSdk`: 35
- `minSdk`: 24
- `targetSdk`: 35

### 支持的 ABI
```
armeabi-v7a, arm64-v8a, x86, x86_64
```

### 混淆
Release 构建默认关闭混淆（`minifyEnabled = false`），如需开启请在 `build.gradle` 中启用并配置 `proguard-rules.pro`。

# Audio Player

一个基于 Java 的 Android 音乐播放器，基于 [shahadot786/android-music-player](https://github.com/shahadot786/android-music-player) 二次开发，支持本地音乐与 WebDAV 云音乐播放。采用 Material 3 设计，搭载 ExoPlayer / Media3 播放引擎，提供后台播放和沉浸式播放界面。

## 功能

- **本地音乐** — 扫描设备音频文件，按艺术家+专辑排列，展示标题、艺术家和专辑封面
- **WebDAV 云音乐** — 通过 WebDAV 协议连接远程服务器（兼容 Alist），支持目录浏览、流式播放
- **播放器** — 波形进度条、播放控制、毛玻璃背景、专辑封面、时间显示
- **迷你播放条** — MainActivity 底部常驻，实时同步播放状态
- **后台播放** — Media3 MediaSessionService 实现，独立于 Activity 生命周期
- **通知栏控制** — 前台服务媒体样式通知，支持 Android 13+ 动态权限
- **本地账户** — 邮箱+密码注册/登录，状态持久化
- **音量控制** — 系统媒体音量实时调节

## 截图

| 音乐列表 | 播放界面 | 云音乐 | 设置 |
|---------|---------|-------|-----|
| 歌曲列表，展示歌曲名、艺术家、专辑封面 | 毛玻璃背景、波形进度条、播放/暂停/上下曲 | WebDAV 登录 + 文件浏览 | 账户登录/注册 + 音量滑块 |

## 技术栈

| 组件 | 方案 |
|------|------|
| 语言 | Java 17 |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 36 |
| 播放引擎 | ExoPlayer (via Media3) |
| 云协议 | WebDAV (原始 Socket 实现 PROPFIND) |
| 图片加载 | Glide + glide-transformations |
| 波形控件 | WaveformSeekBar |
| HTTP | OkHttp |
| UI | Material 3, ConstraintLayout, ViewBinding |
| 导航 | BottomNavigationView + Fragment |

## 云音乐配置

连接地址格式：`http://服务器IP:端口/dav`

推荐使用 [Alist](https://alist.nn.ma/)，默认 WebDAV 路径为 `http://192.168.1.100:5244/dav`。

支持的音频格式：`mp3, wav, flac, ogg, aac, wma, m4a, opus`

认证凭证保存在本地，下次自动登录。支持 HTTPS（包括自签名证书）。

## 构建

### 前置要求
- JDK 17+
- Android SDK 36
- Gradle (项目包含 Gradle Wrapper)

### 命令

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# 安装到已连接设备
./gradlew installDebug
```

Release 签名使用 `release.jks`，密码 `android123`。

## 项目结构

```
app/src/main/java/com/ran/kk_music_player/
├── MainActivity.java          # 主入口，底部导航 + 迷你播放条
├── MusicFragment.java         # 本地音乐列表
├── CloudFragment.java         # 云音乐登录/浏览
├── SettingsFragment.java      # 设置（账户 + 音量）
├── PlayerActivity.java        # 全屏播放器
├── MusicService.java          # 后台播放服务 (MediaSessionService)
├── CoverCache.java            # 专辑封面缓存
├── Song.java                  # 歌曲数据模型
├── SongAdapter.java           # 本地歌曲适配器
├── CloudSongAdapter.java      # 云文件适配器
├── WebDavClient.java          # WebDAV 客户端
└── NowPlayingStore.java       # 全局状态单例
```

## 权限

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_AUDIO` | 读取本地音频（Android 13+） |
| `READ_EXTERNAL_STORAGE` | 读取本地音频（Android 12 及以下） |
| `INTERNET` | WebDAV 连接和流媒体 |
| `FOREGROUND_SERVICE` | 后台播放 |
| `POST_NOTIFICATIONS` | 播放通知（Android 13+ 动态请求） |

## 版本

当前版本：v2.1 | `applicationId`: `com.ran.kk_music_player`

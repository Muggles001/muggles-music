# 麻瓜音乐（Muggles Music）

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="麻瓜音乐图标" width="144" />
</p>

面向 Android TV / 电视盒子的音乐客户端，支持直接导入 LX 自定义音源或连接 LXserver，并针对遥控器操作和长时间播放进行了适配。

> 本项目是基于 [boluofan/music-tv](https://github.com/boluofan/music-tv) 开发的独立维护版，原作者为 [boluofan](https://github.com/boluofan)。本项目与原作者不存在隶属或官方背书关系；原项目仍是本项目的重要基础。

当前维护仓库：[Muggles001/muggles-music](https://github.com/Muggles001/muggles-music)

默认分支：[`main`](https://github.com/Muggles001/muggles-music/tree/main)

## 最新版本

当前版本为 [`v2.0.3`](https://github.com/Muggles001/muggles-music/releases/tag/v2.0.3)。本版新增跨平台歌单导入、歌单管理与收藏视图，并统一手机扫码入口和 TV 遥控器焦点行为。

发布页提供 Android 5.0（API 21）及以上可安装的通用 APK。`v1.0.2` 已启用新的发布证书；已安装 `v1.0.2` 及之后版本的用户可以直接覆盖更新。

## 主要功能

- 适配 Android TV 与 D-Pad 遥控器操作。
- 支持直接导入 LX 兼容自定义音源，或连接 LXserver 使用。
- 提供搜索、排行榜、歌单、收藏、播放队列和同步歌词。
- 支持通过链接或手机扫码导入主流音乐平台歌单；重复导入同一歌单时刷新内容并保留本地歌单名。
- 直连模式的收藏与歌单仅保存在电视本机。
- 提供沉浸式播放界面和面向电视的浏览、焦点与设置体验。

## 与原版的关系

麻瓜音乐基于以下开源项目继续开发：

1. [boluofan/music-tv](https://github.com/boluofan/music-tv)，作者 [boluofan](https://github.com/boluofan)。
2. 原项目说明其基于 [GanHuaLin/rouroumusic-tv](https://github.com/GanHuaLin/rouroumusic-tv)（RouRouMusic）开发。
3. 后端服务使用 [XCQ0607/lxserver](https://github.com/XCQ0607/lxserver)。

本项目当前主要修改包括：

- 深度连接 LXserver，支持账号登录、Token 鉴权、歌单、搜索、排行榜和多音乐平台播放。
- 重构 TV 主界面、页面切换和焦点导航，统一侧栏、列表、分页与遥控器操作方式。
- 歌单广场和我的歌单采用一致的详情页、歌曲分页及播放控制。
- 支持标准 LRC、毫秒逐字歌词和中文翻译，并提供共享平面空间排版、自然换行及每首歌曲独立的时间校准。

完整版本记录见 [CHANGELOG.md](CHANGELOG.md)。

完整来源及修改声明见 [NOTICE](NOTICE)。

## 安装与配置

1. 从本仓库发布页下载 APK，或按照下方说明自行构建。
2. 安装到 Android TV 或电视盒子。
3. 首次启动时选择“直接使用音源”或“连接 LXserver”。两种方式均支持手机扫码配置。

直连模式仅接受符合 LX Music `globalThis.lx 2.0.0` 契约的 JavaScript 音源地址，不支持 M3U、单曲直链或任意 REST API 地址。应用不会内置或分发第三方音源。

服务地址示例：`http://192.168.1.100:9527`。请确保电视可以访问该地址。

## 自行构建

```bash
git clone https://github.com/Muggles001/muggles-music.git
cd muggles-music
bash gradlew :app:assembleDebug
```

建议使用 JDK 17 或更高版本，并安装项目所需的 Android SDK。构建生成的通用 APK 位于 `app/build/outputs/apk/debug/app-universal-debug.apk`。

构建前可运行基础验证：

```bash
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 项目结构

生产代码按 `ui`、`player`、`catalog`、`library`、`source`、`remote`、`settings` 和 `local` 划分职责，应用根包仅保留启动入口。界面资源位于 `app/src/main/res/`，JVM 与设备测试分别位于 `app/src/test/` 和 `app/src/androidTest/`。

## AI 创作声明

麻瓜音乐独立维护版的代码与文档修改由 OpenAI Codex 纯 AI 生成和实施，功能由项目维护者进行实机验证。上游项目及其既有代码不属于该声明范围。

## 开源协议

本项目沿用原项目的 [Apache License 2.0](LICENSE)。再发布或修改时请保留许可证、版权与来源说明。

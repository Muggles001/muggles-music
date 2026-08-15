# 麻瓜音乐（Muggles Music）

面向 Android TV / 电视盒子的 LXserver 音乐客户端，针对遥控器操作和长时间播放进行了适配。

> 本项目是基于 [boluofan/music-tv](https://github.com/boluofan/music-tv) 开发的独立维护版，原作者为 [boluofan](https://github.com/boluofan)。本项目与原作者不存在隶属或官方背书关系；原项目仍是本项目的重要基础。

当前维护仓库：[Muggles001/muggles-music](https://github.com/Muggles001/muggles-music)

默认分支：[`main`](https://github.com/Muggles001/muggles-music/tree/main)

## 最新版本

当前版本为 [`v1.0.2`](https://github.com/Muggles001/muggles-music/releases/tag/v1.0.2)。本版完成电视端主界面重构，并将歌单、歌词和遥控器操作统一到新的交互体系中。

## 主要功能

- 适配 Android TV 与 D-Pad 遥控器操作。
- 连接 [XCQ0607/lxserver](https://github.com/XCQ0607/lxserver)，支持登录、歌单、搜索、排行榜与播放。
- 专辑封面背景、播放队列，以及支持原文/翻译、高亮和手动校准的同步歌词界面。
- 统一的一级页面导航，搭配玻璃质感侧栏和遥控器友好的焦点动画。
- 歌单广场和歌单详情使用翻页歌曲列表；“我的歌单”与歌单广场复用同一详情页。
- 播放链路支持 LXserver Token、直连媒体地址、网络唤醒及较完整的错误诊断。

## 与原版的关系

麻瓜音乐基于以下开源项目继续开发：

1. [boluofan/music-tv](https://github.com/boluofan/music-tv)，作者 [boluofan](https://github.com/boluofan)。
2. 原项目说明其基于 [GanHuaLin/rouroumusic-tv](https://github.com/GanHuaLin/rouroumusic-tv)（RouRouMusic）开发。
3. 后端服务使用 [XCQ0607/lxserver](https://github.com/XCQ0607/lxserver)。

本项目当前主要修改包括：

- 深度连接 LXserver，支持账号登录、Token 鉴权、歌单、搜索、排行榜和多音乐平台播放。
- 重构 TV 主界面、页面切换和焦点导航，统一侧栏、列表、分页与遥控器操作方式。
- 歌单广场和我的歌单采用一致的详情页、歌曲分页及播放控制。
- 支持标准 LRC、毫秒逐字歌词和中文翻译，并提供居中高亮、平滑滚动、原文/译文视觉层级及每首歌曲独立的时间校准。

完整版本记录见 [CHANGELOG.md](CHANGELOG.md)。

完整来源及修改声明见 [NOTICE](NOTICE)。

## 安装与配置

1. 从本仓库发布页下载 APK，或按照下方说明自行构建。
2. 安装到 Android TV 或电视盒子。
3. 在首次启动页填写 LXserver 地址、用户名和密码，也可以使用手机扫码配置。

服务地址示例：`http://192.168.1.100:9527`。请确保电视可以访问该地址。

## 自行构建

```bash
git clone https://github.com/Muggles001/muggles-music.git
cd muggles-music
./gradlew assembleDebug
```

建议使用 JDK 17 或更高版本，并安装项目所需的 Android SDK。构建生成的通用 APK 位于 `app/build/outputs/apk/debug/app-universal-debug.apk`。

## AI 创作声明

麻瓜音乐独立维护版的代码与文档修改由 OpenAI Codex 纯 AI 生成和实施，功能由项目维护者进行实机验证。上游项目及其既有代码不属于该声明范围。

## 开源协议

本项目沿用原项目的 [Apache License 2.0](LICENSE)。再发布或修改时请保留许可证、版权与来源说明。

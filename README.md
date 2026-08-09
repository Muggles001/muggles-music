# 麻瓜音乐（Muggles Music）

面向 Android TV / 电视盒子的 LXserver 音乐客户端，针对遥控器操作和长时间播放进行了适配。

> 本项目是基于 [boluofan/music-tv](https://github.com/boluofan/music-tv) 开发的独立维护版，原作者为 [boluofan](https://github.com/boluofan)。本项目与原作者不存在隶属或官方背书关系；原项目仍是本项目的重要基础。

当前维护仓库：[Muggles001/muggles-music](https://github.com/Muggles001/muggles-music)

默认分支：[`main`](https://github.com/Muggles001/muggles-music/tree/main)

## 主要功能

- 适配 Android TV 与 D-Pad 遥控器操作。
- 连接 [XCQ0607/lxserver](https://github.com/XCQ0607/lxserver)，支持登录、歌单、搜索、排行榜与播放。
- 专辑封面背景、播放队列和同步歌词界面。
- 歌单广场使用每页 12 项的遥控器翻页模式，避免无限滚动导致焦点无法进入底部导航。
- 播放链路支持 LXserver Token、直连媒体地址、网络唤醒及较完整的错误诊断。

## 与原版的关系

麻瓜音乐基于以下开源项目继续开发：

1. [boluofan/music-tv](https://github.com/boluofan/music-tv)，作者 [boluofan](https://github.com/boluofan)。
2. 原项目说明其基于 [GanHuaLin/rouroumusic-tv](https://github.com/GanHuaLin/rouroumusic-tv)（RouRouMusic）开发。
3. 后端服务使用 [XCQ0607/lxserver](https://github.com/XCQ0607/lxserver)。

本项目当前主要修改包括：

- 修复 LXserver 地址、登录 Token 与音乐 URL 请求链路。
- 改善 TV 端播放稳定性、生命周期清理和错误信息展示。
- 将歌单广场的无限滚动调整为适合遥控器的 12 项翻页。

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

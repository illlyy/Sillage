# AGENTS.md

> 给 AI 助手（opencode / Claude Code / Cursor 等）与人类贡献者的项目快速入门。完整内部架构文档位于 `docs/`（**仅本地，已 gitignore，不会上传 GitHub**）。

## 项目一句话

**Sillage**（仓库名 Fcode）—— Android 上的 AI 编程与移动开发工作区：原生 Compose AI 聊天 + Codex agent 任务 + Git/工作区 + Termux 终端 + Codex WebUI + MCP/Skills + 悬浮球 + 可选 Mihomo 代理。

- applicationId `com.ilyop.codex`，namespace `com.termux`，GPL-3.0
- Kotlin + Jetpack Compose；Java（Termux 内核与桥接层）；WebView；NDK

## 模块结构

```
:app                主应用（代码主要在 com.termux.app 扁平包，靠前缀分职责）
:termux-shared      Termux 共享库（对外发布 JitPack，改动需保守）
:terminal-view      终端渲染视图
:terminal-emulator  纯终端核心
vendor/             codex-web（资产快照源）、rikkahub-native（只读参考，不编译）
```

## 架构要点（详情在 docs/02、03）

- **单子进程双宿主**：`CodexNativeRuntime`（进程级单例）持有 `CodexAppServerBridge`（`codex app-server --stdio` JSONL 桥）；原生聊天（`CodexChatActivity`）与 WebView WebUI（`CodexHomeActivity` + `CodexDesktopBridge`）共享同一桥，旋转/重建不中断。
- **事件驱动状态**：子进程通知 → 解码为 `NativeProtocolEvent`（sealed，带 `sequence`）→ 门控/批处理 → `NativeChatState`（@Stable）→ Reducer（`NativeActivityReducer`）→ Compose。
- **无 DI / 无 ViewModel / 无 Room / 无 WorkManager**：进程级单例 + SharedPreferences(JSON) + 文件存储。新代码遵循此模式，不引入新框架。

## 命名前缀即架构

`Native*` 内部实现 · `Fcode*` 自研 UI · `Rikka*` 从 RikkaHub 移植 · `Codex*` 桥接层 · `Termux*` 终端

## 常用命令

```powershell
.\gradlew.bat testDebugUnitTest   # 单测（61 个文件，JUnit4 + Robolectric）
.\gradlew.bat assembleDebug       # debug APK
```

## 关键约定速查

- 新增协议事件必须带 `sequence`，并补 Decoder + Reducer + 单测。
- 存储统一走 Store 类（SharedPreferences 文件 `codex_mobile`），禁止散写。
- 流式输出必须经 `NativeStreamEventBatcher`，禁止绕过门控直接派发。
- `assets/codex-desktop/` 是产物，手改会被 `tools/build-codex-web-assets.ps1` 覆盖；升级走 `vendor/codex-web/UPSTREAM.md` 流程。
- 版本常量在 `gradle.properties`（无 version catalog）；路径常量在 termux-shared `TermuxConstants`。
- 用户可见文案中英双语（`values/` + `values-en/`），代码注释/日志用英文。
- 敏感数据（API Key、Token、订阅地址）永不进日志、崩溃上报、文档。

## 注意事项（踩过的坑）

- **Popup 内禁止 `fillMaxSize()`**：Compose Popup/Dialog 是 wrap-content 窗口，内容里用 `fillMaxSize()` 会按窗口最大高度（≈整屏）测量，把面板撑成竖向一大条。面板内的背景层要用 `Modifier.matchParentSize()`（不参与测量，只匹配父级）。例：`PixelStarrySky`（UltraEffects.kt）。
- **置顶动画用独立窗口**：Compose Popup 是独立 window，activity 窗口内的内容（即使 `zIndex` 再高）永远画不到 Popup 之上。要在所有面板之上显示动画，用 `WindowManager` + `TYPE_APPLICATION_PANEL`（带 activity window token）+ `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` 加全屏透明层，结束后 `removeView`。例：`UltraShockwaveOverlay`（UltraEffects.kt）。
- **发布流程**：打 `v*.*.*` tag 推送到 GitHub 后由 Actions（`.github/workflows/release.yml`）自动构建签名 APK + `update.json` 并发布。tag 名必须与 `versionName` 一致（`v0.3.0` ↔ `0.3.0`）。确认 CI 结果用**一次性 API 查询**（`https://api.github.com/repos/illlyy/Sillage/actions/runs?event=push&per_page=1`）或网页，**不要在终端里长轮询 GitHub API**（while 循环轮询会长时间挂起并阻塞会话）。
- **手机调试**：设备通过 USB 连接后 `adb shell uiautomator dump` 可拿 Compose 节点 bounds（中文乱码不影响解析），配合 `input tap/swipe` 驱动 UI；截屏用 `screencap -p /sdcard/x.png` + `adb pull`（PowerShell 直接重定向 `exec-out` 会损坏二进制）。

## 详细文档

`docs/README.md` 有按序阅读指南（01 总览 → 02 架构 → 03 代码地图 → 04 规范 → 05 持久化 → 06 协议桥接 → 07 性能 → 08 构建 → 09 测试 → 10 流程 → 11 路线图）。该目录仅存在于本地。

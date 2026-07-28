<h1 align="center">Sillage</h1>

<p align="center"><strong>Android 上的 AI 编程与移动开发工作区</strong></p>

<p align="center">在手机或平板上与 AI 协作、管理项目、运行终端并完成开发任务。</p>

<p align="center">
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&amp;logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&amp;logoColor=white">
  <a href="./LICENSE.md"><img alt="GPL-3.0-only" src="https://img.shields.io/badge/license-GPL--3.0--only-blue.svg"></a>
</p>

<p align="center">
  <a href="../../releases/latest">下载最新版</a> ·
  <a href="./CHANGELOG.md">更新日志</a> ·
  <a href="../../issues">问题反馈</a>
</p>

## 关于 Sillage

Sillage 是一款为 Android 设计的 AI 编程应用，将原生 AI 对话、项目工作区、Git、Termux 终端和扩展工具整合在一起。

你可以直接在移动设备上打开项目、向 AI 描述任务、查看执行过程、审批操作、检查代码变更，并继续完成后续开发工作。

Sillage 不提供模型账号或 API 额度，使用前需要配置自己的兼容 API 服务。

## 功能介绍

- **原生 AI 对话**：支持流式回复、Markdown、代码块、表格、公式、推理摘要以及图片和文件附件。
- **Agent 任务协作**：支持执行计划、操作审批、用户追问、子代理任务和任务状态提醒。
- **模型与 API 配置**：可保存多套 API 配置，自定义服务地址、API Key、模型、推理强度和模型能力。
- **项目与 Git**：查看项目状态和代码差异，暂存文件、推送分支、使用独立 Worktree，并创建工作区快照。
- **MCP 与 Skills**：添加 MCP 服务，管理工具权限，并安装或卸载 Skills。
- **Termux 开发环境**：内置终端，可安装 Codex CLI、Git、Node.js、Python、Rust、Go、Java、C/C++、SSH、Tmux 等工具。
- **Codex WebUI**：除原生聊天外，也可以使用内置 WebUI 处理项目和对话。
- **Android 系统集成**：支持从其他应用分享文本、图片和文件，提供悬浮球、后台任务和系统通知。
- **个性化界面**：支持浅色、深色和跟随系统模式，提供 Material 与 Liquid Glass 风格、主题配色和自定义聊天背景。
- **应用内代理**：可选安装 Mihomo，管理订阅和节点，仅代理 Sillage 自身请求，不影响其他应用。

## 快速开始

1. 前往 [Releases](../../releases/latest) 下载并安装最新 APK。
2. 首次启动后，在“设置 → 开发工具与环境”中安装 Codex CLI 和需要的开发工具。
3. 在“设置 → 模型与 API”中添加服务地址、API Key 和模型。
4. 新建对话，选择项目目录和模型，然后输入你的任务。

## 系统要求

- Android 7.0 或更高版本；
- 无需 Root；
- 需要用户自行准备兼容的模型 API；
- 内置 Mihomo 当前仅支持 ARM64 设备；
- Liquid Glass 折射效果需要 Android 12 或更高版本，旧系统会自动使用普通样式。

## 从源码构建

构建环境：

- JDK 17；
- Android SDK Platform 37.0；
- Android NDK `22.1.7171670`。

在项目根目录配置 `local.properties`：

```properties
sdk.dir=D\:\\path\\to\\android-sdk
```

Windows：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Linux / macOS：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

生成的 Debug APK 位于 `app/build/outputs/apk/debug/`。

## 参与贡献

欢迎提交 [Issue](../../issues) 或 Pull Request。反馈问题时，请提供应用版本、Android 版本、设备架构和复现步骤，并确保日志中不包含 API Key、Token、订阅地址或私人项目内容。

## 开源项目

Sillage 使用或参考了以下项目：

- [Termux](https://github.com/termux/termux-app)
- [codex-web](https://github.com/0xcaff/codex-web)
- [Mihomo](https://github.com/MetaCubeX/mihomo)
- [MetaCubeXD](https://github.com/MetaCubeX/metacubexd)
- [RikkaHub](https://github.com/rikkahub/rikkahub)

本项目不是 OpenAI、Termux、Mihomo、MetaCubeX、codex-web 或 RikkaHub 的官方产品。

## 许可证

Android 基础工程采用 [GPL-3.0-only](LICENSE.md) 许可证。第三方组件和资源遵循各自许可证，详情请查看 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

<p align="center">由 <a href="https://github.com/illlyy">ILY_op</a> 维护</p>

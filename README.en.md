<h1 align="center">Sillage</h1>

<p align="center"><strong>AI coding workspace for Android, powered by the real Codex CLI</strong></p>

<p align="center">Collaborate with AI, manage projects, run a terminal and get development work done — on your phone or tablet.</p>

<p align="center">
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&amp;logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&amp;logoColor=white">
  <a href="./LICENSE.md"><img alt="GPL-3.0-only" src="https://img.shields.io/badge/license-GPL--3.0--only-blue.svg"></a>
</p>

<p align="center">
  English · <a href="README.md">中文</a>
</p>

<p align="center">
  <a href="../../releases/latest">Download latest</a> ·
  <a href="./CHANGELOG.md">Changelog</a> ·
  <a href="../../issues">Issues</a>
</p>

## About Sillage

Most "AI chat" apps on mobile are toys. Sillage is not one of them — it runs on the **real Codex CLI** as its backend, bringing the complete desktop-grade agent experience to Android:

- **A real agent, not just a chatbot**: the AI makes execution plans, runs commands and edits code — and asks for your approval before touching anything sensitive, all visible in real time.
- **A complete dev environment**: built-in Termux terminal, Git workspace, MCP/Skills extensions and the Codex WebUI — one APK is a whole dev machine.
- **Native experience**: everything runs in a native Compose UI with smooth streaming output and deep customization.

Open a project, describe the task to the AI, watch it execute in real time, approve operations, review code changes and continue development — no desktop machine required.

Bring your own API key — no account needed. Free and open source. Sillage does not provide model accounts or API credits.

## Feature Overview

| Module | Description |
|---|---|
| [Native AI Chat](#1-native-ai-chat) | Streaming replies, Markdown rendering, reasoning summaries, image/file attachments, effort selection |
| [Session Management](#2-session-management) | Sidebar session list, search, favorites, history restore |
| [Agent Collaboration](#3-agent-collaboration) | Execution plans, operation approvals, user questions, subagents, task notifications |
| [Projects & Git](#4-projects--git) | Code status and diff, staging and commit, branch push, worktrees, snapshots |
| [MCP & Skills](#5-mcp--skills) | MCP server management, tool permissions, Skills installation |
| [Termux Dev Environment](#6-termux-dev-environment) | Built-in terminal, one-tap install for Codex CLI, Git, Node, Python and more |
| [Codex WebUI](#7-codex-webui) | Bundled desktop-style WebUI sharing the same session as native chat |
| [Models & API Config](#8-models--api-config) | Multiple API profiles, custom service URLs, dual backend |
| [Customizable UI](#9-customizable-ui) | Theme colors, Liquid Glass style, custom chat backgrounds |
| [In-App Proxy](#10-in-app-proxy) | Optional Mihomo, subscription/node management, app-only proxying |
| [Android Integration](#11-android-integration) | Share entry, floating bubble, background tasks, system notifications |
| [App Updates](#12-app-updates) | GitHub Releases update check with SHA-256 and signature verification |

---

## 1. Native AI Chat

The core experience: a native chat interface backed by the **real Codex CLI**, with streaming output, rich Markdown, code highlighting, tables and formulas — rendered in real time during generation. All agent capabilities (planning, execution, approvals, subagents) are driven locally by the Codex CLI.

- **Streaming output**: reasoning and answers appear piece by piece, live.
- **Rich text rendering**: headings, lists, tables, formulas, code blocks with syntax highlighting; optionally enable "streaming Markdown rendering" for live preview during generation.
- **Reasoning summaries**: view the model's thinking process, with selectable effort levels (including Ultra, with a shockwave animation and pixel-star effect).
- **Attachments**: send images and files to give the AI full context.
- **Model switching**: change model and reasoning effort mid-conversation.

<p align="center">
  <img src="art/screenshots/chat-1.jpg" width="240" alt="Chat screen 1">
  <img src="art/screenshots/chat-2.jpg" width="240" alt="Chat screen 2">
  <img src="art/screenshots/effort-panel.jpg" width="240" alt="Effort panel">
</p>

## 2. Session Management

- **Sidebar session list**: swipe from the screen edge to manage all conversations.
- **New / search / favorite**: quickly find past conversations and pin the ones you use often.
- **History restore**: full history snapshots restored from Codex session files — context survives re-entry.
- **Work panel**: shows the current goal, execution plan, checkpoints and Git snapshot status.

<p align="center">
  <img src="art/screenshots/drawer.jpg" width="240" alt="Sidebar session list">
</p>

## 3. Agent Collaboration

- **Execution plans**: the AI presents a step-by-step plan before executing.
- **Operation approvals**: sensitive operations (file writes, command execution) ask for your approval first.
- **User questions**: the AI stops and asks you whenever a decision is needed mid-task.
- **Subagents**: parallel subtasks shown as subagent cards with live progress.
- **Task notifications**: system notifications keep you posted while AI tasks run in the background.
- **Conversation compaction**: long conversations are automatically compacted to save tokens and stay coherent.

## 4. Projects & Git

- **Project workspace**: pick a project directory and go, with favorites and recent projects.
- **Code status & diff**: see file changes at any time; every AI edit is shown as a diff card.
- **Staging & push**: stage files, commit and push branches — all from the chat.
- **Separate worktrees**: work in an isolated Git worktree without touching your main branch.
- **Workspace snapshots**: create snapshots at key milestones and roll back to a safe state anytime.

## 5. MCP & Skills

- **MCP server management**: add and manage MCP servers; the work panel shows the online status of each server.
- **Tool permissions**: control which tools the AI may call, per server.
- **Skills installation**: browse and install Skills to give the AI ready-made expertise; selected Skills are actually passed to the model.

## 6. Termux Dev Environment

A complete built-in Termux environment — a ready-to-use mobile development base:

- **One-tap tool install**: Codex CLI, Git, Node.js, Python, Rust, Go, Java, C/C++, SSH, Tmux and more, installed on demand.
- **Full terminal**: a Linux-like Bash environment for running any command.
- **Centralized management**: install, update and remove dev tools from a single settings page.

<p align="center">
  <img src="art/screenshots/dev-tools.jpg" width="240" alt="Dev tools page">
</p>

## 7. Codex WebUI

In addition to the native chat, Sillage bundles the Codex desktop WebUI (loaded in a WebView):

- Users who prefer a desktop layout can handle projects and conversations in the WebUI.
- Native chat and WebUI share the same AI session and backend process — switching never interrupts your work.

<p align="center">
  <img src="art/screenshots/webui.jpg" width="320" alt="Codex WebUI">
</p>

## 8. Models & API Config

- **Multiple API profiles**: save several sets of service URL, API key and model, and switch freely.
- **Custom endpoints**: OpenAI-compatible interfaces with custom Base URL.
- **Reasoning effort**: tune the reasoning level and model capabilities per session.
- **Connection test**: verify connectivity with one tap after configuration.
- **Dual backend**: switch between Codex and Claude backends, each with independent config, sharing the same chat UI.

## 9. Customizable UI

- **Appearance mode**: light, dark, follow system.
- **UI style**: Material and Liquid Glass.
- **Theme colors**: a wide range of color schemes applied instantly.
- **Chat background**: solid colors or custom images; or turn themes off for the most minimal chat view.
- **Liquid Glass parameters**: developers can fine-tune blur and refraction parameters in settings.
- **Typography**: adjust font size and UI transparency.

<p align="center">
  <img src="art/screenshots/theme.jpg" width="240" alt="Theme settings">
  <img src="art/screenshots/liquid-glass.jpg" width="240" alt="Liquid Glass parameters">
  <img src="art/screenshots/chat-plain.jpg" width="240" alt="Chat without theme">
</p>

## 10. In-App Proxy

- **Optional Mihomo**: a built-in proxy core, enabled on demand.
- **Subscription & node management**: import subscription links, manage nodes and rules.
- **App-only proxying**: affects only Sillage's own requests — never other apps on your device.

<p align="center">
  <img src="art/screenshots/proxy.jpg" width="240" alt="Proxy settings">
</p>

## 11. Android Integration

- **Share entry**: share text, images and files to the AI from any other app.
- **Floating bubble**: a global bubble for quick access to task bubbles and entry points.
- **Background tasks**: AI tasks keep running in the background, kept alive by a foreground service.
- **System notifications**: real-time alerts for task completion, pending approvals and status changes.
- **Launcher shortcuts**: one-tap access to chat, WebUI, terminal and settings.

## 12. App Updates

- **Update checks**: automatic on launch, manual in settings.
- **Security verification**: SHA-256, package name and signing certificate are all verified.
- **Browser download**: downloads open in your browser for the widest compatibility.

---

## Quick Start

1. Grab the latest APK from [Releases](../../releases/latest) and install it.
2. On first launch, install Codex CLI and the dev tools you need under "Settings → Dev Tools & Environment".
3. Add your service URL, API key and model under "Settings → Models & API".
4. Start a new conversation, pick a project directory and model, then describe your task.

## Requirements

- Android 7.0 or later;
- No root required;
- A compatible model API of your own;
- The built-in Mihomo currently supports ARM64 devices only;
- Liquid Glass refraction requires Android 12 or later; older systems automatically fall back to a plain style.

## Building from Source

Build environment:

- JDK 17;
- Android SDK Platform 37.0;
- Android NDK `22.1.7171670`.

Configure `local.properties` in the project root:

```properties
sdk.dir=D\:\\path\\to\\android-sdk
```

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Linux / macOS:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/`.

## Contributing

Issues and Pull Requests are welcome. When reporting a problem, please include the app version, Android version, device architecture and reproduction steps — and make sure logs contain no API keys, tokens, subscription URLs or private project content.

## Open Source Projects

Sillage uses or references the following projects:

- [Termux](https://github.com/termux/termux-app)
- [codex-web](https://github.com/0xcaff/codex-web)
- [Mihomo](https://github.com/MetaCubeX/mihomo)
- [MetaCubeXD](https://github.com/MetaCubeX/metacubexd)
- [RikkaHub](https://github.com/rikkahub/rikkahub)

This project is not an official product of OpenAI, Termux, Mihomo, MetaCubeX, codex-web or RikkaHub.

## License

The Android base project is licensed under [GPL-3.0-only](LICENSE.md). Third-party components and assets follow their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.

---

<p align="center">Maintained by <a href="https://github.com/illlyy">ILY_op</a></p>

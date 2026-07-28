# Fcode for Android

Fcode 是一个基于 Termux Android 工程的非官方应用，集成 Codex Web/native UI、终端能力和 Mihomo 资源。它不是 OpenAI、Termux、Mihomo、MetaCubeX 或 RikkaHub 的官方产品。

仓库已公开维护，当前默认分支是 `fcode-main`。如果你只是想安装应用，请直接前往 [GitHub Releases](https://github.com/illlyy/Fcode/releases)；如果你要参与开发，请先阅读下面的构建和许可证说明。

## 主要功能

- 在 Android 上运行 Codex Web/native 对话界面。
- 保留 Termux 的终端和会话能力，应用包名为 `com.ilyop.codex`。
- 集成 Mihomo/MetaCubeXD 资源和 Fcode 自定义 UI。
- 从 GitHub Releases 检查更新，并使用默认浏览器打开正式 APK 下载链接。

## 下载与安装

- [下载最新正式版](https://github.com/illlyy/Fcode/releases/latest)
- [查看全部版本和更新说明](https://github.com/illlyy/Fcode/releases)

正式版 APK 由 GitHub Actions 构建并附带 `update.json`、`sha256sums.txt` 和 R8 mapping。应用内更新读取的地址是：

```text
https://github.com/illlyy/Fcode/releases/latest/download/update.json
```

Fcode 使用独立的应用包名和签名，不能覆盖官方 `com.termux`。首次从开发签名切换到生产签名时，也不能直接覆盖安装；请先备份 `$HOME`、`$PREFIX` 和应用配置，再卸载旧包并安装正式版。不要把生产 keystore、密码、API key、Token 或代理订阅地址提交到 Git。

## 项目结构

```text
app/                         Android 应用和 Fcode UI
terminal-emulator/           终端模拟器模块
terminal-view/               终端视图模块
termux-shared/               Termux 共用库
vendor/codex-web/            Codex Web 源码快照及 Android bridge
vendor/rikkahub-native/      RikkaHub native UI 参考快照
app/src/main/assets/         运行时 WebView、Mihomo 等固定资源
tools/                       WebView 资源构建和维护脚本
```

根目录不再保存 ADB dump、截图、logcat 和性能采样；这些文件属于本地 QA 产物，已通过 `.gitignore` 排除。

## 本地构建

要求：Android SDK、Java 17、NDK `22.1.7171670`。先在根目录创建被忽略的 `local.properties`，填写本机 SDK 路径：

```properties
sdk.dir=C:\\Android\\Sdk
```

Windows PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Linux/macOS：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

如需重新生成 Codex Web 快照，先阅读 [`vendor/codex-web/UPSTREAM.md`](vendor/codex-web/UPSTREAM.md)，再运行：

```powershell
.\tools\build-codex-web-assets.ps1
```

脚本默认只生成候选资源，确认后再使用 `-Apply` 写入 APK assets。

## 发布正式版

版本号由 [`app/build.gradle`](app/build.gradle) 中的 `versionCode` 和 `versionName` 控制。每次发布按以下顺序操作：

1. 同时递增版本号，更新 [`RELEASE_NOTES.md`](RELEASE_NOTES.md) 和 [`CHANGELOG.md`](CHANGELOG.md)。
2. 提交变更并创建与 `versionName` 完全一致的标签，例如 `v0.118.4`。
3. 推送提交和标签：

   ```bash
   git push origin fcode-main
   git push origin v0.118.4
   ```

4. [`Publish signed release`](.github/workflows/release.yml) 会运行测试、构建 R8 release APK、验证签名，生成 `update.json` 和 SHA-256 校验文件，并创建 GitHub Release。

首次配置仓库时，在 GitHub Actions Secrets 中设置：

- `FCODE_KEYSTORE_BASE64`
- `FCODE_STORE_PASSWORD`
- `FCODE_KEY_ALIAS`
- `FCODE_KEY_PASSWORD`

生产 keystore 必须只保存在安全的密码管理器和 GitHub Secrets 中。若把发布资产放到另一个公开仓库，可设置 Actions Variable `FCODE_RELEASE_REPOSITORY`（例如 `illlyy/Fcode-Releases`），并为工作流提供具有 Contents write 权限的 `FCODE_RELEASE_TOKEN`。默认情况下，发布到当前仓库。

### 更新说明怎么写

每个版本都把面向用户的变化写进 `RELEASE_NOTES.md`，再同步到 `CHANGELOG.md`。只写用户能感知或维护者需要知道的内容，建议使用下面的结构：

```markdown
## 新增
- 新增了什么功能，用户在哪里可以找到它。

## 修复
- 修复了什么问题，什么场景不再出错。

## 变更
- 行为、兼容性或配置是否发生变化。

## 安全
- 签名、权限、凭据处理等重要变化。

## 已知问题
- 当前版本仍存在的限制，以及临时解决办法。
```

不要把内部 commit hash、测试截图或未经确认的功能计划当作已发布功能；这些内容留在 issue 或开发记录中。

## 应用内更新协议

`update.json` 至少包含以下字段：

| 字段 | 作用 |
| --- | --- |
| `versionCode` | 必须大于已安装版本的整数版本号 |
| `versionName` | 展示给用户的语义化版本 |
| `minSdk` | APK 支持的最低 Android API |
| `force` | 是否强制更新 |
| `apkUrl` | 必须是 HTTPS 下载地址 |
| `sha256` | APK 的 64 位十六进制 SHA-256 |
| `changelog` | 弹窗和 Release 中显示的更新内容 |

客户端只信任 HTTPS 清单，并依据 `versionCode` 判断是否存在新版本；确认更新后会用默认浏览器打开 `apkUrl`。下载完成后由 Android 安装器校验应用签名，Release 同时提供 SHA-256 供手动核对。没有正式 Release 时，`update.json` 不存在是正常的；发布第一个匹配版本的 tag 后，地址才会生效。

## 分支策略

分支历史是线性的：`main` → `fcode-main` → `feat/rikka-compose-ui`。因此不需要三方 merge。当前功能分支已经包含默认分支的全部提交，维护时以 `fcode-main` 为唯一发布分支；`feat/rikka-compose-ui` 仅作为迁移期间的备份，确认新 Release 正常后再删除远程备份分支。

## 安全与隐私

- Codex Web 和 Mihomo 控制器默认只监听 loopback，不要绑定到 `0.0.0.0` 或直接暴露公网。
- 不要提交 API key、Bearer token、GitHub token、代理凭据、订阅 URL、生产签名材料或真实用户日志。
- 公开仓库中的第三方源码、WebView 快照、字体、图标和二进制资源仍受其各自许可证约束；请阅读 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 及资源目录中的许可证文件。

## 许可证

Android 基础工程按 GPLv3-only 发布，模块例外和第三方许可证以各目录中的原始文件为准。详见 [`LICENSE.md`](LICENSE.md) 和 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。上游 Termux 文档和项目说明见 [termux/termux-app](https://github.com/termux/termux-app)。

问题反馈和功能讨论请使用 [GitHub Issues](https://github.com/illlyy/Fcode/issues)。

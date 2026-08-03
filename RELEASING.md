# RELEASING.md — 发布 & 推送更新指南（AI / 人类通用）

> 给任何智能体或开发者：照着这份文档就能从「改完代码」走到「用户手机收到更新」。
> 发布是 **tag 驱动的 CI 流程**：你只负责「改版本号 → 提交 → 打 tag → 推送」，GitHub Actions
> 自动构建签名 APK、生成 `update.json`、发布到 GitHub Releases。应用内更新靠 Releases 上的 `update.json`。

## 0. 全流程鸟瞰

```
改代码（含单测通过）
   ↓ ① 版本号 app/build.gradle（versionCode 递增 + versionName）
   ↓ ② CHANGELOG.md + RELEASE_NOTES.md
   ↓ ③ 跑一遍 :app:testDebugUnitTest（必须全绿）
   ↓ ④ 提交 + 推送 fcode-main
   ↓ ⑤ 打 tag vX.Y.Z（必须与 versionName 完全一致）并推送
   ↓ ⑥ GitHub Actions release.yml 自动：跑单测 → 签 APK → 生成 update.json + sha256sums.txt → 建 GitHub Release
   ↓ ⑦ 核对 CI 结果 + 应用内「检查更新」自测
```

## 1. 版本号约定（硬性）

- `versionName` 必须是 `X.Y.Z`（semver，构建脚本强校验）。例：`0.3.2`。
- `versionCode` 是整数，**必须比上次发布大**（本地/CI 不自动校验重复，靠发布者自查）。
- **tag 名 = `v` + versionName**：`versionName 0.3.2` ↔ `tag v0.3.2`。
  - release.yml 的触发条件是 push 形如 `v*.*.*` 的 tag；
  - 工作流还会校验 `v$versionName == $GITHUB_REF_NAME`，不一致直接失败。

## 2. 发布前必改的三个文件

1. `app/build.gradle` → `defaultConfig.versionCode` / `versionName`。
2. `CHANGELOG.md` → 顶部加 `## [X.Y.Z] - 日期` 条目（面向用户，中英均可，与历史风格一致）。
3. `RELEASE_NOTES.md` → 整个文件替换为本版本发布说明。它会被 Actions 写进 GitHub Release 描述和 `update.json` 的 changelog。

> 风格参考：最近一次提交 `Release 0.3.1: Ultra meteor sky upgrade and large-file refactoring`。

## 3. 提交 & 推送

```bash
# 3.1 确认只提交该提交的内容，没有意外文件（敏感文件 *.jks/*.keystore/*.key/.env* 绝不提交）
git status
git diff --stat

# 3.2 提交（英文短句，与历史一致）
git add -A
git commit -m "Release 0.3.2: Claude streaming fix, streaming markdown toggle, collapse fix, MCP/skills UI, top bar consolidation"

# 3.3 推送主分支（默认远程 origin = https://github.com/illlyy/Sillage.git）
git push origin fcode-main
```

## 4. 打 tag 并推送（触发 CI 发布）

```bash
# tag 必须与 versionName 完全一致
git tag v0.3.2
git push origin v0.3.2
```

推送 tag 后，GitHub Actions `.github/workflows/release.yml` 开始执行：
- 校验签名 Secret（`FCODE_KEYSTORE_BASE64` 等）→ 还原 keystore；
- `./gradlew testDebugUnitTest assembleRelease`；
- 校验 `v$versionName == tag`；
- `apksigner` 验证签名；生成 `Fcode-v0.3.2.apk` + `sha256sums.txt` + `update.json`；
- 上传 R8 `mapping.txt` 到 artifact；
- `gh release create` 发布。

## 5. 核对产物（发布后必做）

```bash
# 5.1 查 CI 运行状态（一次性 API 查询；不要 while 循环轮询）
#     替换 owner/repo 为你实际的仓库
curl -s "https://api.github.com/repos/illlyy/Sillage/actions/runs?event=push&per_page=1" \
  | grep -E '"name"|"status"|"conclusion"' | head

# 5.2 或直接看网页：GitHub → Actions → 最新 run → 应为绿色 success
# 5.3 打开 GitHub Releases 页面，核对 v0.3.2 发布包含：
#     Fcode-v0.3.2.apk · update.json · sha256sums.txt
```

发布清单 `update.json` 结构（应用内更新读取）：

```json
{
  "versionCode": 1008,
  "versionName": "0.3.2",
  "minSdk": 24,
  "force": false,
  "apkUrl": "https://github.com/<repo>/releases/download/v0.3.2/Fcode-v0.3.2.apk",
  "sha256": "...",
  "publishedAt": "...",
  "changelog": "（来自 RELEASE_NOTES.md）"
}
```

## 6. 应用内「检查更新」自测

1. 手机上打开 Fcode → 设置 → 检查更新（或新版本提示入口）。
2. 应弹出 0.3.2 更新提示，含 changelog。
3. 确认下载按钮指向 `apkUrl`、校验 SHA-256 通过、可正常安装。
4. 如果没提示：检查 `update.json` 的 `versionCode` 是否 > 已安装版本的 `versionCode`，以及 URL 是否可访问。

## 7. 发布清单 & 回滚

- 发布产物契约：`RELEASE_NOTES.md` → GitHub Releases；`update.json` → 应用内更新。
- **回滚**：旧版本 APK 仍在 Releases；但应用内更新只认 `update.json`（永远指 latest），
  回滚需手动重新发布一个更高 versionCode 的版本，或把 `update.json` 指向旧包。
- `update.json` 里 `force: true` 会强制升级（慎用）。

## 8. CI 失败常见原因

| 失败点 | 原因 / 处理 |
|---|---|
| `Tag vX.Y.Z does not match versionName` | tag 与 versionName 不一致，改一致后重推 |
| `test -n "$KEYSTORE_BASE64"` 失败 | 仓库 Secrets 缺 `FCODE_KEYSTORE_BASE64` 等签名凭据（GitHub → Settings → Secrets） |
| bootstrap 下载失败 | CI 网络问题，重跑 |
| `gh release create` 失败 | Release 已存在（重复 tag）；删除远程 tag 重来，或改版本号 |

## 9. 敏感信息红线（发布者必读）

- API Key / Token / 订阅地址 / 私人项目内容**永不进**日志、文档、issue、Release 说明。
- release keystore 与密码只存在于 GitHub Secrets；本地 `.gitignore` 已排除 `*.jks/*.keystore/*.key`。
- 发布前 `git status` 确认无意外文件（尤其是凭据/日志）。

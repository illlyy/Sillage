# Fcode 更新日志

本文件记录面向用户的重要变化。每次发布时，同时更新仓库根目录的
`RELEASE_NOTES.md`；GitHub Actions 会将该文件写入 GitHub Release 和 `update.json`。

## [0.118.4] - 2026-07-25

### 新增

- 应用内 GitHub Release 更新检查、下载和安装流程。
- 设置页手动检查更新入口和正式版低频自动检查。
- 下载完成通知及待安装状态恢复。

### 安全

- APK SHA-256、包名、版本号和签名证书校验。
- 生产签名从 GitHub Actions Secrets 注入，不再使用仓库内的公开开发密钥。

### 构建

- 新增 Tag 驱动的正式发布工作流。
- 自动生成并上传 `update.json`、`sha256sums.txt` 和正式 APK。
- 单独保存每个版本的 R8 `mapping.txt`。

## 改进

- 检测到新版本后改为使用默认浏览器打开 GitHub APK 下载链接。
- 更新弹窗会提示从浏览器下载列表打开 APK 完成安装。
- 不再为新的更新创建 Android 系统下载任务；首次启动时会取消并清理旧版遗留任务。

## 安全

- 更新清单继续通过 HTTPS 从 GitHub Releases 获取。
- APK 安装时由 Android 校验生产签名，Release 继续提供 SHA-256 校验文件。

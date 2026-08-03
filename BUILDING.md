# BUILDING.md — 构建指南（AI / 人类通用）

> 这份文档给任何智能体或开发者：照着它就能在干净的机器上构建 Sillage（Fcode）的 debug 或 release APK。
> 完整构建内部结构见 `docs/08-build-vendor.md`（仅本地）；这里是可执行的操作手册。

## 1. 前置条件

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 必须设置 `JAVA_HOME`，否则 Gradle 无法运行 |
| Android SDK | platform 37 (android-37.0)、build-tools | `sdkmanager "platforms;android-37.0"` |
| NDK | 22.1.7171670 | 通过 `gradle.properties` 固定，勿用本机默认 |
| 网络 | 需要外网 | 构建时会从 termux-packages 下载 `bootstrap-{arch}.zip`（SHA-256 固定）；离线构建会失败（设计如此） |
| Gradle wrapper | 9.4.1 | 用 `./gradlew`（不要装系统 Gradle） |

Gradle 从 `local.properties`（`sdk.dir=...`）或环境变量定位 Android SDK。`local.properties` 已被 gitignore，不提交。

## 2. 版本常量

- `app/build.gradle` 的 `defaultConfig`：`versionCode`（整数，必须随发布递增）+ `versionName`（语义化 `X.Y.Z`）。
- 环境变量 `TERMUX_APP_VERSION_NAME` 可临时覆盖 `versionName`（CI 或本地测试用）。
- 其他版本常量（minSdk/targetSdk/compileSdk/ndkVersion/markwonVersion）在 `gradle.properties`。

## 3. 构建命令（Windows PowerShell / Git Bash 均可）

```bash
# 0) 设置 JDK（替换成你的 JDK 17 路径）
export JAVA_HOME="/d/Androiddevev/toolchain/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"

# 1) 单元测试（发布前必跑；61 个测试文件，JUnit4 + Robolectric）
./gradlew :app:testDebugUnitTest

# 2) debug APK
./gradlew :app:assembleDebug

# 3) release APK（R8 minify；无签名环境变量时为「未签名」APK）
./gradlew :app:assembleRelease
```

### 产物位置

| 构建 | APK 路径 |
|---|---|
| debug | `app/build/outputs/apk/debug/app-<abi>-debug.apk`（按 ABI 拆分 + universal） |
| release | `app/build/outputs/apk/release/app-<abi>-release-unsigned.apk` 或 `app-<abi>-release.apk`（已签名） |

### 只在某个 ABI 上构建（加快本地验证）

```bash
./gradlew :app:assembleDebug -Preactivesplit=arm64-v8a
```

（`-P` 前缀的 Gradle 属性；等价地在 `app/build.gradle` 里改 `include`。）

## 4. 签名

- **debug**：自动用 `app/dev_keystore.jks`（仓库内，仅调试用）。
- **release**：需要以下**环境变量**全部非空才会签名；否则产出**未签名** APK：

  ```
  FCODE_KEYSTORE_PATH=<keystore 文件绝对路径>
  FCODE_STORE_PASSWORD=<store 密码>
  FCODE_KEY_ALIAS=<别名>
  FCODE_KEY_PASSWORD=<key 密码>
  ```

  这些凭据在 GitHub Actions 里通过 Secrets 提供（见 RELEASING.md）。**不要把真实 release keystore/密码提交进仓库或文档。**

## 5. 其它构建相关环境变量

| 变量 | 作用 | 默认 |
|---|---|---|
| `TERMUX_SPLIT_APKS_FOR_RELEASE_BUILDS` | release 是否按 ABI 拆分 | `0`（universal 单包，F-Droid 兼容） |
| `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS` | debug 是否按 ABI 拆分 | `1` |
| `FCODE_UPDATE_MANIFEST_URL` | 应用内更新清单 URL | `https://github.com/illlyy/Fcode/releases/latest/download/update.json` |
| `TERMUX_APP_VERSION_NAME` | 覆盖 versionName | 空 |

## 6. 安装到手机调试

```bash
# 设备连接后（adb 在 SDK platform-tools 下）
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# 启动应用
adb shell monkey -p com.ilyop.codex -c android.intent.category.LAUNCHER 1

# UI 自动化调试（Compose 节点 bounds 可从 uiautomator dump 拿，中文乱码不影响解析）
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
adb shell input tap <x> <y>      # 点击
adb shell input text "hello"     # 输入 ASCII（先点输入框聚焦，等 ~3s 再输）
adb shell screencap -p /sdcard/x.png && adb pull /sdcard/x.png
```

## 7. 常见问题

- **`JAVA_HOME` 未设置 / 版本不对** → `./gradlew` 立即报 `Unable to locate a Java Runtime`。设 `JAVA_HOME` 到 JDK 17。
- **构建卡在 bootstrap 下载** → 需要外网；镜像/代理异常时检查网络，SHA 不匹配会报错（那是版本改动的正常提醒）。
- **`assembleRelease` 产出 unsigned APK** → 缺 `FCODE_*` 签名环境变量。要发布必须走 RELEASING.md 的 tag → CI 流程（CI 里有真实签名 Secret）。
- **`assembleRelease` 时 Gradle daemon 崩溃（`insufficient memory ... Chunk::new`）** → 本地 R8 内存吃紧。用 `--no-daemon` + 更大堆并把堆基址抬到 4GB 之上，给原生内存留地址空间：
  ```bash
  ./gradlew :app:assembleRelease --no-daemon -Dorg.gradle.jvmargs="-Xmx3g -XX:HeapBaseMinAddress=4g \
  --add-exports=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED"
  ```
  （`-Dorg.gradle.jvmargs` 会覆盖 `gradle.properties` 里的 `-Xmx2048M`；GitHub Actions runner 内存充足，无此问题。）
- **版本号不合法** → `validateVersionName` 强制 `X.Y.Z`（semver 2.0.0）。写 `0.3.2`，不要写 `0.3.2` 之外的形式。
- **改了 `assets/codex-desktop/` 但被覆盖** → 那是构建产物，改它会丢；升级走 `tools/build-codex-web-assets.ps1`（见 docs/08）。

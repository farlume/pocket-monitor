# 开发指南

应用安装与使用见 [README](../README.zh-CN.md)。本页说明源码构建和实现位置。

## 本地开发

需要 JDK 17、Android SDK 35 / Build Tools 35.0.0、NDK 28.2.13676358（编译 USB Bulk 停流 JNI）。设置 `ANDROID_HOME`，或在不提交的 `local.properties` 中填写 `sdk.dir`。

```bash
git clone https://github.com/farlume/pocket-monitor.git
cd pocket-monitor
java -version
```

仓库为私有时，克隆需要具备访问权限的 GitHub 账号。首次构建需联网下载 Gradle 和 Maven 依赖；使用仓库自带 Wrapper，无需安装全局 Gradle。Windows 将 `./gradlew` 换为 `gradlew.bat`。

例如，本机 SDK 安装于 `/home/your-user/Android/Sdk` 时，在项目根目录创建 `local.properties`：

```properties
sdk.dir=/home/your-user/Android/Sdk
```

构建、检查和安装：

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest :androidApp:lintDebug
adb devices
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

安装前需连接手机、开启 USB 调试并允许该电脑调试。覆盖安装需要签名一致；签名不一致时，卸载旧包会同时清除已保存的格式设置。

版本固定：Kotlin 2.1.21、Compose Multiplatform 1.8.1、AGP 8.9.2、Gradle 8.14.2、UVCAndroid 1.0.13。构建依赖来自 Google Maven、Maven Central 和 Gradle Plugin Portal。

## Release 构建

Debug 构建不需要发布密钥。Release 构建需要从私密存储向进程注入 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`，不要将值写进命令记录、Gradle 文件或仓库。

```bash
./gradlew --no-daemon --no-configuration-cache :androidApp:lintRelease :androidApp:assembleRelease :androidApp:bundleRelease
```

APK 位于 `androidApp/build/outputs/apk/release/androidApp-release.apk`，AAB 位于 `androidApp/build/outputs/bundle/release/androidApp-release.aab`。未配置签名时 Release 任务会失败，Debug 构建仍可运行。GitHub Environment 配置、自动上传和标签发行见[发行指南](RELEASING.md)。

## 代码结构与协作

- `shared/src/commonMain`：Compose 界面、连接状态、设备/格式模型、模式选择与视频比例计算。
- `androidApp/src/main`：权限、USB 设备识别、原生 UVC 采集、TextureView 与生命周期管理。
- `shared/src/commonTest`：共享逻辑测试。
- `androidApp/src/test`：Robolectric 生命周期、权限边界和 Compose 交互测试。
- `docs/VALIDATION.md`：已完成的验证与真机验收步骤。
- `THIRD_PARTY_NOTICES.md`、APK 内 `assets/licenses/`：第三方组件及许可证。

`MainActivity` 负责权限入口、前后台生命周期和 TextureView；`UvcCaptureController` 负责 USB 授权、模式协商、采集状态及恢复；`PreviewFrames` 负责每次连接的帧统计，`SuccessfulModeStore` 负责格式持久化。共享的 `MonitorScreen` 通过状态和事件回调与 Android 采集层交互。

USB 和 native 操作在独立 HandlerThread 上串行执行；连接代次用于丢弃旧授权/打开/帧回调。原生渲染停止后才释放 Surface。视频直接渲染到 TextureView，不逐帧转成 Compose Bitmap；帧回调只用于统计，不保存视频数据。

开发约定见 [AGENTS.md](../AGENTS.md)，版本变化见[更新记录](../CHANGELOG.md)。测试报告位置和真机验收清单见[验证记录](VALIDATION.md)。修改功能时同步相关说明；自动化结果与真实设备验证结果分别记录。

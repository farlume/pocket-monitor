# 项目开发约定

本文件适用于整个仓库。用户当前任务优先；更深目录若新增 `AGENTS.md`，其约定适用于对应目录。日常说明和交付默认使用中文，代码标识符沿用英文。

## 项目范围

随身屏（Pocket Monitor）是通过 UVC 采集卡在 Android 手机上预览 HDMI 视频的应用，使用 Kotlin Multiplatform / Compose Multiplatform。先阅读 `README.md`、`docs/VALIDATION.md` 及相关实现，再修改代码。

- 唯一应用目标是 Android 8.0+；`shared` 的 JVM 目标用于测试，不是桌面客户端。
- 当前包含 Android 9+ 蓝牙键盘/触控板，不包含音频、录制、截图、USB 键鼠回传或 iPhone 接收端。新增能力需以用户任务为依据。
- 当前版本和 SDK 配置以 `androidApp/build.gradle.kts` 为准，插件版本在根 `build.gradle.kts`。不要因工具提示就顺带升级依赖。
- 真机采集尚待验证；不得把模拟器、Robolectric、界面截图或收到卡自生彩条当作真实 HDMI 验收。

## 代码位置

| 路径或类 | 职责 |
| --- | --- |
| `shared/.../CaptureModel.kt` | 状态、设备与模式模型、排序、降级、连接代次、显示比例 |
| `shared/.../MonitorScreen.kt` | 共享 Compose UI，通过回调表达用户操作 |
| `shared/.../DevicePage.kt`、`SettingsPage.kt`、`MonitorTheme.kt` | 设备选择、持久化偏好界面与多主题 |
| `shared/src/commonMain/composeResources/` | 中英文 UI 资源；错误状态通过 `CaptureMessage` 在界面翻译 |
| `androidApp/.../MainActivity.kt` | 权限入口、生命周期、TextureView 和显示变换 |
| `androidApp/.../BluetoothInputController.kt`、`AndroidHidTransport.kt` | 前台蓝牙 HID 注册、明确选机、报告队列与退出清理 |
| `shared/.../HidInput.kt`、`InputPage.kt` | HID 报告、美式键位和双语键鼠界面 |
| `androidApp/.../AppLocale.kt`、`AppPreferencesStore.kt` | 应用内语言环境与本地偏好存储 |
| `androidApp/.../UvcCaptureController.kt` | USB 发现与授权、原生采集、连接恢复、状态发布 |
| `androidApp/.../PreviewFrames.kt` | 单次连接的首帧、帧计数和持续出帧判断 |
| `androidApp/.../SuccessfulModeStore.kt` | 按 VID/PID 保存有效模式 |
| `shared/src/commonTest` | 共享模型和算法测试 |
| `androidApp/src/test` | Android JVM、Robolectric 和 Compose 交互测试 |

以上实现文件位于各模块 `src/commonMain/kotlin/dev/icelum/pocketmonitor` 或 `src/main/kotlin/dev/icelum/pocketmonitor`。共享代码不得引入 Android API；平台行为留在 Android 模块。遵循既有 Kotlin 风格，避免与任务无关的重构和批量格式化。

## 采集约束

1. 控制命令和 UI 状态在主线程协调，USB/native 操作在 `UvcPreview` HandlerThread 串行执行。不要在 UI 线程阻塞打开或关闭设备。
2. 保留 `CaptureSession` / epoch 校验。断开、切换格式、进入后台后，旧授权、打开和帧回调不能更新新连接；每次连接使用独立 `PreviewFrames`。
3. 停止原生渲染后才能释放 Surface / SurfaceTexture，关闭相机、USB control block 和 monitor 的顺序必须保持可控。
4. 采集只在前台运行。后台、销毁和拔出时释放资源；多张同 VID/PID 设备不能被自动任选其一；用户主动断开或拒绝 USB 权限后不要立即自动重连。
5. 只协商设备实际报告的 MJPEG/YUY2 模式。默认优先接近 720p/30 的 MJPEG，保存模式只有仍在设备能力列表中才可恢复。
6. 启动 8 秒无帧或启动异常时最多降级重连两次；降级不能增加像素数量或帧率，也不能从 MJPEG 自动切到 YUY2。已经出帧后的中断不能触发无限重连。
7. 保存模式须满足持续出帧判断，不能只依据成功打开设备或偶发首帧。VID/PID 表示型号，同型号设备共用设置。
8. 保持 TextureView 直接渲染；不要为 Compose 每帧复制 Bitmap。界面 fps 表示采集流统计，不能声称测得 HDMI 锁定状态或端到端延迟。
9. 预览、设备、键鼠、设置是页面标签，当前仅允许单卡采集。切换页面、主题或语言不能销毁已挂载的预览 View；隐藏预览控件必须移出无障碍遍历。

## 蓝牙输入约束

- Android 8 保留视频功能；仅 Android 9+ 创建 HID Device 平台实现。按需申请附近设备权限，不扫描、不申请位置权限。
- 仅向用户明确选择的已配对电脑发送。每次前台注册独立代次，过期回调不得改变当前会话。
- 后台、主动停止、错误和超时均清空发送队列，尝试松开所有按键/鼠标按钮并注销；返回前台需用户重新启用连接。
- 文本只支持美式键位 ASCII，最多 200 字符，发送前整段校验；不保存、不记录输入内容。中文通过电脑输入法处理，不承诺直接 Unicode 注入。
- 输入报告必须成对按下/释放，节流发送并限制队列；鼠标移动合并发送。不可声称已验证真机键鼠、BIOS 或开机前输入。

## 本地构建与验证

使用 JDK 17、Android SDK 35 / Build Tools 35.0.0 和 NDK 28.2.13676358，通过 `ANDROID_HOME` 或未跟踪的 `local.properties` 设置 SDK。使用仓库自带 Gradle Wrapper：

```bash
./gradlew :shared:jvmTest
./gradlew :androidApp:testDebugUnitTest :androidApp:lintDebug
./gradlew :androidApp:assembleDebug
python3 scripts/check_project.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

- 只改文档：检查事实、相对链接和 `git diff --check`，无需重跑应用测试。
- 修改发行资料脚本：运行 Python 发布/文档打包测试；使用 `package_docs.py` 检查已提交版本的实际文档 ZIP，保持图片、相对链接、源码提交与校验清单一致。
- 修改共享模型、模式选择或几何计算：运行 `:shared:jvmTest`；为有行为变化的边界补充有意义的回归测试。
- 修改 Android 生命周期、权限、UI 或持久化：运行 Android 单元测试和 Lint；改动影响共享逻辑时同时运行共享测试。
- 修改构建、依赖、Manifest、资源或准备交付 APK：构建 Debug，并执行相关测试及 Lint。
- USB/native 行为需要真机验证，按 `docs/VALIDATION.md` 记录设备和步骤。没有硬件时明确说明未验证，不能用自动化测试结果替代。

APK 位于 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。报告位于 `shared/build/reports/tests/jvmTest/`、`androidApp/build/reports/tests/testDebugUnitTest/` 和 `androidApp/build/reports/lint-results-debug.html`。交付时说明实际执行的检查、结果和剩余限制；不要把历史验证写成本次执行结果。

## 文档、提交和发行

- 修改前检查 `git status`，保留用户已有改动；提交前检查差异，只纳入任务相关文件。提交信息沿用 `feat:`、`fix:`、`docs:` 等简洁形式。
- 用户可见行为变化同步 `README.md`；版本发布同步 `CHANGELOG.md` 和 `docs/releases/`。版本号与 versionCode 在 Android 构建文件维护，普通文档改动不增加版本。
- `docs/VALIDATION.md` 只记录实际完成的验证，硬件未完成事项保留明确说明。
- `artifacts/`、构建输出、`local.properties`、`.env`、密钥和签名文件不提交；不把本机基础设施、凭据或私有配置写入项目文档。
- 本地提交、推送和创建 Release 是不同操作，按用户已授权范围执行。推送代码不代表发布 APK；Release 中列出的附件必须实际存在且与版本对应。
- 保留 `THIRD_PARTY_NOTICES.md` 与 `androidApp/src/main/assets/licenses/`。变更第三方依赖或分发方式时同步来源、版本和对应许可材料，不自行更改项目授权方式。
- 中英文字符串保持相同键与编号占位符；新增用户可见文字使用资源。详见 `docs/TRANSLATING.md`。README 截图由测试渲染真实 UI，更新时检查两种语言的布局，不生成假采集画面。
- `main` 和 PR 只运行 CI，不读取发布密钥；签名发布只由 `v*` 标签触发，使用仅允许版本标签的 `release` Environment。修改工作流时保留签名缺失即失败、证书指纹校验、版本与标签一致及私钥清理逻辑，具体步骤见 `docs/RELEASING.md`。

# Privacy / 隐私说明

Pocket Monitor previews video locally. The app does not request Internet access and has no analytics, advertising SDK, account system, video uploads, recording or cloud synchronization.

- **Camera permission** allows Android to expose UVC video to the app. **USB permission** grants access to the selected capture card.
- **Nearby devices** permissions are requested only for Bluetooth input: connecting a selected paired computer and optionally making the phone discoverable. The app does not scan for devices or request location. Keyboard text and pointer events go only to the selected computer; input text and Bluetooth addresses are not saved or logged by the app. Android manages pairing records. Input stops and pending reports are discarded in the background.
- Theme, accent, language and preview preferences are stored in app-private preferences. Working capture formats are stored by USB vendor/product ID, not by device serial number.
- Capture stops when the app leaves the foreground. Android backup is disabled. Uninstalling the app or clearing its storage removes preferences.
- Diagnostic logs can contain capture errors and device details. Logs and screenshots submitted to GitHub are shared by you, outside the app; remove private information first.
- **Devices → Connection diagnostics** shows a bounded in-memory history of connection stages, phone/app version, USB vendor/product ID, formats, link speed and video endpoint descriptors. Opening this dialog also reads a bounded excerpt from Android's log buffer, filtered to this process ID and UVC library tags; no other app or Bluetooth input logs are included. Collection runs in the background with a timeout and needs no additional log-reading permission. It contains no video or keyboard input. Copying it writes the report to the system clipboard only when you tap Copy; the app does not upload it.

This describes the current source. Forks and future versions may behave differently. See [security reporting](../SECURITY.md) for concerns.

## 中文

随身屏在本地预览视频，不申请联网权限，不含统计分析、广告 SDK、账号系统、视频上传、录制或云同步。

- **相机权限**用于 Android 上的 UVC 视频访问；**USB 权限**用于访问所选采集卡。
- **附近的设备权限**仅用于蓝牙输入，连接明确选择的已配对电脑，以及按需允许电脑发现手机。不扫描设备，不申请定位权限。输入文本和鼠标事件只发送到所选电脑；App 不保存或记录输入文本、蓝牙地址，配对记录由 Android 系统管理。离开前台停止输入并丢弃待发送报告。
- 主题、配色、语言和预览偏好保存在应用私有设置中。有效采集格式按 USB 厂商/产品 ID 保存，不使用设备序列号。
- 应用离开前台时停止采集；已禁用 Android 备份。卸载应用或清除存储会删除本地偏好。
- 诊断日志可能包含采集错误和设备信息。你主动提交到 GitHub 的日志或截图不属于 App 内数据传输，请先移除个人信息。
- **设备 → 连接诊断**显示内存中条数有限的连接阶段、手机/应用版本、USB 厂商/产品 ID、格式、连接速率和视频端点信息。打开弹窗时还会从 Android 日志缓冲区读取长度受限的记录，仅接受本进程且标签为 UVC 库的日志，不包含其他应用或蓝牙输入日志。收集在后台执行并设有超时，不新增日志读取权限；报告不包含视频或键盘输入。只有点击「复制诊断」时才将报告写入系统剪贴板，App 不会上传。

本说明对应当前源码，分支或后续版本可能不同。安全问题请参考[报告方式](../SECURITY.zh-CN.md)。

# Hardware compatibility / 硬件兼容性

**No phone/capture-card combination has completed hardware validation yet.** UI tests, an APK build and a card-generated color-bar screen are not evidence of real HDMI capture.

Use Android 8.0+ with USB Host/OTG and a UVC capture card advertising MJPEG or YUY2. Advertised modes and a card's marketing resolution do not guarantee stable performance. Only one card is opened at a time.

To contribute a result, use the [hardware compatibility issue template](https://github.com/farlume/pocket-monitor/issues/new?template=hardware_report.md) and follow the [validation checklist](VALIDATION.md). Include the phone/Android version, card model and VID:PID, USB topology/power, HDMI source/output, capture format, whether the actual moving source picture was visible, and the result of a 15-minute preview, rotation, unplug/replug and background/resume. Do not include serial numbers or private screen content.

Verified combinations will be added here with links to their test reports. Until then, there is no recommended or certified capture-card model.

## 中文

**目前没有完成真机验收的手机/采集卡组合。** UI 测试、APK 构建成功或采集卡自生彩条均不能证明已采集到真实 HDMI 输入。

要求 Android 8.0+、USB Host/OTG，以及支持 MJPEG 或 YUY2 的 UVC 采集卡。设备上报格式与商品标称分辨率不代表实际稳定性能；当前一次只打开一张卡。

欢迎通过[硬件兼容性模板](https://github.com/farlume/pocket-monitor/issues/new?template=hardware_report.md)提交结果，并按[验证清单](VALIDATION.md)测试。请记录手机与 Android 版本、卡型号和 VID:PID、USB 连接与供电、HDMI 来源/输出规格、采集格式、是否实际看到源画面变化，以及持续 15 分钟预览、旋转、拔插和后台恢复的表现。不要附设备序列号或私人屏幕内容。

完成验证后会在此添加组合和报告链接；现阶段没有推荐或认证的采集卡型号。

## Bluetooth input / 蓝牙键鼠

Bluetooth input in v0.1.5 requires Android 9+ and a phone that exposes the HID Device profile, plus a host supporting Bluetooth keyboards/mice. Android 8 keeps video preview but cannot enable Bluetooth input. No phone/computer pairing has been hardware-validated. BIOS/pre-boot use and USB keyboard emulation are not supported. Report the phone, Android version, computer OS, host keyboard layout and reconnect/background behavior; redact Bluetooth addresses.

v0.1.5 的蓝牙键鼠需要 Android 9+、手机系统提供 HID Device，以及支持蓝牙键鼠的电脑。Android 8 仍可视频预览，不能启用蓝牙输入。尚无通过真机验证的手机/电脑组合；不支持 BIOS / 开机前使用或 USB 键盘模拟。反馈时记录手机、Android 版本、电脑系统、键盘布局和断开/后台行为，隐藏蓝牙地址。

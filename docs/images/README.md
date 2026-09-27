# Screenshots / 界面截图

| Page / 页面 | English | 简体中文 |
| --- | --- | --- |
| Preview / 预览 | [Open image](preview-en.png) | [查看图片](preview-zh.png) |
| Devices / 设备 | [Open image](devices-en.png) | [查看图片](devices-zh.png) |
| Keyboard / 键鼠 | [Open image](input-en.png) | [查看图片](input-zh.png) |
| Settings / 设置 | [Open image](settings-en.png) | [查看图片](settings-zh.png) |
| App icon / 应用图标 | [PNG](app-icon.png) | [SVG 源图](app-icon.svg) |

The screenshots show the actual app UI rendered by Robolectric, without a connected capture card or computer. The Keyboard page shows a simulated ready state with no paired devices. These are not evidence of working HDMI capture or Bluetooth input. The images are publicly accessible without signing in; offline readers need the complete source archive, not a standalone README attachment.

截图由 Robolectric 渲染实际应用 UI，展示未连接采集卡或电脑的状态；键鼠页使用无配对设备的就绪状态测试数据，不作为 HDMI 或蓝牙输入成功的证据。图片已公开，无需登录；离线阅读请下载并解压完整源码包，单独下载 README 附件不含图片。

## 更新双语截图

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'dev.icelum.pocketmonitor.AppSettingsTest.renderEnglishAndChinesePages'
```

`preview-*`、`devices-*`、`input-*`、`settings-*` 的输出位于 `androidApp/build/reports/screenshots/`。检查实际图片后再复制到本目录；不要用生成的假视频替代真实采集内容。README 使用标准 Markdown 图片链接，保留文件名大小写和相对路径。

图标的矢量源为 `app-icon.svg`，与 Android 启动图标保持一致。README 使用 PNG 导出，便于不支持 SVG 的预览器查看。更新矢量图后同步导出：

```bash
convert -background none -density 192 docs/images/app-icon.svg -resize 128x128 PNG32:docs/images/app-icon.png
```

## 旧版首页截图

`phone-idle.png` 来自 `MonitorScreenTest.idleScreenExplainsWiringAndOffersPermission` 对实际 Compose 界面的 Robolectric 渲染，展示 Android 竖屏、未连接采集卡、未授予相机权限的首页。图片未合成视频内容，不作为真实 HDMI 采集验证证据。

重新生成：

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'dev.icelum.pocketmonitor.MonitorScreenTest.idleScreenExplainsWiringAndOffersPermission' --rerun-tasks
```

输出为 `androidApp/build/reports/screenshots/phone-idle.png`。确认文字、布局与当前产品一致后，复制到本目录；不要复制包含私人视频、账号信息或设备序列号的图片。

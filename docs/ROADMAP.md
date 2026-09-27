# Roadmap / 功能计划

Ideas are ordered by usefulness to a portable monitor. They are **not implemented features or release promises**.

| Priority | Possible feature | Why it helps |
| --- | --- | --- |
| 1 | Validated hardware compatibility reports and capture recovery | Makes black screens, power issues and format failures easier to explain. |
| 2 | Freeze frame and save a screenshot | Lets users inspect small text and share a problem; requires a clear capture/save action. |
| 3 | Grid, center marker and safe-area overlays | Helps composition and alignment without modifying captured video. |
| 4 | Per-card display presets | Restores rotation, crop/zoom and overlays for common setups. |
| Later | Audio monitoring and recording | Needs separate permission, synchronization, storage and performance work. |

The current source has Preview, Devices, Keyboard and Settings tabs. Bluetooth keyboard/touchpad support is included in v0.1.5 but not yet hardware-validated. Concurrent multi-card sessions are not supported; they would need independent capture lifecycles and bandwidth/power validation. Discuss a use case in a feature issue before starting a large change.

## 中文

以下按便携监视器的使用价值排序，**尚未实现，也不承诺发布时间**。

| 优先级 | 候选功能 | 用途 |
| --- | --- | --- |
| 1 | 硬件兼容性验收报告、采集恢复 | 更容易判断黑屏、供电或格式协商问题 |
| 2 | 冻结画面、保存截图 | 查看小字或分享问题，需要明确的截取与保存操作 |
| 3 | 网格、中心标记、安全框 | 辅助构图和对齐，不修改采集视频 |
| 4 | 按采集卡保存显示预设 | 恢复常用旋转、裁切/缩放和辅助线 |
| 后续 | 音频监听、视频录制 | 需单独处理权限、音画同步、存储和性能 |

当前源码的四个标签用于预览、设备、键鼠和设置，不是多路采集会话。蓝牙键盘/触控板已包含在 v0.1.5，尚未完成真机验收。多卡并发需要独立采集生命周期和带宽/供电验证。较大功能请先通过 Issue 说明使用场景。

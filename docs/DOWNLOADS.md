# Downloads / 下载说明

## English

Open the [latest release](https://github.com/farlume/pocket-monitor/releases/latest) and choose the file ending in **`-release.apk`** under Assets. Open that file on an Android 8.0+ phone. GitHub **Source code** archives contain source files, not an installable app; `.aab` files are for store distribution.

For an update, use a Release APK signed with the same key as the installed Release version. Debug and Release builds use different keys. Removing an incompatible installation clears its local settings.

For offline documentation, download and extract `*-source.zip` with its directory structure intact, then open its `README.md` in a Markdown viewer. Do not copy the README out of the archive by itself: screenshots and linked guides are in `docs/`.

Starting with **v0.1.4**, releases provide `*-docs.zip`. Use it for a smaller offline copy containing both languages, screenshots, guides and available license texts. Keep its directory structure intact. For **v0.1.3**, use the source ZIP; that version does not have a separate documentation ZIP.

To verify one downloaded APK against the `SHA256SUMS` file from the **same release**:

```bash
# Linux
sha256sum pocket-monitor-0.1.6-release.apk
# macOS
shasum -a 256 pocket-monitor-0.1.6-release.apk
```

```powershell
# Windows PowerShell
Get-FileHash .\pocket-monitor-0.1.6-release.apk -Algorithm SHA256
```

Compare the result with the line naming that APK in `SHA256SUMS`; use the actual version's filename. If you downloaded all assets, Linux users can run `sha256sum --check SHA256SUMS`, or macOS users `shasum -a 256 --check SHA256SUMS`, from that directory. Missing assets cause the full check to fail; that does not by itself mean the APK is damaged.

## 简体中文

打开[最新版本](https://github.com/farlume/pocket-monitor/releases/latest)，在 Assets 中选择 **`-release.apk`** 结尾的文件，在 Android 8.0+ 手机上打开安装。GitHub 的 **Source code** 是源码，不是安装包；`.aab` 用于商店分发。

升级时使用与已安装 Release 同签名的 APK。Debug 与 Release 使用不同签名；卸载不兼容的旧安装会清除本地设置。

离线阅读请下载并完整解压 `*-source.zip`，用 Markdown 阅读器打开其中的 `README.zh-CN.md`。截图和其他指南都在 `docs/`，不要只拷贝 README 文件。

从 **v0.1.4** 起提供 `*-docs.zip`，可下载这个较小的文档包离线阅读，其中包含双语说明、截图、指南与已有许可文本；解压时保留目录结构。**v0.1.3** 没有单独的文档 ZIP，使用该版本的源码 ZIP 即可。

要验证某个 APK，下载同一版本的 `SHA256SUMS`，按上面的 Linux、macOS 或 PowerShell 命令计算 APK 的 SHA-256，再与清单中同名文件的一行比较。文件名按实际版本替换。只有下载了全部附件，才适合对整个清单执行 `--check`；未下载其他附件导致的检查失败不代表 APK 本身损坏。

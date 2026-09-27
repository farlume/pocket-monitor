# Development

[简体中文](DEVELOPMENT.md) · [Product guide](../README.md)

Use JDK 17, Android SDK 35, Build Tools 35.0.0 and NDK 28.2.13676358 (for the USB Bulk stop JNI helper). Set `ANDROID_HOME` or put `sdk.dir=/your/android/sdk` in an untracked `local.properties`. Use the bundled Gradle Wrapper; do not commit local SDK paths or keys.

```bash
./gradlew :shared:jvmTest
./gradlew :androidApp:testDebugUnitTest :androidApp:lintDebug
./gradlew :androidApp:assembleDebug
python3 scripts/check_project.py
git diff --check
```

The installable Debug APK is `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. With an authorized Android device, install it using `adb install -r` followed by that path. An existing installation with a different signing key must be removed first; uninstalling clears saved preferences.

The shared module holds Compose UI and platform-independent capture models. Android USB operations, permissions and preferences live in `androidApp`. The shared JVM target is for tests, not a desktop app. Read [AGENTS.md](../AGENTS.md) before changing session invalidation or native resource cleanup.

Android tests use Robolectric with native graphics to exercise real Compose interactions and render screenshots. They do not perform actual USB video transfer. See [translation and screenshot instructions](TRANSLATING.md), [hardware compatibility](COMPATIBILITY.md) and the [validation checklist](VALIDATION.md).

`main` and pull requests run CI only. A pushed `v*` tag runs tests, builds a signed APK/AAB and publishes a GitHub release with matching source, third-party material and checksums. Signing secrets belong to the tag-restricted GitHub `release` Environment; see [release operations](RELEASING.md). Local Release tasks intentionally fail if signing variables are missing.

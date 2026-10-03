package dev.icelum.pocketmonitor

import android.content.Context

internal class AppPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun read() = AppPreferences(
        theme = enumValue("theme", ThemeMode.System),
        accent = enumValue("accent", AccentColor.Mint),
        language = enumValue("language", AppLanguage.System),
        keepScreenOn = booleanValue("keep_screen_on", true),
        showFrameStats = booleanValue("show_frame_stats", true),
        keyboardLayout = enumValue("keyboard_layout", KeyboardLayout.Tkl87),
        keyboardTheme = enumValue("keyboard_theme", KeyboardTheme.Ivory),
        keyboardHaptics = booleanValue("keyboard_haptics", true),
    )

    private inline fun <reified T : Enum<T>> enumValue(key: String, default: T): T =
        runCatching { enumValues<T>().find { it.name == preferences.getString(key, null) } }.getOrNull() ?: default

    private fun booleanValue(key: String, default: Boolean): Boolean =
        runCatching { preferences.getBoolean(key, default) }.getOrDefault(default)

    fun write(value: AppPreferences) {
        preferences.edit()
            .putString("theme", value.theme.name)
            .putString("accent", value.accent.name)
            .putString("language", value.language.name)
            .putBoolean("keep_screen_on", value.keepScreenOn)
            .putBoolean("show_frame_stats", value.showFrameStats)
            .putString("keyboard_layout", value.keyboardLayout.name)
            .putString("keyboard_theme", value.keyboardTheme.name)
            .putBoolean("keyboard_haptics", value.keyboardHaptics)
            .apply()
    }
}

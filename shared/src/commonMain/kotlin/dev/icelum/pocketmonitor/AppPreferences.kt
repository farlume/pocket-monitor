package dev.icelum.pocketmonitor

enum class AppTab { Preview, Devices, Input, Settings }
enum class ThemeMode { System, Light, Dark }
enum class AccentColor { Mint, Ocean, Amber }
enum class AppLanguage(val languageTag: String?) { System(null), English("en"), Chinese("zh-CN") }

data class AppPreferences(
    val theme: ThemeMode = ThemeMode.System,
    val accent: AccentColor = AccentColor.Mint,
    val language: AppLanguage = AppLanguage.System,
    val keepScreenOn: Boolean = true,
    val showFrameStats: Boolean = true,
)

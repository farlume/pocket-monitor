package dev.icelum.pocketmonitor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferencesStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearPreferences() {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun settingsSurviveStoreRecreation() {
        val expected = AppPreferences(ThemeMode.Dark, AccentColor.Ocean, AppLanguage.English, false, false,
            KeyboardLayout.Compact84, KeyboardTheme.BlackGold, false)
        AppPreferencesStore(context).write(expected)
        assertEquals(expected, AppPreferencesStore(context).read())
    }

    @Test fun unknownEnumsAndWrongTypesFallBackWithoutLosingValidSettings() {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit()
            .putString("theme", "future-theme").putInt("accent", 9)
            .putString("language", "Chinese").putString("keep_screen_on", "bad-value")
            .putBoolean("show_frame_stats", false).commit()
        assertEquals(AppPreferences(language = AppLanguage.Chinese, showFrameStats = false), AppPreferencesStore(context).read())
    }

    @Test fun corruptKeyboardPreferencesFallBackToPlayableDefaults() {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit()
            .putString("keyboard_layout", "future-layout").putInt("keyboard_theme", 4)
            .putString("keyboard_haptics", "wrong-type").commit()
        assertEquals(AppPreferences(), AppPreferencesStore(context).read())
    }
}

package com.open.wuling.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

/**
 * 主题偏好设置持久化
 * 使用 DataStore Preferences 保存主题模式、自定义颜色、背景图片等
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // 主题模式: 0=跟随系统, 1=浅色, 2=深色
        val THEME_MODE = intPreferencesKey("theme_mode")

        // 自定义颜色开关
        val USE_CUSTOM_COLORS = booleanPreferencesKey("use_custom_colors")

        // 自定义背景开关
        val USE_CUSTOM_BACKGROUND = booleanPreferencesKey("use_custom_background")

        // 背景图片路径
        val BACKGROUND_IMAGE_PATH = stringPreferencesKey("background_image_path")

        // 背景模糊效果
        val BACKGROUND_BLUR = floatPreferencesKey("background_blur")

        // 背景暗色遮罩开关
        val BACKGROUND_DIM_ENABLED = booleanPreferencesKey("background_dim_enabled")

        // 卡片透明度 (0f=全透明, 1f=不透明)
        val CARD_ALPHA = floatPreferencesKey("card_alpha")

        // 自定义主色
        val CUSTOM_PRIMARY_COLOR = intPreferencesKey("custom_primary_color")

        // 自定义背景色
        val CUSTOM_BACKGROUND_COLOR = intPreferencesKey("custom_background_color")

        // 自定义卡片色
        val CUSTOM_CARD_COLOR = intPreferencesKey("custom_card_color")

        // 自定义文字主色
        val CUSTOM_TEXT_PRIMARY_COLOR = intPreferencesKey("custom_text_primary_color")

        // 自定义文字次色
        val CUSTOM_TEXT_SECONDARY_COLOR = intPreferencesKey("custom_text_secondary_color")

        // 自定义图标色
        val CUSTOM_ICON_COLOR = intPreferencesKey("custom_icon_color")
    }

    val themeModeFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: 0
    }

    val useCustomColorsFlow: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[USE_CUSTOM_COLORS] ?: false
    }

    val useCustomBackgroundFlow: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[USE_CUSTOM_BACKGROUND] ?: false
    }

    val backgroundImagePathFlow: Flow<String?> = context.themeDataStore.data.map { prefs ->
        prefs[BACKGROUND_IMAGE_PATH]
    }

    val backgroundBlurFlow: Flow<Float> = context.themeDataStore.data.map { prefs ->
        prefs[BACKGROUND_BLUR] ?: 0f
    }

    val backgroundDimEnabledFlow: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[BACKGROUND_DIM_ENABLED] ?: true
    }

    val cardAlphaFlow: Flow<Float> = context.themeDataStore.data.map { prefs ->
        prefs[CARD_ALPHA] ?: 0.95f
    }

    val customPrimaryColorFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[CUSTOM_PRIMARY_COLOR] ?: 0xFF2D7AF6.toInt()
    }

    val customBackgroundColorFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[CUSTOM_BACKGROUND_COLOR] ?: 0xFF0A0A0C.toInt()
    }

    val customCardColorFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[CUSTOM_CARD_COLOR] ?: 0xFF1A1A1E.toInt()
    }

    val customTextPrimaryColorFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[CUSTOM_TEXT_PRIMARY_COLOR] ?: 0xFFFFFFFF.toInt()
    }

    val customTextSecondaryColorFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[CUSTOM_TEXT_SECONDARY_COLOR] ?: 0xFFB0B0B0.toInt()
    }

    val customIconColorFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[CUSTOM_ICON_COLOR] ?: 0xFF2D7AF6.toInt()
    }

    suspend fun setThemeMode(mode: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    suspend fun setUseCustomColors(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[USE_CUSTOM_COLORS] = enabled
        }
    }

    suspend fun setUseCustomBackground(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[USE_CUSTOM_BACKGROUND] = enabled
        }
    }

    suspend fun setBackgroundImagePath(path: String?) {
        context.themeDataStore.edit { prefs ->
            if (path != null) {
                prefs[BACKGROUND_IMAGE_PATH] = path
            } else {
                prefs.remove(BACKGROUND_IMAGE_PATH)
            }
        }
    }

    suspend fun setBackgroundBlur(blur: Float) {
        context.themeDataStore.edit { prefs ->
            prefs[BACKGROUND_BLUR] = blur
        }
    }

    suspend fun setBackgroundDimEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[BACKGROUND_DIM_ENABLED] = enabled
        }
    }

    suspend fun setCardAlpha(alpha: Float) {
        context.themeDataStore.edit { prefs ->
            prefs[CARD_ALPHA] = alpha
        }
    }

    suspend fun setCustomPrimaryColor(color: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[CUSTOM_PRIMARY_COLOR] = color
        }
    }

    suspend fun setCustomBackgroundColor(color: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[CUSTOM_BACKGROUND_COLOR] = color
        }
    }

    suspend fun setCustomCardColor(color: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[CUSTOM_CARD_COLOR] = color
        }
    }

    suspend fun setCustomTextPrimaryColor(color: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[CUSTOM_TEXT_PRIMARY_COLOR] = color
        }
    }

    suspend fun setCustomTextSecondaryColor(color: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[CUSTOM_TEXT_SECONDARY_COLOR] = color
        }
    }

    suspend fun setCustomIconColor(color: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[CUSTOM_ICON_COLOR] = color
        }
    }

    suspend fun clearCustomSettings() {
        context.themeDataStore.edit { prefs ->
            prefs[USE_CUSTOM_COLORS] = false
            prefs[USE_CUSTOM_BACKGROUND] = false
            prefs.remove(BACKGROUND_IMAGE_PATH)
            prefs[BACKGROUND_BLUR] = 0f
            prefs[BACKGROUND_DIM_ENABLED] = true
            prefs[CARD_ALPHA] = 0.95f
        }
    }
}

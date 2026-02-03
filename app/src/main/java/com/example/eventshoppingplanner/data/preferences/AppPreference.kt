package com.example.eventshoppingplanner.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore(name = "app_settings")

/**
 * アプリ全体の設定を管理するPreference
 */
class AppPreference(private val context: Context) {

    companion object {
        // 実行モード時ドラッグ有効/無効（デフォルト: 無効）
        private val EXECUTE_MODE_DRAG_ENABLED = booleanPreferencesKey("execute_mode_drag_enabled")

        // カード表示スケール（デフォルト: 1.0f）
        private val CARD_SCALE = floatPreferencesKey("card_scale")

        // スケールの範囲
        const val MIN_CARD_SCALE = 0.3f
        const val MAX_CARD_SCALE = 1.5f
        const val DEFAULT_CARD_SCALE = 1.0f
    }

    /**
     * 実行モード時ドラッグ有効/無効
     */
    val executeModeDragEnabled: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[EXECUTE_MODE_DRAG_ENABLED] ?: false
    }

    suspend fun setExecuteModeDragEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[EXECUTE_MODE_DRAG_ENABLED] = enabled
        }
    }

    /**
     * カード表示スケール
     */
    val cardScale: Flow<Float> = context.appDataStore.data.map { preferences ->
        preferences[CARD_SCALE] ?: DEFAULT_CARD_SCALE
    }

    suspend fun setCardScale(scale: Float) {
        val clampedScale = scale.coerceIn(MIN_CARD_SCALE, MAX_CARD_SCALE)
        context.appDataStore.edit { preferences ->
            preferences[CARD_SCALE] = clampedScale
        }
    }
}
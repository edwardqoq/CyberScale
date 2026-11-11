package com.cyberscale.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 轻量显示偏好（只有单位），用 SharedPreferences + StateFlow 暴露给 Compose。 */
class SettingsStore(context: Context) {

    companion object {
        private const val PREF = "cyberscale_settings"
        private const val KEY_UNIT = "weight_unit"
    }

    private val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val _unit = MutableStateFlow(readUnit())
    val unit: StateFlow<WeightUnit> = _unit.asStateFlow()

    private fun readUnit(): WeightUnit = try {
        WeightUnit.valueOf(prefs.getString(KEY_UNIT, WeightUnit.KG.name) ?: WeightUnit.KG.name)
    } catch (e: IllegalArgumentException) {
        WeightUnit.KG
    }

    fun setUnit(unit: WeightUnit) {
        prefs.edit().putString(KEY_UNIT, unit.name).apply()
        _unit.value = unit
    }
}

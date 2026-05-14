package com.llsl.viper4android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset

@Composable
fun resolvePresetName(preset: EqPreset): String {
    val resId = preset.nameKey?.let { EffectDispatcher.EQ_PRESET_NAME_RES[it] }
    return if (resId != null) stringResource(resId) else preset.name
}

@Composable
fun resolvePresetName(preset: DsPreset): String {
    val resId = preset.nameKey?.let { EffectDispatcher.DS_PRESET_NAME_RES[it] }
    return if (resId != null) stringResource(resId) else preset.name
}

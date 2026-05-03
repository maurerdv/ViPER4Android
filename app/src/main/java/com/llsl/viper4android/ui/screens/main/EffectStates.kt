package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset

data class OutputState(
    val volume: Int = 11,
    val channelPan: Int = 0,
    val limiter: Int = 5,
    val spkVolume: Int = 11,
    val spkChannelPan: Int = 0,
    val spkLimiter: Int = 5,
)

data class AgcState(
    val enabled: Boolean = false,
    val strength: Int = 0,
    val maxGain: Int = 3,
    val outputThreshold: Int = 3,
    val spkEnabled: Boolean = false,
    val spkStrength: Int = 0,
    val spkMaxGain: Int = 3,
    val spkOutputThreshold: Int = 3
)

data class FetState(
    val enabled: Boolean = false,
    val threshold: Int = -60,
    val ratio: Int = 100,
    val autoKnee: Boolean = true,
    val knee: Int = 0,
    val kneeMulti: Int = 0,
    val autoGain: Boolean = true,
    val gain: Int = 0,
    val autoAttack: Boolean = true,
    val attack: Int = 1,
    val maxAttack: Int = 44,
    val autoRelease: Boolean = true,
    val release: Int = 100,
    val maxRelease: Int = 200,
    val crest: Int = 100,
    val adapt: Int = 50,
    val noClip: Boolean = true,
    val spkEnabled: Boolean = false,
    val spkThreshold: Int = -60,
    val spkRatio: Int = 100,
    val spkAutoKnee: Boolean = true,
    val spkKnee: Int = 0,
    val spkKneeMulti: Int = 0,
    val spkAutoGain: Boolean = true,
    val spkGain: Int = 0,
    val spkAutoAttack: Boolean = true,
    val spkAttack: Int = 1,
    val spkMaxAttack: Int = 44,
    val spkAutoRelease: Boolean = true,
    val spkRelease: Int = 100,
    val spkMaxRelease: Int = 200,
    val spkCrest: Int = 100,
    val spkAdapt: Int = 50,
    val spkNoClip: Boolean = true
)

data class DdcState(
    val enabled: Boolean = false,
    val device: String = "",
    val spkEnabled: Boolean = false,
    val spkDevice: String = ""
)

data class VseState(
    val enabled: Boolean = false,
    val strength: Int = 10,
    val exciter: Int = 0,
    val spkEnabled: Boolean = false,
    val spkStrength: Int = 10,
    val spkExciter: Int = 0
)

data class EqState(
    val enabled: Boolean = false,
    val bandCount: Int = 10,
    val presetId: Long? = null,
    val bands: String = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
    val bandsMap: Map<Int, String> = mapOf(10 to "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;"),
    val presets: List<EqPreset> = emptyList(),
    val spkEnabled: Boolean = false,
    val spkBandCount: Int = 10,
    val spkPresetId: Long? = null,
    val spkBands: String = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
    val spkBandsMap: Map<Int, String> = mapOf(10 to "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;"),
    val spkPresets: List<EqPreset> = emptyList()
)

data class ConvolverState(
    val enabled: Boolean = false,
    val kernel: String = "",
    val crossChannel: Int = 0,
    val spkEnabled: Boolean = false,
    val spkKernel: String = "",
    val spkCrossChannel: Int = 0
)

data class FieldSurroundState(
    val enabled: Boolean = false,
    val widening: Int = 0,
    val midImage: Int = 5,
    val depth: Int = 0,
    val spkEnabled: Boolean = false,
    val spkWidening: Int = 0,
    val spkMidImage: Int = 5,
    val spkDepth: Int = 0
)

data class DiffSurroundState(
    val enabled: Boolean = false,
    val delay: Int = 4,
    val reverse: Boolean = false,
    val wetDryMix: Int = 100,
    val lpCutoff: Int = 0,
    val spkEnabled: Boolean = false,
    val spkDelay: Int = 4,
    val spkReverse: Boolean = false,
    val spkWetDryMix: Int = 100,
    val spkLpCutoff: Int = 0
)

data class VheState(
    val enabled: Boolean = false,
    val quality: Int = 0,
    val spkEnabled: Boolean = false,
    val spkQuality: Int = 0
)

data class ReverbState(
    val enabled: Boolean = false,
    val roomSize: Int = 0,
    val width: Int = 0,
    val dampening: Int = 0,
    val wet: Int = 0,
    val dry: Int = 50,
    val spkEnabled: Boolean = false,
    val spkRoomSize: Int = 0,
    val spkWidth: Int = 0,
    val spkDampening: Int = 0,
    val spkWet: Int = 0,
    val spkDry: Int = 50
)

data class DynamicSystemState(
    val enabled: Boolean = false,
    val device: Int = 0,
    val strength: Int = 50,
    val presetId: Long? = null,
    val presets: List<DsPreset> = emptyList(),
    val xLow: Int = 100,
    val xHigh: Int = 5600,
    val yLow: Int = 40,
    val yHigh: Int = 80,
    val sideGainLow: Int = 50,
    val sideGainHigh: Int = 50,
    val spkEnabled: Boolean = false,
    val spkDevice: Int = 0,
    val spkStrength: Int = 50,
    val spkPresetId: Long? = null,
    val spkPresets: List<DsPreset> = emptyList(),
    val spkXLow: Int = 100,
    val spkXHigh: Int = 5600,
    val spkYLow: Int = 40,
    val spkYHigh: Int = 80,
    val spkSideGainLow: Int = 50,
    val spkSideGainHigh: Int = 50
)

data class BassState(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 55,
    val gain: Int = 0,
    val antiPop: Boolean = true,
    val spkEnabled: Boolean = false,
    val spkMode: Int = 0,
    val spkFrequency: Int = 55,
    val spkGain: Int = 0,
    val spkAntiPop: Boolean = true,
)

data class BassMonoState(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 55,
    val gain: Int = 0,
    val antiPop: Boolean = true,
    val spkEnabled: Boolean = false,
    val spkMode: Int = 0,
    val spkFrequency: Int = 55,
    val spkGain: Int = 0,
    val spkAntiPop: Boolean = true
)

data class ClarityState(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val gain: Int = 1,
    val spkEnabled: Boolean = false,
    val spkMode: Int = 0,
    val spkGain: Int = 1
)

data class CureState(
    val enabled: Boolean = false,
    val strength: Int = 0,
    val spkEnabled: Boolean = false,
    val spkStrength: Int = 0,
)

data class AnalogXState(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val spkEnabled: Boolean = false,
    val spkMode: Int = 0
)

data class TubeSimulatorState(
    val enabled: Boolean = false,
    val spkEnabled: Boolean = false,
)

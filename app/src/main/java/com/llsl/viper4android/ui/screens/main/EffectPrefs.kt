package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.repository.ViperRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject

sealed class EffectPref<T>(
    val effectKey: String,
    val paramId: Int,
    val jsonKey: String,
    val defaultValue: T,
    val get: (MainUiState) -> T,
    val set: MainUiState.(T) -> MainUiState,
) {
    val prefKey: String =
        if (paramId != -1) {
            paramId.toString()
        } else if (effectKey.isEmpty()) {
            jsonKey
        } else {
            "${effectKey}_$jsonKey"
        }

    abstract fun toRaw(value: T): Int
}

class IntPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: Int,
    get: (MainUiState) -> Int,
    set: MainUiState.(Int) -> MainUiState,
) : EffectPref<Int>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: Int): Int = value
}

class BoolPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: Boolean,
    get: (MainUiState) -> Boolean,
    set: MainUiState.(Boolean) -> MainUiState,
) : EffectPref<Boolean>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: Boolean): Int = if (value) 1 else 0
}

class StringPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: String,
    get: (MainUiState) -> String,
    set: MainUiState.(String) -> MainUiState,
) : EffectPref<String>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: String): Int = 0
}

class NullableLongPref(
    effectKey: String,
    jsonKey: String,
    get: (MainUiState) -> Long?,
    set: MainUiState.(Long?) -> MainUiState,
) : EffectPref<Long?>(effectKey, -1, jsonKey, null, get, set) {
    override fun toRaw(value: Long?): Int = value?.toInt() ?: -1
}

class IntListPref(
    effectKey: String,
    jsonKey: String,
    defaultValue: List<Int>,
    get: (MainUiState) -> List<Int>,
    set: MainUiState.(List<Int>) -> MainUiState,
) : EffectPref<List<Int>>(effectKey, -1, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: List<Int>): Int = 0
}

class BoolListPref(
    effectKey: String,
    jsonKey: String,
    defaultValue: List<Boolean>,
    get: (MainUiState) -> List<Boolean>,
    set: MainUiState.(List<Boolean>) -> MainUiState,
) : EffectPref<List<Boolean>>(effectKey, -1, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: List<Boolean>): Int = 0
}

class DoubleListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Double>,
    get: (MainUiState) -> List<Double>,
    set: MainUiState.(List<Double>) -> MainUiState,
) : EffectPref<List<Double>>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: List<Double>): Int = 0
}

abstract class EffectGroupBuilder(
    val effectKey: String,
) {
    private val prefList = mutableListOf<EffectPref<*>>()

    protected fun int(
        paramId: Int,
        jsonKey: String,
        default: Int,
        get: (MainUiState) -> Int,
        set: MainUiState.(Int) -> MainUiState,
    ): IntPref {
        val pref = IntPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun bool(
        paramId: Int,
        jsonKey: String,
        default: Boolean,
        get: (MainUiState) -> Boolean,
        set: MainUiState.(Boolean) -> MainUiState,
    ): BoolPref {
        val pref = BoolPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun string(
        paramId: Int,
        jsonKey: String,
        default: String,
        get: (MainUiState) -> String,
        set: MainUiState.(String) -> MainUiState,
    ): StringPref {
        val pref = StringPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun nullableLong(
        jsonKey: String,
        get: (MainUiState) -> Long?,
        set: MainUiState.(Long?) -> MainUiState,
    ): NullableLongPref {
        val pref = NullableLongPref(effectKey, jsonKey, get, set)
        prefList += pref
        return pref
    }

    protected fun intList(
        jsonKey: String,
        default: List<Int>,
        get: (MainUiState) -> List<Int>,
        set: MainUiState.(List<Int>) -> MainUiState,
    ): IntListPref {
        val pref = IntListPref(effectKey, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun boolList(
        jsonKey: String,
        default: List<Boolean>,
        get: (MainUiState) -> List<Boolean>,
        set: MainUiState.(List<Boolean>) -> MainUiState,
    ): BoolListPref {
        val pref = BoolListPref(effectKey, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun doubleList(
        paramId: Int,
        jsonKey: String,
        default: List<Double>,
        get: (MainUiState) -> List<Double>,
        set: MainUiState.(List<Double>) -> MainUiState,
    ): DoubleListPref {
        val pref = DoubleListPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    fun toGroup(): EffectGroup = EffectGroup(effectKey, prefList.toList())
}

data class EffectGroup(
    val effectKey: String,
    val prefs: List<EffectPref<*>>,
)

class MasterLimiterEffect : EffectGroupBuilder("masterLimiter") {
    val threshold =
        int(
            ViperParams.kParamMasterLimiterThreshold,
            "threshold",
            100,
            { it.out.limiter },
            { copy(out = out.copy(limiter = it)) },
        )
    val outputVolume =
        int(
            ViperParams.kParamMasterLimiterOutputVolume,
            "outputVolume",
            100,
            { it.out.volume },
            { copy(out = out.copy(volume = it)) },
        )
    val channelPan =
        int(
            ViperParams.kParamMasterLimiterChannelPan,
            "channelPan",
            0,
            { it.out.channelPan },
            { copy(out = out.copy(channelPan = it)) },
        )
}

class PlaybackGainControlEffect : EffectGroupBuilder("playbackGainControl") {
    val enable =
        bool(
            ViperParams.kParamPlaybackGainControlEnable,
            "enable",
            false,
            { it.playbackGainControl.enable },
            { copy(playbackGainControl = playbackGainControl.copy(enable = it)) },
        )
    val strength =
        int(
            ViperParams.kParamPlaybackGainControlStrength,
            "strength",
            100,
            { it.playbackGainControl.strength },
            { copy(playbackGainControl = playbackGainControl.copy(strength = it)) },
        )
    val maxGain =
        int(
            ViperParams.kParamPlaybackGainControlMaxGain,
            "maxGain",
            100,
            { it.playbackGainControl.maxGain },
            { copy(playbackGainControl = playbackGainControl.copy(maxGain = it)) },
        )
    val outputThreshold =
        int(
            ViperParams.kParamPlaybackGainControlOutputThreshold,
            "outputThreshold",
            100,
            { it.playbackGainControl.outputThreshold },
            { copy(playbackGainControl = playbackGainControl.copy(outputThreshold = it)) },
        )
}

class LufsEffect : EffectGroupBuilder("lufs") {
    val enable =
        bool(
            ViperParams.kParamLufsEnable,
            "enable",
            false,
            { it.lufs.enable },
            { copy(lufs = lufs.copy(enable = it)) },
        )
    val target =
        int(
            ViperParams.kParamLufsTarget,
            "target",
            140,
            { it.lufs.target },
            { copy(lufs = lufs.copy(target = it)) },
        )
    val maxGain =
        int(
            ViperParams.kParamLufsMaxGain,
            "maxGain",
            60,
            { it.lufs.maxGain },
            { copy(lufs = lufs.copy(maxGain = it)) },
        )
    val speed =
        int(
            ViperParams.kParamLufsSpeed,
            "speed",
            1,
            { it.lufs.speed },
            { copy(lufs = lufs.copy(speed = it)) },
        )
}

class FetCompressorEffect : EffectGroupBuilder("fetCompressor") {
    val enable =
        bool(
            ViperParams.kParamFetCompressorEnable,
            "enable",
            false,
            { it.fetCompressor.enable },
            { copy(fetCompressor = fetCompressor.copy(enable = it)) },
        )
    val threshold =
        int(
            ViperParams.kParamFetCompressorThreshold,
            "threshold",
            -18,
            { it.fetCompressor.threshold },
            { copy(fetCompressor = fetCompressor.copy(threshold = it)) },
        )
    val ratio =
        int(
            ViperParams.kParamFetCompressorRatio,
            "ratio",
            100,
            { it.fetCompressor.ratio },
            { copy(fetCompressor = fetCompressor.copy(ratio = it)) },
        )
    val kneeAuto =
        bool(
            ViperParams.kParamFetCompressorKneeAuto,
            "kneeAuto",
            true,
            { it.fetCompressor.kneeAuto },
            { copy(fetCompressor = fetCompressor.copy(kneeAuto = it)) },
        )
    val knee =
        int(
            ViperParams.kParamFetCompressorKnee,
            "knee",
            0,
            { it.fetCompressor.knee },
            { copy(fetCompressor = fetCompressor.copy(knee = it)) },
        )
    val kneeMulti =
        int(
            ViperParams.kParamFetCompressorKneeMulti,
            "kneeMulti",
            0,
            { it.fetCompressor.kneeMulti },
            { copy(fetCompressor = fetCompressor.copy(kneeMulti = it)) },
        )
    val gainAuto =
        bool(
            ViperParams.kParamFetCompressorGainAuto,
            "gainAuto",
            true,
            { it.fetCompressor.gainAuto },
            { copy(fetCompressor = fetCompressor.copy(gainAuto = it)) },
        )
    val gain =
        int(
            ViperParams.kParamFetCompressorGain,
            "gain",
            0,
            { it.fetCompressor.gain },
            { copy(fetCompressor = fetCompressor.copy(gain = it)) },
        )
    val attackAuto =
        bool(
            ViperParams.kParamFetCompressorAttackAuto,
            "attackAuto",
            true,
            { it.fetCompressor.attackAuto },
            { copy(fetCompressor = fetCompressor.copy(attackAuto = it)) },
        )
    val attack =
        int(
            ViperParams.kParamFetCompressorAttack,
            "attack",
            20,
            { it.fetCompressor.attack },
            { copy(fetCompressor = fetCompressor.copy(attack = it)) },
        )
    val maxAttack =
        int(
            ViperParams.kParamFetCompressorMaxAttack,
            "maxAttack",
            80,
            { it.fetCompressor.maxAttack },
            { copy(fetCompressor = fetCompressor.copy(maxAttack = it)) },
        )
    val releaseAuto =
        bool(
            ViperParams.kParamFetCompressorReleaseAuto,
            "releaseAuto",
            true,
            { it.fetCompressor.releaseAuto },
            { copy(fetCompressor = fetCompressor.copy(releaseAuto = it)) },
        )
    val release =
        int(
            ViperParams.kParamFetCompressorRelease,
            "release",
            50,
            { it.fetCompressor.release },
            { copy(fetCompressor = fetCompressor.copy(release = it)) },
        )
    val maxRelease =
        int(
            ViperParams.kParamFetCompressorMaxRelease,
            "maxRelease",
            100,
            { it.fetCompressor.maxRelease },
            { copy(fetCompressor = fetCompressor.copy(maxRelease = it)) },
        )
    val crest =
        int(
            ViperParams.kParamFetCompressorCrest,
            "crest",
            100,
            { it.fetCompressor.crest },
            { copy(fetCompressor = fetCompressor.copy(crest = it)) },
        )
    val adapt =
        int(
            ViperParams.kParamFetCompressorAdapt,
            "adapt",
            50,
            { it.fetCompressor.adapt },
            { copy(fetCompressor = fetCompressor.copy(adapt = it)) },
        )
    val noClip =
        bool(
            ViperParams.kParamFetCompressorNoClip,
            "noClip",
            true,
            { it.fetCompressor.noClip },
            { copy(fetCompressor = fetCompressor.copy(noClip = it)) },
        )
}

class MultibandCompressorEffect : EffectGroupBuilder("multibandCompressor") {
    val enable =
        bool(
            ViperParams.kParamMultibandCompressorEnable,
            "enable",
            false,
            { it.multibandCompressor.enable },
            { copy(multibandCompressor = multibandCompressor.copy(enable = it)) },
        )
    val bandEnables =
        boolList(
            "bandEnables",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.bandEnables },
            { copy(multibandCompressor = multibandCompressor.copy(bandEnables = it)) },
        )
    val crossovers =
        intList(
            "crossovers",
            listOf(120, 500, 4000, 8000),
            { it.multibandCompressor.crossovers },
            { copy(multibandCompressor = multibandCompressor.copy(crossovers = it)) },
        )
    val thresholds =
        intList(
            "thresholds",
            listOf(-18, -18, -18, -18, -18),
            { it.multibandCompressor.thresholds },
            { copy(multibandCompressor = multibandCompressor.copy(thresholds = it)) },
        )
    val ratios =
        intList(
            "ratios",
            listOf(50, 50, 50, 50, 50),
            { it.multibandCompressor.ratios },
            { copy(multibandCompressor = multibandCompressor.copy(ratios = it)) },
        )
    val gains =
        intList(
            "gains",
            listOf(0, 0, 0, 0, 0),
            { it.multibandCompressor.gains },
            { copy(multibandCompressor = multibandCompressor.copy(gains = it)) },
        )
    val knees =
        intList(
            "knees",
            listOf(0, 0, 0, 0, 0),
            { it.multibandCompressor.knees },
            { copy(multibandCompressor = multibandCompressor.copy(knees = it)) },
        )
    val kneeMultis =
        intList(
            "kneeMultis",
            listOf(0, 0, 0, 0, 0),
            { it.multibandCompressor.kneeMultis },
            { copy(multibandCompressor = multibandCompressor.copy(kneeMultis = it)) },
        )
    val attacks =
        intList(
            "attacks",
            listOf(1, 1, 1, 1, 1),
            { it.multibandCompressor.attacks },
            { copy(multibandCompressor = multibandCompressor.copy(attacks = it)) },
        )
    val maxAttacks =
        intList(
            "maxAttacks",
            listOf(44, 44, 44, 44, 44),
            { it.multibandCompressor.maxAttacks },
            { copy(multibandCompressor = multibandCompressor.copy(maxAttacks = it)) },
        )
    val releases =
        intList(
            "releases",
            listOf(100, 100, 100, 100, 100),
            { it.multibandCompressor.releases },
            { copy(multibandCompressor = multibandCompressor.copy(releases = it)) },
        )
    val maxReleases =
        intList(
            "maxReleases",
            listOf(200, 200, 200, 200, 200),
            { it.multibandCompressor.maxReleases },
            { copy(multibandCompressor = multibandCompressor.copy(maxReleases = it)) },
        )
    val crests =
        intList(
            "crests",
            listOf(100, 100, 100, 100, 100),
            { it.multibandCompressor.crests },
            { copy(multibandCompressor = multibandCompressor.copy(crests = it)) },
        )
    val adapts =
        intList(
            "adapts",
            listOf(50, 50, 50, 50, 50),
            { it.multibandCompressor.adapts },
            { copy(multibandCompressor = multibandCompressor.copy(adapts = it)) },
        )
    val kneeAutos =
        boolList(
            "kneeAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.kneeAutos },
            { copy(multibandCompressor = multibandCompressor.copy(kneeAutos = it)) },
        )
    val gainAutos =
        boolList(
            "gainAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.gainAutos },
            { copy(multibandCompressor = multibandCompressor.copy(gainAutos = it)) },
        )
    val attackAutos =
        boolList(
            "attackAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.attackAutos },
            { copy(multibandCompressor = multibandCompressor.copy(attackAutos = it)) },
        )
    val releaseAutos =
        boolList(
            "releaseAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.releaseAutos },
            { copy(multibandCompressor = multibandCompressor.copy(releaseAutos = it)) },
        )
    val noClips =
        boolList(
            "noClips",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.noClips },
            { copy(multibandCompressor = multibandCompressor.copy(noClips = it)) },
        )
}

class DdcEffect : EffectGroupBuilder("ddc") {
    val enable =
        bool(
            ViperParams.kParamDdcEnable,
            "enable",
            false,
            { it.ddc.enable },
            { copy(ddc = ddc.copy(enable = it)) },
        )
    val device =
        string(
            -1,
            "device",
            "",
            { it.ddc.device },
            { copy(ddc = ddc.copy(device = it)) },
        )
}

class SpectrumExtensionEffect : EffectGroupBuilder("spectrumExtension") {
    val enable =
        bool(
            ViperParams.kParamSpectrumExtensionEnable,
            "enable",
            false,
            { it.spectrumExtension.enable },
            { copy(spectrumExtension = spectrumExtension.copy(enable = it)) },
        )
    val strength =
        int(
            ViperParams.kParamSpectrumExtensionStrength,
            "strength",
            7600,
            { it.spectrumExtension.strength },
            { copy(spectrumExtension = spectrumExtension.copy(strength = it)) },
        )
    val exciter =
        int(
            ViperParams.kParamSpectrumExtensionExciter,
            "exciter",
            0,
            { it.spectrumExtension.exciter },
            { copy(spectrumExtension = spectrumExtension.copy(exciter = it)) },
        )
}

class EqualizerEffect : EffectGroupBuilder("equalizer") {
    val enable =
        bool(
            ViperParams.kParamEqualizerEnable,
            "enable",
            false,
            { it.eq.enable },
            { copy(eq = eq.copy(enable = it)) },
        )
    val bandCount =
        int(
            ViperParams.kParamEqualizerBandCount,
            "bandCount",
            10,
            { it.eq.bandCount },
            { copy(eq = eq.copy(bandCount = it)) },
        )
    val bands =
        doubleList(
            ViperParams.kParamEqualizerBandLevel,
            "bands",
            List(10) { 0.0 },
            { it.eq.bands },
            { copy(eq = eq.copy(bands = it)) },
        )
    val presetId =
        nullableLong(
            "presetId",
            { it.eq.presetId },
            { copy(eq = eq.copy(presetId = it)) },
        )
}

class DynamicEqEffect : EffectGroupBuilder("dynamicEq") {
    val enable =
        bool(
            ViperParams.kParamDynamicEqEnable,
            "enable",
            false,
            { it.dynamicEq.enable },
            { copy(dynamicEq = dynamicEq.copy(enable = it)) },
        )
    val bandCount =
        int(
            -1,
            "bandCount",
            3,
            { it.dynamicEq.bandCount },
            { copy(dynamicEq = dynamicEq.copy(bandCount = it)) },
        )
    val freqs =
        intList(
            "freqs",
            listOf(60, 150, 400, 1000, 2500, 5000, 8000, 12000),
            { it.dynamicEq.freqs },
            { copy(dynamicEq = dynamicEq.copy(freqs = it)) },
        )
    val qs =
        intList(
            "qs",
            listOf(100, 100, 150, 150, 150, 200, 200, 200),
            { it.dynamicEq.qs },
            { copy(dynamicEq = dynamicEq.copy(qs = it)) },
        )
    val gains =
        intList(
            "gains",
            listOf(0, 0, 0, 0, 0, 0, 0, 0),
            { it.dynamicEq.gains },
            { copy(dynamicEq = dynamicEq.copy(gains = it)) },
        )
    val thresholds =
        intList(
            "thresholds",
            listOf(-300, -300, -250, -250, -200, -200, -200, -200),
            { it.dynamicEq.thresholds },
            { copy(dynamicEq = dynamicEq.copy(thresholds = it)) },
        )
    val attacks =
        intList(
            "attacks",
            listOf(10, 10, 10, 10, 10, 10, 10, 10),
            { it.dynamicEq.attacks },
            { copy(dynamicEq = dynamicEq.copy(attacks = it)) },
        )
    val releases =
        intList(
            "releases",
            listOf(100, 100, 100, 100, 100, 100, 100, 100),
            { it.dynamicEq.releases },
            { copy(dynamicEq = dynamicEq.copy(releases = it)) },
        )
    val filterTypes =
        intList(
            "filterTypes",
            listOf(0, 0, 0, 0, 0, 0, 0, 0),
            { it.dynamicEq.filterTypes },
            { copy(dynamicEq = dynamicEq.copy(filterTypes = it)) },
        )
}

class ConvolverEffect : EffectGroupBuilder("convolver") {
    val enable =
        bool(
            ViperParams.kParamConvolverEnable,
            "enable",
            false,
            { it.convolver.enable },
            { copy(convolver = convolver.copy(enable = it)) },
        )
    val kernelFile =
        string(
            -1,
            "kernelFile",
            "",
            { it.convolver.kernelFile },
            { copy(convolver = convolver.copy(kernelFile = it)) },
        )
    val crossChannel =
        int(
            ViperParams.kParamConvolverCrossChannel,
            "crossChannel",
            0,
            { it.convolver.crossChannel },
            { copy(convolver = convolver.copy(crossChannel = it)) },
        )
}

class FieldSurroundEffect : EffectGroupBuilder("fieldSurround") {
    val enable =
        bool(
            ViperParams.kParamFieldSurroundEnable,
            "enable",
            false,
            { it.fieldSurround.enable },
            { copy(fieldSurround = fieldSurround.copy(enable = it)) },
        )
    val widening =
        int(
            ViperParams.kParamFieldSurroundWidening,
            "widening",
            0,
            { it.fieldSurround.widening },
            { copy(fieldSurround = fieldSurround.copy(widening = it)) },
        )
    val midImage =
        int(
            ViperParams.kParamFieldSurroundMidImage,
            "midImage",
            5,
            { it.fieldSurround.midImage },
            { copy(fieldSurround = fieldSurround.copy(midImage = it)) },
        )
    val depth =
        int(
            ViperParams.kParamFieldSurroundDepth,
            "depth",
            0,
            { it.fieldSurround.depth },
            { copy(fieldSurround = fieldSurround.copy(depth = it)) },
        )
}

class DiffSurroundEffect : EffectGroupBuilder("diffSurround") {
    val enable =
        bool(
            ViperParams.kParamDiffSurroundEnable,
            "enable",
            false,
            { it.diffSurround.enable },
            { copy(diffSurround = diffSurround.copy(enable = it)) },
        )
    val delay =
        int(
            ViperParams.kParamDiffSurroundDelay,
            "delay",
            5,
            { it.diffSurround.delay },
            { copy(diffSurround = diffSurround.copy(delay = it)) },
        )
    val reverse =
        bool(
            ViperParams.kParamDiffSurroundReverse,
            "reverse",
            false,
            { it.diffSurround.reverse },
            { copy(diffSurround = diffSurround.copy(reverse = it)) },
        )
    val wetDryMix =
        int(
            ViperParams.kParamDiffSurroundWetDryMix,
            "wetDryMix",
            100,
            { it.diffSurround.wetDryMix },
            { copy(diffSurround = diffSurround.copy(wetDryMix = it)) },
        )
    val lpCutoff =
        int(
            ViperParams.kParamDiffSurroundLpCutoff,
            "lpCutoff",
            0,
            { it.diffSurround.lpCutoff },
            { copy(diffSurround = diffSurround.copy(lpCutoff = it)) },
        )
}

class StereoImagerEffect : EffectGroupBuilder("stereoImager") {
    val enable =
        bool(
            ViperParams.kParamStereoImagerEnable,
            "enable",
            false,
            { it.stereoImager.enable },
            { copy(stereoImager = stereoImager.copy(enable = it)) },
        )
    val lowWidth =
        int(
            ViperParams.kParamStereoImagerLowWidth,
            "lowWidth",
            100,
            { it.stereoImager.lowWidth },
            { copy(stereoImager = stereoImager.copy(lowWidth = it)) },
        )
    val midWidth =
        int(
            ViperParams.kParamStereoImagerMidWidth,
            "midWidth",
            100,
            { it.stereoImager.midWidth },
            { copy(stereoImager = stereoImager.copy(midWidth = it)) },
        )
    val highWidth =
        int(
            ViperParams.kParamStereoImagerHighWidth,
            "highWidth",
            100,
            { it.stereoImager.highWidth },
            { copy(stereoImager = stereoImager.copy(highWidth = it)) },
        )
    val lowCrossover =
        int(
            ViperParams.kParamStereoImagerLowCrossover,
            "lowCrossover",
            200,
            { it.stereoImager.lowCrossover },
            { copy(stereoImager = stereoImager.copy(lowCrossover = it)) },
        )
    val highCrossover =
        int(
            ViperParams.kParamStereoImagerHighCrossover,
            "highCrossover",
            4000,
            { it.stereoImager.highCrossover },
            { copy(stereoImager = stereoImager.copy(highCrossover = it)) },
        )
}

class HeadphoneSurroundEffect : EffectGroupBuilder("headphoneSurround") {
    val enable =
        bool(
            ViperParams.kParamHeadphoneSurroundEnable,
            "enable",
            false,
            { it.headphoneSurround.enable },
            { copy(headphoneSurround = headphoneSurround.copy(enable = it)) },
        )
    val quality =
        int(
            ViperParams.kParamHeadphoneSurroundQuality,
            "quality",
            0,
            { it.headphoneSurround.quality },
            { copy(headphoneSurround = headphoneSurround.copy(quality = it)) },
        )
}

class ReverbEffect : EffectGroupBuilder("reverb") {
    val enable =
        bool(
            ViperParams.kParamReverbEnable,
            "enable",
            false,
            { it.reverb.enable },
            { copy(reverb = reverb.copy(enable = it)) },
        )
    val roomSize =
        int(
            ViperParams.kParamReverbRoomSize,
            "roomSize",
            0,
            { it.reverb.roomSize },
            { copy(reverb = reverb.copy(roomSize = it)) },
        )
    val width =
        int(
            ViperParams.kParamReverbWidth,
            "width",
            0,
            { it.reverb.width },
            { copy(reverb = reverb.copy(width = it)) },
        )
    val damp =
        int(
            ViperParams.kParamReverbDamp,
            "damp",
            0,
            { it.reverb.damp },
            { copy(reverb = reverb.copy(damp = it)) },
        )
    val wet =
        int(
            ViperParams.kParamReverbWet,
            "wet",
            0,
            { it.reverb.wet },
            { copy(reverb = reverb.copy(wet = it)) },
        )
    val dry =
        int(
            ViperParams.kParamReverbDry,
            "dry",
            100,
            { it.reverb.dry },
            { copy(reverb = reverb.copy(dry = it)) },
        )
}

class DynamicSystemEffect : EffectGroupBuilder("dynamicSystem") {
    val enable =
        bool(
            ViperParams.kParamDynamicSystemEnable,
            "enable",
            false,
            { it.dynamicSystem.enable },
            { copy(dynamicSystem = dynamicSystem.copy(enable = it)) },
        )
    val presetId =
        nullableLong(
            "presetId",
            { it.dynamicSystem.presetId },
            { copy(dynamicSystem = dynamicSystem.copy(presetId = it)) },
        )
    val device =
        int(
            -1,
            "device",
            0,
            { it.dynamicSystem.device },
            { copy(dynamicSystem = dynamicSystem.copy(device = it)) },
        )
    val strength =
        int(
            -1,
            "strength",
            50,
            { it.dynamicSystem.strength },
            { copy(dynamicSystem = dynamicSystem.copy(strength = it)) },
        )
    val xLow =
        int(
            -1,
            "xLow",
            100,
            { it.dynamicSystem.xLow },
            { copy(dynamicSystem = dynamicSystem.copy(xLow = it)) },
        )
    val xHigh =
        int(
            -1,
            "xHigh",
            5600,
            { it.dynamicSystem.xHigh },
            { copy(dynamicSystem = dynamicSystem.copy(xHigh = it)) },
        )
    val yLow =
        int(
            -1,
            "yLow",
            40,
            { it.dynamicSystem.yLow },
            { copy(dynamicSystem = dynamicSystem.copy(yLow = it)) },
        )
    val yHigh =
        int(
            -1,
            "yHigh",
            80,
            { it.dynamicSystem.yHigh },
            { copy(dynamicSystem = dynamicSystem.copy(yHigh = it)) },
        )
    val sideGainLow =
        int(
            -1,
            "sideGainLow",
            50,
            { it.dynamicSystem.sideGainLow },
            { copy(dynamicSystem = dynamicSystem.copy(sideGainLow = it)) },
        )
    val sideGainHigh =
        int(
            -1,
            "sideGainHigh",
            50,
            { it.dynamicSystem.sideGainHigh },
            { copy(dynamicSystem = dynamicSystem.copy(sideGainHigh = it)) },
        )
}

class PsychoacousticBassEffect : EffectGroupBuilder("psychoacousticBass") {
    val enable =
        bool(
            ViperParams.kParamPsychoacousticBassEnable,
            "enable",
            false,
            { it.psychoacousticBass.enable },
            { copy(psychoacousticBass = psychoacousticBass.copy(enable = it)) },
        )
    val cutoff =
        int(
            ViperParams.kParamPsychoacousticBassCutoff,
            "cutoff",
            80,
            { it.psychoacousticBass.cutoff },
            { copy(psychoacousticBass = psychoacousticBass.copy(cutoff = it)) },
        )
    val intensity =
        int(
            ViperParams.kParamPsychoacousticBassIntensity,
            "intensity",
            50,
            { it.psychoacousticBass.intensity },
            { copy(psychoacousticBass = psychoacousticBass.copy(intensity = it)) },
        )
    val harmonicOrder =
        int(
            ViperParams.kParamPsychoacousticBassHarmonicOrder,
            "harmonicOrder",
            3,
            { it.psychoacousticBass.harmonicOrder },
            { copy(psychoacousticBass = psychoacousticBass.copy(harmonicOrder = it)) },
        )
    val originalLevel =
        int(
            ViperParams.kParamPsychoacousticBassOriginalLevel,
            "originalLevel",
            100,
            { it.psychoacousticBass.originalLevel },
            { copy(psychoacousticBass = psychoacousticBass.copy(originalLevel = it)) },
        )
}

class BassEffect : EffectGroupBuilder("bass") {
    val enable =
        bool(
            ViperParams.kParamBassEnable,
            "enable",
            false,
            { it.bass.enable },
            { copy(bass = bass.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.kParamBassMode,
            "mode",
            0,
            { it.bass.mode },
            { copy(bass = bass.copy(mode = it)) },
        )
    val frequency =
        int(
            ViperParams.kParamBassFrequency,
            "frequency",
            55,
            { it.bass.frequency },
            { copy(bass = bass.copy(frequency = it)) },
        )
    val gain =
        int(
            ViperParams.kParamBassGain,
            "gain",
            50,
            { it.bass.gain },
            { copy(bass = bass.copy(gain = it)) },
        )
    val antiPop =
        bool(
            ViperParams.kParamBassAntiPop,
            "antiPop",
            false,
            { it.bass.antiPop },
            { copy(bass = bass.copy(antiPop = it)) },
        )
}

class BassMonoEffect : EffectGroupBuilder("bassMono") {
    val enable =
        bool(
            ViperParams.kParamBassMonoEnable,
            "enable",
            false,
            { it.bassMono.enable },
            { copy(bassMono = bassMono.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.kParamBassMonoMode,
            "mode",
            0,
            { it.bassMono.mode },
            { copy(bassMono = bassMono.copy(mode = it)) },
        )
    val frequency =
        int(
            ViperParams.kParamBassMonoFrequency,
            "frequency",
            55,
            { it.bassMono.frequency },
            { copy(bassMono = bassMono.copy(frequency = it)) },
        )
    val gain =
        int(
            ViperParams.kParamBassMonoGain,
            "gain",
            50,
            { it.bassMono.gain },
            { copy(bassMono = bassMono.copy(gain = it)) },
        )
    val antiPop =
        bool(
            ViperParams.kParamBassMonoAntiPop,
            "antiPop",
            false,
            { it.bassMono.antiPop },
            { copy(bassMono = bassMono.copy(antiPop = it)) },
        )
}

class ClarityEffect : EffectGroupBuilder("clarity") {
    val enable =
        bool(
            ViperParams.kParamClarityEnable,
            "enable",
            false,
            { it.clarity.enable },
            { copy(clarity = clarity.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.kParamClarityMode,
            "mode",
            0,
            { it.clarity.mode },
            { copy(clarity = clarity.copy(mode = it)) },
        )
    val gain =
        int(
            ViperParams.kParamClarityGain,
            "gain",
            50,
            { it.clarity.gain },
            { copy(clarity = clarity.copy(gain = it)) },
        )
}

class CureEffect : EffectGroupBuilder("cure") {
    val enable =
        bool(
            ViperParams.kParamCureEnable,
            "enable",
            false,
            { it.cure.enable },
            { copy(cure = cure.copy(enable = it)) },
        )
    val crossfeedPreset =
        int(
            ViperParams.kParamCureCrossfeedPreset,
            "crossfeedPreset",
            0,
            { it.cure.crossfeedPreset },
            { copy(cure = cure.copy(crossfeedPreset = it)) },
        )
}

class TubeSimulatorEffect : EffectGroupBuilder("tubeSimulator") {
    val enable =
        bool(
            ViperParams.kParamTubeSimulatorEnable,
            "enable",
            false,
            { it.tubeSimulator.enable },
            { copy(tubeSimulator = tubeSimulator.copy(enable = it)) },
        )
}

class AnalogXEffect : EffectGroupBuilder("analogX") {
    val enable =
        bool(
            ViperParams.kParamAnalogXEnable,
            "enable",
            false,
            { it.analogX.enable },
            { copy(analogX = analogX.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.kParamAnalogXMode,
            "mode",
            0,
            { it.analogX.mode },
            { copy(analogX = analogX.copy(mode = it)) },
        )
}

class SpeakerCorrectionEffect : EffectGroupBuilder("speakerCorrection") {
    val enable =
        bool(
            ViperParams.kParamSpeakerCorrectionEnable,
            "enable",
            false,
            { it.speakerCorrection.enable },
            { copy(speakerCorrection = speakerCorrection.copy(enable = it)) },
        )
}

object Effects {
    val masterEnable: BoolPref =
        BoolPref(
            effectKey = "",
            paramId = -1,
            jsonKey = "masterEnable",
            defaultValue = false,
            get = { it.masterEnable },
            set = { copy(masterEnable = it) },
        )

    val masterLimiter = MasterLimiterEffect()
    val playbackGainControl = PlaybackGainControlEffect()
    val lufs = LufsEffect()
    val fetCompressor = FetCompressorEffect()
    val multibandCompressor = MultibandCompressorEffect()
    val ddc = DdcEffect()
    val spectrumExtension = SpectrumExtensionEffect()
    val equalizer = EqualizerEffect()
    val dynamicEq = DynamicEqEffect()
    val convolver = ConvolverEffect()
    val fieldSurround = FieldSurroundEffect()
    val diffSurround = DiffSurroundEffect()
    val stereoImager = StereoImagerEffect()
    val headphoneSurround = HeadphoneSurroundEffect()
    val reverb = ReverbEffect()
    val dynamicSystem = DynamicSystemEffect()
    val psychoacousticBass = PsychoacousticBassEffect()
    val bass = BassEffect()
    val bassMono = BassMonoEffect()
    val clarity = ClarityEffect()
    val cure = CureEffect()
    val tubeSimulator = TubeSimulatorEffect()
    val analogX = AnalogXEffect()
    val speakerCorrection = SpeakerCorrectionEffect()
}

val EFFECT_GROUPS: List<EffectGroup> =
    listOf(
        Effects.masterLimiter,
        Effects.playbackGainControl,
        Effects.lufs,
        Effects.fetCompressor,
        Effects.multibandCompressor,
        Effects.ddc,
        Effects.spectrumExtension,
        Effects.equalizer,
        Effects.dynamicEq,
        Effects.convolver,
        Effects.fieldSurround,
        Effects.diffSurround,
        Effects.stereoImager,
        Effects.headphoneSurround,
        Effects.reverb,
        Effects.dynamicSystem,
        Effects.psychoacousticBass,
        Effects.bass,
        Effects.bassMono,
        Effects.clarity,
        Effects.cure,
        Effects.tubeSimulator,
        Effects.analogX,
        Effects.speakerCorrection,
    ).map { it.toGroup() }

val EFFECT_PREFS: List<EffectPref<*>> =
    listOf(Effects.masterEnable) + EFFECT_GROUPS.flatMap { it.prefs }

val EFFECT_PREFS_BY_PARAM_ID: Map<Int, EffectPref<*>> =
    EFFECT_PREFS.filter { it.paramId != -1 }.associateBy { it.paramId }

private fun spJoinInts(list: List<Int>): String = list.joinToString(";")

private fun spSplitInts(
    s: String,
    default: List<Int>,
): List<Int> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.mapNotNull { it.toIntOrNull() }
}

private fun spJoinBools(list: List<Boolean>): String = list.joinToString(";") { if (it) "1" else "0" }

private fun spSplitBools(
    s: String,
    default: List<Boolean>,
): List<Boolean> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.map { it == "1" }
}

private fun spJoinDoubles(list: List<Double>): String = list.joinToString(";") { String.format(java.util.Locale.US, "%.1f", it) }

private fun spSplitDoubles(
    s: String,
    default: List<Double>,
): List<Double> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.mapNotNull { it.toDoubleOrNull() }
}

suspend fun loadEffectPrefs(
    repository: ViperRepository,
    state: MainUiState = MainUiState(),
): MainUiState {
    var s = state
    for (pref in EFFECT_PREFS) {
        s =
            when (pref) {
                is IntPref -> {
                    pref.set(s, repository.getIntPreference(pref.prefKey, pref.defaultValue).first())
                }

                is BoolPref -> {
                    pref.set(s, repository.getBooleanPreference(pref.prefKey, pref.defaultValue).first())
                }

                is StringPref -> {
                    pref.set(s, repository.getStringPreference(pref.prefKey, pref.defaultValue).first())
                }

                is NullableLongPref -> {
                    val raw = repository.getIntPreference(pref.prefKey, -1).first()
                    pref.set(s, if (raw < 0) null else raw.toLong())
                }

                is IntListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinInts(pref.defaultValue)).first()
                    pref.set(s, spSplitInts(raw, pref.defaultValue))
                }

                is BoolListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinBools(pref.defaultValue)).first()
                    pref.set(s, spSplitBools(raw, pref.defaultValue))
                }

                is DoubleListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinDoubles(pref.defaultValue)).first()
                    pref.set(s, spSplitDoubles(raw, pref.defaultValue))
                }
            }
    }
    return s
}

suspend fun saveEffectPrefs(
    repository: ViperRepository,
    state: MainUiState,
) {
    for (pref in EFFECT_PREFS) {
        when (pref) {
            is IntPref -> repository.setIntPreference(pref.prefKey, pref.get(state))
            is BoolPref -> repository.setBooleanPreference(pref.prefKey, pref.get(state))
            is StringPref -> repository.setStringPreference(pref.prefKey, pref.get(state))
            is NullableLongPref -> repository.setIntPreference(pref.prefKey, pref.get(state)?.toInt() ?: -1)
            is IntListPref -> repository.setStringPreference(pref.prefKey, spJoinInts(pref.get(state)))
            is BoolListPref -> repository.setStringPreference(pref.prefKey, spJoinBools(pref.get(state)))
            is DoubleListPref -> repository.setStringPreference(pref.prefKey, spJoinDoubles(pref.get(state)))
        }
    }
}

const val PRESET_SCHEMA_VERSION = 2
private const val KEY_SCHEMA_VERSION = "schemaVersion"
private const val KEY_NAME = "name"
private const val KEY_CREATED_AT = "createdAt"

fun serializeEffectPrefs(state: MainUiState): JSONObject = serializeEffectPrefs(state, name = null, createdAt = null)

fun serializeEffectPrefs(
    state: MainUiState,
    name: String?,
    createdAt: Long?,
): JSONObject {
    val root = JSONObject()
    if (name != null || createdAt != null) {
        root.put(KEY_SCHEMA_VERSION, PRESET_SCHEMA_VERSION)
        if (name != null) root.put(KEY_NAME, name)
        if (createdAt != null) root.put(KEY_CREATED_AT, createdAt)
    }
    putPrefValue(root, Effects.masterEnable, state)
    for (group in EFFECT_GROUPS) {
        val obj = JSONObject()
        for (pref in group.prefs) {
            putPrefValue(obj, pref, state)
        }
        root.put(group.effectKey, obj)
    }
    return root
}

private fun putPrefValue(
    obj: JSONObject,
    pref: EffectPref<*>,
    state: MainUiState,
) {
    when (pref) {
        is IntPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is BoolPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is StringPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is NullableLongPref -> {
            val v = pref.get(state)
            if (v == null) obj.put(pref.jsonKey, JSONObject.NULL) else obj.put(pref.jsonKey, v)
        }

        is IntListPref -> {
            val arr = org.json.JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }

        is BoolListPref -> {
            val arr = org.json.JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }

        is DoubleListPref -> {
            val arr = org.json.JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }
    }
}

fun deserializeEffectPrefs(
    obj: JSONObject,
    state: MainUiState,
): MainUiState {
    var s = state
    s = applyPrefFromJson(s, Effects.masterEnable, obj)
    for (group in EFFECT_GROUPS) {
        val sub = obj.optJSONObject(group.effectKey) ?: continue
        for (pref in group.prefs) {
            s = applyPrefFromJson(s, pref, sub)
        }
    }
    return s
}

private fun applyPrefFromJson(
    state: MainUiState,
    pref: EffectPref<*>,
    obj: JSONObject,
): MainUiState {
    if (!obj.has(pref.jsonKey)) return state
    return when (pref) {
        is IntPref -> {
            pref.set(state, obj.optInt(pref.jsonKey, pref.get(state)))
        }

        is BoolPref -> {
            pref.set(state, obj.optBoolean(pref.jsonKey, pref.get(state)))
        }

        is StringPref -> {
            pref.set(state, obj.optString(pref.jsonKey, pref.get(state)))
        }

        is NullableLongPref -> {
            val v =
                if (obj.isNull(pref.jsonKey)) {
                    null
                } else {
                    val raw = obj.optInt(pref.jsonKey, -1)
                    if (raw < 0) null else raw.toLong()
                }
            pref.set(state, v)
        }

        is IntListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Int>()
            for (i in 0 until arr.length()) list.add(arr.optInt(i, 0))
            pref.set(state, list.toList())
        }

        is BoolListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Boolean>()
            for (i in 0 until arr.length()) list.add(arr.optBoolean(i, false))
            pref.set(state, list.toList())
        }

        is DoubleListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Double>()
            for (i in 0 until arr.length()) list.add(arr.optDouble(i, 0.0))
            pref.set(state, list.toList())
        }
    }
}

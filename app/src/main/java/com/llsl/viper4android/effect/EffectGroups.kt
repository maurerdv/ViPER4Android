package com.llsl.viper4android.effect

import com.llsl.viper4android.viper.ViperParams

data class EffectGroup(
    val effectKey: String,
    val prefs: List<EffectPref<*>>,
)

abstract class EffectGroupBuilder(
    val effectKey: String,
) {
    private val prefList = mutableListOf<EffectPref<*>>()

    protected fun int(
        paramId: Int,
        jsonKey: String,
        default: Int,
        get: (EffectState) -> Int,
        set: EffectState.(Int) -> EffectState,
    ): IntPref {
        val pref = IntPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun bool(
        paramId: Int,
        jsonKey: String,
        default: Boolean,
        get: (EffectState) -> Boolean,
        set: EffectState.(Boolean) -> EffectState,
    ): BoolPref {
        val pref = BoolPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun string(
        paramId: Int,
        jsonKey: String,
        default: String,
        get: (EffectState) -> String,
        set: EffectState.(String) -> EffectState,
    ): StringPref {
        val pref = StringPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun nullableLong(
        jsonKey: String,
        get: (EffectState) -> Long?,
        set: EffectState.(Long?) -> EffectState,
    ): NullableLongPref {
        val pref = NullableLongPref(effectKey, jsonKey, get, set)
        prefList += pref
        return pref
    }

    protected fun intList(
        jsonKey: String,
        default: List<Int>,
        get: (EffectState) -> List<Int>,
        set: EffectState.(List<Int>) -> EffectState,
    ): IntListPref {
        val pref = IntListPref(effectKey, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun boolList(
        jsonKey: String,
        default: List<Boolean>,
        get: (EffectState) -> List<Boolean>,
        set: EffectState.(List<Boolean>) -> EffectState,
    ): BoolListPref {
        val pref = BoolListPref(effectKey, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun doubleList(
        paramId: Int,
        jsonKey: String,
        default: List<Double>,
        get: (EffectState) -> List<Double>,
        set: EffectState.(List<Double>) -> EffectState,
    ): DoubleListPref {
        val pref = DoubleListPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    fun toGroup(): EffectGroup = EffectGroup(effectKey, prefList.toList())
}

class MasterLimiterEffect : EffectGroupBuilder("masterLimiter") {
    val threshold =
        int(
            ViperParams.PARAM_MASTER_LIMITER_THRESHOLD,
            "threshold",
            100,
            { it.out.limiter },
            { copy(out = out.copy(limiter = it)) },
        )
    val outputVolume =
        int(
            ViperParams.PARAM_MASTER_LIMITER_OUTPUT_VOLUME,
            "outputVolume",
            100,
            { it.out.volume },
            { copy(out = out.copy(volume = it)) },
        )
    val channelPan =
        int(
            ViperParams.PARAM_MASTER_LIMITER_CHANNEL_PAN,
            "channelPan",
            0,
            { it.out.channelPan },
            { copy(out = out.copy(channelPan = it)) },
        )
}

class PlaybackGainControlEffect : EffectGroupBuilder("playbackGainControl") {
    val enable =
        bool(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_ENABLE,
            "enable",
            false,
            { it.playbackGainControl.enable },
            { copy(playbackGainControl = playbackGainControl.copy(enable = it)) },
        )
    val strength =
        int(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_STRENGTH,
            "strength",
            100,
            { it.playbackGainControl.strength },
            { copy(playbackGainControl = playbackGainControl.copy(strength = it)) },
        )
    val maxGain =
        int(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_MAX_GAIN,
            "maxGain",
            100,
            { it.playbackGainControl.maxGain },
            { copy(playbackGainControl = playbackGainControl.copy(maxGain = it)) },
        )
    val outputThreshold =
        int(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_OUTPUT_THRESHOLD,
            "outputThreshold",
            100,
            { it.playbackGainControl.outputThreshold },
            { copy(playbackGainControl = playbackGainControl.copy(outputThreshold = it)) },
        )
}

class LufsEffect : EffectGroupBuilder("lufs") {
    val enable =
        bool(
            ViperParams.PARAM_LUFS_ENABLE,
            "enable",
            false,
            { it.lufs.enable },
            { copy(lufs = lufs.copy(enable = it)) },
        )
    val target =
        int(
            ViperParams.PARAM_LUFS_TARGET,
            "target",
            140,
            { it.lufs.target },
            { copy(lufs = lufs.copy(target = it)) },
        )
    val maxGain =
        int(
            ViperParams.PARAM_LUFS_MAX_GAIN,
            "maxGain",
            60,
            { it.lufs.maxGain },
            { copy(lufs = lufs.copy(maxGain = it)) },
        )
    val speed =
        int(
            ViperParams.PARAM_LUFS_SPEED,
            "speed",
            1,
            { it.lufs.speed },
            { copy(lufs = lufs.copy(speed = it)) },
        )
}

class FetCompressorEffect : EffectGroupBuilder("fetCompressor") {
    val enable =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_ENABLE,
            "enable",
            false,
            { it.fetCompressor.enable },
            { copy(fetCompressor = fetCompressor.copy(enable = it)) },
        )
    val threshold =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_THRESHOLD,
            "threshold",
            -18,
            { it.fetCompressor.threshold },
            { copy(fetCompressor = fetCompressor.copy(threshold = it)) },
        )
    val ratio =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_RATIO,
            "ratio",
            100,
            { it.fetCompressor.ratio },
            { copy(fetCompressor = fetCompressor.copy(ratio = it)) },
        )
    val kneeAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_KNEE_AUTO,
            "kneeAuto",
            true,
            { it.fetCompressor.kneeAuto },
            { copy(fetCompressor = fetCompressor.copy(kneeAuto = it)) },
        )
    val knee =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_KNEE,
            "knee",
            0,
            { it.fetCompressor.knee },
            { copy(fetCompressor = fetCompressor.copy(knee = it)) },
        )
    val kneeMulti =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_KNEE_MULTI,
            "kneeMulti",
            0,
            { it.fetCompressor.kneeMulti },
            { copy(fetCompressor = fetCompressor.copy(kneeMulti = it)) },
        )
    val gainAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_GAIN_AUTO,
            "gainAuto",
            true,
            { it.fetCompressor.gainAuto },
            { copy(fetCompressor = fetCompressor.copy(gainAuto = it)) },
        )
    val gain =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_GAIN,
            "gain",
            0,
            { it.fetCompressor.gain },
            { copy(fetCompressor = fetCompressor.copy(gain = it)) },
        )
    val attackAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_ATTACK_AUTO,
            "attackAuto",
            true,
            { it.fetCompressor.attackAuto },
            { copy(fetCompressor = fetCompressor.copy(attackAuto = it)) },
        )
    val attack =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_ATTACK,
            "attack",
            20,
            { it.fetCompressor.attack },
            { copy(fetCompressor = fetCompressor.copy(attack = it)) },
        )
    val maxAttack =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_MAX_ATTACK,
            "maxAttack",
            80,
            { it.fetCompressor.maxAttack },
            { copy(fetCompressor = fetCompressor.copy(maxAttack = it)) },
        )
    val releaseAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_RELEASE_AUTO,
            "releaseAuto",
            true,
            { it.fetCompressor.releaseAuto },
            { copy(fetCompressor = fetCompressor.copy(releaseAuto = it)) },
        )
    val release =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_RELEASE,
            "release",
            50,
            { it.fetCompressor.release },
            { copy(fetCompressor = fetCompressor.copy(release = it)) },
        )
    val maxRelease =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_MAX_RELEASE,
            "maxRelease",
            100,
            { it.fetCompressor.maxRelease },
            { copy(fetCompressor = fetCompressor.copy(maxRelease = it)) },
        )
    val crest =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_CREST,
            "crest",
            100,
            { it.fetCompressor.crest },
            { copy(fetCompressor = fetCompressor.copy(crest = it)) },
        )
    val adapt =
        int(
            ViperParams.PARAM_FET_COMPRESSOR_ADAPT,
            "adapt",
            50,
            { it.fetCompressor.adapt },
            { copy(fetCompressor = fetCompressor.copy(adapt = it)) },
        )
    val noClip =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_NO_CLIP,
            "noClip",
            true,
            { it.fetCompressor.noClip },
            { copy(fetCompressor = fetCompressor.copy(noClip = it)) },
        )
}

class MultibandCompressorEffect : EffectGroupBuilder("multibandCompressor") {
    val enable =
        bool(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_ENABLE,
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
            ViperParams.PARAM_DDC_ENABLE,
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
            ViperParams.PARAM_SPECTRUM_EXTENSION_ENABLE,
            "enable",
            false,
            { it.spectrumExtension.enable },
            { copy(spectrumExtension = spectrumExtension.copy(enable = it)) },
        )
    val strength =
        int(
            ViperParams.PARAM_SPECTRUM_EXTENSION_STRENGTH,
            "strength",
            7600,
            { it.spectrumExtension.strength },
            { copy(spectrumExtension = spectrumExtension.copy(strength = it)) },
        )
    val exciter =
        int(
            ViperParams.PARAM_SPECTRUM_EXTENSION_EXCITER,
            "exciter",
            0,
            { it.spectrumExtension.exciter },
            { copy(spectrumExtension = spectrumExtension.copy(exciter = it)) },
        )
}

class EqualizerEffect : EffectGroupBuilder("equalizer") {
    val enable =
        bool(
            ViperParams.PARAM_EQUALIZER_ENABLE,
            "enable",
            false,
            { it.eq.enable },
            { copy(eq = eq.copy(enable = it)) },
        )
    val bandCount =
        int(
            ViperParams.PARAM_EQUALIZER_BAND_COUNT,
            "bandCount",
            10,
            { it.eq.bandCount },
            { copy(eq = eq.copy(bandCount = it)) },
        )
    val bands =
        doubleList(
            ViperParams.PARAM_EQUALIZER_BAND_LEVEL,
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
            ViperParams.PARAM_DYNAMIC_EQ_ENABLE,
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
            ViperParams.PARAM_CONVOLVER_ENABLE,
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
            ViperParams.PARAM_CONVOLVER_CROSS_CHANNEL,
            "crossChannel",
            0,
            { it.convolver.crossChannel },
            { copy(convolver = convolver.copy(crossChannel = it)) },
        )
}

class FieldSurroundEffect : EffectGroupBuilder("fieldSurround") {
    val enable =
        bool(
            ViperParams.PARAM_FIELD_SURROUND_ENABLE,
            "enable",
            false,
            { it.fieldSurround.enable },
            { copy(fieldSurround = fieldSurround.copy(enable = it)) },
        )
    val widening =
        int(
            ViperParams.PARAM_FIELD_SURROUND_WIDENING,
            "widening",
            0,
            { it.fieldSurround.widening },
            { copy(fieldSurround = fieldSurround.copy(widening = it)) },
        )
    val midImage =
        int(
            ViperParams.PARAM_FIELD_SURROUND_MID_IMAGE,
            "midImage",
            5,
            { it.fieldSurround.midImage },
            { copy(fieldSurround = fieldSurround.copy(midImage = it)) },
        )
    val depth =
        int(
            ViperParams.PARAM_FIELD_SURROUND_DEPTH,
            "depth",
            0,
            { it.fieldSurround.depth },
            { copy(fieldSurround = fieldSurround.copy(depth = it)) },
        )
}

class DiffSurroundEffect : EffectGroupBuilder("diffSurround") {
    val enable =
        bool(
            ViperParams.PARAM_DIFF_SURROUND_ENABLE,
            "enable",
            false,
            { it.diffSurround.enable },
            { copy(diffSurround = diffSurround.copy(enable = it)) },
        )
    val delay =
        int(
            ViperParams.PARAM_DIFF_SURROUND_DELAY,
            "delay",
            5,
            { it.diffSurround.delay },
            { copy(diffSurround = diffSurround.copy(delay = it)) },
        )
    val reverse =
        bool(
            ViperParams.PARAM_DIFF_SURROUND_REVERSE,
            "reverse",
            false,
            { it.diffSurround.reverse },
            { copy(diffSurround = diffSurround.copy(reverse = it)) },
        )
    val wetDryMix =
        int(
            ViperParams.PARAM_DIFF_SURROUND_WET_DRY_MIX,
            "wetDryMix",
            100,
            { it.diffSurround.wetDryMix },
            { copy(diffSurround = diffSurround.copy(wetDryMix = it)) },
        )
    val lpCutoff =
        int(
            ViperParams.PARAM_DIFF_SURROUND_LP_CUTOFF,
            "lpCutoff",
            0,
            { it.diffSurround.lpCutoff },
            { copy(diffSurround = diffSurround.copy(lpCutoff = it)) },
        )
}

class StereoImagerEffect : EffectGroupBuilder("stereoImager") {
    val enable =
        bool(
            ViperParams.PARAM_STEREO_IMAGER_ENABLE,
            "enable",
            false,
            { it.stereoImager.enable },
            { copy(stereoImager = stereoImager.copy(enable = it)) },
        )
    val lowWidth =
        int(
            ViperParams.PARAM_STEREO_IMAGER_LOW_WIDTH,
            "lowWidth",
            100,
            { it.stereoImager.lowWidth },
            { copy(stereoImager = stereoImager.copy(lowWidth = it)) },
        )
    val midWidth =
        int(
            ViperParams.PARAM_STEREO_IMAGER_MID_WIDTH,
            "midWidth",
            100,
            { it.stereoImager.midWidth },
            { copy(stereoImager = stereoImager.copy(midWidth = it)) },
        )
    val highWidth =
        int(
            ViperParams.PARAM_STEREO_IMAGER_HIGH_WIDTH,
            "highWidth",
            100,
            { it.stereoImager.highWidth },
            { copy(stereoImager = stereoImager.copy(highWidth = it)) },
        )
    val lowCrossover =
        int(
            ViperParams.PARAM_STEREO_IMAGER_LOW_CROSSOVER,
            "lowCrossover",
            200,
            { it.stereoImager.lowCrossover },
            { copy(stereoImager = stereoImager.copy(lowCrossover = it)) },
        )
    val highCrossover =
        int(
            ViperParams.PARAM_STEREO_IMAGER_HIGH_CROSSOVER,
            "highCrossover",
            4000,
            { it.stereoImager.highCrossover },
            { copy(stereoImager = stereoImager.copy(highCrossover = it)) },
        )
}

class HeadphoneSurroundEffect : EffectGroupBuilder("headphoneSurround") {
    val enable =
        bool(
            ViperParams.PARAM_HEADPHONE_SURROUND_ENABLE,
            "enable",
            false,
            { it.headphoneSurround.enable },
            { copy(headphoneSurround = headphoneSurround.copy(enable = it)) },
        )
    val quality =
        int(
            ViperParams.PARAM_HEADPHONE_SURROUND_QUALITY,
            "quality",
            0,
            { it.headphoneSurround.quality },
            { copy(headphoneSurround = headphoneSurround.copy(quality = it)) },
        )
}

class ReverbEffect : EffectGroupBuilder("reverb") {
    val enable =
        bool(
            ViperParams.PARAM_REVERB_ENABLE,
            "enable",
            false,
            { it.reverb.enable },
            { copy(reverb = reverb.copy(enable = it)) },
        )
    val roomSize =
        int(
            ViperParams.PARAM_REVERB_ROOM_SIZE,
            "roomSize",
            0,
            { it.reverb.roomSize },
            { copy(reverb = reverb.copy(roomSize = it)) },
        )
    val width =
        int(
            ViperParams.PARAM_REVERB_WIDTH,
            "width",
            0,
            { it.reverb.width },
            { copy(reverb = reverb.copy(width = it)) },
        )
    val damp =
        int(
            ViperParams.PARAM_REVERB_DAMP,
            "damp",
            0,
            { it.reverb.damp },
            { copy(reverb = reverb.copy(damp = it)) },
        )
    val wet =
        int(
            ViperParams.PARAM_REVERB_WET,
            "wet",
            0,
            { it.reverb.wet },
            { copy(reverb = reverb.copy(wet = it)) },
        )
    val dry =
        int(
            ViperParams.PARAM_REVERB_DRY,
            "dry",
            100,
            { it.reverb.dry },
            { copy(reverb = reverb.copy(dry = it)) },
        )
}

class DynamicSystemEffect : EffectGroupBuilder("dynamicSystem") {
    val enable =
        bool(
            ViperParams.PARAM_DYNAMIC_SYSTEM_ENABLE,
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
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_ENABLE,
            "enable",
            false,
            { it.psychoacousticBass.enable },
            { copy(psychoacousticBass = psychoacousticBass.copy(enable = it)) },
        )
    val cutoff =
        int(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_CUTOFF,
            "cutoff",
            80,
            { it.psychoacousticBass.cutoff },
            { copy(psychoacousticBass = psychoacousticBass.copy(cutoff = it)) },
        )
    val intensity =
        int(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_INTENSITY,
            "intensity",
            50,
            { it.psychoacousticBass.intensity },
            { copy(psychoacousticBass = psychoacousticBass.copy(intensity = it)) },
        )
    val harmonicOrder =
        int(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_HARMONIC_ORDER,
            "harmonicOrder",
            3,
            { it.psychoacousticBass.harmonicOrder },
            { copy(psychoacousticBass = psychoacousticBass.copy(harmonicOrder = it)) },
        )
    val originalLevel =
        int(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_ORIGINAL_LEVEL,
            "originalLevel",
            100,
            { it.psychoacousticBass.originalLevel },
            { copy(psychoacousticBass = psychoacousticBass.copy(originalLevel = it)) },
        )
}

class BassEffect : EffectGroupBuilder("bass") {
    val enable =
        bool(
            ViperParams.PARAM_BASS_ENABLE,
            "enable",
            false,
            { it.bass.enable },
            { copy(bass = bass.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_BASS_MODE,
            "mode",
            0,
            { it.bass.mode },
            { copy(bass = bass.copy(mode = it)) },
        )
    val frequency =
        int(
            ViperParams.PARAM_BASS_FREQUENCY,
            "frequency",
            55,
            { it.bass.frequency },
            { copy(bass = bass.copy(frequency = it)) },
        )
    val gain =
        int(
            ViperParams.PARAM_BASS_GAIN,
            "gain",
            50,
            { it.bass.gain },
            { copy(bass = bass.copy(gain = it)) },
        )
    val antiPop =
        bool(
            ViperParams.PARAM_BASS_ANTI_POP,
            "antiPop",
            false,
            { it.bass.antiPop },
            { copy(bass = bass.copy(antiPop = it)) },
        )
}

class BassMonoEffect : EffectGroupBuilder("bassMono") {
    val enable =
        bool(
            ViperParams.PARAM_BASS_MONO_ENABLE,
            "enable",
            false,
            { it.bassMono.enable },
            { copy(bassMono = bassMono.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_BASS_MONO_MODE,
            "mode",
            0,
            { it.bassMono.mode },
            { copy(bassMono = bassMono.copy(mode = it)) },
        )
    val frequency =
        int(
            ViperParams.PARAM_BASS_MONO_FREQUENCY,
            "frequency",
            55,
            { it.bassMono.frequency },
            { copy(bassMono = bassMono.copy(frequency = it)) },
        )
    val gain =
        int(
            ViperParams.PARAM_BASS_MONO_GAIN,
            "gain",
            50,
            { it.bassMono.gain },
            { copy(bassMono = bassMono.copy(gain = it)) },
        )
    val antiPop =
        bool(
            ViperParams.PARAM_BASS_MONO_ANTI_POP,
            "antiPop",
            false,
            { it.bassMono.antiPop },
            { copy(bassMono = bassMono.copy(antiPop = it)) },
        )
}

class ClarityEffect : EffectGroupBuilder("clarity") {
    val enable =
        bool(
            ViperParams.PARAM_CLARITY_ENABLE,
            "enable",
            false,
            { it.clarity.enable },
            { copy(clarity = clarity.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_CLARITY_MODE,
            "mode",
            0,
            { it.clarity.mode },
            { copy(clarity = clarity.copy(mode = it)) },
        )
    val gain =
        int(
            ViperParams.PARAM_CLARITY_GAIN,
            "gain",
            50,
            { it.clarity.gain },
            { copy(clarity = clarity.copy(gain = it)) },
        )
}

class CureEffect : EffectGroupBuilder("cure") {
    val enable =
        bool(
            ViperParams.PARAM_CURE_ENABLE,
            "enable",
            false,
            { it.cure.enable },
            { copy(cure = cure.copy(enable = it)) },
        )
    val crossfeedPreset =
        int(
            ViperParams.PARAM_CURE_CROSSFEED_PRESET,
            "crossfeedPreset",
            0,
            { it.cure.crossfeedPreset },
            { copy(cure = cure.copy(crossfeedPreset = it)) },
        )
}

class TubeSimulatorEffect : EffectGroupBuilder("tubeSimulator") {
    val enable =
        bool(
            ViperParams.PARAM_TUBE_SIMULATOR_ENABLE,
            "enable",
            false,
            { it.tubeSimulator.enable },
            { copy(tubeSimulator = tubeSimulator.copy(enable = it)) },
        )
}

class AnalogXEffect : EffectGroupBuilder("analogX") {
    val enable =
        bool(
            ViperParams.PARAM_ANALOG_X_ENABLE,
            "enable",
            false,
            { it.analogX.enable },
            { copy(analogX = analogX.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_ANALOG_X_MODE,
            "mode",
            0,
            { it.analogX.mode },
            { copy(analogX = analogX.copy(mode = it)) },
        )
}

class SpeakerCorrectionEffect : EffectGroupBuilder("speakerCorrection") {
    val enable =
        bool(
            ViperParams.PARAM_SPEAKER_CORRECTION_ENABLE,
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

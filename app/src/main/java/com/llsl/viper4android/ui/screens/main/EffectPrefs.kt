package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.repository.ViperRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject

sealed class EffectPref<T>(
    val hpPrefKey: String,
    val spkPrefKey: String,
    val jsonKey: String,
    val spkJsonKey: String,
    val defaultValue: T,
    val getHp: (MainUiState) -> T,
    val setHp: MainUiState.(T) -> MainUiState,
    val getSpk: (MainUiState) -> T,
    val setSpk: MainUiState.(T) -> MainUiState
)

class IntPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    defaultValue: Int,
    getHp: (MainUiState) -> Int,
    setHp: MainUiState.(Int) -> MainUiState,
    getSp: (MainUiState) -> Int,
    setSp: MainUiState.(Int) -> MainUiState
) : EffectPref<Int>(
    hpPrefKey,
    spkPrefKey,
    jsonKey,
    spkJsonKey,
    defaultValue,
    getHp,
    setHp,
    getSp,
    setSp
)

class BoolPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    defaultValue: Boolean,
    getHp: (MainUiState) -> Boolean,
    setHp: MainUiState.(Boolean) -> MainUiState,
    getSp: (MainUiState) -> Boolean,
    setSp: MainUiState.(Boolean) -> MainUiState
) : EffectPref<Boolean>(
    hpPrefKey,
    spkPrefKey,
    jsonKey,
    spkJsonKey,
    defaultValue,
    getHp,
    setHp,
    getSp,
    setSp
)

class StringPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    defaultValue: String,
    getHp: (MainUiState) -> String,
    setHp: MainUiState.(String) -> MainUiState,
    getSp: (MainUiState) -> String,
    setSp: MainUiState.(String) -> MainUiState
) : EffectPref<String>(
    hpPrefKey,
    spkPrefKey,
    jsonKey,
    spkJsonKey,
    defaultValue,
    getHp,
    setHp,
    getSp,
    setSp
)

class NullableLongPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    getHp: (MainUiState) -> Long?,
    setHp: MainUiState.(Long?) -> MainUiState,
    getSp: (MainUiState) -> Long?,
    setSp: MainUiState.(Long?) -> MainUiState
) : EffectPref<Long?>(hpPrefKey, spkPrefKey, jsonKey, spkJsonKey, null, getHp, setHp, getSp, setSp)

val EFFECT_PREFS: List<EffectPref<*>> = listOf(
    BoolPref(
        hpPrefKey = ViperRepository.PREF_MASTER_ENABLE,
        spkPrefKey = "spk_${ViperRepository.PREF_MASTER_ENABLE}",
        jsonKey = "masterEnabled", spkJsonKey = "spkMasterEnabled",
        defaultValue = false,
        getHp = { it.masterEnabled }, setHp = { copy(masterEnabled = it) },
        getSp = { it.spkMasterEnabled }, setSp = { copy(spkMasterEnabled = it) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_OUTPUT_VOLUME}",
        spkPrefKey = "${ViperParams.PARAM_SPK_OUTPUT_VOLUME}",
        jsonKey = "outputVolume", spkJsonKey = "spkOutputVolume",
        defaultValue = 11,
        getHp = { it.out.volume }, setHp = { copy(out = out.copy(volume = it)) },
        getSp = { it.out.spkVolume }, setSp = { copy(out = out.copy(spkVolume = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CHANNEL_PAN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CHANNEL_PAN}",
        jsonKey = "channelPan", spkJsonKey = "spkChannelPan",
        defaultValue = 0,
        getHp = { it.out.channelPan }, setHp = { copy(out = out.copy(channelPan = it)) },
        getSp = { it.out.spkChannelPan }, setSp = { copy(out = out.copy(spkChannelPan = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_LIMITER}",
        spkPrefKey = "${ViperParams.PARAM_SPK_LIMITER}",
        jsonKey = "limiter", spkJsonKey = "spkLimiter",
        defaultValue = 5,
        getHp = { it.out.limiter }, setHp = { copy(out = out.copy(limiter = it)) },
        getSp = { it.out.spkLimiter }, setSp = { copy(out = out.copy(spkLimiter = it)) }
    ),

    // AGC
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_ENABLE}",
        jsonKey = "agcEnabled", spkJsonKey = "spkAgcEnabled",
        defaultValue = false,
        getHp = { it.agc.enabled }, setHp = { copy(agc = agc.copy(enabled = it)) },
        getSp = { it.agc.spkEnabled }, setSp = { copy(agc = agc.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_RATIO}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_RATIO}",
        jsonKey = "agcStrength", spkJsonKey = "spkAgcStrength",
        defaultValue = 0,
        getHp = { it.agc.strength }, setHp = { copy(agc = agc.copy(strength = it)) },
        getSp = { it.agc.spkStrength }, setSp = { copy(agc = agc.copy(spkStrength = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_MAX_SCALER}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_MAX_SCALER}",
        jsonKey = "agcMaxGain", spkJsonKey = "spkAgcMaxGain",
        defaultValue = 3,
        getHp = { it.agc.maxGain }, setHp = { copy(agc = agc.copy(maxGain = it)) },
        getSp = { it.agc.spkMaxGain }, setSp = { copy(agc = agc.copy(spkMaxGain = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_VOLUME}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_VOLUME}",
        jsonKey = "agcOutputThreshold",
        spkJsonKey = "spkAgcOutputThreshold",
        defaultValue = 3,
        getHp = { it.agc.outputThreshold },
        setHp = { copy(agc = agc.copy(outputThreshold = it)) },
        getSp = { it.agc.spkOutputThreshold },
        setSp = { copy(agc = agc.copy(spkOutputThreshold = it)) }
    ),

    // FET Compressor
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE}",
        jsonKey = "fetEnabled", spkJsonKey = "spkFetEnabled",
        defaultValue = false,
        getHp = { it.fet.enabled }, setHp = { copy(fet = fet.copy(enabled = it)) },
        getSp = { it.fet.spkEnabled }, setSp = { copy(fet = fet.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD}",
        jsonKey = "fetThreshold", spkJsonKey = "spkFetThreshold",
        defaultValue = 100,
        getHp = { it.fet.threshold }, setHp = { copy(fet = fet.copy(threshold = it)) },
        getSp = { it.fet.spkThreshold }, setSp = { copy(fet = fet.copy(spkThreshold = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO}",
        jsonKey = "fetRatio", spkJsonKey = "spkFetRatio",
        defaultValue = 100,
        getHp = { it.fet.ratio }, setHp = { copy(fet = fet.copy(ratio = it)) },
        getSp = { it.fet.spkRatio }, setSp = { copy(fet = fet.copy(spkRatio = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE}",
        jsonKey = "fetAutoKnee", spkJsonKey = "spkFetAutoKnee",
        defaultValue = true,
        getHp = { it.fet.autoKnee }, setHp = { copy(fet = fet.copy(autoKnee = it)) },
        getSp = { it.fet.spkAutoKnee }, setSp = { copy(fet = fet.copy(spkAutoKnee = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE}",
        jsonKey = "fetKnee", spkJsonKey = "spkFetKnee",
        defaultValue = 0,
        getHp = { it.fet.knee }, setHp = { copy(fet = fet.copy(knee = it)) },
        getSp = { it.fet.spkKnee }, setSp = { copy(fet = fet.copy(spkKnee = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI}",
        jsonKey = "fetKneeMulti", spkJsonKey = "spkFetKneeMulti",
        defaultValue = 0,
        getHp = { it.fet.kneeMulti }, setHp = { copy(fet = fet.copy(kneeMulti = it)) },
        getSp = { it.fet.spkKneeMulti }, setSp = { copy(fet = fet.copy(spkKneeMulti = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN}",
        jsonKey = "fetAutoGain", spkJsonKey = "spkFetAutoGain",
        defaultValue = true,
        getHp = { it.fet.autoGain }, setHp = { copy(fet = fet.copy(autoGain = it)) },
        getSp = { it.fet.spkAutoGain }, setSp = { copy(fet = fet.copy(spkAutoGain = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN}",
        jsonKey = "fetGain", spkJsonKey = "spkFetGain",
        defaultValue = 0,
        getHp = { it.fet.gain }, setHp = { copy(fet = fet.copy(gain = it)) },
        getSp = { it.fet.spkGain }, setSp = { copy(fet = fet.copy(spkGain = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK}",
        jsonKey = "fetAutoAttack", spkJsonKey = "spkFetAutoAttack",
        defaultValue = true,
        getHp = { it.fet.autoAttack }, setHp = { copy(fet = fet.copy(autoAttack = it)) },
        getSp = { it.fet.spkAutoAttack }, setSp = { copy(fet = fet.copy(spkAutoAttack = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK}",
        jsonKey = "fetAttack", spkJsonKey = "spkFetAttack",
        defaultValue = 20,
        getHp = { it.fet.attack }, setHp = { copy(fet = fet.copy(attack = it)) },
        getSp = { it.fet.spkAttack }, setSp = { copy(fet = fet.copy(spkAttack = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK}",
        jsonKey = "fetMaxAttack", spkJsonKey = "spkFetMaxAttack",
        defaultValue = 80,
        getHp = { it.fet.maxAttack }, setHp = { copy(fet = fet.copy(maxAttack = it)) },
        getSp = { it.fet.spkMaxAttack }, setSp = { copy(fet = fet.copy(spkMaxAttack = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE}",
        jsonKey = "fetAutoRelease", spkJsonKey = "spkFetAutoRelease",
        defaultValue = true,
        getHp = { it.fet.autoRelease }, setHp = { copy(fet = fet.copy(autoRelease = it)) },
        getSp = { it.fet.spkAutoRelease }, setSp = { copy(fet = fet.copy(spkAutoRelease = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE}",
        jsonKey = "fetRelease", spkJsonKey = "spkFetRelease",
        defaultValue = 50,
        getHp = { it.fet.release }, setHp = { copy(fet = fet.copy(release = it)) },
        getSp = { it.fet.spkRelease }, setSp = { copy(fet = fet.copy(spkRelease = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE}",
        jsonKey = "fetMaxRelease", spkJsonKey = "spkFetMaxRelease",
        defaultValue = 100,
        getHp = { it.fet.maxRelease }, setHp = { copy(fet = fet.copy(maxRelease = it)) },
        getSp = { it.fet.spkMaxRelease }, setSp = { copy(fet = fet.copy(spkMaxRelease = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_CREST}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST}",
        jsonKey = "fetCrest", spkJsonKey = "spkFetCrest",
        defaultValue = 100,
        getHp = { it.fet.crest }, setHp = { copy(fet = fet.copy(crest = it)) },
        getSp = { it.fet.spkCrest }, setSp = { copy(fet = fet.copy(spkCrest = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT}",
        jsonKey = "fetAdapt", spkJsonKey = "spkFetAdapt",
        defaultValue = 50,
        getHp = { it.fet.adapt }, setHp = { copy(fet = fet.copy(adapt = it)) },
        getSp = { it.fet.spkAdapt }, setSp = { copy(fet = fet.copy(spkAdapt = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP}",
        jsonKey = "fetNoClip", spkJsonKey = "spkFetNoClip",
        defaultValue = true,
        getHp = { it.fet.noClip }, setHp = { copy(fet = fet.copy(noClip = it)) },
        getSp = { it.fet.spkNoClip }, setSp = { copy(fet = fet.copy(spkNoClip = it)) }
    ),

    // DDC
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DDC_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DDC_ENABLE}",
        jsonKey = "ddcEnabled", spkJsonKey = "spkDdcEnabled",
        defaultValue = false,
        getHp = { it.ddc.enabled }, setHp = { copy(ddc = ddc.copy(enabled = it)) },
        getSp = { it.ddc.spkEnabled }, setSp = { copy(ddc = ddc.copy(spkEnabled = it)) }
    ),
    StringPref(
        hpPrefKey = ViperRepository.PREF_DDC_DEVICE,
        spkPrefKey = "spk_${ViperRepository.PREF_DDC_DEVICE}",
        jsonKey = "ddcDevice", spkJsonKey = "spkDdcDevice",
        defaultValue = "",
        getHp = { it.ddc.device }, setHp = { copy(ddc = ddc.copy(device = it)) },
        getSp = { it.ddc.spkDevice }, setSp = { copy(ddc = ddc.copy(spkDevice = it)) }
    ),

    // Spectrum Extension
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE}",
        jsonKey = "vseEnabled", spkJsonKey = "spkVseEnabled",
        defaultValue = false,
        getHp = { it.vse.enabled }, setHp = { copy(vse = vse.copy(enabled = it)) },
        getSp = { it.vse.spkEnabled }, setSp = { copy(vse = vse.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK}",
        jsonKey = "vseStrength", spkJsonKey = "spkVseStrength",
        defaultValue = 10,
        getHp = { it.vse.strength }, setHp = { copy(vse = vse.copy(strength = it)) },
        getSp = { it.vse.spkStrength }, setSp = { copy(vse = vse.copy(spkStrength = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
        jsonKey = "vseExciter", spkJsonKey = "spkVseExciter",
        defaultValue = 0,
        getHp = { it.vse.exciter }, setHp = { copy(vse = vse.copy(exciter = it)) },
        getSp = { it.vse.spkExciter }, setSp = { copy(vse = vse.copy(spkExciter = it)) }
    ),

    // EQ
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_EQ_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_EQ_ENABLE}",
        jsonKey = "eqEnabled", spkJsonKey = "spkEqEnabled",
        defaultValue = false,
        getHp = { it.eq.enabled }, setHp = { copy(eq = eq.copy(enabled = it)) },
        getSp = { it.eq.spkEnabled }, setSp = { copy(eq = eq.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_EQ_BAND_COUNT}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_EQ_BAND_COUNT}",
        jsonKey = "eqBandCount", spkJsonKey = "spkEqBandCount",
        defaultValue = 10,
        getHp = { it.eq.bandCount }, setHp = { copy(eq = eq.copy(bandCount = it)) },
        getSp = { it.eq.spkBandCount }, setSp = { copy(eq = eq.copy(spkBandCount = it)) }
    ),
    StringPref(
        hpPrefKey = "${ViperParams.PARAM_HP_EQ_BAND_LEVEL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}",
        jsonKey = "eqBands", spkJsonKey = "spkEqBands",
        defaultValue = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        getHp = { it.eq.bands }, setHp = { copy(eq = eq.copy(bands = it)) },
        getSp = { it.eq.spkBands }, setSp = { copy(eq = eq.copy(spkBands = it)) }
    ),
    NullableLongPref(
        hpPrefKey = ViperRepository.PREF_EQ_PRESET_ID,
        spkPrefKey = "spk_${ViperRepository.PREF_EQ_PRESET_ID}",
        jsonKey = "eqPresetId", spkJsonKey = "spkEqPresetId",
        getHp = { it.eq.presetId }, setHp = { copy(eq = eq.copy(presetId = it)) },
        getSp = { it.eq.spkPresetId }, setSp = { copy(eq = eq.copy(spkPresetId = it)) }
    ),

    // Convolver
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CONVOLVER_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_CONVOLVER_ENABLE}",
        jsonKey = "convolverEnabled",
        spkJsonKey = "spkConvolverEnabled",
        defaultValue = false,
        getHp = { it.convolver.enabled },
        setHp = { copy(convolver = convolver.copy(enabled = it)) },
        getSp = { it.convolver.spkEnabled },
        setSp = { copy(convolver = convolver.copy(spkEnabled = it)) }
    ),
    StringPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL}",
        jsonKey = "convolverKernel",
        spkJsonKey = "spkConvolverKernel",
        defaultValue = "",
        getHp = { it.convolver.kernel },
        setHp = { copy(convolver = convolver.copy(kernel = it)) },
        getSp = { it.convolver.spkKernel },
        setSp = { copy(convolver = convolver.copy(spkKernel = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL}",
        jsonKey = "convolverCrossChannel",
        spkJsonKey = "spkConvolverCrossChannel",
        defaultValue = 0,
        getHp = { it.convolver.crossChannel },
        setHp = { copy(convolver = convolver.copy(crossChannel = it)) },
        getSp = { it.convolver.spkCrossChannel },
        setSp = { copy(convolver = convolver.copy(spkCrossChannel = it)) }
    ),

    // Field Surround
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE}",
        jsonKey = "fieldSurroundEnabled",
        spkJsonKey = "spkFieldSurroundEnabled",
        defaultValue = false,
        getHp = { it.fieldSurround.enabled },
        setHp = { copy(fieldSurround = fieldSurround.copy(enabled = it)) },
        getSp = { it.fieldSurround.spkEnabled },
        setSp = { copy(fieldSurround = fieldSurround.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING}",
        jsonKey = "fieldSurroundWidening",
        spkJsonKey = "spkFieldSurroundWidening",
        defaultValue = 0,
        getHp = { it.fieldSurround.widening },
        setHp = { copy(fieldSurround = fieldSurround.copy(widening = it)) },
        getSp = { it.fieldSurround.spkWidening },
        setSp = { copy(fieldSurround = fieldSurround.copy(spkWidening = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE}",
        jsonKey = "fieldSurroundMidImage",
        spkJsonKey = "spkFieldSurroundMidImage",
        defaultValue = 5,
        getHp = { it.fieldSurround.midImage },
        setHp = { copy(fieldSurround = fieldSurround.copy(midImage = it)) },
        getSp = { it.fieldSurround.spkMidImage },
        setSp = { copy(fieldSurround = fieldSurround.copy(spkMidImage = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH}",
        jsonKey = "fieldSurroundDepth",
        spkJsonKey = "spkFieldSurroundDepth",
        defaultValue = 0,
        getHp = { it.fieldSurround.depth },
        setHp = { copy(fieldSurround = fieldSurround.copy(depth = it)) },
        getSp = { it.fieldSurround.spkDepth },
        setSp = { copy(fieldSurround = fieldSurround.copy(spkDepth = it)) }
    ),

    // Diff Surround
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE}",
        jsonKey = "diffSurroundEnabled",
        spkJsonKey = "spkDiffSurroundEnabled",
        defaultValue = false,
        getHp = { it.diffSurround.enabled },
        setHp = { copy(diffSurround = diffSurround.copy(enabled = it)) },
        getSp = { it.diffSurround.spkEnabled },
        setSp = { copy(diffSurround = diffSurround.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_DELAY}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY}",
        jsonKey = "diffSurroundDelay",
        spkJsonKey = "spkDiffSurroundDelay",
        defaultValue = 4,
        getHp = { it.diffSurround.delay },
        setHp = { copy(diffSurround = diffSurround.copy(delay = it)) },
        getSp = { it.diffSurround.spkDelay },
        setSp = { copy(diffSurround = diffSurround.copy(spkDelay = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE}",
        jsonKey = "diffSurroundReverse",
        spkJsonKey = "spkDiffSurroundReverse",
        defaultValue = false,
        getHp = { it.diffSurround.reverse },
        setHp = { copy(diffSurround = diffSurround.copy(reverse = it)) },
        getSp = { it.diffSurround.spkReverse },
        setSp = { copy(diffSurround = diffSurround.copy(spkReverse = it)) }
    ),

    // VHE (Headphone Surround)
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE}",
        jsonKey = "vheEnabled", spkJsonKey = "spkVheEnabled",
        defaultValue = false,
        getHp = { it.vhe.enabled }, setHp = { copy(vhe = vhe.copy(enabled = it)) },
        getSp = { it.vhe.spkEnabled }, setSp = { copy(vhe = vhe.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH}",
        jsonKey = "vheQuality", spkJsonKey = "spkVheQuality",
        defaultValue = 0,
        getHp = { it.vhe.quality }, setHp = { copy(vhe = vhe.copy(quality = it)) },
        getSp = { it.vhe.spkQuality }, setSp = { copy(vhe = vhe.copy(spkQuality = it)) }
    ),

    // Reverb
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ENABLE}",
        jsonKey = "reverbEnabled", spkJsonKey = "spkReverbEnabled",
        defaultValue = false,
        getHp = { it.reverb.enabled }, setHp = { copy(reverb = reverb.copy(enabled = it)) },
        getSp = { it.reverb.spkEnabled }, setSp = { copy(reverb = reverb.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_SIZE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_SIZE}",
        jsonKey = "reverbRoomSize", spkJsonKey = "spkReverbRoomSize",
        defaultValue = 0,
        getHp = { it.reverb.roomSize }, setHp = { copy(reverb = reverb.copy(roomSize = it)) },
        getSp = { it.reverb.spkRoomSize }, setSp = { copy(reverb = reverb.copy(spkRoomSize = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_WIDTH}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH}",
        jsonKey = "reverbWidth", spkJsonKey = "spkReverbWidth",
        defaultValue = 0,
        getHp = { it.reverb.width }, setHp = { copy(reverb = reverb.copy(width = it)) },
        getSp = { it.reverb.spkWidth }, setSp = { copy(reverb = reverb.copy(spkWidth = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING}",
        jsonKey = "reverbDampening",
        spkJsonKey = "spkReverbDampening",
        defaultValue = 0,
        getHp = { it.reverb.dampening },
        setHp = { copy(reverb = reverb.copy(dampening = it)) },
        getSp = { it.reverb.spkDampening },
        setSp = { copy(reverb = reverb.copy(spkDampening = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL}",
        jsonKey = "reverbWet", spkJsonKey = "spkReverbWet",
        defaultValue = 0,
        getHp = { it.reverb.wet }, setHp = { copy(reverb = reverb.copy(wet = it)) },
        getSp = { it.reverb.spkWet }, setSp = { copy(reverb = reverb.copy(spkWet = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL}",
        jsonKey = "reverbDry", spkJsonKey = "spkReverbDry",
        defaultValue = 50,
        getHp = { it.reverb.dry }, setHp = { copy(reverb = reverb.copy(dry = it)) },
        getSp = { it.reverb.spkDry }, setSp = { copy(reverb = reverb.copy(spkDry = it)) }
    ),

    // Dynamic System
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE}",
        jsonKey = "dynamicSystemEnabled",
        spkJsonKey = "spkDynamicSystemEnabled",
        defaultValue = false,
        getHp = { it.dynamicSystem.enabled },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(enabled = it)) },
        getSp = { it.dynamicSystem.spkEnabled },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkEnabled = it)) }
    ),
    NullableLongPref(
        hpPrefKey = ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID,
        spkPrefKey = "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
        jsonKey = "dsPresetId",
        spkJsonKey = "spkDsPresetId",
        getHp = { it.dynamicSystem.presetId },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(presetId = it)) },
        getSp = { it.dynamicSystem.spkPresetId },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkPresetId = it)) }
    ),
    IntPref(
        hpPrefKey = ViperRepository.PERF_DYNAMIC_SYS_DEVICE,
        spkPrefKey = "spk_${ViperRepository.PERF_DYNAMIC_SYS_DEVICE}",
        jsonKey = "dynamicSystemDevice",
        spkJsonKey = "spkDynamicSystemDevice",
        defaultValue = 0,
        getHp = { it.dynamicSystem.device },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(device = it)) },
        getSp = { it.dynamicSystem.spkDevice },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkDevice = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH}",
        jsonKey = "dynamicSystemStrength",
        spkJsonKey = "spkDynamicSystemStrength",
        defaultValue = 50,
        getHp = { it.dynamicSystem.strength },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(strength = it)) },
        getSp = { it.dynamicSystem.spkStrength },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkStrength = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
        jsonKey = "dsXLow",
        spkJsonKey = "spkDsXLow",
        defaultValue = 100,
        getHp = { it.dynamicSystem.xLow },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(xLow = it)) },
        getSp = { it.dynamicSystem.spkXLow },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkXLow = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
        jsonKey = "dsXHigh",
        spkJsonKey = "spkDsXHigh",
        defaultValue = 5600,
        getHp = { it.dynamicSystem.xHigh },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(xHigh = it)) },
        getSp = { it.dynamicSystem.spkXHigh },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkXHigh = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
        jsonKey = "dsYLow",
        spkJsonKey = "spkDsYLow",
        defaultValue = 40,
        getHp = { it.dynamicSystem.yLow },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(yLow = it)) },
        getSp = { it.dynamicSystem.spkYLow },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkYLow = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
        jsonKey = "dsYHigh",
        spkJsonKey = "spkDsYHigh",
        defaultValue = 80,
        getHp = { it.dynamicSystem.yHigh },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(yHigh = it)) },
        getSp = { it.dynamicSystem.spkYHigh },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkYHigh = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
        jsonKey = "dsSideGainLow",
        spkJsonKey = "spkDsSideGainLow",
        defaultValue = 50,
        getHp = { it.dynamicSystem.sideGainLow },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(sideGainLow = it)) },
        getSp = { it.dynamicSystem.spkSideGainLow },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkSideGainLow = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
        jsonKey = "dsSideGainHigh",
        spkJsonKey = "spkDsSideGainHigh",
        defaultValue = 50,
        getHp = { it.dynamicSystem.sideGainHigh },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(sideGainHigh = it)) },
        getSp = { it.dynamicSystem.spkSideGainHigh },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spkSideGainHigh = it)) }
    ),

    // Tube Simulator
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE}",
        jsonKey = "tubeSimulatorEnabled", spkJsonKey = "spkTubeSimulatorEnabled",
        defaultValue = false,
        getHp = { it.tube.enabled }, setHp = { copy(tube = tube.copy(enabled = it)) },
        getSp = { it.tube.spkEnabled }, setSp = { copy(tube = tube.copy(spkEnabled = it)) }
    ),

    // Bass
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_ENABLE}",
        jsonKey = "bassEnabled", spkJsonKey = "spkBassEnabled",
        defaultValue = false,
        getHp = { it.bass.enabled }, setHp = { copy(bass = bass.copy(enabled = it)) },
        getSp = { it.bass.spkEnabled }, setSp = { copy(bass = bass.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MODE}",
        jsonKey = "bassMode", spkJsonKey = "spkBassMode",
        defaultValue = 0,
        getHp = { it.bass.mode }, setHp = { copy(bass = bass.copy(mode = it)) },
        getSp = { it.bass.spkMode }, setSp = { copy(bass = bass.copy(spkMode = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_FREQUENCY}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_FREQUENCY}",
        jsonKey = "bassFrequency", spkJsonKey = "spkBassFrequency",
        defaultValue = 55,
        getHp = { it.bass.frequency }, setHp = { copy(bass = bass.copy(frequency = it)) },
        getSp = { it.bass.spkFrequency }, setSp = { copy(bass = bass.copy(spkFrequency = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_GAIN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_GAIN}",
        jsonKey = "bassGain", spkJsonKey = "spkBassGain",
        defaultValue = 0,
        getHp = { it.bass.gain }, setHp = { copy(bass = bass.copy(gain = it)) },
        getSp = { it.bass.spkGain }, setSp = { copy(bass = bass.copy(spkGain = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_ANTI_POP}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_ANTI_POP}",
        jsonKey = "bassAntiPop", spkJsonKey = "spkBassAntiPop",
        defaultValue = true,
        getHp = { it.bass.antiPop }, setHp = { copy(bass = bass.copy(antiPop = it)) },
        getSp = { it.bass.spkAntiPop }, setSp = { copy(bass = bass.copy(spkAntiPop = it)) }
    ),

    // Bass Mono
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_ENABLE}",
        jsonKey = "bassMonoEnabled",
        spkJsonKey = "spkBassMonoEnabled",
        defaultValue = false,
        getHp = { it.bassMono.enabled },
        setHp = { copy(bassMono = bassMono.copy(enabled = it)) },
        getSp = { it.bassMono.spkEnabled },
        setSp = { copy(bassMono = bassMono.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_MODE}",
        jsonKey = "bassMonoMode", spkJsonKey = "spkBassMonoMode",
        defaultValue = 0,
        getHp = { it.bassMono.mode }, setHp = { copy(bassMono = bassMono.copy(mode = it)) },
        getSp = { it.bassMono.spkMode }, setSp = { copy(bassMono = bassMono.copy(spkMode = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_FREQUENCY}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY}",
        jsonKey = "bassMonoFrequency",
        spkJsonKey = "spkBassMonoFrequency",
        defaultValue = 55,
        getHp = { it.bassMono.frequency },
        setHp = { copy(bassMono = bassMono.copy(frequency = it)) },
        getSp = { it.bassMono.spkFrequency },
        setSp = { copy(bassMono = bassMono.copy(spkFrequency = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_GAIN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_GAIN}",
        jsonKey = "bassMonoGain", spkJsonKey = "spkBassMonoGain",
        defaultValue = 0,
        getHp = { it.bassMono.gain }, setHp = { copy(bassMono = bassMono.copy(gain = it)) },
        getSp = { it.bassMono.spkGain }, setSp = { copy(bassMono = bassMono.copy(spkGain = it)) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_ANTI_POP}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP}",
        jsonKey = "bassMonoAntiPop",
        spkJsonKey = "spkBassMonoAntiPop",
        defaultValue = true,
        getHp = { it.bassMono.antiPop },
        setHp = { copy(bassMono = bassMono.copy(antiPop = it)) },
        getSp = { it.bassMono.spkAntiPop },
        setSp = { copy(bassMono = bassMono.copy(spkAntiPop = it)) }
    ),

    // Clarity
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CLARITY_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CLARITY_ENABLE}",
        jsonKey = "clarityEnabled", spkJsonKey = "spkClarityEnabled",
        defaultValue = false,
        getHp = { it.clarity.enabled }, setHp = { copy(clarity = clarity.copy(enabled = it)) },
        getSp = { it.clarity.spkEnabled }, setSp = { copy(clarity = clarity.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CLARITY_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CLARITY_MODE}",
        jsonKey = "clarityMode", spkJsonKey = "spkClarityMode",
        defaultValue = 0,
        getHp = { it.clarity.mode }, setHp = { copy(clarity = clarity.copy(mode = it)) },
        getSp = { it.clarity.spkMode }, setSp = { copy(clarity = clarity.copy(spkMode = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CLARITY_GAIN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CLARITY_GAIN}",
        jsonKey = "clarityGain", spkJsonKey = "spkClarityGain",
        defaultValue = 1,
        getHp = { it.clarity.gain }, setHp = { copy(clarity = clarity.copy(gain = it)) },
        getSp = { it.clarity.spkGain }, setSp = { copy(clarity = clarity.copy(spkGain = it)) }
    ),

    // Cure
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CURE_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CURE_ENABLE}",
        jsonKey = "cureEnabled", spkJsonKey = "spkCureEnabled",
        defaultValue = false,
        getHp = { it.cure.enabled }, setHp = { copy(cure = cure.copy(enabled = it)) },
        getSp = { it.cure.spkEnabled }, setSp = { copy(cure = cure.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CURE_STRENGTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CURE_STRENGTH}",
        jsonKey = "cureStrength", spkJsonKey = "spkCureStrength",
        defaultValue = 0,
        getHp = { it.cure.strength }, setHp = { copy(cure = cure.copy(strength = it)) },
        getSp = { it.cure.spkStrength }, setSp = { copy(cure = cure.copy(spkStrength = it)) }
    ),

    // AnalogX
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_ANALOGX_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_ANALOGX_ENABLE}",
        jsonKey = "analogxEnabled", spkJsonKey = "spkAnalogxEnabled",
        defaultValue = false,
        getHp = { it.analog.enabled }, setHp = { copy(analog = analog.copy(enabled = it)) },
        getSp = { it.analog.spkEnabled }, setSp = { copy(analog = analog.copy(spkEnabled = it)) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_ANALOGX_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_ANALOGX_MODE}",
        jsonKey = "analogxMode", spkJsonKey = "spkAnalogxMode",
        defaultValue = 0,
        getHp = { it.analog.mode }, setHp = { copy(analog = analog.copy(mode = it)) },
        getSp = { it.analog.spkMode }, setSp = { copy(analog = analog.copy(spkMode = it)) }
    ),

    // Speaker-only
    BoolPref(
        hpPrefKey = "spk_${ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE}",
        jsonKey = "speakerOptEnabled", spkJsonKey = "speakerOptEnabled",
        defaultValue = false,
        getHp = { it.speakerOptEnabled }, setHp = { copy(speakerOptEnabled = it) },
        getSp = { it.speakerOptEnabled }, setSp = { copy(speakerOptEnabled = it) }
    )
)

suspend fun loadEffectPrefs(
    repository: ViperRepository,
    isSpk: Boolean,
    state: MainUiState = MainUiState()
): MainUiState {
    var s = state
    for (pref in EFFECT_PREFS) {
        s = when (pref) {
            is IntPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = repository.getIntPreference(key, pref.defaultValue).first()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is BoolPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = repository.getBooleanPreference(key, pref.defaultValue).first()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is StringPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = repository.getStringPreference(key, pref.defaultValue).first()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is NullableLongPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val raw = repository.getIntPreference(key, -1).first()
                val value = if (raw < 0) null else raw.toLong()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }
        }
    }
    return s
}

suspend fun saveEffectPrefs(
    repository: ViperRepository,
    state: MainUiState,
    isSpk: Boolean
) {
    for (pref in EFFECT_PREFS) {
        when (pref) {
            is IntPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setIntPreference(key, value)
            }

            is BoolPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setBooleanPreference(key, value)
            }

            is StringPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setStringPreference(key, value)
            }

            is NullableLongPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setIntPreference(key, value?.toInt() ?: -1)
            }
        }
    }
}

fun serializeEffectPrefs(state: MainUiState, isSpk: Boolean): JSONObject {
    val obj = JSONObject()
    for (pref in EFFECT_PREFS) {
        val jsonKey = if (isSpk) pref.spkJsonKey else pref.jsonKey
        when (pref) {
            is IntPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value)
            }

            is BoolPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value)
            }

            is StringPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value)
            }

            is NullableLongPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value ?: -1)
            }
        }
    }
    return obj
}

fun deserializeEffectPrefs(
    obj: JSONObject,
    state: MainUiState,
    isSpk: Boolean
): MainUiState {
    var s = state
    for (pref in EFFECT_PREFS) {
        val jsonKey = if (isSpk) pref.spkJsonKey else pref.jsonKey
        s = when (pref) {
            is IntPref -> {
                val fallback = if (isSpk) pref.getSpk(s) else pref.getHp(s)
                val value = obj.optInt(jsonKey, fallback)
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is BoolPref -> {
                val fallback = if (isSpk) pref.getSpk(s) else pref.getHp(s)
                val value = obj.optBoolean(jsonKey, fallback)
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is StringPref -> {
                val fallback = if (isSpk) pref.getSpk(s) else pref.getHp(s)
                val value = obj.optString(jsonKey, fallback)
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is NullableLongPref -> {
                val value = obj.optInt(jsonKey, -1).let { if (it < 0) null else it.toLong() }
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }
        }
    }
    return s
}

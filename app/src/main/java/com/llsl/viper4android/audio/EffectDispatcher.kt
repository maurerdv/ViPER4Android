package com.llsl.viper4android.audio


import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.ui.screens.main.MainUiState
import com.llsl.viper4android.ui.screens.main.loadEffectPrefs
import com.llsl.viper4android.utils.FileLogger
import kotlinx.coroutines.flow.first
import java.util.Locale

object EffectDispatcher {

    val OUTPUT_VOLUME_VALUES = intArrayOf(
        1, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100,
        110, 120, 130, 140, 150, 160, 170, 180, 190, 200
    )
    val OUTPUT_DB_VALUES = intArrayOf(30, 50, 70, 80, 90, 100)
    val PLAYBACK_GAIN_RATIO_VALUES = intArrayOf(50, 100, 300)
    val MULTI_FACTOR_VALUES = intArrayOf(
        100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 3000
    )
    val VSE_BARK_VALUES = intArrayOf(
        2200, 2800, 3400, 4000, 4600, 5200, 5800, 6400, 7000, 7600, 8200
    )
    val DIFF_SURROUND_DELAY_VALUES = IntArray(20) { (it + 1) * 100 }
    val FIELD_SURROUND_WIDENING_VALUES = intArrayOf(0, 100, 200, 300, 400, 500, 600, 700, 800)

    val BASS_GAIN_DB_LABELS = arrayOf(
        "3.5", "6.0", "8.0", "9.5", "10.9", "12.0",
        "13.1", "14.0", "14.8", "15.6", "16.1", "17.0",
        "17.5", "18.1", "18.6", "19.1", "19.5", "20.0", "20.4", "20.8"
    )
    val BASS_SUBWOOFER_GAIN_DB_LABELS = arrayOf(
        "1.9", "8.0", "11.5", "14.0", "15.9", "17.5",
        "18.8", "20.0", "21.0", "21.9", "22.8", "23.5",
        "24.2", "24.9", "25.5", "26.0", "26.5", "27.0", "27.5", "28.0"
    )
    val CLARITY_GAIN_DB_LABELS = arrayOf(
        "0.0", "3.5", "6.0", "8.0", "10.0", "11.0",
        "12.0", "13.0", "14.0", "14.8"
    )

    val EQ_PRESETS = listOf(
        "4.5;4.5;3.5;1.2;1.0;0.5;1.4;1.75;3.5;2.5;",
        "6.0;4.0;2.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-4.0;-2.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;-3.0;-3.0;-3.0;-5.0;",
        "3.0;2.0;1.0;0.5;0.5;0.0;-1.0;-2.0;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;6.0;4.0;1.0;-1.0;-0.5;1.0;1.5;2.5;3.0;",
        "4.0;3.0;1.0;0.0;-0.5;0.0;1.5;2.5;3.5;4.0;",
        "3.0;2.0;1.5;1.0;0.5;-0.5;-1.5;-2.0;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;1.0;2.0;3.0;4.0;5.0;",
        "0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-4.0;-5.0;",
        "-1.0;-0.5;0.0;1.5;3.0;3.0;2.0;1.0;0.0;-1.0;"
    )

    val EQ_PRESETS_15 = listOf(
        "4.5;4.5;4.5;4.0;2.5;1.0;1.0;1.0;0.5;1.0;1.5;2.0;3.0;3.0;2.5;",
        "6.0;5.5;4.0;2.5;1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-5.5;-4.0;-2.5;-1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-2.0;-3.0;-3.0;-3.0;-3.5;-5.0;",
        "3.0;2.5;2.0;1.5;1.0;0.5;0.5;0.5;0.0;-0.5;-1.5;-2.0;-2.5;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;4.0;6.0;4.5;3.0;1.0;-0.5;-1.0;-0.5;0.5;1.0;1.5;2.0;2.5;3.0;",
        "4.0;3.5;3.0;1.5;0.5;0.0;-0.5;-0.5;0.0;1.0;2.0;2.5;3.0;3.5;4.0;",
        "3.0;2.5;2.0;1.5;1.5;1.0;0.5;0.0;-0.5;-1.0;-1.5;-2.0;-2.5;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;1.0;1.5;2.5;3.0;3.5;4.5;5.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-1.0;-1.5;-2.5;-3.0;-3.5;-4.5;-5.0;",
        "-1.0;-1.0;-0.5;0.0;0.5;1.5;2.5;3.0;3.0;2.5;1.5;1.0;0.5;-0.5;-1.0;"
    )

    val EQ_PRESETS_25 = listOf(
        "4.5;4.5;4.5;4.5;4.0;4.0;3.5;2.5;1.0;1.0;1.0;1.0;0.5;0.5;1.0;1.0;1.5;1.5;2.0;2.5;3.5;3.0;3.0;2.5;2.5;",
        "6.0;6.0;5.5;4.5;3.5;2.5;2.0;1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-6.0;-5.5;-4.5;-3.5;-2.5;-2.0;-1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.5;-4.5;-5.0;-5.0;",
        "3.0;3.0;2.5;2.5;1.5;1.5;1.0;1.0;0.5;0.5;0.5;0.5;0.0;0.0;-0.5;-0.5;-1.5;-1.5;-2.0;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;3.0;4.0;5.0;5.5;4.5;4.0;3.0;1.0;0.5;-0.5;-1.0;-0.5;-0.5;0.0;0.5;1.0;1.5;1.5;2.0;2.5;2.5;3.0;3.0;3.0;",
        "4.0;4.0;3.5;3.5;2.5;1.5;1.0;0.5;0.0;0.0;-0.5;-0.5;0.0;0.0;0.5;1.0;2.0;2.0;2.5;3.0;3.5;3.5;4.0;4.0;4.0;",
        "3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.5;1.0;1.0;0.5;0.5;0.0;-0.5;-1.0;-1.0;-1.5;-2.0;-2.0;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;1.0;1.5;1.5;2.5;2.5;3.0;3.5;4.0;4.5;4.5;5.0;5.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-1.0;-1.5;-1.5;-2.5;-2.5;-3.0;-3.5;-4.0;-4.5;-4.5;-5.0;-5.0;",
        "-1.0;-1.0;-1.0;-0.5;-0.5;0.0;0.0;0.5;1.5;2.0;2.5;3.0;3.0;3.0;2.5;2.5;1.5;1.5;1.0;0.5;0.0;-0.5;-0.5;-1.0;-1.0;"
    )

    val EQ_PRESETS_31 = listOf(
        "4.5;4.5;4.5;4.5;4.5;4.5;4.0;4.0;3.5;2.5;2.0;1.0;1.0;1.0;1.0;1.0;0.5;0.5;1.0;1.0;1.5;1.5;1.5;2.0;2.5;3.0;3.5;3.0;3.0;2.5;2.5;",
        "6.0;6.0;6.0;5.5;4.5;4.0;3.5;2.5;2.0;1.5;0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-6.0;-6.0;-5.5;-4.5;-4.0;-3.5;-2.5;-2.0;-1.5;-0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.5;-4.5;-5.0;-5.0;",
        "3.0;3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.0;1.0;0.5;0.5;0.5;0.5;0.5;0.5;0.0;0.0;-0.5;-0.5;-1.0;-1.5;-1.5;-2.0;-2.5;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;3.0;3.0;4.0;5.0;6.0;5.5;4.5;4.0;3.0;2.0;1.0;0.5;-0.5;-1.0;-1.0;-0.5;-0.5;0.0;0.5;1.0;1.0;1.5;1.5;2.0;2.0;2.5;2.5;3.0;3.0;3.0;",
        "4.0;4.0;4.0;3.5;3.5;3.0;2.5;1.5;1.0;0.5;0.5;0.0;0.0;-0.5;-0.5;-0.5;0.0;0.0;0.5;1.0;1.5;2.0;2.0;2.5;3.0;3.0;3.5;3.5;4.0;4.0;4.0;",
        "3.0;3.0;3.0;2.5;2.5;2.0;2.0;1.5;1.5;1.5;1.0;1.0;1.0;0.5;0.5;0.0;0.0;-0.5;-1.0;-1.0;-1.5;-1.5;-2.0;-2.0;-2.5;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;0.5;1.0;1.5;1.5;2.0;2.5;2.5;3.0;3.5;3.5;4.0;4.5;4.5;5.0;5.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-0.5;-1.0;-1.5;-1.5;-2.0;-2.5;-2.5;-3.0;-3.5;-3.5;-4.0;-4.5;-4.5;-5.0;-5.0;",
        "-1.0;-1.0;-1.0;-1.0;-0.5;-0.5;-0.5;0.0;0.0;0.5;1.0;1.5;2.0;2.5;3.0;3.0;3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.0;0.5;0.5;0.0;-0.5;-0.5;-1.0;-1.0;"
    )

    val DYNAMIC_SYSTEM_DEVICES = listOf(
        "140;6200;40;60;10;80",
        "180;5800;55;80;10;70",
        "300;5600;60;105;10;50",
        "600;5400;60;105;10;20",
        "100;5600;40;80;50;50",
        "1200;6200;40;80;0;20",
        "1000;6200;40;80;0;10",
        "800;6200;40;80;10;0",
        "400;6200;40;80;10;0",
        "1200;6200;50;90;15;10",
        "1000;6200;50;90;30;10",
        "1100;6200;60;100;20;0",
        "1200;6200;50;100;10;50",
        "1200;6200;60;100;0;30",
        "1200;6200;40;80;0;30",
        "1000;6200;60;100;0;0",
        "1000;6200;60;120;0;0",
        "1000;6200;80;140;0;0",
        "800;6200;80;140;0;0",
        "0;0;0;0;0;0",
        "180;5400;40;60;50;0",
        "1200;6000;40;60;0;80",
        "140;5400;40;60;0;0"
    )

    val DYNAMIC_SYSTEM_DEVICE_NAMES = listOf(
        "Extreme Headphone (v2)", "High-End Headphone (v2)",
        "Common Headphone (v2)", "Low-End Headphone (v2)",
        "Common Earphone (v2)", "Extreme Headphone (v1)",
        "High-End Headphone (v1)", "Common Headphone (v1)",
        "Common Earphone (v1)", "Apple Earphone",
        "Monster Earphone", "Motorola Earphone",
        "Philips Earphone", "SHP2000",
        "SHP9000", "Unknown Type I",
        "Unknown Type II", "Unknown Type III",
        "Unknown Type IV", "Unknown Type V",
        "pittvandewitt flavor #1", "pittvandewitt flavor #2",
        "pittvandewitt flavor #3"
    )

    val EQ_BAND_LABELS_10 =
        listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    val EQ_BAND_LABELS_15 = listOf(
        "25Hz",
        "40Hz",
        "63Hz",
        "100Hz",
        "160Hz",
        "250Hz",
        "400Hz",
        "630Hz",
        "1kHz",
        "1.6kHz",
        "2.5kHz",
        "4kHz",
        "6.3kHz",
        "10kHz",
        "16kHz"
    )
    val EQ_BAND_LABELS_25 = listOf(
        "20Hz",
        "31Hz",
        "40Hz",
        "50Hz",
        "80Hz",
        "100Hz",
        "125Hz",
        "160Hz",
        "250Hz",
        "315Hz",
        "400Hz",
        "500Hz",
        "800Hz",
        "1kHz",
        "1.25kHz",
        "1.6kHz",
        "2.5kHz",
        "3.15kHz",
        "4kHz",
        "5kHz",
        "8kHz",
        "10kHz",
        "12.5kHz",
        "16kHz",
        "20kHz"
    )
    val EQ_BAND_LABELS_31 = listOf(
        "20Hz",
        "25Hz",
        "31Hz",
        "40Hz",
        "50Hz",
        "63Hz",
        "80Hz",
        "100Hz",
        "125Hz",
        "160Hz",
        "200Hz",
        "250Hz",
        "315Hz",
        "400Hz",
        "500Hz",
        "630Hz",
        "800Hz",
        "1kHz",
        "1.25kHz",
        "1.6kHz",
        "2kHz",
        "2.5kHz",
        "3.15kHz",
        "4kHz",
        "5kHz",
        "6.3kHz",
        "8kHz",
        "10kHz",
        "12.5kHz",
        "16kHz",
        "20kHz"
    )

    fun eqBandLabelsForCount(count: Int): List<String> = when (count) {
        15 -> EQ_BAND_LABELS_15; 25 -> EQ_BAND_LABELS_25; 31 -> EQ_BAND_LABELS_31; else -> EQ_BAND_LABELS_10
    }

    private fun ensureBandCount(rawBands: String, expectedCount: Int): String {
        val actualCount = rawBands.split(";").count { it.isNotBlank() }
        return if (actualCount != expectedCount) {
            List(expectedCount) { 0f }.joinToString(";") {
                String.format(Locale.US, "%.1f", it)
            } + ";"
        } else {
            rawBands
        }
    }

    val EQ_GRAPH_LABELS_10 = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val EQ_GRAPH_LABELS_15 = listOf(
        "25",
        "40",
        "63",
        "100",
        "160",
        "250",
        "400",
        "630",
        "1k",
        "1.6k",
        "2.5k",
        "4k",
        "6.3k",
        "10k",
        "16k"
    )
    val EQ_GRAPH_LABELS_25 = listOf(
        "20",
        "31",
        "40",
        "50",
        "80",
        "100",
        "125",
        "160",
        "250",
        "315",
        "400",
        "500",
        "800",
        "1k",
        "1.25k",
        "1.6k",
        "2.5k",
        "3.15k",
        "4k",
        "5k",
        "8k",
        "10k",
        "12.5k",
        "16k",
        "20k"
    )
    val EQ_GRAPH_LABELS_31 = listOf(
        "20",
        "25",
        "31",
        "40",
        "50",
        "63",
        "80",
        "100",
        "125",
        "160",
        "200",
        "250",
        "315",
        "400",
        "500",
        "630",
        "800",
        "1k",
        "1.25k",
        "1.6k",
        "2k",
        "2.5k",
        "3.15k",
        "4k",
        "5k",
        "6.3k",
        "8k",
        "10k",
        "12.5k",
        "16k",
        "20k"
    )

    fun eqGraphLabelsForCount(count: Int): List<String> = when (count) {
        15 -> EQ_GRAPH_LABELS_15; 25 -> EQ_GRAPH_LABELS_25; 31 -> EQ_GRAPH_LABELS_31; else -> EQ_GRAPH_LABELS_10
    }

    fun dispatchFullState(effect: ViperEffect, state: MainUiState, masterEnabled: Boolean) {
        val mode = if (state.fxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
        FileLogger.d(
            "Dispatch",
            "Dispatch: fullState mode=$mode master=${if (masterEnabled) "ON" else "OFF"}"
        )
        if (state.fxType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchHeadphoneState(effect, state)
        } else {
            dispatchSpeakerState(effect, state)
        }
    }

    fun dispatchHeadphoneState(effect: ViperEffect, state: MainUiState) {
        FileLogger.d(
            "Dispatch",
            "Dispatch: headphone outputVol=${state.out.volume} pan=${state.out.channelPan} limiter=${state.out.limiter}"
        )
        effect.setParameter(
            ViperParams.PARAM_HP_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(state.out.volume) { 100 })
        effect.setParameter(ViperParams.PARAM_HP_CHANNEL_PAN, state.out.channelPan)
        effect.setParameter(
            ViperParams.PARAM_HP_LIMITER,
            OUTPUT_DB_VALUES.getOrElse(state.out.limiter) { 100 })

        effect.setParameter(ViperParams.PARAM_HP_AGC_ENABLE, if (state.agc.enabled) 1 else 0)
        FileLogger.d("Dispatch", "AGC: ${if (state.agc.enabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agc.strength) { 50 })
        effect.setParameter(
            ViperParams.PARAM_HP_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(state.agc.maxGain) { 100 })
        effect.setParameter(
            ViperParams.PARAM_HP_AGC_VOLUME,
            OUTPUT_DB_VALUES.getOrElse(state.agc.outputThreshold) { 100 })

        FileLogger.d("Dispatch", "FET: ${if (state.fet.enabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE,
            if (state.fet.enabled) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD, state.fet.threshold)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO, state.fet.ratio)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
            if (state.fet.autoKnee) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE, state.fet.knee)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI, state.fet.kneeMulti)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
            if (state.fet.autoGain) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN, state.fet.gain)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
            if (state.fet.autoAttack) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK, state.fet.attack)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK, state.fet.maxAttack)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
            if (state.fet.autoRelease) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE, state.fet.release)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE, state.fet.maxRelease)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_CREST, state.fet.crest)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT, state.fet.adapt)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
            if (state.fet.noClip) 100 else 0
        )

        effect.setParameter(ViperParams.PARAM_HP_DDC_ENABLE, if (state.ddc.enabled) 1 else 0)
        FileLogger.d("Dispatch", "DDC: ${if (state.ddc.enabled) "ON" else "OFF"}")

        effect.setParameter(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE,
            if (state.vse.enabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VSE: ${if (state.vse.enabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(state.vse.strength) { 7600 })
        effect.setParameter(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (state.vse.exciter * 5.6).toInt()
        )

        effect.setParameter(ViperParams.PARAM_HP_EQ_BAND_COUNT, state.eq.bandCount)
        effect.setParameter(ViperParams.PARAM_HP_EQ_ENABLE, if (state.eq.enabled) 1 else 0)
        FileLogger.d(
            "Dispatch",
            "EQ: ${if (state.eq.enabled) "ON" else "OFF"} bands=${state.eq.bandCount}"
        )
        dispatchEqBands(effect, ViperParams.PARAM_HP_EQ_BAND_LEVEL, state.eq.bands)

        effect.setParameter(
            ViperParams.PARAM_HP_CONVOLVER_ENABLE,
            if (state.convolver.enabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Convolver: ${if (state.convolver.enabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL,
            state.convolver.crossChannel
        )

        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE,
            if (state.fieldSurround.enabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "FieldSurround: ${if (state.fieldSurround.enabled) "ON" else "OFF"}"
        )
        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurround.widening) { 0 })
        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE,
            state.fieldSurround.midImage * 10 + 100
        )
        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH,
            state.fieldSurround.depth * 75 + 200
        )

        effect.setParameter(
            ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE,
            if (state.diffSurround.enabled) 1 else 0
        )
        FileLogger.d("Dispatch", "DiffSurround: ${if (state.diffSurround.enabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurround.delay) { 500 })
        effect.setParameter(
            ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE,
            if (state.diffSurround.reverse) 1 else 0
        )

        effect.setParameter(
            ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE,
            if (state.vhe.enabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VHE: ${if (state.vhe.enabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH, state.vhe.quality)

        effect.setParameter(ViperParams.PARAM_HP_REVERB_ENABLE, if (state.reverb.enabled) 1 else 0)
        FileLogger.d("Dispatch", "Reverb: ${if (state.reverb.enabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_SIZE, state.reverb.roomSize * 10)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_WIDTH, state.reverb.width * 10)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING, state.reverb.dampening)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL, state.reverb.wet)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL, state.reverb.dry)

        dispatchDynamicSystem(
            effect,
            state.dynamicSystem.enabled,
            state.dynamicSystem.device,
            state.dynamicSystem.strength,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        effect.setParameter(
            ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE,
            if (state.tube.enabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "TubeSimulator: ${if (state.tube.enabled) "ON" else "OFF"}"
        )

        effect.setParameter(ViperParams.PARAM_HP_BASS_ENABLE, if (state.bass.enabled) 1 else 0)
        FileLogger.d("Dispatch", "Bass: ${if (state.bass.enabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_BASS_MODE, state.bass.mode)
        effect.setParameter(ViperParams.PARAM_HP_BASS_FREQUENCY, state.bass.frequency + 15)
        effect.setParameter(ViperParams.PARAM_HP_BASS_GAIN, state.bass.gain * 50 + 50)
        effect.setParameter(ViperParams.PARAM_HP_BASS_ANTI_POP, if (state.bass.antiPop) 1 else 0)

        effect.setParameter(
            ViperParams.PARAM_HP_BASS_MONO_ENABLE,
            if (state.bassMono.enabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Bass Mono: ${if (state.bassMono.enabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_BASS_MONO_MODE, state.bassMono.mode)
        effect.setParameter(ViperParams.PARAM_HP_BASS_MONO_FREQUENCY, state.bassMono.frequency + 15)
        effect.setParameter(ViperParams.PARAM_HP_BASS_MONO_GAIN, state.bassMono.gain * 50 + 50)
        effect.setParameter(
            ViperParams.PARAM_HP_BASS_MONO_ANTI_POP,
            if (state.bassMono.antiPop) 1 else 0
        )

        effect.setParameter(
            ViperParams.PARAM_HP_CLARITY_ENABLE,
            if (state.clarity.enabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Clarity: ${if (state.clarity.enabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_CLARITY_MODE, state.clarity.mode)
        effect.setParameter(ViperParams.PARAM_HP_CLARITY_GAIN, state.clarity.gain * 50)

        effect.setParameter(ViperParams.PARAM_HP_CURE_ENABLE, if (state.cure.enabled) 1 else 0)
        FileLogger.d("Dispatch", "Cure: ${if (state.cure.enabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_CURE_STRENGTH, state.cure.strength)

        effect.setParameter(ViperParams.PARAM_HP_ANALOGX_ENABLE, if (state.analog.enabled) 1 else 0)
        FileLogger.d("Dispatch", "AnalogX: ${if (state.analog.enabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_ANALOGX_MODE, state.analog.mode)
    }

    fun dispatchSpeakerState(effect: ViperEffect, state: MainUiState) {
        FileLogger.d(
            "Dispatch",
            "Dispatch: speaker outputVol=${state.out.spkVolume} pan=${state.out.spkChannelPan} limiter=${state.out.spkLimiter}"
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(state.out.spkVolume) { 100 })
        effect.setParameter(ViperParams.PARAM_SPK_CHANNEL_PAN, state.out.spkChannelPan)
        effect.setParameter(
            ViperParams.PARAM_SPK_LIMITER,
            OUTPUT_DB_VALUES.getOrElse(state.out.spkLimiter) { 100 })

        effect.setParameter(ViperParams.PARAM_SPK_AGC_ENABLE, if (state.agc.spkEnabled) 1 else 0)
        FileLogger.d("Dispatch", "AGC: ${if (state.agc.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_SPK_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agc.spkStrength) { 50 })
        effect.setParameter(
            ViperParams.PARAM_SPK_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(state.agc.spkMaxGain) { 100 })
        effect.setParameter(
            ViperParams.PARAM_SPK_AGC_VOLUME,
            OUTPUT_DB_VALUES.getOrElse(state.agc.spkOutputThreshold) { 100 })

        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE,
            if (state.fet.spkEnabled) 100 else 0
        )
        FileLogger.d("Dispatch", "FET: ${if (state.fet.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD, state.fet.spkThreshold)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO, state.fet.spkRatio)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
            if (state.fet.spkAutoKnee) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE, state.fet.spkKnee)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI, state.fet.spkKneeMulti)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
            if (state.fet.spkAutoGain) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN, state.fet.spkGain)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
            if (state.fet.spkAutoAttack) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK, state.fet.spkAttack)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK, state.fet.spkMaxAttack)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
            if (state.fet.spkAutoRelease) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE, state.fet.spkRelease)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
            state.fet.spkMaxRelease
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST, state.fet.spkCrest)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT, state.fet.spkAdapt)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
            if (state.fet.spkNoClip) 100 else 0
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_CONVOLVER_ENABLE,
            if (state.convolver.spkEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Convolver: ${if (state.convolver.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL,
            state.convolver.spkCrossChannel
        )

        effect.setParameter(ViperParams.PARAM_SPK_EQ_BAND_COUNT, state.eq.spkBandCount)
        effect.setParameter(ViperParams.PARAM_SPK_EQ_ENABLE, if (state.eq.spkEnabled) 1 else 0)
        FileLogger.d(
            "Dispatch",
            "EQ: ${if (state.eq.spkEnabled) "ON" else "OFF"} bands=${state.eq.spkBandCount}"
        )
        dispatchEqBands(effect, ViperParams.PARAM_SPK_EQ_BAND_LEVEL, state.eq.spkBands)

        effect.setParameter(
            ViperParams.PARAM_SPK_REVERB_ENABLE,
            if (state.reverb.spkEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Reverb: ${if (state.reverb.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_SIZE, state.reverb.spkRoomSize * 10)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH, state.reverb.spkWidth * 10)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING, state.reverb.spkDampening)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL, state.reverb.spkWet)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL, state.reverb.spkDry)

        effect.setParameter(ViperParams.PARAM_SPK_DDC_ENABLE, if (state.ddc.spkEnabled) 1 else 0)
        FileLogger.d("Dispatch", "DDC: ${if (state.ddc.spkEnabled) "ON" else "OFF"}")

        effect.setParameter(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE,
            if (state.vse.spkEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VSE: ${if (state.vse.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(state.vse.spkStrength) { 7600 })
        effect.setParameter(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (state.vse.spkExciter * 5.6).toInt()
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE,
            if (state.fieldSurround.spkEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "FieldSurround: ${if (state.fieldSurround.spkEnabled) "ON" else "OFF"}"
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurround.spkWidening) { 0 })
        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE,
            state.fieldSurround.spkMidImage * 10 + 100
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH,
            state.fieldSurround.spkDepth * 75 + 200
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE,
            if (state.speakerOptEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "SpeakerOpt: ${if (state.speakerOptEnabled) "ON" else "OFF"}")

        effect.setParameter(
            ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE,
            if (state.diffSurround.spkEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "DiffSurround: ${if (state.diffSurround.spkEnabled) "ON" else "OFF"}"
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurround.spkDelay) { 500 })
        effect.setParameter(
            ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE,
            if (state.diffSurround.spkReverse) 1 else 0
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE,
            if (state.vhe.spkEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VHE: ${if (state.vhe.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH, state.vhe.spkQuality)

        dispatchDynamicSystem(
            effect,
            state.dynamicSystem.spkEnabled,
            state.dynamicSystem.spkDevice,
            state.dynamicSystem.spkStrength,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE,
            if (state.tube.spkEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "TubeSimulator: ${if (state.tube.spkEnabled) "ON" else "OFF"}"
        )

        effect.setParameter(ViperParams.PARAM_SPK_BASS_ENABLE, if (state.bass.spkEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Bass: ${if (state.bass.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_BASS_MODE, state.bass.spkMode)
        effect.setParameter(ViperParams.PARAM_SPK_BASS_FREQUENCY, state.bass.spkFrequency + 15)
        effect.setParameter(ViperParams.PARAM_SPK_BASS_GAIN, state.bass.spkGain * 50 + 50)
        effect.setParameter(
            ViperParams.PARAM_SPK_BASS_ANTI_POP,
            if (state.bass.spkAntiPop) 1 else 0
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_BASS_MONO_ENABLE,
            if (state.bassMono.spkEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Bass Mono: ${if (state.bassMono.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_BASS_MONO_MODE, state.bassMono.spkMode)
        effect.setParameter(
            ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY,
            state.bassMono.spkFrequency + 15
        )
        effect.setParameter(ViperParams.PARAM_SPK_BASS_MONO_GAIN, state.bassMono.spkGain * 50 + 50)
        effect.setParameter(
            ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP,
            if (state.bassMono.spkAntiPop) 1 else 0
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_CLARITY_ENABLE,
            if (state.clarity.spkEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Clarity: ${if (state.clarity.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_CLARITY_MODE, state.clarity.spkMode)
        effect.setParameter(ViperParams.PARAM_SPK_CLARITY_GAIN, state.clarity.spkGain * 50)

        effect.setParameter(ViperParams.PARAM_SPK_CURE_ENABLE, if (state.cure.spkEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Cure: ${if (state.cure.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_CURE_STRENGTH, state.cure.spkStrength)

        effect.setParameter(
            ViperParams.PARAM_SPK_ANALOGX_ENABLE,
            if (state.analog.spkEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "AnalogX: ${if (state.analog.spkEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_ANALOGX_MODE, state.analog.spkMode)
    }

    fun dispatchEqBands(effect: ViperEffect, param: Int, bandsString: String) {
        val bands = bandsString.split(";").filter { it.isNotBlank() }
        for ((index, bandStr) in bands.withIndex()) {
            val level = (bandStr.toFloatOrNull() ?: 0f) * 100
            effect.setParameter(param, index, level.toInt())
        }
    }

    private fun dispatchDynamicSystem(
        effect: ViperEffect,
        enabled: Boolean,
        deviceIndex: Int,
        strength: Int,
        paramEnable: Int,
        paramStrength: Int,
        paramXCoeffs: Int,
        paramYCoeffs: Int,
        paramSideGain: Int
    ) {
        effect.setParameter(paramEnable, if (enabled) 1 else 0)
        FileLogger.d(
            "Dispatch",
            "DynamicSystem: ${if (enabled) "ON" else "OFF"} device=$deviceIndex strength=$strength"
        )
        effect.setParameter(paramStrength, strength * 20 + 100)
        val dsCoeffs = DYNAMIC_SYSTEM_DEVICES.getOrElse(deviceIndex) { "100;5600;40;80;50;50" }
        val dsParts = dsCoeffs.split(";").map { it.toIntOrNull() ?: 0 }
        if (dsParts.size >= 6) {
            effect.setParameter(paramXCoeffs, dsParts[0], dsParts[1])
            effect.setParameter(paramYCoeffs, dsParts[2], dsParts[3])
            effect.setParameter(paramSideGain, dsParts[4], dsParts[5])
        }
    }

    suspend fun loadFullStateFromPrefs(repository: ViperRepository): MainUiState {
        val fxType =
            repository.getIntPreference(ViperRepository.PREF_FX_TYPE, ViperParams.FX_TYPE_HEADPHONE)
                .first()
        var s = loadEffectPrefs(repository, isSpk = false)
        s = loadEffectPrefs(repository, isSpk = true, state = s)
        val eqBands = ensureBandCount(s.eq.bands, s.eq.bandCount)
        val spkEqBands = ensureBandCount(s.eq.spkBands, s.eq.spkBandCount)
        return s.copy(fxType = fxType, eq = s.eq.copy(bands = eqBands, spkBands = spkEqBands))
    }
}

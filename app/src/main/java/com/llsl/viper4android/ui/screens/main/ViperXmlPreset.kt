package com.llsl.viper4android.ui.screens.main

import org.json.JSONObject

/**
 * Import legacy ViPER4Android XML presets into the app's preset format.
 *
 * Handles the v2.7.2.x `<map>` layout and the older layouts before it (different
 * parameter ids and value ranges), translating them into the same JSON the app
 * uses for its own presets.
 *
 * Parsing is deliberately lenient (not a strict XML parser): some presets in the
 * wild have a mangled `&` in kernel names, single-quote xml declarations, extra
 * fields, and spaces before `/>`.
 */
object ViperXmlPreset {
    /**
     * Is this a legacy ViPER xml preset? Identified by the master-enable param
     * (id 36868), which every preset carries in every version/layout. The app's
     * own json presets use the "masterEnabled"/"spkMasterEnabled" keys instead,
     * so this also tells the two formats apart (and rejects xml that isn't a
     * ViPER preset).
     */
    fun isViperXml(content: String): Boolean = content.contains("name=\"36868\"")

    /**
     * Speaker (true) or headphone (false). Newer presets store the mode in
     * param 32775; older ones don't, so fall back to the output type in the
     * file name (device keywords win over "speaker").
     */
    fun isSpeaker(
        content: String,
        fileName: String,
    ): Boolean {
        Regex("""name="32775"\s+value="([12])"""").find(content)?.let {
            return it.groupValues[1] == "2"
        }
        val n = fileName.lowercase()
        return when {
            n.contains("bluetooth") || n.contains("headset") || n.contains("usb") -> false
            n.contains("speaker") -> true
            else -> false
        }
    }

    /** Translate a legacy ViPER xml preset into the app's json preset. */
    fun toJson(
        content: String,
        isSpk: Boolean,
    ): JSONObject {
        val xv = parse(content)
        normalize(xv)

        var state = MainUiState()
        val prefByKey = EFFECT_PREFS.associateBy { it.jsonKey }

        fun setB(
            key: String,
            v: Boolean,
        ) {
            (prefByKey[key] as? BoolPref)?.let { state = if (isSpk) it.setSpk(state, v) else it.setHp(state, v) }
        }

        fun setI(
            key: String,
            v: Int,
        ) {
            (prefByKey[key] as? IntPref)?.let { state = if (isSpk) it.setSpk(state, v) else it.setHp(state, v) }
        }

        fun setS(
            key: String,
            v: String,
        ) {
            (prefByKey[key] as? StringPref)?.let { state = if (isSpk) it.setSpk(state, v) else it.setHp(state, v) }
        }

        fun setL(
            key: String,
            v: Long?,
        ) {
            (prefByKey[key] as? NullableLongPref)?.let { state = if (isSpk) it.setSpk(state, v) else it.setHp(state, v) }
        }

        fun int(id: String): Int? = xv[id]?.trim()?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

        // booleans copied straight across
        for ((id, key) in BOOL_MAP) xv[id]?.let { setB(key, it.trim() == "true") }
        // same-scale integers
        for ((id, key) in INT_MAP) int(id)?.let { setI(key, it) }
        // verbatim strings
        for ((id, key) in STR_MAP) xv[id]?.let { setS(key, it) }
        // mode integers stored as <string>
        for ((id, key) in MODE_MAP) xv[id]?.trim()?.toIntOrNull()?.let { setI(key, it) }

        // bass and clarity gain use a x50 scale in the app
        int("65577")?.let { setI("bassGain", it * 50 + 50) }
        int("65580")?.let { setI("clarityGain", it * 50) }

        // dynamic system "device" packs six numbers:
        // xLow;xHigh;yLow;yHigh;sideLow;sideHigh
        xv["65570;65571;65572"]?.takeIf { it.isNotEmpty() }?.split(";")?.let { p ->
            if (p.size >= 6) {
                p[0].toIntOrNull()?.let { setI("dsXLow", it) }
                p[1].toIntOrNull()?.let { setI("dsXHigh", it) }
                p[2].toIntOrNull()?.let { setI("dsYLow", it) }
                p[3].toIntOrNull()?.let { setI("dsYHigh", it) }
                p[4].toIntOrNull()?.let { setI("dsSideGainLow", it) }
                p[5].toIntOrNull()?.let { setI("dsSideGainHigh", it) }
                setI("dynamicSystemDevice", 0)
                setL("dsPresetId", null)
            }
        }

        // the equalizer is always a 10 band curve here
        setI("eqBandCount", 10)

        // turn the master switch and gain control on so the preset does
        // something when it is applied
        setB("masterEnabled", true)
        setB("agcEnabled", true)

        return serializeEffectPrefs(state, isSpk)
    }

    /** Parse the `<map>` body into id -> value (value attr for int/boolean, text for string). */
    private fun parse(content: String): MutableMap<String, String> {
        val xv = HashMap<String, String>()
        val element = Regex("""<(int|boolean|string)\s+name="([^"]*)"""")
        val valueAttr = Regex("""value="([^"]*)"""")
        val stringText = Regex(""">([^<]*)</string>""")
        for (line in content.lineSequence()) {
            val m = element.find(line) ?: continue
            val tag = m.groupValues[1]
            val name = m.groupValues[2]
            if (tag == "string") {
                // repair the mangled "&" some presets wrote ("></string>amp;")
                val repaired =
                    line
                        .replace("></string>amp;", "&amp;")
                        .replace(">Select impulse response file</string>amp;", "&amp;")
                var v =
                    stringText
                        .find(repaired)
                        ?.groupValues
                        ?.get(1)
                        ?.let { xmlUnescape(it) } ?: ""
                // "no kernel selected" placeholder text means an empty kernel
                v =
                    v
                        .replace("Select impulse response file", "")
                        .replace("Choose Impulse Response", "")
                        .replace("Selecione o arquivo de impulso de resposta", "")
                if (v == "Kernel") v = ""
                xv[name] = v
            } else {
                xv[name] = valueAttr.find(line)?.groupValues?.get(1) ?: ""
            }
        }
        return xv
    }

    /** Bring older preset layouts up to the v2.7.2.x one. */
    private fun normalize(xv: MutableMap<String, String>) {
        fun adopt(
            old: String,
            new: String,
        ) {
            if (xv.containsKey(old) && !xv.containsKey(new)) xv[new] = xv[old]!!
        }

        // room size / width moved id and used to be 0-100 instead of 0-10
        fun adoptRoom(
            old: String,
            new: String,
        ) {
            val n = (xv[new] ?: xv[old])?.toIntOrNull() ?: return
            xv[new] = (if (n > 10) n / 10 else n).toString()
        }
        // the FET compressor block used a different run of ids (offset by 17)
        for (j in 0..16) adopt((65627 + j).toString(), (65610 + j).toString())
        adopt("65595", "65551")
        adopt("65596", "65552") // equalizer
        adopt("65589", "65538")
        adopt("65591;65592;65593", "65540;65541;65542") // convolver
        adopt("65594", "65543")
        adopt("65597", "65559")
        adopt("65600", "65562") // reverb
        adopt("65601", "65563")
        adopt("65602", "65564")
        adoptRoom("65598", "65560")
        adoptRoom("65599", "65561")

        // a few sliders older presets stored on a wider scale (rescale only when
        // the value is out of the current range)
        fun rescale(
            id: String,
            max: Int,
            f: (Int) -> Int,
        ) {
            val n = xv[id]?.toIntOrNull() ?: return
            if (n > max) xv[id] = f(n).toString()
        }
        rescale("65554;65556", 8) { (it - 120) / 10 }
        rescale("65555", 10) { (it - 120) / 10 }
        rescale("65558", 19) { it / 100 - 1 }
        rescale("65573", 100) { (it - 100) / 20 }
        rescale("65576", 135) { it - 15 }
        rescale("65577", 11) { (it - 50) / 50 }
        rescale("65580", 9) { it / 50 }
    }

    private fun xmlUnescape(s: String): String =
        s
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    // parameter id -> json key, for values that copy straight across
    private val BOOL_MAP =
        mapOf(
            "36868" to "masterEnabled",
            "65565" to "agcEnabled",
            "65610" to "fetEnabled",
            "65614" to "fetAutoKnee",
            "65616" to "fetAutoGain",
            "65618" to "fetAutoAttack",
            "65620" to "fetAutoRelease",
            "65626" to "fetNoClip",
            "65546" to "ddcEnabled",
            "65548" to "vseEnabled",
            "65551" to "eqEnabled",
            "65538" to "convolverEnabled",
            "65553" to "fieldSurroundEnabled",
            "65557" to "diffSurroundEnabled",
            "65544" to "vheEnabled",
            "65559" to "reverbEnabled",
            "65569" to "dynamicSystemEnabled",
            "65583" to "tubeSimulatorEnabled",
            "65574" to "bassEnabled",
            "65578" to "clarityEnabled",
            "65581" to "cureEnabled",
            "65584" to "analogxEnabled",
            "65603" to "speakerOptEnabled",
        )
    private val INT_MAP =
        mapOf(
            "65611" to "fetThreshold",
            "65612" to "fetRatio",
            "65613" to "fetKnee",
            "65615" to "fetGain",
            "65617" to "fetAttack",
            "65619" to "fetRelease",
            "65621" to "fetKneeMulti",
            "65622" to "fetMaxAttack",
            "65623" to "fetMaxRelease",
            "65624" to "fetCrest",
            "65625" to "fetAdapt",
            "65543" to "convolverCrossChannel",
            "65555" to "fieldSurroundMidImage",
            "65554;65556" to "fieldSurroundWidening",
            "65558" to "diffSurroundDelay",
            "65545" to "vheQuality",
            "65560" to "reverbRoomSize",
            "65561" to "reverbWidth",
            "65562" to "reverbDampening",
            "65563" to "reverbWet",
            "65564" to "reverbDry",
            "65573" to "dynamicSystemStrength",
            "65576" to "bassFrequency",
            "65582" to "cureStrength",
            "65585" to "analogxMode",
        )
    private val STR_MAP =
        mapOf(
            "65552" to "eqBands",
            "65547" to "ddcDevice",
            "65540;65541;65542" to "convolverKernel",
        )

    // mode stored as a <string> in xml but a number in the app
    private val MODE_MAP = mapOf("65575" to "bassMode", "65579" to "clarityMode")
}

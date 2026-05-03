package com.llsl.viper4android.ui.screens.main

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.ByteArrayParam
import com.llsl.viper4android.audio.ConfigChannel
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.audio.ParamEntry
import com.llsl.viper4android.audio.ViperEffect
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.service.ViperService
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import javax.inject.Inject

data class DriverStatus(
    val installed: Boolean = false,
    val versionCode: Int = -1,
    val versionName: String = "",
    val architecture: String = "",
    val streaming: Boolean = false,
    val samplingRate: Int = 0
)

data class MainUiState(
    val fxType: Int = ViperParams.FX_TYPE_HEADPHONE,

    val masterEnabled: Boolean = false,
    val spkMasterEnabled: Boolean = false,

    val out: OutputState = OutputState(),
    val agc: AgcState = AgcState(),
    val fet: FetState = FetState(),
    val ddc: DdcState = DdcState(),
    val vse: VseState = VseState(),
    val eq: EqState = EqState(),
    val convolver: ConvolverState = ConvolverState(),
    val fieldSurround: FieldSurroundState = FieldSurroundState(),
    val diffSurround: DiffSurroundState = DiffSurroundState(),
    val vhe: VheState = VheState(),
    val reverb: ReverbState = ReverbState(),
    val dynamicSystem: DynamicSystemState = DynamicSystemState(),
    val bass: BassState = BassState(),
    val bassMono: BassMonoState = BassMonoState(),
    val clarity: ClarityState = ClarityState(),
    val cure: CureState = CureState(),
    val analog: AnalogXState = AnalogXState(),
    val tube: TubeSimulatorState = TubeSimulatorState(),

    val speakerOptEnabled: Boolean = false,

    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: ViperRepository
) : AndroidViewModel(application) {

    companion object {
        val OUTPUT_VOLUME_VALUES get() = EffectDispatcher.OUTPUT_VOLUME_VALUES
        val OUTPUT_DB_VALUES get() = EffectDispatcher.OUTPUT_DB_VALUES
        val PLAYBACK_GAIN_RATIO_VALUES get() = EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES
        val MULTI_FACTOR_VALUES get() = EffectDispatcher.MULTI_FACTOR_VALUES
        val VSE_BARK_VALUES get() = EffectDispatcher.VSE_BARK_VALUES
        val DIFF_SURROUND_DELAY_VALUES get() = EffectDispatcher.DIFF_SURROUND_DELAY_VALUES
        val FIELD_SURROUND_WIDENING_VALUES get() = EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES
        val BASS_GAIN_DB_LABELS get() = EffectDispatcher.BASS_GAIN_DB_LABELS
        val BASS_SUBWOOFER_GAIN_DB_LABELS get() = EffectDispatcher.BASS_SUBWOOFER_GAIN_DB_LABELS
        val CLARITY_GAIN_DB_LABELS get() = EffectDispatcher.CLARITY_GAIN_DB_LABELS

        const val PREF_AUTO_START = "auto_start"
        const val PREF_AIDL_MODE = "aidl_mode"
        const val PREF_GLOBAL_MODE = "global_mode"
        const val PREF_DEBUG_MODE = "debug_mode"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val presetList: StateFlow<List<Preset>> = repository.getAllPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceSettingsList: StateFlow<List<DeviceSettings>> = repository.getAllDeviceSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _driverStatus = MutableStateFlow(DriverStatus())
    val driverStatus: StateFlow<DriverStatus> = _driverStatus.asStateFlow()

    private val _vdcFileList = MutableStateFlow<List<String>>(emptyList())
    val vdcFileList: StateFlow<List<String>> = _vdcFileList.asStateFlow()

    private val _kernelFileList = MutableStateFlow<List<String>>(emptyList())
    val kernelFileList: StateFlow<List<String>> = _kernelFileList.asStateFlow()

    private val _autoStartEnabled = MutableStateFlow(false)
    val autoStartEnabled: StateFlow<Boolean> = _autoStartEnabled.asStateFlow()


    private val _aidlModeEnabled = MutableStateFlow(false)
    val aidlModeEnabled: StateFlow<Boolean> = _aidlModeEnabled.asStateFlow()

    private val _globalModeEnabled = MutableStateFlow(false)
    val globalModeEnabled: StateFlow<Boolean> = _globalModeEnabled.asStateFlow()

    private val _debugModeEnabled = MutableStateFlow(false)
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    private var viperService: ViperService? = null
    private var serviceBound = false
    private val audioOutputDetector = AudioOutputDetector(application)
    private var activeDeviceType: Int = ViperParams.FX_TYPE_HEADPHONE
    private var currentDeviceId: String = AudioDevice.ID_SPEAKER
    private var eqPresetsJob: Job? = null
    private var spkEqPresetsJob: Job? = null
    private var dsPresetsJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? ViperService.LocalBinder ?: return
            viperService = localBinder.service
            serviceBound = true
            applyFullState()
            queryDriverStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viperService = null
            serviceBound = false
        }
    }

    init {
        loadSettingsPreferences()
        refreshFileLists()
        val initialDevice = audioOutputDetector.activeDevice.value
        currentDeviceId = initialDevice.id
        val initialFxType =
            if (initialDevice.isHeadphone) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
        activeDeviceType = initialFxType
        viewModelScope.launch {
            loadInitialState()
            val initialDbName =
                repository.getDeviceSettings(initialDevice.id)?.deviceName ?: initialDevice.name
            _uiState.update {
                it.copy(
                    fxType = initialFxType,
                    activeDeviceName = initialDbName,
                    activeDeviceId = initialDevice.id
                )
            }
            loadEqPresetsForBandCount(_uiState.value.eq.bandCount, isSpk = false)
            loadEqPresetsForBandCount(_uiState.value.eq.spkBandCount, isSpk = true)
            loadDsPresets()
            loadDeviceSettings(initialDevice)
            ensureDeviceEntry(initialDevice)
            bindToService()
            audioOutputDetector.activeDevice.collect { device ->
                val detectedType =
                    if (device.isHeadphone) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
                if (device.id != currentDeviceId) {
                    saveCurrentDeviceSettings()
                    activeDeviceType = detectedType
                    currentDeviceId = device.id
                    val dbName = repository.getDeviceSettings(device.id)?.deviceName ?: device.name
                    _uiState.update {
                        it.copy(
                            fxType = detectedType,
                            activeDeviceName = dbName,
                            activeDeviceId = device.id
                        )
                    }
                    repository.setIntPreference(ViperRepository.PREF_FX_TYPE, detectedType)
                    ConfigChannel.setActiveFxType(detectedType)
                    loadDeviceSettings(device)
                }
                ensureDeviceEntry(device)
            }
        }
    }

    private fun loadDsPresets() {
        dsPresetsJob?.cancel()
        dsPresetsJob = viewModelScope.launch {
            repository.getAllDsPresets().collect { presets ->
                _uiState.update {
                    it.copy(
                        dynamicSystem = it.dynamicSystem.copy(
                            presets = presets,
                            spkPresets = presets
                        )
                    )
                }
            }
        }
    }

    private fun bindToService() {
        val intent = Intent(getApplication(), ViperService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCleared() {
        super.onCleared()
        runBlocking(Dispatchers.IO) { saveCurrentDeviceSettings() }
        audioOutputDetector.stop()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        viperService = null
    }

    private suspend fun loadInitialState() {
        loadHeadphonePreferences()
        loadSpeakerPreferences()
    }

    private fun loadEqPresetsForBandCount(bandCount: Int, isSpk: Boolean) {
        if (isSpk) {
            spkEqPresetsJob?.cancel()
            spkEqPresetsJob = viewModelScope.launch {
                repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                    _uiState.update { it.copy(eq = it.eq.copy(spkPresets = presets)) }
                }
            }
        } else {
            eqPresetsJob?.cancel()
            eqPresetsJob = viewModelScope.launch {
                repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                    _uiState.update { it.copy(eq = it.eq.copy(presets = presets)) }
                }
            }
        }
    }

    private suspend fun loadHeadphonePreferences() {
        val fxType =
            repository.getIntPreference(ViperRepository.PREF_FX_TYPE, ViperParams.FX_TYPE_HEADPHONE)
                .first()
        val state = loadEffectPrefs(repository, isSpk = false, state = _uiState.value)

        val eqBandCount = state.eq.bandCount
        val rawEqBands = state.eq.bands
        val parsedBandCount = rawEqBands.split(";").count { it.isNotBlank() }
        val eqBands = if (parsedBandCount != eqBandCount) {
            List(eqBandCount) { 0f }.joinToString(";") {
                String.format(Locale.US, "%.1f", it)
            } + ";"
        } else {
            rawEqBands
        }
        val eqBandsMap = mutableMapOf<Int, String>()
        for (bc in listOf(10, 15, 25, 31)) {
            val defaultBands =
                List(bc) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
            eqBandsMap[bc] = repository.getStringPreference("eq_bands_$bc", defaultBands).first()
        }
        eqBandsMap[eqBandCount] = eqBands

        _uiState.update {
            state.copy(fxType = fxType, eq = state.eq.copy(bands = eqBands, bandsMap = eqBandsMap))
        }
    }

    private suspend fun loadSpeakerPreferences() {
        val state = loadEffectPrefs(repository, isSpk = true, state = _uiState.value)

        val spkEqBandCount = state.eq.spkBandCount
        val rawSpkEqBands = state.eq.spkBands
        val parsedSpkBandCount = rawSpkEqBands.split(";").count { it.isNotBlank() }
        val spkEqBands = if (parsedSpkBandCount != spkEqBandCount) {
            List(spkEqBandCount) { 0f }.joinToString(";") {
                String.format(Locale.US, "%.1f", it)
            } + ";"
        } else {
            rawSpkEqBands
        }
        val spkEqBandsMap = mutableMapOf<Int, String>()
        for (bc in listOf(10, 15, 25, 31)) {
            val defaultBands =
                List(bc) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
            spkEqBandsMap[bc] =
                repository.getStringPreference("spk_eq_bands_$bc", defaultBands).first()
        }
        spkEqBandsMap[spkEqBandCount] = spkEqBands

        _uiState.update {
            state.copy(eq = state.eq.copy(spkBands = spkEqBands, spkBandsMap = spkEqBandsMap))
        }
    }

    private fun applyFullState() {
        val service = viperService ?: return
        val state = _uiState.value
        ConfigChannel.setActiveFxType(activeDeviceType)
        val isMasterOn =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.spkMasterEnabled else state.masterEnabled
        val mode = if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
        FileLogger.d(
            "ViewModel",
            "Dispatch: applyFullState mode=$mode master=${if (isMasterOn) "ON" else "OFF"}"
        )

        val byteArrayParams = mutableListOf<ByteArrayParam>()

        val ddcEnabled =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.ddc.spkEnabled else state.ddc.enabled
        val ddcDevice =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.ddc.spkDevice else state.ddc.device
        FileLogger.i("ViewModel", "applyFullState: ddcEnabled=$ddcEnabled ddcDevice='$ddcDevice'")
        if (ddcEnabled && ddcDevice.isNotEmpty()) {
            val ba = prepareDdcByteArray(ddcDevice)
            FileLogger.i("ViewModel", "applyFullState: DDC byteArray=${ba?.data?.size ?: "null"}")
            ba?.let { byteArrayParams.add(it) }
        }

        val convolverEnabled =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.convolver.spkEnabled else state.convolver.enabled
        val kernel =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.convolver.spkKernel else state.convolver.kernel
        FileLogger.i(
            "ViewModel",
            "applyFullState: convolverEnabled=$convolverEnabled kernel='$kernel'"
        )
        if (convolverEnabled && kernel.isNotEmpty()) {
            val ba = prepareConvolverByteArray(kernel)
            FileLogger.i(
                "ViewModel",
                "applyFullState: convolver byteArray=${ba?.data?.size ?: "null"}"
            )
            ba?.let { byteArrayParams.add(it) }
        }

        service.dispatchFullState(
            state.copy(fxType = activeDeviceType),
            isMasterOn,
            byteArrayParams.ifEmpty { null }
        )
    }

    private fun prepareDdcByteArray(name: String): ByteArrayParam? {
        return try {
            val file = File(getFilesDir("DDC"), "$name.vdc")
            FileLogger.i(
                "ViewModel",
                "prepareDdc: file=${file.absolutePath} exists=${file.exists()}"
            )
            if (!file.exists()) return null
            val lines = file.readLines()
            var coeffs44100: FloatArray? = null
            var coeffs48000: FloatArray? = null
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SR_44100:") -> {
                        coeffs44100 = trimmed.removePrefix("SR_44100:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }

                    trimmed.startsWith("SR_48000:") -> {
                        coeffs48000 = trimmed.removePrefix("SR_48000:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }
                }
            }
            if (coeffs44100 == null || coeffs48000 == null) return null
            if (coeffs44100.size != coeffs48000.size) return null
            if (coeffs44100.size % 5 != 0) return null
            val arrSize = coeffs44100.size
            val naturalSize = 4 + arrSize * 4 * 2
            val wireSize = when {
                naturalSize <= 256 -> 256
                naturalSize <= 1024 -> 1024
                else -> return null
            }
            val buffer = ByteBuffer.allocate(wireSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(arrSize)
            for (f in coeffs44100) buffer.putFloat(f)
            for (f in coeffs48000) buffer.putFloat(f)
            ByteArrayParam(ViperParams.PARAM_HP_DDC_COEFFICIENTS, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to prepare DDC: $name", e)
            null
        }
    }

    private fun prepareConvolverByteArray(fileName: String): ByteArrayParam? {
        if (!_aidlModeEnabled.value) return null
        return try {
            val file = File(getFilesDir("Kernel"), fileName)
            FileLogger.i(
                "ViewModel",
                "prepareConvolver: file=${file.absolutePath} exists=${file.exists()}"
            )
            if (!file.exists()) return null
            val safeName = fileName.replace("'", "")
            val subDir = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
            val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
            RootShell.copyFile(file, kernelPath)
            FileLogger.i("ViewModel", "Kernel copied to $kernelPath (for full state)")
            val param = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL else ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
            val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(pathBytes.size)
            buffer.put(pathBytes)
            ByteArrayParam(param, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to prepare kernel: $fileName", e)
            null
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"} (headphone)")
        _uiState.update { it.copy(masterEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(ViperRepository.PREF_MASTER_ENABLE, enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            viperService?.setEffectEnabled(enabled)
            if (enabled) applyFullState()
        }
    }

    fun setSpkMasterEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"} (speaker)")
        _uiState.update { it.copy(spkMasterEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperRepository.PREF_MASTER_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            viperService?.setEffectEnabled(enabled)
            if (enabled) applyFullState()
        }
    }

    fun setFxType(type: Int) {
        val mode = if (type == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
        FileLogger.i("ViewModel", "Dispatch: fxType=$mode")
        _uiState.update { it.copy(fxType = type) }
        viewModelScope.launch {
            repository.setIntPreference(ViperRepository.PREF_FX_TYPE, type)
        }
        ConfigChannel.setActiveFxType(type)
        applyFullState()
    }

    fun setOutputVolume(value: Int) {
        _uiState.update { it.copy(out = it.out.copy(volume = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_OUTPUT_VOLUME}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(value) { 100 })
    }

    fun setChannelPan(value: Int) {
        _uiState.update { it.copy(out = it.out.copy(channelPan = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CHANNEL_PAN}",
            ViperParams.PARAM_HP_CHANNEL_PAN,
            value
        )
    }

    fun setLimiter(value: Int) {
        _uiState.update { it.copy(out = it.out.copy(limiter = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_LIMITER}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_LIMITER, OUTPUT_DB_VALUES.getOrElse(value) { 100 })
    }

    fun setAgcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "AGC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(agc = it.agc.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_AGC_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_AGC_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(
                        ViperParams.PARAM_HP_AGC_RATIO,
                        intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agc.strength) { 50 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_AGC_MAX_SCALER,
                        intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(state.agc.maxGain) { 100 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_AGC_VOLUME,
                        intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.agc.outputThreshold) { 100 })
                    )
                )
            )
        }
    }

    fun setAgcStrength(value: Int) {
        _uiState.update { it.copy(agc = it.agc.copy(strength = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_AGC_RATIO}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(value) { 50 })
    }

    fun setAgcMaxGain(value: Int) {
        _uiState.update { it.copy(agc = it.agc.copy(maxGain = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_AGC_MAX_SCALER}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(value) { 100 })
    }

    fun setAgcOutputThreshold(value: Int) {
        _uiState.update { it.copy(agc = it.agc.copy(outputThreshold = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_AGC_VOLUME}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_AGC_VOLUME, OUTPUT_DB_VALUES.getOrElse(value) { 100 })
    }

    fun setFetEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "FET Compressor: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(fet = it.fet.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE,
                        intArrayOf(if (enabled) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD,
                        intArrayOf(state.fet.threshold)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO,
                        intArrayOf(state.fet.ratio)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
                        intArrayOf(if (state.fet.autoKnee) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE,
                        intArrayOf(state.fet.knee)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI,
                        intArrayOf(state.fet.kneeMulti)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
                        intArrayOf(if (state.fet.autoGain) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN,
                        intArrayOf(state.fet.gain)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
                        intArrayOf(if (state.fet.autoAttack) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK,
                        intArrayOf(state.fet.attack)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK,
                        intArrayOf(state.fet.maxAttack)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
                        intArrayOf(if (state.fet.autoRelease) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE,
                        intArrayOf(state.fet.release)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE,
                        intArrayOf(state.fet.maxRelease)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_CREST,
                        intArrayOf(state.fet.crest)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT,
                        intArrayOf(state.fet.adapt)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
                        intArrayOf(if (state.fet.noClip) 100 else 0)
                    )
                )
            )
        }
    }

    fun setFetThreshold(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(threshold = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD,
            value
        )
    }

    fun setFetRatio(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(ratio = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO,
            value
        )
    }

    fun setFetAutoKnee(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(autoKnee = value)) }
        saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
            value
        )
    }

    fun setFetKnee(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(knee = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE,
            value
        )
    }

    fun setFetKneeMulti(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(kneeMulti = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI,
            value
        )
    }

    fun setFetAutoGain(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(autoGain = value)) }
        saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
            value
        )
    }

    fun setFetGain(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(gain = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN,
            value
        )
    }

    fun setFetAutoAttack(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(autoAttack = value)) }
        saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
            value
        )
    }

    fun setFetAttack(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(attack = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK,
            value
        )
    }

    fun setFetMaxAttack(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(maxAttack = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK,
            value
        )
    }

    fun setFetAutoRelease(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(autoRelease = value)) }
        saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
            value
        )
    }

    fun setFetRelease(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(release = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE,
            value
        )
    }

    fun setFetMaxRelease(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(maxRelease = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE,
            value
        )
    }

    fun setFetCrest(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(crest = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_CREST}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_CREST,
            value
        )
    }

    fun setFetAdapt(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(adapt = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT,
            value
        )
    }

    fun setFetNoClip(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(noClip = value)) }
        saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
            value
        )
    }

    fun setDdcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "DDC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(ddc = it.ddc.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_DDC_ENABLE}",
                enabled
            )
        }
        val device = _uiState.value.ddc.device
        val effectiveEnabled = enabled && device.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            loadVdcByName(device, ViperParams.PARAM_HP_DDC_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchInt(ViperParams.PARAM_HP_DDC_ENABLE, 0)
        }
    }

    fun setDdcDevice(device: String) {
        FileLogger.i("ViewModel", "DDC selected: $device")
        _uiState.update { it.copy(ddc = it.ddc.copy(device = device)) }
        viewModelScope.launch {
            repository.setStringPreference(
                ViperRepository.PREF_DDC_DEVICE,
                device
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            if (device.isEmpty()) {
                dispatchInt(ViperParams.PARAM_HP_DDC_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.ddc.enabled) ViperParams.PARAM_HP_DDC_ENABLE else null
                loadVdcByName(device, enableParam)
            }
        }
    }

    fun setVseEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "VSE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(vse = it.vse.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
                        intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(state.vse.strength) { 7600 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                        intArrayOf((state.vse.exciter * 5.6).toInt())
                    )
                )
            )
        }
    }

    fun setVseStrength(value: Int) {
        _uiState.update { it.copy(vse = it.vse.copy(strength = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(value) { 7600 })
    }

    fun setVseExciter(value: Int) {
        _uiState.update { it.copy(vse = it.vse.copy(exciter = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (value * 5.6).toInt()
        )
    }

    fun setEqEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "EQ: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(eq = it.eq.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_EQ_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_EQ_ENABLE, intArrayOf(if (enabled) 1 else 0))
                )
            )
        }
    }

    fun setEqPreset(presetId: Long) {
        val state = _uiState.value
        val preset = state.eq.presets.find { it.id == presetId } ?: return
        val bands = preset.bands
        val bandCount = state.eq.bandCount
        _uiState.update { state ->
            val updatedMap = state.eq.bandsMap.toMutableMap().apply { put(bandCount, bands) }
            state.copy(
                eq = state.eq.copy(
                    presetId = presetId,
                    bands = bands,
                    bandsMap = updatedMap
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, presetId.toInt())
            repository.setStringPreference("${ViperParams.PARAM_HP_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("eq_bands_$bandCount", bands)
        }
        hpDispatchEqBands(bands)
    }

    fun setEqBands(bands: String) {
        val bandCount = _uiState.value.eq.bandCount
        _uiState.update { state ->
            val updatedMap = state.eq.bandsMap.toMutableMap().apply { put(bandCount, bands) }
            state.copy(eq = state.eq.copy(bands = bands, bandsMap = updatedMap))
        }
        viewModelScope.launch {
            repository.setStringPreference("${ViperParams.PARAM_HP_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("eq_bands_$bandCount", bands)
        }
        hpDispatchEqBands(bands)
    }

    fun setEqBandCount(count: Int) {
        val currentState = _uiState.value
        val oldCount = currentState.eq.bandCount
        FileLogger.d("ViewModel", "EQ band count: $oldCount -> $count")
        val updatedMap = currentState.eq.bandsMap.toMutableMap().apply {
            put(oldCount, currentState.eq.bands)
        }
        val defaultBands =
            List(count) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        val bands = updatedMap[count] ?: defaultBands
        _uiState.update {
            it.copy(
                eq = it.eq.copy(
                    bandCount = count,
                    bands = bands,
                    presetId = null,
                    bandsMap = updatedMap
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_EQ_BAND_COUNT}", count)
            repository.setStringPreference("${ViperParams.PARAM_HP_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("eq_bands_$oldCount", currentState.eq.bands)
            repository.setStringPreference("eq_bands_$count", bands)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchEqBands(
                ViperParams.PARAM_HP_EQ_BAND_LEVEL,
                bands,
                ViperParams.PARAM_HP_EQ_BAND_COUNT,
                count
            )
        }
        loadEqPresetsForBandCount(count, isSpk = false)
    }

    fun addEqPreset(name: String) {
        val state = _uiState.value
        val preset = EqPreset(name = name, bandCount = state.eq.bandCount, bands = state.eq.bands)
        viewModelScope.launch {
            val id = repository.saveEqPreset(preset)
            _uiState.update { it.copy(eq = it.eq.copy(presetId = id)) }
            repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, id.toInt())
        }
    }

    fun deleteEqPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteEqPresetById(presetId)
            if (_uiState.value.eq.presetId == presetId) {
                _uiState.update { it.copy(eq = it.eq.copy(presetId = null)) }
                repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, -1)
            }
        }
    }

    fun resetEqBands() {
        val bandCount = _uiState.value.eq.bandCount
        val flatBands =
            List(bandCount) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        setEqBands(flatBands)
        _uiState.update { it.copy(eq = it.eq.copy(presetId = null)) }
        viewModelScope.launch { repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, -1) }
    }

    fun setConvolverEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Convolver: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(convolver = it.convolver.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_CONVOLVER_ENABLE}",
                enabled
            )
        }
        val kernel = _uiState.value.convolver.kernel
        val effectiveEnabled = enabled && kernel.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            loadKernelByName(kernel, ViperParams.PARAM_HP_CONVOLVER_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchInt(ViperParams.PARAM_HP_CONVOLVER_ENABLE, 0)
        }
    }

    fun setConvolverKernel(kernel: String) {
        FileLogger.i("ViewModel", "Convolver kernel selected: $kernel")
        _uiState.update { it.copy(convolver = it.convolver.copy(kernel = kernel)) }
        viewModelScope.launch {
            repository.setStringPreference(
                "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                kernel
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            if (kernel.isEmpty()) {
                dispatchInt(ViperParams.PARAM_HP_CONVOLVER_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.convolver.enabled) ViperParams.PARAM_HP_CONVOLVER_ENABLE else null
                loadKernelByName(kernel, enableParam)
            }
        }
    }

    fun setConvolverCrossChannel(value: Int) {
        _uiState.update { it.copy(convolver = it.convolver.copy(crossChannel = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL}",
            ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL,
            value
        )
    }

    fun setFieldSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Field Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
                        intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurround.widening) { 0 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE,
                        intArrayOf(state.fieldSurround.midImage * 10 + 100)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH,
                        intArrayOf(state.fieldSurround.depth * 75 + 200)
                    )
                )
            )
        }
    }

    fun setFieldSurroundWidening(value: Int) {
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(widening = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(value) { 0 })
    }

    fun setFieldSurroundMidImage(value: Int) {
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(midImage = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE, value * 10 + 100)
    }

    fun setFieldSurroundDepth(value: Int) {
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(depth = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH, value * 75 + 200)
    }

    fun setDiffSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Diff Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(diffSurround = it.diffSurround.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
                        intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurround.delay) { 500 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE,
                        intArrayOf(if (state.diffSurround.reverse) 1 else 0)
                    )
                )
            )
        }
    }

    fun setDiffSurroundDelay(value: Int) {
        _uiState.update { it.copy(diffSurround = it.diffSurround.copy(delay = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DIFF_SURROUND_DELAY}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(value) { 500 })
    }

    fun setDiffSurroundReverse(reverse: Boolean) {
        _uiState.update { it.copy(diffSurround = it.diffSurround.copy(reverse = reverse)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE}",
                reverse
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE, if (reverse) 1 else 0)
    }

    fun setVheEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "VHE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(vhe = it.vhe.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH,
                        intArrayOf(state.vhe.quality)
                    )
                )
            )
        }
    }

    fun setVheQuality(value: Int) {
        _uiState.update { it.copy(vhe = it.vhe.copy(quality = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH}",
            ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH,
            value
        )
    }

    fun setReverbEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Reverb: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(reverb = it.reverb.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_REVERB_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_SIZE,
                        intArrayOf(state.reverb.roomSize * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_WIDTH,
                        intArrayOf(state.reverb.width * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING,
                        intArrayOf(state.reverb.dampening)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL,
                        intArrayOf(state.reverb.wet)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL,
                        intArrayOf(state.reverb.dry)
                    )
                )
            )
        }
    }

    fun setReverbRoomSize(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(roomSize = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_SIZE}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_SIZE, value * 10)
    }

    fun setReverbWidth(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(width = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_WIDTH}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_WIDTH, value * 10)
    }

    fun setReverbDampening(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(dampening = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING, value)
    }

    fun setReverbWet(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(wet = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL, value)
    }

    fun setReverbDry(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(dry = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL}",
            ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL,
            value
        )
    }

    fun setDynamicSystemEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Dynamic System: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE}",
                enabled
            )
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemStrength(value: Int) {
        _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(strength = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH}",
                value
            )
        }
        hpDispatchDynamicSystem()
    }

    private fun hpDispatchDynamicSystem() {
        if (activeDeviceType != ViperParams.FX_TYPE_HEADPHONE) return
        val state = _uiState.value
        viperService?.dispatchParamsBatch(
            listOf(
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE,
                    intArrayOf(if (state.dynamicSystem.enabled) 1 else 0)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH,
                    intArrayOf(state.dynamicSystem.strength * 20 + 100)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS,
                    intArrayOf(state.dynamicSystem.xLow, state.dynamicSystem.xHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
                    intArrayOf(state.dynamicSystem.yLow, state.dynamicSystem.yHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN,
                    intArrayOf(state.dynamicSystem.sideGainLow, state.dynamicSystem.sideGainHigh)
                )
            )
        )
    }

    fun setDynamicSystemPreset(presetId: Long) {
        val preset = _uiState.value.dynamicSystem.presets.find { it.id == presetId } ?: return
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    presetId = presetId,
                    xLow = preset.xLow,
                    xHigh = preset.xHigh,
                    yLow = preset.yLow,
                    yHigh = preset.yHigh,
                    sideGainLow = preset.sideGainLow,
                    sideGainHigh = preset.sideGainHigh
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID,
                presetId.toInt()
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                preset.xLow
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                preset.xHigh
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                preset.yLow
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                preset.yHigh
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                preset.sideGainLow
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                preset.sideGainHigh
            )
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemXLow(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    xLow = value,
                    presetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                value
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemXHigh(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    xHigh = value,
                    presetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                value
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemYLow(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    yLow = value,
                    presetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                value
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemYHigh(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    yHigh = value,
                    presetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                value
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemSideGainLow(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    sideGainLow = value,
                    presetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                value
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemSideGainHigh(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    sideGainHigh = value,
                    presetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                value
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun addDynamicSystemPreset(name: String) {
        val state = _uiState.value
        val preset = DsPreset(
            name = name,
            xLow = state.dynamicSystem.xLow,
            xHigh = state.dynamicSystem.xHigh,
            yLow = state.dynamicSystem.yLow,
            yHigh = state.dynamicSystem.yHigh,
            sideGainLow = state.dynamicSystem.sideGainLow,
            sideGainHigh = state.dynamicSystem.sideGainHigh
        )
        viewModelScope.launch {
            val id = repository.saveDsPreset(preset)
            _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(presetId = id)) }
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, id.toInt())
        }
    }

    fun deleteDynamicSystemPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteDsPresetById(presetId)
            if (_uiState.value.dynamicSystem.presetId == presetId) {
                _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(presetId = null)) }
                repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
            }
        }
    }

    fun resetDynamicSystemCoefficients() {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    xLow = 100, xHigh = 5600,
                    yLow = 40, yHigh = 80,
                    sideGainLow = 50, sideGainHigh = 50,
                    presetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID,
                -1
            )
        }
        hpDispatchDynamicSystem()
    }

    fun setTubeSimulatorEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Tube Simulator: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(tube = it.tube.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    )
                )
            )
        }
    }

    fun setBassEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Bass: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(bass = it.bass.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_BASS_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(ViperParams.PARAM_HP_BASS_MODE, intArrayOf(state.bass.mode)),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_FREQUENCY,
                        intArrayOf(state.bass.frequency + 15)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_GAIN,
                        intArrayOf(state.bass.gain * 50 + 50)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_ANTI_POP,
                        intArrayOf(if (state.bass.antiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setBassMode(mode: Int) {
        _uiState.update { it.copy(bass = it.bass.copy(mode = mode)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_BASS_MODE}",
            ViperParams.PARAM_HP_BASS_MODE,
            mode
        )
    }

    fun setBassFrequency(value: Int) {
        _uiState.update { it.copy(bass = it.bass.copy(frequency = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_BASS_FREQUENCY}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_FREQUENCY, value + 15)
    }

    fun setBassGain(value: Int) {
        _uiState.update { it.copy(bass = it.bass.copy(gain = value)) }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_BASS_GAIN}", value)
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_GAIN, value * 50 + 50)
    }

    fun setBassAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(bass = it.bass.copy(antiPop = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_ANTI_POP}", enabled)
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setBassMonoEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Bass Mono: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(bassMono = it.bassMono.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_MONO_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_MODE,
                        intArrayOf(state.bassMono.mode)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_FREQUENCY,
                        intArrayOf(state.bassMono.frequency + 15)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_GAIN,
                        intArrayOf(state.bassMono.gain * 50 + 50)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_ANTI_POP,
                        intArrayOf(if (state.bassMono.antiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setBassMonoMode(mode: Int) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(mode = mode)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_BASS_MONO_MODE}",
            ViperParams.PARAM_HP_BASS_MONO_MODE,
            mode
        )
    }

    fun setBassMonoFrequency(value: Int) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(frequency = value)) }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_BASS_MONO_FREQUENCY}", value)
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_MONO_FREQUENCY, value + 15)
    }

    fun setBassMonoGain(value: Int) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(gain = value)) }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_BASS_MONO_GAIN}", value)
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_MONO_GAIN, value * 50 + 50)
    }

    fun setBassMonoAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(antiPop = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_MONO_ANTI_POP}", enabled)
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_MONO_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setClarityEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Clarity: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(clarity = it.clarity.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_CLARITY_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_CLARITY_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_CLARITY_MODE, intArrayOf(state.clarity.mode)),
                    ParamEntry(
                        ViperParams.PARAM_HP_CLARITY_GAIN,
                        intArrayOf(state.clarity.gain * 50)
                    )
                )
            )
        }
    }

    fun setClarityMode(mode: Int) {
        _uiState.update { it.copy(clarity = it.clarity.copy(mode = mode)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CLARITY_MODE}",
            ViperParams.PARAM_HP_CLARITY_MODE,
            mode
        )
    }

    fun setClarityGain(value: Int) {
        _uiState.update { it.copy(clarity = it.clarity.copy(gain = value)) }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_CLARITY_GAIN}", value)
        }
        hpDispatchInt(ViperParams.PARAM_HP_CLARITY_GAIN, value * 50)
    }

    fun setCureEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Cure: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(cure = it.cure.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_CURE_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_CURE_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(ViperParams.PARAM_HP_CURE_STRENGTH, intArrayOf(state.cure.strength))
                )
            )
        }
    }

    fun setCureStrength(value: Int) {
        _uiState.update { it.copy(cure = it.cure.copy(strength = value)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CURE_STRENGTH}",
            ViperParams.PARAM_HP_CURE_STRENGTH,
            value
        )
    }

    fun setAnalogxEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "AnalogX: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(analog = it.analog.copy(enabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_ANALOGX_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_ANALOGX_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_ANALOGX_MODE, intArrayOf(state.analog.mode))
                )
            )
        }
    }

    fun setAnalogxMode(mode: Int) {
        _uiState.update { it.copy(analog = it.analog.copy(mode = mode)) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_ANALOGX_MODE}",
            ViperParams.PARAM_HP_ANALOGX_MODE,
            mode
        )
    }

    fun setSpeakerOptEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Speaker Optimization: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(speakerOptEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE}",
                enabled
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE, if (enabled) 1 else 0)
    }

    fun setSpkConvolverEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Convolver: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(convolver = it.convolver.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_SPK_CONVOLVER_ENABLE}",
                enabled
            )
        }
        val kernel = _uiState.value.convolver.spkKernel
        val effectiveEnabled = enabled && kernel.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            loadKernelByName(kernel, ViperParams.PARAM_SPK_CONVOLVER_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            dispatchInt(ViperParams.PARAM_SPK_CONVOLVER_ENABLE, 0)
        }
    }

    fun setSpkConvolverKernel(kernel: String) {
        FileLogger.i("ViewModel", "[Spk] Convolver kernel selected: $kernel")
        _uiState.update { it.copy(convolver = it.convolver.copy(spkKernel = kernel)) }
        viewModelScope.launch {
            repository.setStringPreference(
                "spk_${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                kernel
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            if (kernel.isEmpty()) {
                dispatchInt(ViperParams.PARAM_SPK_CONVOLVER_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.convolver.spkEnabled) ViperParams.PARAM_SPK_CONVOLVER_ENABLE else null
                loadKernelByName(kernel, enableParam)
            }
        }
    }

    fun setSpkConvolverCrossChannel(value: Int) {
        _uiState.update { it.copy(convolver = it.convolver.copy(spkCrossChannel = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL}",
            ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL,
            value
        )
    }

    fun setSpkEqEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] EQ: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(eq = it.eq.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_SPK_EQ_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_SPK_EQ_ENABLE, intArrayOf(if (enabled) 1 else 0))
                )
            )
        }
    }

    fun setSpkEqPreset(presetId: Long) {
        val state = _uiState.value
        val preset = state.eq.spkPresets.find { it.id == presetId } ?: return
        val bands = preset.bands
        val bandCount = state.eq.spkBandCount
        _uiState.update { s ->
            val updatedMap = s.eq.spkBandsMap.toMutableMap().apply { put(bandCount, bands) }
            s.copy(
                eq = s.eq.copy(
                    spkPresetId = presetId,
                    spkBands = bands,
                    spkBandsMap = updatedMap
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PREF_EQ_PRESET_ID}",
                presetId.toInt()
            )
            repository.setStringPreference("${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("spk_eq_bands_$bandCount", bands)
        }
        spkDispatchEqBands(bands)
    }

    fun setSpkEqBands(bands: String) {
        val bandCount = _uiState.value.eq.spkBandCount
        _uiState.update { s ->
            val updatedMap = s.eq.spkBandsMap.toMutableMap().apply { put(bandCount, bands) }
            s.copy(eq = s.eq.copy(spkBands = bands, spkBandsMap = updatedMap))
        }
        viewModelScope.launch {
            repository.setStringPreference("${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("spk_eq_bands_$bandCount", bands)
        }
        spkDispatchEqBands(bands)
    }

    fun setSpkEqBandCount(count: Int) {
        val currentState = _uiState.value
        val oldCount = currentState.eq.spkBandCount
        FileLogger.d("ViewModel", "[Spk] EQ band count: $oldCount -> $count")
        val updatedMap = currentState.eq.spkBandsMap.toMutableMap().apply {
            put(oldCount, currentState.eq.spkBands)
        }
        val defaultBands =
            List(count) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        val bands = updatedMap[count] ?: defaultBands
        _uiState.update {
            it.copy(
                eq = it.eq.copy(
                    spkBandCount = count,
                    spkBands = bands,
                    spkPresetId = null,
                    spkBandsMap = updatedMap
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference("spk_${ViperParams.PARAM_HP_EQ_BAND_COUNT}", count)
            repository.setStringPreference("${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("spk_eq_bands_$oldCount", currentState.eq.spkBands)
            repository.setStringPreference("spk_eq_bands_$count", bands)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            dispatchEqBands(
                ViperParams.PARAM_SPK_EQ_BAND_LEVEL,
                bands,
                ViperParams.PARAM_SPK_EQ_BAND_COUNT,
                count
            )
        }
        loadEqPresetsForBandCount(count, isSpk = true)
    }

    fun addSpkEqPreset(name: String) {
        val state = _uiState.value
        val preset =
            EqPreset(name = name, bandCount = state.eq.spkBandCount, bands = state.eq.spkBands)
        viewModelScope.launch {
            val id = repository.saveEqPreset(preset)
            _uiState.update { it.copy(eq = it.eq.copy(spkPresetId = id)) }
            repository.setIntPreference("spk_${ViperRepository.PREF_EQ_PRESET_ID}", id.toInt())
        }
    }

    fun deleteSpkEqPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteEqPresetById(presetId)
            if (_uiState.value.eq.spkPresetId == presetId) {
                _uiState.update { it.copy(eq = it.eq.copy(spkPresetId = null)) }
                repository.setIntPreference("spk_${ViperRepository.PREF_EQ_PRESET_ID}", -1)
            }
        }
    }

    fun resetSpkEqBands() {
        val bandCount = _uiState.value.eq.spkBandCount
        val flatBands =
            List(bandCount) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        setSpkEqBands(flatBands)
        _uiState.update { it.copy(eq = it.eq.copy(spkPresetId = null)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PREF_EQ_PRESET_ID}",
                -1
            )
        }
    }

    fun setSpkReverbEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Reverb: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(reverb = it.reverb.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_SPK_REVERB_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_SIZE,
                        intArrayOf(state.reverb.spkRoomSize * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH,
                        intArrayOf(state.reverb.spkWidth * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING,
                        intArrayOf(state.reverb.spkDampening)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL,
                        intArrayOf(state.reverb.spkWet)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL,
                        intArrayOf(state.reverb.spkDry)
                    )
                )
            )
        }
    }

    fun setSpkReverbRoomSize(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(spkRoomSize = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_SIZE}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_SIZE, value * 10)
    }

    fun setSpkReverbWidth(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(spkWidth = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH, value * 10)
    }

    fun setSpkReverbDampening(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(spkDampening = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING, value)
    }

    fun setSpkReverbWet(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(spkWet = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL, value)
    }

    fun setSpkReverbDry(value: Int) {
        _uiState.update { it.copy(reverb = it.reverb.copy(spkDry = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL}",
            ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL,
            value
        )
    }

    fun setSpkAgcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] AGC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(agc = it.agc.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_SPK_AGC_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_SPK_AGC_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_AGC_RATIO,
                        intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agc.spkStrength) { 50 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_AGC_MAX_SCALER,
                        intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(state.agc.spkMaxGain) { 100 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_AGC_VOLUME,
                        intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.agc.spkOutputThreshold) { 100 })
                    )
                )
            )
        }
    }

    fun setSpkAgcStrength(value: Int) {
        _uiState.update { it.copy(agc = it.agc.copy(spkStrength = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_AGC_RATIO}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(value) { 50 })
    }

    fun setSpkAgcMaxGain(value: Int) {
        _uiState.update { it.copy(agc = it.agc.copy(spkMaxGain = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_AGC_MAX_SCALER}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(value) { 100 })
    }

    fun setSpkAgcOutputThreshold(value: Int) {
        _uiState.update { it.copy(agc = it.agc.copy(spkOutputThreshold = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_AGC_VOLUME}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_AGC_VOLUME, OUTPUT_DB_VALUES.getOrElse(value) { 100 })
    }

    fun setSpkFetEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] FET Compressor: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(fet = it.fet.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE,
                        intArrayOf(if (enabled) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD,
                        intArrayOf(state.fet.spkThreshold)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO,
                        intArrayOf(state.fet.spkRatio)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
                        intArrayOf(if (state.fet.spkAutoKnee) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE,
                        intArrayOf(state.fet.spkKnee)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI,
                        intArrayOf(state.fet.spkKneeMulti)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
                        intArrayOf(if (state.fet.spkAutoGain) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN,
                        intArrayOf(state.fet.spkGain)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
                        intArrayOf(if (state.fet.spkAutoAttack) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK,
                        intArrayOf(state.fet.spkAttack)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK,
                        intArrayOf(state.fet.spkMaxAttack)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
                        intArrayOf(if (state.fet.spkAutoRelease) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE,
                        intArrayOf(state.fet.spkRelease)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
                        intArrayOf(state.fet.spkMaxRelease)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST,
                        intArrayOf(state.fet.spkCrest)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT,
                        intArrayOf(state.fet.spkAdapt)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
                        intArrayOf(if (state.fet.spkNoClip) 100 else 0)
                    )
                )
            )
        }
    }

    fun setSpkFetThreshold(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkThreshold = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD,
            value
        )
    }

    fun setSpkFetRatio(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkRatio = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO,
            value
        )
    }

    fun setSpkFetAutoKnee(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(spkAutoKnee = value)) }
        spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
            value
        )
    }

    fun setSpkFetKnee(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkKnee = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE,
            value
        )
    }

    fun setSpkFetKneeMulti(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkKneeMulti = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI,
            value
        )
    }

    fun setSpkFetAutoGain(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(spkAutoGain = value)) }
        spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
            value
        )
    }

    fun setSpkFetGain(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkGain = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN,
            value
        )
    }

    fun setSpkFetAutoAttack(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(spkAutoAttack = value)) }
        spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
            value
        )
    }

    fun setSpkFetAttack(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkAttack = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK,
            value
        )
    }

    fun setSpkFetMaxAttack(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkMaxAttack = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK,
            value
        )
    }

    fun setSpkFetAutoRelease(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(spkAutoRelease = value)) }
        spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
            value
        )
    }

    fun setSpkFetRelease(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkRelease = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE,
            value
        )
    }

    fun setSpkFetMaxRelease(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkMaxRelease = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
            value
        )
    }

    fun setSpkFetCrest(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkCrest = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST,
            value
        )
    }

    fun setSpkFetAdapt(value: Int) {
        _uiState.update { it.copy(fet = it.fet.copy(spkAdapt = value)) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT,
            value
        )
    }

    fun setSpkFetNoClip(value: Boolean) {
        _uiState.update { it.copy(fet = it.fet.copy(spkNoClip = value)) }
        spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
            value
        )
    }

    fun setSpkOutputVolume(value: Int) {
        _uiState.update { it.copy(out = it.out.copy(spkVolume = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_OUTPUT_VOLUME}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(value) { 100 })
    }

    fun setSpkLimiter(value: Int) {
        _uiState.update { it.copy(out = it.out.copy(spkLimiter = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_LIMITER}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_LIMITER, OUTPUT_DB_VALUES.getOrElse(value) { 100 })
    }

    fun setSpkDdcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] DDC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(ddc = it.ddc.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DDC_ENABLE}",
                enabled
            )
        }
        val device = _uiState.value.ddc.spkDevice
        val effectiveEnabled = enabled && device.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            loadVdcByName(device, ViperParams.PARAM_SPK_DDC_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            dispatchInt(ViperParams.PARAM_SPK_DDC_ENABLE, 0)
        }
    }

    fun setSpkDdcDevice(device: String) {
        FileLogger.i("ViewModel", "[Spk] DDC selected: $device")
        _uiState.update { it.copy(ddc = it.ddc.copy(spkDevice = device)) }
        viewModelScope.launch {
            repository.setStringPreference(
                "spk_${ViperRepository.PREF_DDC_DEVICE}",
                device
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            if (device.isEmpty()) {
                dispatchInt(ViperParams.PARAM_SPK_DDC_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.ddc.spkEnabled) ViperParams.PARAM_SPK_DDC_ENABLE else null
                loadVdcByName(device, enableParam)
            }
        }
    }

    fun setSpkVseEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] VSE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(vse = it.vse.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
                        intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(state.vse.spkStrength) { 7600 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                        intArrayOf((state.vse.spkExciter * 5.6).toInt())
                    )
                )
            )
        }
    }

    fun setSpkVseStrength(value: Int) {
        _uiState.update { it.copy(vse = it.vse.copy(spkStrength = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(value) { 7600 })
    }

    fun setSpkVseExciter(value: Int) {
        _uiState.update { it.copy(vse = it.vse.copy(spkExciter = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (value * 5.6).toInt()
        )
    }

    fun setSpkFieldSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Field Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
                        intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurround.spkWidening) { 0 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE,
                        intArrayOf(state.fieldSurround.spkMidImage * 10 + 100)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH,
                        intArrayOf(state.fieldSurround.spkDepth * 75 + 200)
                    )
                )
            )
        }
    }

    fun setSpkFieldSurroundWidening(value: Int) {
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(spkWidening = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(value) { 0 })
    }

    fun setSpkFieldSurroundMidImage(value: Int) {
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(spkMidImage = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE, value * 10 + 100)
    }

    fun setSpkFieldSurroundDepth(value: Int) {
        _uiState.update { it.copy(fieldSurround = it.fieldSurround.copy(spkDepth = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH, value * 75 + 200)
    }

    fun setSpkDiffSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Diff Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(diffSurround = it.diffSurround.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
                        intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurround.spkDelay) { 500 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE,
                        intArrayOf(if (state.diffSurround.spkReverse) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkDiffSurroundDelay(value: Int) {
        _uiState.update { it.copy(diffSurround = it.diffSurround.copy(spkDelay = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(value) { 500 })
    }

    fun setSpkDiffSurroundReverse(reverse: Boolean) {
        _uiState.update { it.copy(diffSurround = it.diffSurround.copy(spkReverse = reverse)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE}",
                reverse
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE, if (reverse) 1 else 0)
    }

    fun setSpkVheEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] VHE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(vhe = it.vhe.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH,
                        intArrayOf(state.vhe.spkQuality)
                    )
                )
            )
        }
    }

    fun setSpkVheQuality(value: Int) {
        _uiState.update { it.copy(vhe = it.vhe.copy(spkQuality = value)) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH}",
            ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH,
            value
        )
    }

    fun setSpkDynamicSystemEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Dynamic System: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE}",
                enabled
            )
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDynamicSystemStrength(value: Int) {
        _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(spkStrength = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH}",
                value
            )
        }
        spkDispatchDynamicSystem()
    }

    private fun spkDispatchDynamicSystem() {
        if (activeDeviceType != ViperParams.FX_TYPE_SPEAKER) return
        val state = _uiState.value
        viperService?.dispatchParamsBatch(
            listOf(
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE,
                    intArrayOf(if (state.dynamicSystem.spkEnabled) 1 else 0)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH,
                    intArrayOf(state.dynamicSystem.spkStrength * 20 + 100)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS,
                    intArrayOf(state.dynamicSystem.spkXLow, state.dynamicSystem.spkXHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
                    intArrayOf(state.dynamicSystem.spkYLow, state.dynamicSystem.spkYHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN,
                    intArrayOf(
                        state.dynamicSystem.spkSideGainLow,
                        state.dynamicSystem.spkSideGainHigh
                    )
                )
            )
        )
    }

    fun setSpkDsPreset(presetId: Long) {
        val preset = _uiState.value.dynamicSystem.spkPresets.find { it.id == presetId } ?: return
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkPresetId = presetId,
                    spkXLow = preset.xLow, spkXHigh = preset.xHigh,
                    spkYLow = preset.yLow, spkYHigh = preset.yHigh,
                    spkSideGainLow = preset.sideGainLow, spkSideGainHigh = preset.sideGainHigh
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                presetId.toInt()
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                preset.xLow
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                preset.xHigh
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                preset.yLow
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                preset.yHigh
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                preset.sideGainLow
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                preset.sideGainHigh
            )
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsXLow(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkXLow = value,
                    spkPresetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                value
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsXHigh(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkXHigh = value,
                    spkPresetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                value
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsYLow(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkYLow = value,
                    spkPresetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                value
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsYHigh(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkYHigh = value,
                    spkPresetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                value
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsSideGainLow(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkSideGainLow = value,
                    spkPresetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                value
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsSideGainHigh(value: Int) {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkSideGainHigh = value,
                    spkPresetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                value
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun addSpkDsPreset(name: String) {
        val state = _uiState.value
        val preset = DsPreset(
            name = name,
            xLow = state.dynamicSystem.spkXLow,
            xHigh = state.dynamicSystem.spkXHigh,
            yLow = state.dynamicSystem.spkYLow,
            yHigh = state.dynamicSystem.spkYHigh,
            sideGainLow = state.dynamicSystem.spkSideGainLow,
            sideGainHigh = state.dynamicSystem.spkSideGainHigh
        )
        viewModelScope.launch {
            val id = repository.saveDsPreset(preset)
            _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(spkPresetId = id)) }
            repository.setIntPreference(
                "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                id.toInt()
            )
        }
    }

    fun deleteSpkDsPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteDsPresetById(presetId)
            if (_uiState.value.dynamicSystem.spkPresetId == presetId) {
                _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(spkPresetId = null)) }
                repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
            }
        }
    }

    fun resetSpkDsCoefficients() {
        _uiState.update {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    spkXLow = 100, spkXHigh = 5600,
                    spkYLow = 40, spkYHigh = 80,
                    spkSideGainLow = 50, spkSideGainHigh = 50,
                    spkPresetId = null
                )
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                -1
            )
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkTubeSimulatorEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Tube Simulator: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(tube = it.tube.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkBassEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Bass: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(bass = it.bass.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_BASS_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_BASS_MODE, intArrayOf(state.bass.spkMode)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_FREQUENCY,
                        intArrayOf(state.bass.spkFrequency + 15)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_GAIN,
                        intArrayOf(state.bass.spkGain * 50 + 50)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_ANTI_POP,
                        intArrayOf(if (state.bass.spkAntiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkBassMode(mode: Int) {
        _uiState.update { it.copy(bass = it.bass.copy(spkMode = mode)) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_BASS_MODE}",
            ViperParams.PARAM_SPK_BASS_MODE,
            mode
        )
    }

    fun setSpkBassFrequency(value: Int) {
        _uiState.update { it.copy(bass = it.bass.copy(spkFrequency = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_FREQUENCY}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_FREQUENCY, value + 15)
    }

    fun setSpkBassGain(value: Int) {
        _uiState.update { it.copy(bass = it.bass.copy(spkGain = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_GAIN}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_GAIN, value * 50 + 50)
    }

    fun setSpkBassAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(bass = it.bass.copy(spkAntiPop = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_BASS_ANTI_POP}", enabled)
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setSpkBassMonoEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Bass Mono: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(bassMono = it.bassMono.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_MODE,
                        intArrayOf(state.bassMono.spkMode)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY,
                        intArrayOf(state.bassMono.spkFrequency + 15)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_GAIN,
                        intArrayOf(state.bassMono.spkGain * 50 + 50)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP,
                        intArrayOf(if (state.bassMono.spkAntiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkBassMonoMode(mode: Int) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(spkMode = mode)) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_BASS_MONO_MODE}",
            ViperParams.PARAM_SPK_BASS_MONO_MODE,
            mode
        )
    }

    fun setSpkBassMonoFrequency(value: Int) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(spkFrequency = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY, value + 15)
    }

    fun setSpkBassMonoGain(value: Int) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(spkGain = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_GAIN}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_MONO_GAIN, value * 50 + 50)
    }

    fun setSpkBassMonoAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(bassMono = it.bassMono.copy(spkAntiPop = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP}",
                enabled
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setSpkClarityEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Clarity: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(clarity = it.clarity.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_CLARITY_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_CLARITY_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_CLARITY_MODE,
                        intArrayOf(state.clarity.spkMode)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_CLARITY_GAIN,
                        intArrayOf(state.clarity.spkGain * 50)
                    )
                )
            )
        }
    }

    fun setSpkClarityMode(mode: Int) {
        _uiState.update { it.copy(clarity = it.clarity.copy(spkMode = mode)) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_CLARITY_MODE}",
            ViperParams.PARAM_SPK_CLARITY_MODE,
            mode
        )
    }

    fun setSpkClarityGain(value: Int) {
        _uiState.update { it.copy(clarity = it.clarity.copy(spkGain = value)) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_CLARITY_GAIN}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_CLARITY_GAIN, value * 50)
    }

    fun setSpkCureEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Cure: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(cure = it.cure.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_CURE_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_CURE_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_CURE_STRENGTH,
                        intArrayOf(state.cure.spkStrength)
                    )
                )
            )
        }
    }

    fun setSpkCureStrength(value: Int) {
        _uiState.update { it.copy(cure = it.cure.copy(spkStrength = value)) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_CURE_STRENGTH}",
            ViperParams.PARAM_SPK_CURE_STRENGTH,
            value
        )
    }

    fun setSpkAnalogxEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] AnalogX: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(analog = it.analog.copy(spkEnabled = enabled)) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_ANALOGX_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val state = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_ANALOGX_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_ANALOGX_MODE, intArrayOf(state.analog.spkMode))
                )
            )
        }
    }

    fun setSpkAnalogxMode(mode: Int) {
        _uiState.update { it.copy(analog = it.analog.copy(spkMode = mode)) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_ANALOGX_MODE}",
            ViperParams.PARAM_SPK_ANALOGX_MODE,
            mode
        )
    }

    fun setSpkChannelPan(value: Int) {
        _uiState.update { it.copy(out = it.out.copy(spkChannelPan = value)) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_CHANNEL_PAN}",
            ViperParams.PARAM_SPK_CHANNEL_PAN,
            value
        )
    }

    private suspend fun ensureDeviceEntry(device: AudioDevice) {
        val existing = repository.getDeviceSettings(device.id)
        if (existing == null) {
            val state = _uiState.value
            val isSpk = !device.isHeadphone
            repository.saveDeviceSettings(
                DeviceSettings(
                    deviceId = device.id,
                    deviceName = device.name,
                    isHeadphone = device.isHeadphone,
                    settingsJson = serializeEffectPrefs(state, isSpk).toString()
                )
            )
        } else {
            repository.updateDeviceLastConnected(device.id)
        }
    }

    private suspend fun saveCurrentDeviceSettings() {
        val state = _uiState.value
        val isSpk = activeDeviceType == ViperParams.FX_TYPE_SPEAKER
        val json = serializeEffectPrefs(state, isSpk).toString()
        val existing = repository.getDeviceSettings(currentDeviceId)
        repository.saveDeviceSettings(
            DeviceSettings(
                deviceId = currentDeviceId,
                deviceName = existing?.deviceName ?: state.activeDeviceName,
                isHeadphone = existing?.isHeadphone ?: !isSpk,
                settingsJson = json
            )
        )
    }

    private suspend fun loadDeviceSettings(device: AudioDevice) {
        val saved = repository.getDeviceSettings(device.id) ?: return
        val isSpk = !device.isHeadphone
        val json = JSONObject(saved.settingsJson)
        _uiState.update { deserializeEffectPrefs(json, it, isSpk) }
        saveEffectPrefs(repository, _uiState.value, isSpk)
        applyFullState()
    }

    fun renameDevice(deviceId: String, name: String) {
        viewModelScope.launch {
            repository.renameDevice(deviceId, name)
            if (deviceId == currentDeviceId) {
                _uiState.update { it.copy(activeDeviceName = name) }
            }
        }
    }

    fun deleteDeviceSettings(deviceId: String) {
        viewModelScope.launch { repository.deleteDeviceSettings(deviceId) }
    }

    fun saveDevicePreset(deviceId: String) {
        viewModelScope.launch {
            val existing = repository.getDeviceSettings(deviceId) ?: return@launch
            val state = _uiState.value
            val isSpk = !existing.isHeadphone
            val json = serializeEffectPrefs(state, isSpk).toString()
            repository.saveDeviceSettings(existing.copy(settingsJson = json))
        }
    }

    fun loadDevicePreset(deviceId: String) {
        viewModelScope.launch {
            val saved = repository.getDeviceSettings(deviceId) ?: return@launch
            val isSpk = !saved.isHeadphone
            val json = JSONObject(saved.settingsJson)
            _uiState.update { deserializeEffectPrefs(json, it, isSpk) }
            saveEffectPrefs(repository, _uiState.value, isSpk)
            applyFullState()
        }
    }

    private fun getFilesDir(subDir: String): File {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), subDir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun copyUriToFile(uri: Uri, destDir: File, fallbackName: String): File? {
        val context = getApplication<Application>()
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: fallbackName
        val destFile = File(destDir, fileName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to copy file", e)
            null
        }
    }

    fun importPresetFile(uri: Uri): Boolean {
        return try {
            val destDir = getFilesDir("Preset")
            val destFile = copyUriToFile(uri, destDir, "preset.json") ?: return false
            val json = destFile.readText()
            val obj = JSONObject(json)
            val isSpk = obj.has("spkMasterEnabled") && !obj.has("masterEnabled")
            val fxType = if (isSpk) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
            deserializeAndApplyStateForMode(json, fxType)
            viewModelScope.launch { persistStateForMode(fxType) }
            if (fxType == activeDeviceType) {
                applyFullState()
            }
            val presetName = destFile.nameWithoutExtension
            viewModelScope.launch {
                val existing = repository.getPresetByNameAndFxType(presetName, fxType)
                if (existing != null) {
                    repository.updatePreset(
                        existing.copy(
                            settingsJson = json,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    repository.savePreset(
                        Preset(
                            name = presetName,
                            fxType = fxType,
                            settingsJson = json
                        )
                    )
                }
            }
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to import preset", e)
            false
        }
    }

    fun importKernel(uri: Uri): Boolean {
        return try {
            val destDir = getFilesDir("Kernel")
            copyUriToFile(uri, destDir, "kernel.wav") ?: return false
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to import kernel", e)
            false
        }
    }

    fun importVdc(uri: Uri): Boolean {
        return try {
            val destDir = getFilesDir("DDC")
            copyUriToFile(uri, destDir, "imported.vdc") ?: return false
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to import VDC", e)
            false
        }
    }

    fun refreshFileLists() {
        val ddcDir = getFilesDir("DDC")
        _vdcFileList.value = ddcDir.listFiles()
            ?.filter { it.extension == "vdc" }
            ?.map { it.nameWithoutExtension }
            ?.sorted() ?: emptyList()

        val kernelDir = getFilesDir("Kernel")
        _kernelFileList.value = kernelDir.listFiles()
            ?.map { it.name }
            ?.sorted() ?: emptyList()
    }

    fun deleteVdcFile(name: String): Boolean {
        return try {
            val file = File(getFilesDir("DDC"), "$name.vdc")
            if (!file.exists()) return false
            file.delete()
            val state = _uiState.value
            if (state.ddc.device == name) {
                _uiState.update { it.copy(ddc = it.ddc.copy(device = "")) }
                viewModelScope.launch {
                    repository.setStringPreference(
                        ViperRepository.PREF_DDC_DEVICE,
                        ""
                    )
                }
            }
            if (state.ddc.spkDevice == name) {
                _uiState.update { it.copy(ddc = it.ddc.copy(spkDevice = "")) }
                viewModelScope.launch {
                    repository.setStringPreference(
                        "spk_${ViperRepository.PREF_DDC_DEVICE}",
                        ""
                    )
                }
            }
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to delete VDC: $name", e)
            false
        }
    }

    fun deleteKernelFile(fileName: String): Boolean {
        return try {
            val file = File(getFilesDir("Kernel"), fileName)
            if (!file.exists()) return false
            file.delete()
            val state = _uiState.value
            if (state.convolver.kernel == fileName) {
                _uiState.update { it.copy(convolver = it.convolver.copy(kernel = "")) }
                viewModelScope.launch {
                    repository.setStringPreference(
                        "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                        ""
                    )
                }
            }
            if (state.convolver.spkKernel == fileName) {
                _uiState.update { it.copy(convolver = it.convolver.copy(spkKernel = "")) }
                viewModelScope.launch {
                    repository.setStringPreference(
                        "spk_${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                        ""
                    )
                }
            }
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to delete kernel: $fileName", e)
            false
        }
    }

    fun loadVdcByName(name: String, enableParam: Int? = null): Boolean {
        FileLogger.i("ViewModel", "Loading DDC: $name")
        return try {
            val file = File(getFilesDir("DDC"), "$name.vdc")
            if (!file.exists()) return false
            val lines = file.readLines()

            var coeffs44100: FloatArray? = null
            var coeffs48000: FloatArray? = null

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SR_44100:") -> {
                        coeffs44100 = trimmed.removePrefix("SR_44100:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }

                    trimmed.startsWith("SR_48000:") -> {
                        coeffs48000 = trimmed.removePrefix("SR_48000:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }
                }
            }

            if (coeffs44100 == null || coeffs48000 == null) return false
            if (coeffs44100.size != coeffs48000.size) return false
            if (coeffs44100.size % 5 != 0) return false

            val arrSize = coeffs44100.size
            val naturalSize = 4 + arrSize * 4 * 2
            val wireSize = when {
                naturalSize <= 256 -> 256
                naturalSize <= 1024 -> 1024
                else -> return false
            }
            val buffer = ByteBuffer.allocate(wireSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(arrSize)
            for (f in coeffs44100) buffer.putFloat(f)
            for (f in coeffs48000) buffer.putFloat(f)

            val service = viperService ?: return false
            val extras =
                if (enableParam != null) listOf(ParamEntry(enableParam, intArrayOf(1))) else null
            service.dispatchParam(ViperParams.PARAM_HP_DDC_COEFFICIENTS, buffer.array(), extras)
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to load VDC: $name", e)
            false
        }
    }

    fun loadKernelByName(fileName: String, enableParam: Int? = null): Boolean {
        FileLogger.i("ViewModel", "Loading convolver kernel: $fileName")
        return try {
            val file = File(getFilesDir("Kernel"), fileName)
            if (!file.exists()) return false

            if (_aidlModeEnabled.value) {
                return loadKernelViaFile(file, fileName, enableParam)
            }

            val wavBytes = file.readBytes()
            val floatSamples = decodeWavToFloat(wavBytes) ?: return false
            val channelCount = getWavChannelCount(wavBytes)
            val totalFloats = floatSamples.size
            FileLogger.i(
                "ViewModel",
                "Kernel loaded: $fileName samples=$totalFloats ch=$channelCount"
            )

            val service = viperService ?: return false

            val prepareParam = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_PREPARE_BUFFER else ViperParams.PARAM_HP_CONVOLVER_PREPARE_BUFFER
            val setParam = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_BUFFER else ViperParams.PARAM_HP_CONVOLVER_SET_BUFFER
            val commitParam = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_COMMIT_BUFFER else ViperParams.PARAM_HP_CONVOLVER_COMMIT_BUFFER

            service.dispatchParam(prepareParam, totalFloats, channelCount, 0)

            val floatBytes = ByteBuffer.allocate(totalFloats * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in floatSamples) floatBytes.putFloat(f)
            val rawBytes = floatBytes.array()

            val crc = CRC32()
            crc.update(rawBytes)
            val crcValue = crc.value.toInt()

            val maxFloatsPerChunk = 2046
            var offset = 0
            var chunkIndex = 0
            while (offset < totalFloats) {
                val remaining = totalFloats - offset
                val floatsInChunk = minOf(remaining, maxFloatsPerChunk)
                val chunkByteCount = floatsInChunk * 4

                val chunkBuffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
                chunkBuffer.putInt(chunkIndex)
                chunkBuffer.putInt(floatsInChunk)
                chunkBuffer.put(rawBytes, offset * 4, chunkByteCount)

                service.dispatchParam(setParam, chunkBuffer.array())
                offset += floatsInChunk
                chunkIndex++
            }

            val kernelId = fileName.hashCode()
            service.dispatchParam(commitParam, totalFloats, crcValue, kernelId)
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to load kernel: $fileName", e)
            false
        }
    }

    private fun loadKernelViaFile(file: File, fileName: String, enableParam: Int? = null): Boolean {
        return try {
            val safeName = fileName.replace("'", "")
            val subDir = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
            val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
            RootShell.copyFile(file, kernelPath)
            FileLogger.i("ViewModel", "Kernel copied to $kernelPath")

            val param = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL else ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
            val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(pathBytes.size)
            buffer.put(pathBytes)
            val service = viperService ?: return false
            val extras =
                if (enableParam != null) listOf(ParamEntry(enableParam, intArrayOf(1))) else null
            service.dispatchParam(param, buffer.array(), extras)
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to load kernel via file: $fileName", e)
            false
        }
    }

    private fun getWavChannelCount(wavBytes: ByteArray): Int {
        if (wavBytes.size < 44) return 1
        val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(22)
        return buf.short.toInt()
    }

    private fun decodeWavToFloat(wavBytes: ByteArray): FloatArray? {
        if (wavBytes.size < 44) return null
        val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        val riff = ByteArray(4)
        buf.get(riff)
        if (String(riff) != "RIFF") return null
        buf.int
        val wave = ByteArray(4)
        buf.get(wave)
        if (String(wave) != "WAVE") return null

        var audioFormat = 0
        var numChannels = 0
        var bitsPerSample = 0
        var dataBytes: ByteArray? = null

        while (buf.remaining() >= 8) {
            val chunkId = ByteArray(4)
            buf.get(chunkId)
            val chunkSize = buf.int
            val chunkIdStr = String(chunkId)

            when (chunkIdStr) {
                "fmt " -> {
                    val fmtStart = buf.position()
                    audioFormat = buf.short.toInt() and 0xFFFF
                    numChannels = buf.short.toInt() and 0xFFFF
                    buf.int
                    buf.int
                    buf.short
                    bitsPerSample = buf.short.toInt() and 0xFFFF
                    buf.position(fmtStart + chunkSize)
                }

                "data" -> {
                    val safeSize = minOf(chunkSize, buf.remaining())
                    dataBytes = ByteArray(safeSize)
                    buf.get(dataBytes)
                }

                else -> {
                    val skip = minOf(chunkSize, buf.remaining())
                    buf.position(buf.position() + skip)
                }
            }
        }

        val data = dataBytes ?: return null
        if (numChannels < 1 || numChannels > 2) return null

        val dataBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = bitsPerSample / 8
        if (bytesPerSample == 0) return null
        val totalSamples = data.size / bytesPerSample
        val result = FloatArray(totalSamples)

        when {
            audioFormat == 1 && bitsPerSample == 16 -> {
                for (i in 0 until totalSamples) result[i] = dataBuf.short.toFloat() / 32768f
            }

            audioFormat == 1 && bitsPerSample == 24 -> {
                for (i in 0 until totalSamples) {
                    val b0 = dataBuf.get().toInt() and 0xFF
                    val b1 = dataBuf.get().toInt() and 0xFF
                    val b2 = dataBuf.get().toInt()
                    result[i] = ((b2 shl 16) or (b1 shl 8) or b0).toFloat() / 8388608f
                }
            }

            audioFormat == 1 && bitsPerSample == 32 -> {
                for (i in 0 until totalSamples) result[i] = dataBuf.int.toFloat() / 2147483648f
            }

            audioFormat == 3 && bitsPerSample == 32 -> {
                for (i in 0 until totalSamples) result[i] = dataBuf.float
            }

            else -> return null
        }

        return result
    }

    private suspend fun persistStateForMode(fxType: Int) {
        val state = _uiState.value
        val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
        saveEffectPrefs(repository, state, isSpk)
        if (isSpk) {
            for ((bc, bands) in state.eq.spkBandsMap) {
                repository.setStringPreference("spk_eq_bands_$bc", bands)
            }
        } else {
            for ((bc, bands) in state.eq.bandsMap) {
                repository.setStringPreference("eq_bands_$bc", bands)
            }
        }
    }

    private fun loadSettingsPreferences() {
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_AUTO_START).collect { v ->
                _autoStartEnabled.value = v
            }
        }
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_AIDL_MODE).collect { v ->
                _aidlModeEnabled.value = v
            }
        }
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_GLOBAL_MODE).collect { v ->
                _globalModeEnabled.value = v
            }
        }
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_DEBUG_MODE).collect { v ->
                _debugModeEnabled.value = v
            }
        }
    }

    fun enableDebugMode() {
        _debugModeEnabled.value = true
        viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, true) }
    }

    fun disableDebugMode() {
        _debugModeEnabled.value = false
        viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, false) }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val fxType = state.fxType
            val mode = if (fxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
            FileLogger.i("ViewModel", "Dispatch: savePreset name=$name mode=$mode")
            val json = serializeStateForMode(state, fxType)
            val preset = Preset(
                name = name,
                fxType = fxType,
                settingsJson = json
            )
            repository.savePreset(preset)
            try {
                val presetDir = getFilesDir("Preset")
                File(presetDir, "$name.json").writeText(json)
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to write preset file", e)
            }
        }
    }

    fun loadPreset(id: Long) {
        viewModelScope.launch {
            val preset = repository.getPresetById(id) ?: return@launch
            val targetFxType = preset.fxType
            val mode = if (targetFxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
            FileLogger.i("ViewModel", "Dispatch: loadPreset name=${preset.name} mode=$mode")
            deserializeAndApplyStateForMode(preset.settingsJson, targetFxType)
            persistStateForMode(targetFxType)
            if (targetFxType == activeDeviceType) {
                applyFullState()
            }
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            val preset = repository.getPresetById(id) ?: return@launch
            repository.deletePresetById(id)
            try {
                val presetDir = getFilesDir("Preset")
                File(presetDir, "${preset.name}.json").delete()
            } catch (_: Exception) {
            }
        }
    }

    fun renamePreset(id: Long, newName: String) {
        viewModelScope.launch {
            val preset = repository.getPresetById(id) ?: return@launch
            repository.updatePreset(
                preset.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                )
            )
            try {
                val presetDir = getFilesDir("Preset")
                val oldFile = File(presetDir, "${preset.name}.json")
                val newFile = File(presetDir, "$newName.json")
                oldFile.renameTo(newFile)
            } catch (_: Exception) {
            }
        }
    }

    fun queryDriverStatus() {
        if (_aidlModeEnabled.value) {
            queryDriverStatusFromFile()
            return
        }
        val effect = viperService?.getGlobalEffect()
        if (effect != null && effect.isCreated) {
            queryDriverStatusFrom(effect)
            return
        }
        val typeUuid = ViperEffect.EFFECT_TYPE_UUID
        val probe = ViperEffect(0, typeUuid)
        if (!probe.create()) {
            _driverStatus.value = DriverStatus(installed = false)
            probe.release()
            return
        }
        queryDriverStatusFrom(probe)
        probe.release()
    }

    private fun queryDriverStatusFromFile() {
        val status = ConfigChannel.readStatus()
        if (status == null || status.versionCode <= 0) {
            if (_driverStatus.value.installed) return
            _driverStatus.value = DriverStatus(installed = false)
            return
        }
        _driverStatus.value = DriverStatus(
            installed = true,
            versionCode = status.versionCode,
            versionName = status.versionName,
            architecture = status.architecture,
            streaming = status.streaming,
            samplingRate = status.sampleRate
        )
    }

    private fun queryDriverStatusFrom(effect: ViperEffect) {
        val versionCode = effect.getDriverVersionCode()
        val archName = effect.getArchitectureString()
        val streaming = effect.isStreaming()
        val samplingRate = effect.getParameter(ViperParams.PARAM_GET_SAMPLING_RATE)

        val versionBytes = effect.getParameter(ViperParams.PARAM_GET_DRIVER_VERSION_NAME, 256)
        val versionName = if (versionBytes.isNotEmpty()) {
            val nullIdx = versionBytes.indexOf(0.toByte())
            if (nullIdx >= 0) String(versionBytes, 0, nullIdx) else String(versionBytes)
        } else {
            versionCode.toString()
        }

        _driverStatus.value = DriverStatus(
            installed = true,
            versionCode = versionCode,
            versionName = versionName,
            architecture = archName,
            streaming = streaming,
            samplingRate = samplingRate
        )
    }

    fun toggleAutoStart(enabled: Boolean) {
        _autoStartEnabled.value = enabled
        viewModelScope.launch {
            repository.setBooleanPreference(PREF_AUTO_START, enabled)
        }
    }

    fun toggleAidlMode(enabled: Boolean) {
        _aidlModeEnabled.value = enabled
        viewModelScope.launch {
            repository.setBooleanPreference(PREF_AIDL_MODE, enabled)
        }
        viperService?.recreateGlobalEffect(enabled)
    }

    fun toggleGlobalMode(enabled: Boolean) {
        _globalModeEnabled.value = enabled
        viewModelScope.launch {
            repository.setBooleanPreference(PREF_GLOBAL_MODE, enabled)
        }
        viperService?.setGlobalMode(enabled)
    }

    private fun serializeStateForMode(state: MainUiState, fxType: Int): String {
        val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
        val obj = serializeEffectPrefs(state, isSpk)
        return obj.toString()
    }

    private fun deserializeAndApplyStateForMode(json: String, fxType: Int) {
        val obj = JSONObject(json)
        val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
        _uiState.update { state ->
            deserializeEffectPrefs(obj, state, isSpk)
        }
    }

    private fun saveAndDispatchInt(prefKey: String, param: Int, value: Int) {
        viewModelScope.launch { repository.setIntPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchInt(param, value)
    }

    private fun saveAndDispatchFetBool(prefKey: String, param: Int, value: Boolean) {
        viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchInt(
            param,
            if (value) 100 else 0
        )
    }

    private fun spkSaveAndDispatchInt(prefKey: String, param: Int, value: Int) {
        viewModelScope.launch { repository.setIntPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchInt(param, value)
    }

    private fun spkSaveAndDispatchFetBool(prefKey: String, param: Int, value: Boolean) {
        viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchInt(
            param,
            if (value) 100 else 0
        )
    }

    private fun dispatchInt(param: Int, value: Int) {
        viperService?.dispatchParam(param, value)
    }

    private fun hpDispatchInt(param: Int, value: Int) {
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchInt(param, value)
    }

    private fun spkDispatchInt(param: Int, value: Int) {
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchInt(param, value)
    }

    private fun dispatchEqBands(
        param: Int,
        bandsString: String,
        bandCountParam: Int = 0,
        bandCount: Int = 0
    ) {
        viperService?.dispatchEqBands(param, bandsString, bandCountParam, bandCount)
    }

    private fun hpDispatchEqBands(bandsString: String) {
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchEqBands(
            ViperParams.PARAM_HP_EQ_BAND_LEVEL,
            bandsString
        )
    }

    private fun spkDispatchEqBands(bandsString: String) {
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchEqBands(
            ViperParams.PARAM_SPK_EQ_BAND_LEVEL,
            bandsString
        )
    }
}

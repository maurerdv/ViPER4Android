package com.llsl.viper4android.ui.screens.main

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llsl.viper4android.BULK_OP_CHANNEL_ID
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.ConfigChannel
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.audio.ParamEntry
import com.llsl.viper4android.audio.ViperEffect
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.audio.WavDecoder
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
import kotlinx.coroutines.delay
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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import javax.inject.Inject

data class DriverStatus(
    val installed: Boolean = false,
    val versionCode: Int = -1,
    val versionName: String = "",
    val architecture: String = "",
    val streaming: Boolean = false,
    val samplingRate: Int = 0,
)

data class MainUiState(
    val masterEnable: Boolean = false,
    val out: OutputState = OutputState(),
    val playbackGainControl: PlaybackGainControlState = PlaybackGainControlState(),
    val lufs: LufsState = LufsState(),
    val fetCompressor: FetCompressorState = FetCompressorState(),
    val multibandCompressor: MultibandCompressorState = MultibandCompressorState(),
    val ddc: DdcState = DdcState(),
    val spectrumExtension: SpectrumExtensionState = SpectrumExtensionState(),
    val eq: EqState = EqState(),
    val dynamicEq: DynamicEqState = DynamicEqState(),
    val convolver: ConvolverState = ConvolverState(),
    val fieldSurround: FieldSurroundState = FieldSurroundState(),
    val diffSurround: DiffSurroundState = DiffSurroundState(),
    val stereoImager: StereoImagerState = StereoImagerState(),
    val headphoneSurround: HeadphoneSurroundState = HeadphoneSurroundState(),
    val reverb: ReverbState = ReverbState(),
    val dynamicSystem: DynamicSystemState = DynamicSystemState(),
    val psychoacousticBass: PsychoacousticBassState = PsychoacousticBassState(),
    val bass: BassState = BassState(),
    val bassMono: BassMonoState = BassMonoState(),
    val clarity: ClarityState = ClarityState(),
    val cure: CureState = CureState(),
    val analogX: AnalogXState = AnalogXState(),
    val tubeSimulator: TubeSimulatorState = TubeSimulatorState(),
    val speakerCorrection: SpeakerCorrectionState = SpeakerCorrectionState(),
    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
)

@Suppress("StaticFieldLeak")
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        application: Application,
        private val repository: ViperRepository,
    ) : AndroidViewModel(application) {
        companion object {
            const val PREF_AUTO_START = "auto_start"
            const val PREF_GLOBAL_MODE = "global_mode"
            const val PREF_DEBUG_MODE = "debug_mode"
            private const val NOTIFY_ID_PRESET_IMPORT = 2
            private const val NOTIFY_ID_PRESET_CLEAR = 3
            private const val NOTIFY_ID_KERNEL_IMPORT = 4
            private const val NOTIFY_ID_VDC_IMPORT = 5
            private const val PROGRESS_NOTIFY_MIN_GAP_MS = 200L
            private const val PROGRESS_DRAIN_DELAY_MS = 250L
        }

        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        val presetList: StateFlow<List<Preset>> =
            repository.getAllPresets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val deviceSettingsList: StateFlow<List<DeviceSettings>> =
            repository.getAllDeviceSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        private var eqPresetsJob: Job? = null
        private var dsPresetsJob: Job? = null

        private val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    val localBinder = binder as? ViperService.LocalBinder ?: return
                    viperService = localBinder.service
                    serviceBound = true
                    viperService?.setStateProvider { _uiState.value }
                    dispatchFullState()
                    queryDriverStatus()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    viperService = null
                    serviceBound = false
                }
            }

        init {
            refreshFileLists()
            val initialDevice = audioOutputDetector.activeDevice.value
            viewModelScope.launch {
                loadSettingsPreferences()
                _uiState.update { loadEffectPrefs(repository, it) }
                val dbName = repository.getDeviceSettings(initialDevice.id)?.deviceName ?: initialDevice.name
                _uiState.update { it.copy(activeDeviceName = dbName, activeDeviceId = initialDevice.id) }
                loadEqPresetsForBandCount(_uiState.value.eq.bandCount)
                loadDsPresets()
                loadDeviceSettings(initialDevice)
                ensureDeviceEntry(initialDevice)
                bindToService()
                audioOutputDetector.activeDevice.collect { device ->
                    val currentId = _uiState.value.activeDeviceId
                    if (device.id != currentId) {
                        val dbName2 = repository.getDeviceSettings(device.id)?.deviceName ?: device.name
                        _uiState.update { it.copy(activeDeviceName = dbName2, activeDeviceId = device.id) }
                        loadDeviceSettings(device)
                    }
                    ensureDeviceEntry(device)
                }
            }
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

        fun <T> applyPref(
            pref: EffectPref<T>,
            value: T,
        ) {
            _uiState.update { pref.set(it, value) }
            viewModelScope.launch {
                persistPref(pref, value)
                if (pref.paramId != -1 && _uiState.value.masterEnable) {
                    viperService?.dispatchParam(pref.paramId, pref.toRaw(value))
                }
            }
        }

        private inline fun ifMasterOn(block: () -> Unit) {
            if (_uiState.value.masterEnable) block()
        }

        @Suppress("UNCHECKED_CAST")
        private suspend fun persistPref(
            pref: EffectPref<*>,
            value: Any?,
        ) {
            when (pref) {
                is IntPref -> {
                    repository.setIntPreference(pref.prefKey, value as Int)
                }

                is BoolPref -> {
                    repository.setBooleanPreference(pref.prefKey, value as Boolean)
                }

                is StringPref -> {
                    repository.setStringPreference(pref.prefKey, value as String)
                }

                is NullableLongPref -> {
                    repository.setIntPreference(pref.prefKey, (value as Long?)?.toInt() ?: -1)
                }

                is IntListPref -> {
                    val list = value as List<Int>
                    repository.setStringPreference(pref.prefKey, list.joinToString(";"))
                }

                is BoolListPref -> {
                    val list = value as List<Boolean>
                    repository.setStringPreference(pref.prefKey, list.joinToString(";") { if (it) "1" else "0" })
                }

                is DoubleListPref -> {
                    val list = value as List<Double>
                    repository.setStringPreference(pref.prefKey, list.joinToString(";") { String.format(Locale.US, "%.1f", it) })
                }
            }
        }

        private fun bindToService() {
            val intent = Intent(getApplication(), ViperService::class.java)
            getApplication<Application>().bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }

        private suspend fun loadSettingsPreferences() {
            _autoStartEnabled.value = repository.getBooleanPreference(PREF_AUTO_START, false).first()
            _globalModeEnabled.value = repository.getBooleanPreference(PREF_GLOBAL_MODE, false).first()
            _debugModeEnabled.value = repository.getBooleanPreference(PREF_DEBUG_MODE, false).first()
            _aidlModeEnabled.value = repository.aidlMode
        }

        private fun loadEqPresetsForBandCount(bandCount: Int) {
            eqPresetsJob?.cancel()
            eqPresetsJob =
                viewModelScope.launch {
                    repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                        _uiState.update { it.copy(eq = it.eq.copy(presets = presets)) }
                    }
                }
        }

        private fun loadDsPresets() {
            dsPresetsJob?.cancel()
            dsPresetsJob =
                viewModelScope.launch {
                    repository.getAllDsPresets().collect { presets ->
                        _uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(presets = presets)) }
                    }
                }
        }

        fun dispatchFullState() {
            val service = viperService ?: return
            val state = _uiState.value
            if (!state.masterEnable) {
                FileLogger.d("ViewModel", "dispatchFullState: skipped (master OFF)")
                return
            }
            FileLogger.d("ViewModel", "dispatchFullState: master=ON")
            service.dispatchFullState(state, true)

            if (state.convolver.enable && state.convolver.kernelFile.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) { stageAndDispatchKernel(state.convolver.kernelFile) }
            }
            if (state.ddc.enable && state.ddc.device.isNotEmpty()) {
                streamDdcCoefficients(state.ddc.device)
            }
        }

        fun setMasterEnabled(enabled: Boolean) {
            FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(masterEnable = enabled) }
            viewModelScope.launch {
                repository.setBooleanPreference(ViperRepository.PREF_MASTER_ENABLE, enabled)
            }
            viperService?.setEffectEnabled(enabled)
            if (enabled) dispatchFullState()
        }

        fun setConvolverKernel(fileName: String) {
            FileLogger.i("ViewModel", "Convolver kernel: $fileName")
            applyPref(Effects.convolver.kernelFile, fileName)
            if (fileName.isEmpty()) {
                ifMasterOn {
                    if (_aidlModeEnabled.value) {
                        viperService?.dispatchConvolverKernelPath("")
                    } else {
                        viperService?.dispatchParam(ViperParams.kParamConvolverPrepareBuffer, 0, 0, 1)
                    }
                }
                return
            }
            viewModelScope.launch(Dispatchers.IO) { stageAndDispatchKernel(fileName) }
        }

        private fun stageAndDispatchKernel(fileName: String) {
            val src = File(getFilesDir("Kernel"), fileName)
            if (!src.exists()) {
                FileLogger.w("ViewModel", "Kernel file missing: $fileName")
                return
            }
            try {
                if (_aidlModeEnabled.value) {
                    stageAndDispatchKernelAidl(src, fileName)
                } else {
                    streamKernelLegacy(src, fileName)
                }
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to stage kernel: $fileName", e)
            }
        }

        private fun stageAndDispatchKernelAidl(
            src: File,
            fileName: String,
        ) {
            val safeName = fileName.replace("'", "")
            val stagedPath = "/data/local/tmp/v4a/kernel/$safeName"
            RootShell.copyFile(src, stagedPath)
            FileLogger.i("ViewModel", "Kernel staged at $stagedPath")
            ifMasterOn { viperService?.dispatchConvolverKernelPath(stagedPath) }
        }

        private fun streamKernelLegacy(
            src: File,
            fileName: String,
        ) {
            val service = viperService ?: return
            val decoded = WavDecoder.decode(src.readBytes())
            val samples = decoded.samples
            val totalFloats = samples.size
            val channelCount = decoded.channels
            FileLogger.i("ViewModel", "Kernel decoded: $fileName samples=$totalFloats ch=$channelCount")

            ifMasterOn { service.dispatchParam(ViperParams.kParamConvolverPrepareBuffer, totalFloats, channelCount, 0) }

            val rawBytes =
                ByteBuffer
                    .allocate(totalFloats * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .also { for (f in samples) it.putFloat(f) }
                    .array()
            val crc = CRC32().apply { update(rawBytes) }.value.toInt()

            val maxFloatsPerChunk = 2046
            var offset = 0
            var chunkIndex = 0
            while (offset < totalFloats) {
                val remaining = totalFloats - offset
                val floatsInChunk = minOf(remaining, maxFloatsPerChunk)
                val chunk = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
                chunk.putInt(chunkIndex)
                chunk.putInt(floatsInChunk)
                chunk.put(rawBytes, offset * 4, floatsInChunk * 4)
                ifMasterOn { service.dispatchParam(ViperParams.kParamConvolverSetBuffer, chunk.array()) }
                offset += floatsInChunk
                chunkIndex++
            }

            val kernelId = fileName.hashCode()
            ifMasterOn {
                service.dispatchParam(ViperParams.kParamConvolverCommitBuffer, totalFloats, crc, kernelId)
            }
            FileLogger.i("ViewModel", "Kernel streamed: $fileName chunks=$chunkIndex crc=0x${crc.toUInt().toString(16)}")
        }

        fun setDdcDevice(name: String) {
            FileLogger.i("ViewModel", "DDC device: $name")
            applyPref(Effects.ddc.device, name)
            if (name.isEmpty()) return
            viewModelScope.launch(Dispatchers.IO) { streamDdcCoefficients(name) }
        }

        private fun streamDdcCoefficients(name: String) {
            val file = File(getFilesDir("DDC"), "$name.vdc")
            if (!file.exists()) {
                FileLogger.w("ViewModel", "VDC file missing: $name")
                return
            }
            val parsed = parseVdc(file) ?: return
            ifMasterOn { viperService?.dispatchDdcCoefficients(parsed.first, parsed.second) }
        }

        private fun parseVdc(file: File): Pair<List<FloatArray>, List<FloatArray>>? {
            try {
                var coeffs44100: FloatArray? = null
                var coeffs48000: FloatArray? = null
                for (line in file.readLines()) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("SR_44100:") -> {
                            coeffs44100 =
                                trimmed
                                    .removePrefix("SR_44100:")
                                    .split(",")
                                    .map { it.trim().toFloat() }
                                    .toFloatArray()
                        }

                        trimmed.startsWith("SR_48000:") -> {
                            coeffs48000 =
                                trimmed
                                    .removePrefix("SR_48000:")
                                    .split(",")
                                    .map { it.trim().toFloat() }
                                    .toFloatArray()
                        }
                    }
                }
                if (coeffs44100 == null || coeffs48000 == null) return null
                if (coeffs44100.size != coeffs48000.size) return null
                if (coeffs44100.size % 5 != 0) return null
                val sec44 = coeffs44100.toList().chunked(5).map { it.toFloatArray() }
                val sec48 = coeffs48000.toList().chunked(5).map { it.toFloatArray() }
                return sec44 to sec48
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to parse VDC: ${file.name}", e)
                return null
            }
        }

        fun setEqPreset(presetId: Long) {
            viewModelScope.launch {
                val preset = repository.getEqPresetById(presetId) ?: return@launch
                val bands: List<Double> =
                    preset.bands
                        .split(";")
                        .filter { it.isNotBlank() }
                        .mapNotNull { it.toDoubleOrNull() }
                applyPref(Effects.equalizer.presetId, presetId)
                applyPref(Effects.equalizer.bands, bands)
                _uiState.update { state ->
                    val updatedMap =
                        state.eq.bandsMap
                            .toMutableMap()
                            .apply { put(state.eq.bandCount, bands) }
                    state.copy(eq = state.eq.copy(bandsMap = updatedMap))
                }
                ifMasterOn { viperService?.dispatchEqBands(bands) }
            }
        }

        fun setEqBands(bands: List<Double>) {
            applyPref(Effects.equalizer.bands, bands)
            _uiState.update { state ->
                val updatedMap =
                    state.eq.bandsMap
                        .toMutableMap()
                        .apply { put(state.eq.bandCount, bands) }
                state.copy(eq = state.eq.copy(bandsMap = updatedMap))
            }
            ifMasterOn { viperService?.dispatchEqBands(bands) }
        }

        fun setEqBandCount(count: Int) {
            val state = _uiState.value
            val oldCount = state.eq.bandCount
            FileLogger.d("ViewModel", "EQ band count: $oldCount -> $count")
            val updatedMap =
                state.eq.bandsMap
                    .toMutableMap()
                    .apply { put(oldCount, state.eq.bands) }
            val defaultBands = List(count) { 0.0 }
            val bands = updatedMap[count] ?: defaultBands
            _uiState.update {
                it.copy(
                    eq =
                        it.eq.copy(
                            bandCount = count,
                            bands = bands,
                            presetId = null,
                            bandsMap = updatedMap,
                        ),
                )
            }

            val joinDoubles: (List<Double>) -> String = { list ->
                list.joinToString(";") { String.format(Locale.US, "%.1f", it) }
            }
            viewModelScope.launch {
                repository.setIntPreference(Effects.equalizer.bandCount.prefKey, count)
                repository.setStringPreference(Effects.equalizer.bands.prefKey, joinDoubles(bands))
                repository.setStringPreference("eq_bands_$oldCount", joinDoubles(state.eq.bands))
                repository.setStringPreference("eq_bands_$count", joinDoubles(bands))
                repository.setIntPreference(Effects.equalizer.presetId.prefKey, -1)
            }
            ifMasterOn { viperService?.dispatchEqBands(bands, count) }
            loadEqPresetsForBandCount(count)
        }

        fun addEqPreset(name: String) {
            val state = _uiState.value
            val bandsStr =
                state.eq.bands.joinToString(";") {
                    String.format(Locale.US, "%.1f", it)
                }
            val preset =
                EqPreset(
                    name = name,
                    bandCount = state.eq.bandCount,
                    bands = bandsStr,
                )
            viewModelScope.launch {
                val id = repository.saveEqPreset(preset)
                applyPref(Effects.equalizer.presetId, id)
            }
        }

        fun deleteEqPreset(presetId: Long) {
            viewModelScope.launch {
                repository.deleteEqPresetById(presetId)
                if (_uiState.value.eq.presetId == presetId) {
                    applyPref(Effects.equalizer.presetId, null)
                }
            }
        }

        fun resetEqBands() {
            val bandCount = _uiState.value.eq.bandCount
            val flat = List(bandCount) { 0.0 }
            setEqBands(flat)
            applyPref(Effects.equalizer.presetId, null)
        }

        private fun dispatchDynamicSystem() {
            ifMasterOn {
                val v = _uiState.value.dynamicSystem
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamDynamicSystemEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(
                            ViperParams.kParamDynamicSystemStrength,
                            intArrayOf(EffectDispatcher.dynamicSystemStrengthToRaw(v.strength)),
                        ),
                        ParamEntry(ViperParams.kParamDynamicSystemXCoefficients, intArrayOf(v.xLow, v.xHigh)),
                        ParamEntry(ViperParams.kParamDynamicSystemYCoefficients, intArrayOf(v.yLow, v.yHigh)),
                        ParamEntry(ViperParams.kParamDynamicSystemSideGain, intArrayOf(v.sideGainLow, v.sideGainHigh)),
                    ),
                )
            }
        }

        fun setDynamicSystemXLow(value: Int) {
            applyPref(Effects.dynamicSystem.xLow, value)
            dispatchDynamicSystem()
        }

        fun setDynamicSystemXHigh(value: Int) {
            applyPref(Effects.dynamicSystem.xHigh, value)
            dispatchDynamicSystem()
        }

        fun setDynamicSystemYLow(value: Int) {
            applyPref(Effects.dynamicSystem.yLow, value)
            dispatchDynamicSystem()
        }

        fun setDynamicSystemYHigh(value: Int) {
            applyPref(Effects.dynamicSystem.yHigh, value)
            dispatchDynamicSystem()
        }

        fun setDynamicSystemSideGainLow(value: Int) {
            applyPref(Effects.dynamicSystem.sideGainLow, value)
            dispatchDynamicSystem()
        }

        fun setDynamicSystemSideGainHigh(value: Int) {
            applyPref(Effects.dynamicSystem.sideGainHigh, value)
            dispatchDynamicSystem()
        }

        fun setDynamicSystemStrength(value: Int) {
            applyPref(Effects.dynamicSystem.strength, value)
            dispatchDynamicSystem()
        }

        fun setDynamicSystemPreset(presetId: Long) {
            viewModelScope.launch {
                val preset = repository.getDsPresetById(presetId) ?: return@launch
                applyPref(Effects.dynamicSystem.presetId, presetId)
                applyPref(Effects.dynamicSystem.xLow, preset.xLow)
                applyPref(Effects.dynamicSystem.xHigh, preset.xHigh)
                applyPref(Effects.dynamicSystem.yLow, preset.yLow)
                applyPref(Effects.dynamicSystem.yHigh, preset.yHigh)
                applyPref(Effects.dynamicSystem.sideGainLow, preset.sideGainLow)
                applyPref(Effects.dynamicSystem.sideGainHigh, preset.sideGainHigh)
                dispatchDynamicSystem()
            }
        }

        fun addDynamicSystemPreset(name: String) {
            val v = _uiState.value.dynamicSystem
            viewModelScope.launch {
                val id =
                    repository.saveDsPreset(
                        DsPreset(
                            name = name,
                            xLow = v.xLow,
                            xHigh = v.xHigh,
                            yLow = v.yLow,
                            yHigh = v.yHigh,
                            sideGainLow = v.sideGainLow,
                            sideGainHigh = v.sideGainHigh,
                        ),
                    )
                applyPref(Effects.dynamicSystem.presetId, id)
            }
        }

        fun deleteDynamicSystemPreset(presetId: Long) {
            viewModelScope.launch {
                repository.deleteDsPresetById(presetId)
                if (_uiState.value.dynamicSystem.presetId == presetId) {
                    applyPref(Effects.dynamicSystem.presetId, null)
                }
            }
        }

        fun resetDynamicSystemCoefficients() {
            applyPref(Effects.dynamicSystem.xLow, Effects.dynamicSystem.xLow.defaultValue)
            applyPref(Effects.dynamicSystem.xHigh, Effects.dynamicSystem.xHigh.defaultValue)
            applyPref(Effects.dynamicSystem.yLow, Effects.dynamicSystem.yLow.defaultValue)
            applyPref(Effects.dynamicSystem.yHigh, Effects.dynamicSystem.yHigh.defaultValue)
            applyPref(Effects.dynamicSystem.sideGainLow, Effects.dynamicSystem.sideGainLow.defaultValue)
            applyPref(Effects.dynamicSystem.sideGainHigh, Effects.dynamicSystem.sideGainHigh.defaultValue)
            applyPref(Effects.dynamicSystem.presetId, null)
            dispatchDynamicSystem()
        }

        private fun replaceAt(
            list: List<Int>,
            index: Int,
            value: Int,
            count: Int = 5,
        ): List<Int> {
            val mutable = list.toMutableList()
            while (mutable.size <= index) mutable.add(0)
            mutable[index] = value
            return if (mutable.size > count) mutable.take(count) else mutable.toList()
        }

        private fun replaceAt(
            list: List<Boolean>,
            index: Int,
            value: Boolean,
            count: Int = 5,
        ): List<Boolean> {
            val mutable = list.toMutableList()
            while (mutable.size <= index) mutable.add(true)
            mutable[index] = value
            return if (mutable.size > count) mutable.take(count) else mutable.toList()
        }

        private fun multibandCompressorBandUpdate(
            pref: IntListPref,
            band: Int,
            value: Int,
            paramId: Int,
        ) {
            val cur = pref.get(_uiState.value)
            val updated = replaceAt(cur, band, value)
            applyPref(pref, updated)
            ifMasterOn { viperService?.dispatchParam(paramId, band, value, 0) }
        }

        private fun multibandCompressorBandUpdateBool(
            pref: BoolListPref,
            band: Int,
            value: Boolean,
            paramId: Int,
        ) {
            val cur = pref.get(_uiState.value)
            val updated = replaceAt(cur, band, value)
            applyPref(pref, updated)
            ifMasterOn { viperService?.dispatchParam(paramId, band, if (value) 1 else 0, 0) }
        }

        fun setMultibandCompressorBandEnable(
            band: Int,
            value: Boolean,
        ) = multibandCompressorBandUpdateBool(
            Effects.multibandCompressor.bandEnables,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandEnable,
        )

        fun setMultibandCompressorCrossover(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.crossovers,
            band,
            value,
            ViperParams.kParamMultibandCompressorCrossoverFrequency,
        )

        fun setMultibandCompressorBandThreshold(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.thresholds,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandThreshold,
        )

        fun setMultibandCompressorBandRatio(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.ratios,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandRatio,
        )

        fun setMultibandCompressorBandGain(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.gains,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandGain,
        )

        fun setMultibandCompressorBandKnee(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.knees,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandKnee,
        )

        fun setMultibandCompressorBandKneeMulti(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.kneeMultis,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandKneeMulti,
        )

        fun setMultibandCompressorBandAttack(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.attacks,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandAttack,
        )

        fun setMultibandCompressorBandMaxAttack(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.maxAttacks,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandMaxAttack,
        )

        fun setMultibandCompressorBandRelease(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.releases,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandRelease,
        )

        fun setMultibandCompressorBandMaxRelease(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.maxReleases,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandMaxRelease,
        )

        fun setMultibandCompressorBandCrest(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.crests,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandCrest,
        )

        fun setMultibandCompressorBandAdapt(
            band: Int,
            value: Int,
        ) = multibandCompressorBandUpdate(
            Effects.multibandCompressor.adapts,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandAdapt,
        )

        fun setMultibandCompressorBandAutoKnee(
            band: Int,
            value: Boolean,
        ) = multibandCompressorBandUpdateBool(
            Effects.multibandCompressor.kneeAutos,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandKneeAuto,
        )

        fun setMultibandCompressorBandAutoGain(
            band: Int,
            value: Boolean,
        ) = multibandCompressorBandUpdateBool(
            Effects.multibandCompressor.gainAutos,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandGainAuto,
        )

        fun setMultibandCompressorBandAutoAttack(
            band: Int,
            value: Boolean,
        ) = multibandCompressorBandUpdateBool(
            Effects.multibandCompressor.attackAutos,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandAttackAuto,
        )

        fun setMultibandCompressorBandAutoRelease(
            band: Int,
            value: Boolean,
        ) = multibandCompressorBandUpdateBool(
            Effects.multibandCompressor.releaseAutos,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandReleaseAuto,
        )

        fun setMultibandCompressorBandNoClip(
            band: Int,
            value: Boolean,
        ) = multibandCompressorBandUpdateBool(
            Effects.multibandCompressor.noClips,
            band,
            value,
            ViperParams.kParamMultibandCompressorBandNoClip,
        )

        private fun dynamicEqBandUpdate(
            pref: IntListPref,
            band: Int,
            value: Int,
            paramId: Int,
        ) {
            val cur = pref.get(_uiState.value)
            val count = _uiState.value.dynamicEq.bandCount
            val updated = replaceAt(cur, band, value, count)
            applyPref(pref, updated)
            ifMasterOn { viperService?.dispatchParam(paramId, band, value, 0) }
        }

        fun setDynamicEqBandFreq(
            band: Int,
            value: Int,
        ) = dynamicEqBandUpdate(Effects.dynamicEq.freqs, band, value, ViperParams.kParamDynamicEqBandFrequency)

        fun setDynamicEqBandQ(
            band: Int,
            value: Int,
        ) = dynamicEqBandUpdate(Effects.dynamicEq.qs, band, value, ViperParams.kParamDynamicEqBandQ)

        fun setDynamicEqBandGain(
            band: Int,
            value: Int,
        ) = dynamicEqBandUpdate(Effects.dynamicEq.gains, band, value, ViperParams.kParamDynamicEqBandGain)

        fun setDynamicEqBandThreshold(
            band: Int,
            value: Int,
        ) = dynamicEqBandUpdate(Effects.dynamicEq.thresholds, band, value, ViperParams.kParamDynamicEqBandThreshold)

        fun setDynamicEqBandAttack(
            band: Int,
            value: Int,
        ) = dynamicEqBandUpdate(Effects.dynamicEq.attacks, band, value, ViperParams.kParamDynamicEqBandAttack)

        fun setDynamicEqBandRelease(
            band: Int,
            value: Int,
        ) = dynamicEqBandUpdate(Effects.dynamicEq.releases, band, value, ViperParams.kParamDynamicEqBandRelease)

        fun setDynamicEqBandFilterType(
            band: Int,
            value: Int,
        ) = dynamicEqBandUpdate(Effects.dynamicEq.filterTypes, band, value, ViperParams.kParamDynamicEqBandFilterType)

        fun addDynamicEqBand() {
            val state = _uiState.value
            val cur = state.dynamicEq
            if (cur.bandCount >= 8) return
            val newCount = cur.bandCount + 1
            val newFreq =
                if (cur.freqs.isEmpty()) {
                    1000
                } else {
                    (cur.freqs.last() * 2).coerceAtMost(20000)
                }
            applyPref(Effects.dynamicEq.freqs, cur.freqs + newFreq)
            applyPref(Effects.dynamicEq.qs, cur.qs + 150)
            applyPref(Effects.dynamicEq.gains, cur.gains + 0)
            applyPref(Effects.dynamicEq.thresholds, cur.thresholds + (-300))
            applyPref(Effects.dynamicEq.attacks, cur.attacks + 10)
            applyPref(Effects.dynamicEq.releases, cur.releases + 100)
            applyPref(Effects.dynamicEq.filterTypes, cur.filterTypes + 0)
            applyPref(Effects.dynamicEq.bandCount, newCount)
            ifMasterOn { viperService?.dispatchParam(ViperParams.kParamDynamicEqBandCount, newCount) }
        }

        fun removeDynamicEqBand(index: Int) {
            val state = _uiState.value
            val cur = state.dynamicEq
            if (cur.bandCount <= 1) return
            if (index !in cur.freqs.indices) return
            val newCount = cur.bandCount - 1

            fun <T> List<T>.removeAt(i: Int) = filterIndexed { idx, _ -> idx != i }
            applyPref(Effects.dynamicEq.bandCount, newCount)
            applyPref(Effects.dynamicEq.freqs, cur.freqs.removeAt(index))
            applyPref(Effects.dynamicEq.qs, cur.qs.removeAt(index))
            applyPref(Effects.dynamicEq.gains, cur.gains.removeAt(index))
            applyPref(Effects.dynamicEq.thresholds, cur.thresholds.removeAt(index))
            applyPref(Effects.dynamicEq.attacks, cur.attacks.removeAt(index))
            applyPref(Effects.dynamicEq.releases, cur.releases.removeAt(index))
            applyPref(Effects.dynamicEq.filterTypes, cur.filterTypes.removeAt(index))
            ifMasterOn { viperService?.dispatchParam(ViperParams.kParamDynamicEqBandCount, newCount) }
        }

        fun setPlaybackGainControlEnabled(enabled: Boolean) {
            applyPref(Effects.playbackGainControl.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.playbackGainControl
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamPlaybackGainControlEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamPlaybackGainControlStrength, intArrayOf(v.strength)),
                        ParamEntry(ViperParams.kParamPlaybackGainControlMaxGain, intArrayOf(v.maxGain)),
                        ParamEntry(ViperParams.kParamPlaybackGainControlOutputThreshold, intArrayOf(v.outputThreshold)),
                    ),
                )
            }
        }

        fun setLufsEnabled(enabled: Boolean) {
            applyPref(Effects.lufs.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.lufs
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamLufsEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamLufsTarget, intArrayOf(v.target)),
                        ParamEntry(ViperParams.kParamLufsMaxGain, intArrayOf(v.maxGain)),
                        ParamEntry(ViperParams.kParamLufsSpeed, intArrayOf(v.speed)),
                    ),
                )
            }
        }

        fun setFetCompressorEnabled(enabled: Boolean) {
            applyPref(Effects.fetCompressor.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.fetCompressor
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamFetCompressorEnable, intArrayOf(if (v.enable) 100 else 0)),
                        ParamEntry(
                            ViperParams.kParamFetCompressorThreshold,
                            intArrayOf(EffectDispatcher.fetCompressorThresholdToRaw(v.threshold)),
                        ),
                        ParamEntry(ViperParams.kParamFetCompressorRatio, intArrayOf(v.ratio)),
                        ParamEntry(ViperParams.kParamFetCompressorKneeAuto, intArrayOf(if (v.kneeAuto) 100 else 0)),
                        ParamEntry(ViperParams.kParamFetCompressorKnee, intArrayOf(EffectDispatcher.fetCompressorKneeToRaw(v.knee))),
                        ParamEntry(ViperParams.kParamFetCompressorKneeMulti, intArrayOf(v.kneeMulti)),
                        ParamEntry(ViperParams.kParamFetCompressorGainAuto, intArrayOf(if (v.gainAuto) 100 else 0)),
                        ParamEntry(ViperParams.kParamFetCompressorGain, intArrayOf(EffectDispatcher.fetCompressorGainToRaw(v.gain))),
                    ),
                )
            }
        }

        fun setMultibandCompressorEnabled(enabled: Boolean) {
            applyPref(Effects.multibandCompressor.enable, enabled)
            ifMasterOn {
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamMultibandCompressorEnable, intArrayOf(if (enabled) 1 else 0)),
                    ),
                )
            }
        }

        fun setDdcEnabled(enabled: Boolean) {
            applyPref(Effects.ddc.enable, enabled)
            if (enabled &&
                _uiState.value.ddc.device
                    .isNotEmpty()
            ) {
                viewModelScope.launch(Dispatchers.IO) {
                    streamDdcCoefficients(_uiState.value.ddc.device)
                }
            }
        }

        fun setSpectrumExtensionEnabled(enabled: Boolean) {
            applyPref(Effects.spectrumExtension.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.spectrumExtension
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamSpectrumExtensionEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamSpectrumExtensionStrength, intArrayOf(v.strength)),
                        ParamEntry(
                            ViperParams.kParamSpectrumExtensionExciter,
                            intArrayOf(EffectDispatcher.spectrumExtensionExciterToRaw(v.exciter)),
                        ),
                    ),
                )
            }
        }

        fun setEqEnabled(enabled: Boolean) {
            applyPref(Effects.equalizer.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.eq
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamEqualizerEnable, intArrayOf(if (v.enable) 1 else 0)),
                    ),
                )
                viperService?.dispatchEqBands(v.bands)
            }
        }

        fun setDynamicEqEnabled(enabled: Boolean) {
            applyPref(Effects.dynamicEq.enable, enabled)
            ifMasterOn {
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamDynamicEqEnable, intArrayOf(if (enabled) 1 else 0)),
                    ),
                )
            }
        }

        fun setConvolverEnabled(enabled: Boolean) {
            applyPref(Effects.convolver.enable, enabled)
            if (enabled &&
                _uiState.value.convolver.kernelFile
                    .isNotEmpty()
            ) {
                viewModelScope.launch(Dispatchers.IO) {
                    stageAndDispatchKernel(_uiState.value.convolver.kernelFile)
                }
            }
        }

        fun setFieldSurroundEnabled(enabled: Boolean) {
            applyPref(Effects.fieldSurround.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.fieldSurround
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamFieldSurroundEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(
                            ViperParams.kParamFieldSurroundWidening,
                            intArrayOf(EffectDispatcher.fieldSurroundWideningToRaw(v.widening)),
                        ),
                        ParamEntry(
                            ViperParams.kParamFieldSurroundMidImage,
                            intArrayOf(EffectDispatcher.fieldSurroundMidImageToRaw(v.midImage)),
                        ),
                        ParamEntry(ViperParams.kParamFieldSurroundDepth, intArrayOf(EffectDispatcher.fieldSurroundDepthToRaw(v.depth))),
                    ),
                )
            }
        }

        fun setDiffSurroundEnabled(enabled: Boolean) {
            applyPref(Effects.diffSurround.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.diffSurround
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamDiffSurroundEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamDiffSurroundDelay, intArrayOf(EffectDispatcher.diffSurroundDelayToRaw(v.delay))),
                        ParamEntry(ViperParams.kParamDiffSurroundReverse, intArrayOf(if (v.reverse) 1 else 0)),
                        ParamEntry(ViperParams.kParamDiffSurroundWetDryMix, intArrayOf(v.wetDryMix)),
                        ParamEntry(ViperParams.kParamDiffSurroundLpCutoff, intArrayOf(v.lpCutoff)),
                    ),
                )
            }
        }

        fun setStereoImagerEnabled(enabled: Boolean) {
            applyPref(Effects.stereoImager.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.stereoImager
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamStereoImagerEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamStereoImagerLowWidth, intArrayOf(v.lowWidth)),
                        ParamEntry(ViperParams.kParamStereoImagerMidWidth, intArrayOf(v.midWidth)),
                        ParamEntry(ViperParams.kParamStereoImagerHighWidth, intArrayOf(v.highWidth)),
                        ParamEntry(ViperParams.kParamStereoImagerLowCrossover, intArrayOf(v.lowCrossover)),
                        ParamEntry(ViperParams.kParamStereoImagerHighCrossover, intArrayOf(v.highCrossover)),
                    ),
                )
            }
        }

        fun setHeadphoneSurroundEnabled(enabled: Boolean) {
            applyPref(Effects.headphoneSurround.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.headphoneSurround
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamHeadphoneSurroundEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamHeadphoneSurroundQuality, intArrayOf(v.quality)),
                    ),
                )
            }
        }

        fun setReverbEnabled(enabled: Boolean) {
            applyPref(Effects.reverb.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.reverb
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamReverbEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamReverbRoomSize, intArrayOf(v.roomSize * 10)),
                        ParamEntry(ViperParams.kParamReverbWidth, intArrayOf(v.width * 10)),
                        ParamEntry(ViperParams.kParamReverbDamp, intArrayOf(v.damp)),
                        ParamEntry(ViperParams.kParamReverbWet, intArrayOf(v.wet)),
                        ParamEntry(ViperParams.kParamReverbDry, intArrayOf(v.dry)),
                    ),
                )
            }
        }

        fun setDynamicSystemEnabled(enabled: Boolean) {
            applyPref(Effects.dynamicSystem.enable, enabled)
            dispatchDynamicSystem()
        }

        fun setTubeSimulatorEnabled(enabled: Boolean) {
            applyPref(Effects.tubeSimulator.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.tubeSimulator
                viperService?.dispatchParamsBatch(
                    listOf(ParamEntry(ViperParams.kParamTubeSimulatorEnable, intArrayOf(if (v.enable) 1 else 0))),
                )
            }
        }

        fun setPsychoacousticBassEnabled(enabled: Boolean) {
            applyPref(Effects.psychoacousticBass.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.psychoacousticBass
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamPsychoacousticBassEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamPsychoacousticBassCutoff, intArrayOf(v.cutoff)),
                        ParamEntry(ViperParams.kParamPsychoacousticBassIntensity, intArrayOf(v.intensity)),
                        ParamEntry(ViperParams.kParamPsychoacousticBassHarmonicOrder, intArrayOf(v.harmonicOrder)),
                        ParamEntry(ViperParams.kParamPsychoacousticBassOriginalLevel, intArrayOf(v.originalLevel)),
                    ),
                )
            }
        }

        fun setBassEnabled(enabled: Boolean) {
            applyPref(Effects.bass.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.bass
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamBassEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamBassMode, intArrayOf(v.mode)),
                        ParamEntry(ViperParams.kParamBassFrequency, intArrayOf(EffectDispatcher.bassFrequencyToRaw(v.frequency))),
                        ParamEntry(ViperParams.kParamBassGain, intArrayOf(v.gain)),
                        ParamEntry(ViperParams.kParamBassAntiPop, intArrayOf(if (v.antiPop) 1 else 0)),
                    ),
                )
            }
        }

        fun setBassMonoEnabled(enabled: Boolean) {
            applyPref(Effects.bassMono.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.bassMono
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamBassMonoEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamBassMonoMode, intArrayOf(v.mode)),
                        ParamEntry(ViperParams.kParamBassMonoFrequency, intArrayOf(EffectDispatcher.bassFrequencyToRaw(v.frequency))),
                        ParamEntry(ViperParams.kParamBassMonoGain, intArrayOf(v.gain)),
                        ParamEntry(ViperParams.kParamBassMonoAntiPop, intArrayOf(if (v.antiPop) 1 else 0)),
                    ),
                )
            }
        }

        fun setClarityEnabled(enabled: Boolean) {
            applyPref(Effects.clarity.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.clarity
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamClarityEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamClarityMode, intArrayOf(v.mode)),
                        ParamEntry(ViperParams.kParamClarityGain, intArrayOf(v.gain)),
                    ),
                )
            }
        }

        fun setCureEnabled(enabled: Boolean) {
            applyPref(Effects.cure.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.cure
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamCureEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamCureCrossfeedPreset, intArrayOf(v.crossfeedPreset)),
                    ),
                )
            }
        }

        fun setAnalogXEnabled(enabled: Boolean) {
            applyPref(Effects.analogX.enable, enabled)
            ifMasterOn {
                val v = _uiState.value.analogX
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(ViperParams.kParamAnalogXEnable, intArrayOf(if (v.enable) 1 else 0)),
                        ParamEntry(ViperParams.kParamAnalogXMode, intArrayOf(v.mode)),
                    ),
                )
            }
        }

        fun setSpeakerCorrectionEnabled(enabled: Boolean) {
            applyPref(Effects.speakerCorrection.enable, enabled)
        }

        private suspend fun ensureDeviceEntry(device: AudioDevice) {
            val existing = repository.getDeviceSettings(device.id)
            if (existing == null) {
                val state = _uiState.value
                repository.saveDeviceSettings(
                    DeviceSettings(
                        deviceId = device.id,
                        deviceName = device.name,
                        isHeadphone = device.isHeadphone,
                        settingsJson = serializeEffectPrefs(state).toString(),
                    ),
                )
            } else {
                repository.updateDeviceLastConnected(device.id)
            }
        }

        private suspend fun saveCurrentDeviceSettings() {
            val state = _uiState.value
            val deviceId = state.activeDeviceId.ifEmpty { return }
            val json = serializeEffectPrefs(state).toString()
            val existing = repository.getDeviceSettings(deviceId)
            repository.saveDeviceSettings(
                DeviceSettings(
                    deviceId = deviceId,
                    deviceName = existing?.deviceName ?: state.activeDeviceName,
                    isHeadphone = existing?.isHeadphone ?: false,
                    settingsJson = json,
                ),
            )
        }

        private suspend fun loadDeviceSettings(device: AudioDevice) {
            val saved = repository.getDeviceSettings(device.id) ?: return
            val json = JSONObject(saved.settingsJson)
            _uiState.update { deserializeEffectPrefs(json, it) }
            saveEffectPrefs(repository, _uiState.value)
            dispatchFullState()
        }

        fun saveSettingsOnBackground() {
            viewModelScope.launch { saveCurrentDeviceSettings() }
        }

        fun renameDevice(
            deviceId: String,
            name: String,
        ) {
            viewModelScope.launch {
                repository.renameDevice(deviceId, name)
                if (deviceId == _uiState.value.activeDeviceId) {
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
                val json = serializeEffectPrefs(_uiState.value).toString()
                repository.saveDeviceSettings(existing.copy(settingsJson = json))
            }
        }

        fun loadDevicePreset(deviceId: String) {
            viewModelScope.launch {
                val saved = repository.getDeviceSettings(deviceId) ?: return@launch
                val json = JSONObject(saved.settingsJson)
                _uiState.update { deserializeEffectPrefs(json, it) }
                saveEffectPrefs(repository, _uiState.value)
                dispatchFullState()
            }
        }

        private fun getFilesDir(subDir: String): File {
            val dir = File(getApplication<Application>().getExternalFilesDir(null), subDir)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        private val lastBulkProgressNotifyMs = ConcurrentHashMap<Int, Long>()

        private fun updateBulkProgress(
            notificationId: Int,
            title: String,
            current: Int,
            total: Int,
        ) {
            val now = System.currentTimeMillis()
            val last = lastBulkProgressNotifyMs[notificationId] ?: 0L
            if (current < total && now - last < PROGRESS_NOTIFY_MIN_GAP_MS) return
            lastBulkProgressNotifyMs[notificationId] = now
            val app = getApplication<Application>()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification =
                NotificationCompat
                    .Builder(app, BULK_OP_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText("$current / $total")
                    .setProgress(total, current, false)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            nm.notify(notificationId, notification)
        }

        private suspend fun completeBulkProgress(
            notificationId: Int,
            title: String,
            content: String,
        ) {
            delay(PROGRESS_DRAIN_DELAY_MS)
            lastBulkProgressNotifyMs.remove(notificationId)
            val app = getApplication<Application>()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification =
                NotificationCompat
                    .Builder(app, BULK_OP_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .build()
            nm.notify(notificationId, notification)
        }

        private fun copyUriToFile(
            uri: Uri,
            destDir: File,
            fallbackName: String,
        ): File? {
            val context = getApplication<Application>()
            val fileName =
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                } ?: fallbackName
            val destFile = File(destDir, fileName)
            return try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                destFile
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to copy file", e)
                null
            }
        }

        fun importPresetFiles(
            uris: List<Uri>,
            notificationTitle: String,
            successStr: String,
            onResult: (Boolean) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val total = uris.size
                val showProgress = total > 10
                val destDir = getFilesDir("Preset")
                val baseState = _uiState.value
                var count = 0
                var lastParsed: MainUiState? = null
                for ((index, uri) in uris.withIndex()) {
                    try {
                        val tmpFile = copyUriToFile(uri, destDir, "import_$index.json")
                        if (tmpFile != null) {
                            val json = tmpFile.readText()
                            val obj = JSONObject(json)
                            val parsed = deserializeEffectPrefs(obj, baseState)
                            val importedName =
                                obj.optString("name", "").ifBlank {
                                    tmpFile.nameWithoutExtension
                                }
                            val importedCreatedAt =
                                obj.optLong("createdAt", System.currentTimeMillis())
                            val effectOnlyJson = serializeEffectPrefs(parsed).toString()
                            repository.savePreset(
                                Preset(
                                    name = importedName,
                                    settingsJson = effectOnlyJson,
                                    createdAt = importedCreatedAt,
                                ),
                            )
                            count++
                            lastParsed = parsed
                        }
                    } catch (e: Exception) {
                        FileLogger.e("ViewModel", "Failed to import preset from $uri", e)
                    }
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_PRESET_IMPORT, notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_PRESET_IMPORT, notificationTitle, "$successStr: $count / $total")
                }
                if (total == 1 && count == 1 && lastParsed != null) {
                    val applied = lastParsed
                    launch(Dispatchers.Main) {
                        _uiState.update { applied }
                        saveEffectPrefs(repository, applied)
                        dispatchFullState()
                    }
                }
                launch(Dispatchers.Main) { onResult(count > 0) }
            }
        }

        fun importKernels(
            uris: List<Uri>,
            notificationTitle: String,
            successStr: String,
            onResult: (Boolean) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val total = uris.size
                val showProgress = total > 50
                val destDir = getFilesDir("Kernel")
                var count = 0
                for ((index, uri) in uris.withIndex()) {
                    try {
                        if (copyUriToFile(uri, destDir, "kernel_$count.wav") != null) count++
                    } catch (e: Exception) {
                        FileLogger.e("ViewModel", "Failed to import kernel from $uri", e)
                    }
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_KERNEL_IMPORT, notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_KERNEL_IMPORT, notificationTitle, "$successStr: $count / $total")
                }
                if (count > 0) refreshFileLists()
                launch(Dispatchers.Main) { onResult(count > 0) }
            }
        }

        fun importVdcs(
            uris: List<Uri>,
            notificationTitle: String,
            successStr: String,
            onResult: (Boolean) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val total = uris.size
                val showProgress = total > 50
                val destDir = getFilesDir("DDC")
                var count = 0
                for ((index, uri) in uris.withIndex()) {
                    try {
                        if (copyUriToFile(uri, destDir, "imported_$count.vdc") != null) count++
                    } catch (e: Exception) {
                        FileLogger.e("ViewModel", "Failed to import VDC from $uri", e)
                    }
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_VDC_IMPORT, notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_VDC_IMPORT, notificationTitle, "$successStr: $count / $total")
                }
                if (count > 0) refreshFileLists()
                launch(Dispatchers.Main) { onResult(count > 0) }
            }
        }

        fun refreshFileLists() {
            val ddcDir = getFilesDir("DDC")
            _vdcFileList.value = ddcDir
                .listFiles()
                ?.filter { it.extension == "vdc" }
                ?.map { it.nameWithoutExtension }
                ?.sorted() ?: emptyList()

            val kernelDir = getFilesDir("Kernel")
            _kernelFileList.value = kernelDir
                .listFiles()
                ?.map { it.name }
                ?.sorted() ?: emptyList()
        }

        fun deleteVdcFile(name: String): Boolean {
            return try {
                val file = File(getFilesDir("DDC"), "$name.vdc")
                if (!file.exists()) return false
                file.delete()
                if (_uiState.value.ddc.device == name) {
                    applyPref(Effects.ddc.device, "")
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
                if (_uiState.value.convolver.kernelFile == fileName) {
                    applyPref(Effects.convolver.kernelFile, "")
                }
                refreshFileLists()
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to delete kernel: $fileName", e)
                false
            }
        }

        fun savePreset(name: String) {
            val createdAt = System.currentTimeMillis()
            val roomJson = serializeEffectPrefs(_uiState.value).toString()
            val fileJson =
                serializeEffectPrefs(
                    _uiState.value,
                    name = name,
                    createdAt = createdAt,
                ).toString()
            viewModelScope.launch {
                try {
                    repository.savePreset(
                        Preset(name = name, settingsJson = roomJson, createdAt = createdAt),
                    )
                    val file = File(getFilesDir("Preset"), "$name.json")
                    FileOutputStream(file).use { fos ->
                        fos.write(fileJson.toByteArray(Charsets.UTF_8))
                        fos.fd.sync()
                    }
                    FileLogger.i("ViewModel", "savePreset: $name -> ${file.absolutePath}")
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "savePreset: failed for name=$name", e)
                }
            }
        }

        fun loadPreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                val json = JSONObject(preset.settingsJson)
                _uiState.update { deserializeEffectPrefs(json, it) }
                saveEffectPrefs(repository, _uiState.value)
                dispatchFullState()
            }
        }

        fun deletePreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                repository.deletePresetById(id)
                try {
                    File(getFilesDir("Preset"), "${preset.name}.json").delete()
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "deletePreset: file remove failed", e)
                }
            }
        }

        fun clearAllPresets(
            notificationTitle: String,
            successStr: String,
            onResult: (Int) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val files =
                    getFilesDir("Preset").listFiles { f -> f.isFile && f.extension == "json" }
                        ?: emptyArray()
                val total = files.size
                val showProgress = total > 10
                var deleted = 0
                files.forEachIndexed { index, file ->
                    if (file.delete()) deleted++
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_PRESET_CLEAR, notificationTitle, index + 1, total)
                    }
                }
                repository.deleteAllPresets()
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_PRESET_CLEAR, notificationTitle, "$successStr: $deleted / $total")
                }
                FileLogger.i("ViewModel", "clearAllPresets: files deleted=$deleted/$total, db wiped")
                launch(Dispatchers.Main) { onResult(deleted) }
            }
        }

        fun updatePreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                val roomJson = serializeEffectPrefs(_uiState.value).toString()
                val updatedAt = System.currentTimeMillis()
                repository.updatePreset(
                    preset.copy(settingsJson = roomJson, updatedAt = updatedAt),
                )
                try {
                    val file = File(getFilesDir("Preset"), "${preset.name}.json")
                    val fileJson =
                        serializeEffectPrefs(_uiState.value, name = preset.name, createdAt = preset.createdAt).toString()
                    FileOutputStream(file).use { fos ->
                        fos.write(fileJson.toByteArray(Charsets.UTF_8))
                        fos.fd.sync()
                    }
                    FileLogger.i("ViewModel", "updatePreset: ${preset.name} -> ${file.absolutePath}")
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "updatePreset: file write failed for ${preset.name}", e)
                }
            }
        }

        fun renamePreset(
            id: Long,
            newName: String,
        ) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                repository.updatePreset(preset.copy(name = newName))
                try {
                    val dir = getFilesDir("Preset")
                    val oldFile = File(dir, "${preset.name}.json")
                    val newFile = File(dir, "$newName.json")
                    if (oldFile.exists()) {
                        oldFile.renameTo(newFile)
                    } else {
                        FileOutputStream(newFile).use { fos ->
                            fos.write(preset.settingsJson.toByteArray(Charsets.UTF_8))
                            fos.fd.sync()
                        }
                    }
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "renamePreset: file rename failed", e)
                }
            }
        }

        fun queryDriverStatus() {
            if (_aidlModeEnabled.value) {
                queryDriverStatusFromFile()
                return
            }
            val active = viperService?.getActiveEffect()
            if (active != null && active.isCreated) {
                queryDriverStatusFrom(active)
                return
            }
            val probe = ViperEffect(0, ViperEffect.EFFECT_TYPE_UUID)
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
            _driverStatus.value =
                DriverStatus(
                    installed = true,
                    versionCode = status.versionCode,
                    versionName = status.versionName,
                    architecture = status.architecture,
                    streaming = status.streaming,
                    samplingRate = status.sampleRate,
                )
        }

        private fun queryDriverStatusFrom(effect: ViperEffect) {
            val versionCode = effect.getDriverVersionCode()
            val archName = effect.getArchitectureString()
            val streaming = effect.isStreaming()
            val samplingRate = effect.getParameter(ViperParams.PARAM_GET_SAMPLING_RATE)
            val versionBytes = effect.getParameter(ViperParams.PARAM_GET_DRIVER_VERSION_NAME, 256)
            val versionName =
                if (versionBytes.isNotEmpty()) {
                    val nullIdx = versionBytes.indexOf(0.toByte())
                    if (nullIdx >= 0) String(versionBytes, 0, nullIdx) else String(versionBytes)
                } else {
                    versionCode.toString()
                }
            _driverStatus.value =
                DriverStatus(
                    installed = true,
                    versionCode = versionCode,
                    versionName = versionName,
                    architecture = archName,
                    streaming = streaming,
                    samplingRate = samplingRate,
                )
        }

        fun enableDebugMode() {
            _debugModeEnabled.value = true
            viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, true) }
        }

        fun disableDebugMode() {
            _debugModeEnabled.value = false
            viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, false) }
        }

        fun toggleAutoStart(enabled: Boolean) {
            _autoStartEnabled.value = enabled
            viewModelScope.launch { repository.setBooleanPreference(PREF_AUTO_START, enabled) }
        }

        fun toggleGlobalMode(enabled: Boolean) {
            _globalModeEnabled.value = enabled
            viewModelScope.launch { repository.setBooleanPreference(PREF_GLOBAL_MODE, enabled) }
            viperService?.setGlobalMode(enabled)
        }
    }

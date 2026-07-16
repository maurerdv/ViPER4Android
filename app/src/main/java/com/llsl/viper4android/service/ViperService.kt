package com.llsl.viper4android.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.util.size
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.llsl.viper4android.R
import com.llsl.viper4android.SERVICE_CHANNEL_ID
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.AudioSessionMonitor
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.deserializeEffectPrefs
import com.llsl.viper4android.effect.serializeEffectPrefs
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import com.llsl.viper4android.viper.ConfigChannel
import com.llsl.viper4android.viper.ViperDispatcher
import com.llsl.viper4android.viper.ViperEffect
import com.llsl.viper4android.viper.ViperParams
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@AndroidEntryPoint
class ViperService : LifecycleService() {
    @Inject
    lateinit var repository: ViperRepository

    companion object {
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.llsl.viper4android.service.START"
        const val ACTION_STOP = "com.llsl.viper4android.service.STOP"

        fun startService(context: Context) {
            val intent =
                Intent(context, ViperService::class.java).apply {
                    action = ACTION_START
                }
            context.startForegroundService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        val service: ViperService get() = this@ViperService
    }

    private val binder = LocalBinder()
    private val sessions = SparseArray<ViperEffect>()
    private var globalEffect: ViperEffect? = null
    private var useAidlTypeUuid: Boolean = true
    private var globalMode: Boolean = false
    private var masterEnabled: Boolean = false
    private var audioOutputDetector: AudioOutputDetector? = null
    private var sessionMonitor: AudioSessionMonitor? = null
    private var stateProvider: (() -> EffectState)? = null
    private var lastUiState: EffectState? = null
    private var lastBulkDdcKey: String? = null
    private var lastBulkConvolverKey: String? = null

    fun setStateProvider(provider: () -> EffectState) {
        stateProvider = provider
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        FileLogger.i("Service", "Service created")
        lifecycleScope.launch {
            useAidlTypeUuid = repository.aidlMode
            globalMode = repository.getBooleanPreference(ViperRepository.PREF_GLOBAL_MODE).first()
            masterEnabled = repository.getBooleanPreference(ViperRepository.PREF_MASTER_ENABLE).first()
            if (masterEnabled) {
                if (globalMode) {
                    initGlobalEffect()
                } else {
                    startSessionMonitor()
                }
            }
            startAudioOutputMonitor()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun initGlobalEffect() {
        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(0, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create global effect")
            return
        }
        globalEffect = effect
        FileLogger.i("Service", "Global effect created (aidlType=$useAidlTypeUuid)")
        lifecycleScope.launch {
            dispatchFullStateToEffect(effect)
            FileLogger.i("Service", "Global effect initialized with full state")
        }
    }

    private var currentServiceDeviceId: String = AudioDevice.ID_SPEAKER

    private fun startAudioOutputMonitor() {
        val detector = AudioOutputDetector(this)
        audioOutputDetector = detector
        currentServiceDeviceId = detector.activeDevice.value.id
        lifecycleScope.launch {
            detector.activeDevice.collect { device ->
                if (device.id != currentServiceDeviceId) {
                    FileLogger.i(
                        "Service",
                        "Device changed: $currentServiceDeviceId -> ${device.id} (${device.name})",
                    )
                    currentServiceDeviceId = device.id
                    reapplyForDevice(device)
                }
            }
        }
    }

    private suspend fun reapplyForDevice(device: AudioDevice) {
        val saved = repository.getDeviceSettings(device.id)
        val state: EffectState =
            if (saved != null) {
                FileLogger.i("Service", "Loading device settings from DB for ${device.id}")
                val baseState = ViperDispatcher.loadFullStateFromPrefs(repository)
                val json = JSONObject(saved.settingsJson)
                deserializeEffectPrefs(json, baseState).also {
                    repository.updateDeviceLastConnected(device.id)
                }
            } else {
                FileLogger.i("Service", "No DB entry for ${device.id}, using DataStore defaults")
                val s = ViperDispatcher.loadFullStateFromPrefs(repository)
                val json = serializeEffectPrefs(s)
                repository.saveDeviceSettings(
                    DeviceSettings(
                        deviceId = device.id,
                        deviceName = device.name,
                        isHeadphone = device.isHeadphone,
                        settingsJson = json.toString(),
                    ),
                )
                s
            }

        val isMasterOn = state.masterEnable
        var shmWritten = false
        globalEffect?.let {
            it.enabled = isMasterOn
            if (useAidlTypeUuid) {
                writeAidlFullState(state)
                shmWritten = true
            } else {
                ViperDispatcher.dispatchFullState(it, state, isMasterOn)
            }
        }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = isMasterOn
            if (useAidlTypeUuid) {
                if (!shmWritten) {
                    writeAidlFullState(state)
                    shmWritten = true
                }
            } else {
                ViperDispatcher.dispatchFullState(effect, state, isMasterOn)
            }
        }
    }

    private suspend fun dispatchFullStateToEffect(
        effect: ViperEffect,
        skipShmWrite: Boolean = false,
    ) {
        val state = ViperDispatcher.loadFullStateFromPrefs(repository)
        val isMasterOn = state.masterEnable
        effect.enabled = isMasterOn
        if (useAidlTypeUuid) {
            if (!skipShmWrite) {
                FileLogger.d(
                    "Service",
                    "AIDL shm apply full state (master=$isMasterOn)",
                )
                writeAidlFullState(state)
            }
            return
        }
        ViperDispatcher.dispatchFullState(effect, state, isMasterOn)
    }

    private fun writeAidlFullState(state: EffectState) {
        lastUiState = state
        ConfigChannel.writeFullState(state)
        if (state.ddc.enable && state.ddc.device.isNotEmpty()) {
            pushBulkDdcFromVdcFile(state.ddc.device)
        }
        if (state.convolver.enable && state.convolver.kernelFile.isNotEmpty()) {
            pushBulkConvolverPath(state.convolver.kernelFile)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                FileLogger.i("Service", "Service started")
            }

            ACTION_STOP -> {
                releaseAllSessions()
                globalEffect?.let {
                    it.enabled = false
                    it.release()
                }
                globalEffect = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSessionMonitor()
        audioOutputDetector?.stop()
        audioOutputDetector = null
        globalEffect?.let {
            it.enabled = false
            it.release()
        }
        globalEffect = null
        releaseAllSessions()
        FileLogger.i("Service", "Service destroyed")
        super.onDestroy()
    }

    private fun startSessionMonitor() {
        stopSessionMonitor()
        val monitor =
            AudioSessionMonitor(
                context = this,
                onSessionOpen = { sessionId, pkg -> openSession(sessionId, pkg) },
                onSessionClose = { sessionId -> closeSession(sessionId) },
            )
        monitor.start()
        sessionMonitor = monitor
    }

    private fun stopSessionMonitor() {
        sessionMonitor?.stop()
        sessionMonitor = null
    }

    private fun openSession(
        sessionId: Int,
        packageName: String,
    ) {
        if (!masterEnabled) {
            FileLogger.d(
                "Service",
                "Master off: skipping per-app session $sessionId ($packageName)",
            )
            return
        }
        if (globalMode) {
            FileLogger.d(
                "Service",
                "Global mode: skipping per-app session $sessionId ($packageName)",
            )
            return
        }
        if (sessions.get(sessionId) != null) {
            FileLogger.w("Service", "Session $sessionId already open")
            return
        }

        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(sessionId, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create effect for session $sessionId ($packageName)")
            return
        }

        sessions.put(sessionId, effect)
        FileLogger.i("Service", "Opened session $sessionId for $packageName")

        lifecycleScope.launch {
            dispatchFullStateToEffect(effect)
            FileLogger.i("Service", "Applied full state to session $sessionId")
        }
    }

    private fun closeSession(sessionId: Int) {
        val effect = sessions.get(sessionId) ?: return
        effect.enabled = false
        effect.release()
        sessions.remove(sessionId)
        FileLogger.i("Service", "Closed session $sessionId")
    }

    private fun releaseAllSessions() {
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = false
            effect.release()
        }
        sessions.clear()
    }

    fun dispatchParam(
        param: Int,
        value: Int,
        republishAidl: Boolean = true,
    ) {
        if (useAidlTypeUuid) {
            if (republishAidl) republishLastStateOnAidl()
            return
        }
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    fun dispatchParam(
        param: Int,
        val1: Int,
        val2: Int,
        val3: Int,
        republishAidl: Boolean = true,
    ) {
        if (useAidlTypeUuid) {
            if (republishAidl) republishLastStateOnAidl()
            return
        }
        globalEffect?.setParameter(param, val1, val2, val3)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, val1, val2, val3)
        }
    }

    fun dispatchParam(
        param: Int,
        value: ByteArray,
        republishAidl: Boolean = true,
    ) {
        if (useAidlTypeUuid) {
            if (republishAidl) republishLastStateOnAidl()
            return
        }
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    private fun republishLastStateOnAidl() {
        val state = stateProvider?.invoke() ?: lastUiState ?: return
        ConfigChannel.writeFullState(state)
        if (state.ddc.enable && state.ddc.device.isNotEmpty()) {
            pushBulkDdcFromVdcFile(state.ddc.device)
        }
        if (state.convolver.enable && state.convolver.kernelFile.isNotEmpty()) {
            pushBulkConvolverPath(state.convolver.kernelFile)
        }
    }

    fun dispatchConvolverKernelPath(stagedPath: String) {
        FileLogger.i(
            "Service",
            "Convolver kernel path dispatch: '$stagedPath'",
        )
        if (useAidlTypeUuid) {
            ConfigChannel.writeBulkConvolverPath(stagedPath)
            lastBulkConvolverKey = null
            return
        }
        val pathBytes = stagedPath.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(pathBytes.size)
        if (pathBytes.isNotEmpty()) buffer.put(pathBytes)
        globalEffect?.setParameter(ViperParams.PARAM_CONVOLVER_SET_KERNEL, buffer.array())
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(
                ViperParams.PARAM_CONVOLVER_SET_KERNEL,
                buffer.array(),
            )
        }
    }

    fun dispatchDdcCoefficients(
        sec44100: List<FloatArray>,
        sec48000: List<FloatArray>,
    ) {
        if (useAidlTypeUuid) {
            val n = sec44100.size
            require(n == sec48000.size) {
                "DDC: section count mismatch ${sec44100.size} vs ${sec48000.size}"
            }
            val perRateSize = sec44100.sumOf { it.size }
            val flat = FloatArray(perRateSize * 2)
            var off = 0
            for (sec in sec44100) {
                System.arraycopy(sec, 0, flat, off, sec.size)
                off += sec.size
            }
            for (sec in sec48000) {
                System.arraycopy(sec, 0, flat, off, sec.size)
                off += sec.size
            }
            ConfigChannel.writeBulkDdc(perRateSize, flat)
            lastBulkDdcKey = null
            return
        }
        val effect = globalEffect ?: return
        ViperDispatcher.dispatchDdcCoefficients(effect, sec44100, sec48000)
    }

    fun dispatchFullState(
        state: EffectState,
        masterEnabled: Boolean,
    ) {
        lastUiState = state
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL shm full state dispatch (master=$masterEnabled)",
            )
            ConfigChannel.writeFullState(state)
            if (state.ddc.enable && state.ddc.device.isNotEmpty()) {
                pushBulkDdcFromVdcFile(state.ddc.device)
            }
            if (state.convolver.enable && state.convolver.kernelFile.isNotEmpty()) {
                pushBulkConvolverPath(state.convolver.kernelFile)
            }
            return
        }
        globalEffect?.let { effect ->
            effect.enabled = masterEnabled
            ViperDispatcher.dispatchFullState(effect, state, masterEnabled)
        }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = masterEnabled
            ViperDispatcher.dispatchFullState(effect, state, masterEnabled)
        }
    }

    private fun pushBulkConvolverPath(fileName: String) {
        if (fileName == lastBulkConvolverKey) return
        val kernelDir = File(getExternalFilesDir(null), "Kernel")
        val src = File(kernelDir, fileName)
        if (!src.exists()) {
            FileLogger.w("Service", "Kernel src missing: $fileName")
            return
        }
        val safeName = fileName.replace("'", "")
        val stagedPath = "/data/local/tmp/v4a/kernel/$safeName"
        val staged = File(stagedPath)
        val needCopy = !(staged.exists() && staged.length() == src.length())
        if (needCopy) {
            FileLogger.d("Service", "Staging kernel '$fileName' to $stagedPath")
            RootShell.copyFile(src, stagedPath)
        } else {
            FileLogger.d("Service", "Kernel already staged at $stagedPath")
        }
        ConfigChannel.writeBulkConvolverPath(stagedPath)
        lastBulkConvolverKey = fileName
    }

    private fun pushBulkDdcFromVdcFile(name: String) {
        if (name == lastBulkDdcKey) return
        val ddcDir = File(getExternalFilesDir(null), "DDC")
        val file = File(ddcDir, "$name.vdc")
        if (!file.exists()) {
            FileLogger.w("Service", "DDC file missing: $name")
            return
        }
        var coeffs44100: FloatArray? = null
        var coeffs48000: FloatArray? = null
        try {
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
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to parse DDC: $name", e)
            return
        }
        val a = coeffs44100
        val b = coeffs48000
        if (a == null || b == null || a.isEmpty() || a.size != b.size || a.size % 5 != 0) {
            FileLogger.w(
                "Service",
                "DDC coefficient parse failure: $name " +
                    "44100=${a?.size} 48000=${b?.size}",
            )
            return
        }
        val flat = FloatArray(a.size * 2)
        System.arraycopy(a, 0, flat, 0, a.size)
        System.arraycopy(b, 0, flat, a.size, b.size)
        ConfigChannel.writeBulkDdc(a.size, flat)
        lastBulkDdcKey = name
    }

    fun getActiveEffect(): ViperEffect? {
        globalEffect?.let { if (it.isCreated) return it }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            if (effect.isCreated) return effect
        }
        return null
    }

    fun setMasterEnabled(enabled: Boolean) {
        if (masterEnabled == enabled) return
        masterEnabled = enabled
        if (enabled) {
            if (globalMode) {
                if (globalEffect == null) initGlobalEffect()
            } else {
                startSessionMonitor()
            }
        } else {
            stopSessionMonitor()
            releaseAllSessions()
            globalEffect?.let {
                it.enabled = false
                it.release()
            }
            globalEffect = null
        }
    }

    fun setGlobalMode(enabled: Boolean) {
        globalMode = enabled
        if (!masterEnabled) {
            stopSessionMonitor()
            releaseAllSessions()
            globalEffect?.let {
                it.enabled = false
                it.release()
            }
            globalEffect = null
            return
        }
        if (enabled) {
            stopSessionMonitor()
            releaseAllSessions()
            if (globalEffect == null) {
                initGlobalEffect()
            }
        } else {
            globalEffect?.let {
                it.enabled = false
                it.release()
            }
            globalEffect = null
            startSessionMonitor()
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat
            .Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}

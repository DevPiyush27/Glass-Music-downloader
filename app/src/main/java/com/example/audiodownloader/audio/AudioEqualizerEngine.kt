package com.example.audiodownloader.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class EqualizerBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val currentLevelMb: Short
) {
    val centerFreqFormatted: String
        get() = if (centerFreqHz >= 1000) {
            val khz = centerFreqHz / 1000f
            if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
        } else {
            "$centerFreqHz Hz"
        }

    val currentLevelDb: Float
        get() = currentLevelMb / 100f

    val minLevelDb: Float
        get() = minLevelMb / 100f

    val maxLevelDb: Float
        get() = maxLevelMb / 100f
}

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val currentPreset: Short = -1, // -1: Custom
    val presets: List<String> = emptyList(),
    val bassBoostStrength: Short = 0, // 0 to 1000
    val bassBoostEnabled: Boolean = false,
    val isBassBoostSupported: Boolean = false
)

/**
 * Robust hardware audio effect engine managing [Equalizer] and [BassBoost] for ExoPlayer.
 *
 * Guarantees:
 * - 100% lifecycle & error safety (surrounds all hardware calls with try/catch to prevent ROM-level crashes).
 * - Automatic persistence of user settings (enabled state, band levels, presets, bass boost) in SharedPreferences.
 * - Dynamic band frequency and native preset resolution from the device's audio hardware.
 */
class AudioEqualizerEngine(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var activeSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    /**
     * Safely binds to an ExoPlayer audioSessionId.
     * Re-uses existing instance if session is identical.
     */
    @Synchronized
    fun bindAudioSession(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) {
            Log.d(TAG, "Skipping invalid audioSessionId: $sessionId")
            return
        }

        if (sessionId == activeSessionId && equalizer != null) {
            Log.d(TAG, "Audio session $sessionId already active.")
            return
        }

        Log.i(TAG, "Binding audio effects to audioSessionId: $sessionId")
        releaseHardwareEffects()

        try {
            val eq = Equalizer(0, sessionId)
            val numBands = eq.numberOfBands
            if (numBands <= 0) {
                Log.w(TAG, "Device reports 0 equalizer bands. Releasing.")
                eq.release()
                _state.value = _state.value.copy(isAvailable = false)
                return
            }

            equalizer = eq
            activeSessionId = sessionId

            // Optional BassBoost
            val bb = try {
                val candidateBb = BassBoost(0, sessionId)
                if (!candidateBb.strengthSupported) {
                    candidateBb.release()
                    null
                } else {
                    bassBoost = candidateBb
                    candidateBb
                }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost not supported on session $sessionId: ${e.message}")
                null
            }

            // Restore saved preferences onto the new hardware instance
            applySavedPreferences(eq, bb)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Equalizer on session $sessionId", e)
            releaseHardwareEffects()
            _state.value = _state.value.copy(isAvailable = false)
        }
    }

    private fun applySavedPreferences(eq: Equalizer, bb: BassBoost?) {
        try {
            val isEnabled = prefs.getBoolean(KEY_ENABLED, false)
            eq.enabled = isEnabled

            val numBands = eq.numberOfBands.toInt()
            val bandRange = eq.bandLevelRange // [minMb, maxMb]
            val minLevel = bandRange.getOrNull(0) ?: -1500
            val maxLevel = bandRange.getOrNull(1) ?: 1500

            val savedPreset = prefs.getInt(KEY_PRESET, -1).toShort()
            val numPresets = eq.numberOfPresets.toInt()
            val presetNames = mutableListOf<String>()
            for (i in 0 until numPresets) {
                try {
                    presetNames.add(eq.getPresetName(i.toShort()))
                } catch (_: Exception) {
                    presetNames.add("Preset ${i + 1}")
                }
            }

            // Apply preset if valid, otherwise custom band levels
            if (savedPreset in 0 until numPresets) {
                try {
                    eq.usePreset(savedPreset)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply saved preset $savedPreset: ${e.message}")
                }
            } else {
                for (b in 0 until numBands) {
                    val key = "${KEY_BAND_PREFIX}_$b"
                    if (prefs.contains(key)) {
                        val savedMb = prefs.getInt(key, 0).toShort()
                        val clampedMb = savedMb.coerceIn(minLevel, maxLevel)
                        try {
                            eq.setBandLevel(b.toShort(), clampedMb)
                        } catch (_: Exception) {}
                    }
                }
            }

            // Read current band levels after preset or saved level application
            val bandsList = mutableListOf<EqualizerBand>()
            for (b in 0 until numBands) {
                val bandIndex = b.toShort()
                val centerFreq = try { eq.getCenterFreq(bandIndex) / 1000 } catch (_: Exception) { 0 }
                val currentLevel = try { eq.getBandLevel(bandIndex) } catch (_: Exception) { 0.toShort() }
                bandsList.add(
                    EqualizerBand(
                        bandIndex = bandIndex,
                        centerFreqHz = centerFreq,
                        minLevelMb = minLevel,
                        maxLevelMb = maxLevel,
                        currentLevelMb = currentLevel
                    )
                )
            }

            // Bass boost configuration
            var bbEnabled = false
            var bbStrength: Short = 0
            if (bb != null) {
                bbEnabled = prefs.getBoolean(KEY_BASS_ENABLED, false)
                bbStrength = prefs.getInt(KEY_BASS_STRENGTH, 0).toShort().coerceIn(0, 1000)
                try {
                    bb.enabled = bbEnabled
                    bb.setStrength(bbStrength)
                } catch (e: Exception) {
                    Log.w(TAG, "Error configuring BassBoost: ${e.message}")
                }
            }

            _state.value = EqualizerState(
                isAvailable = true,
                isEnabled = isEnabled,
                bands = bandsList,
                currentPreset = if (savedPreset in 0 until numPresets) savedPreset else -1,
                presets = presetNames,
                bassBoostStrength = bbStrength,
                bassBoostEnabled = bbEnabled,
                isBassBoostSupported = bb != null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring Equalizer preferences", e)
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
            _state.value = _state.value.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle Equalizer enabled: $enabled", e)
        }
    }

    @Synchronized
    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer ?: return
        try {
            val clamped = levelMb.coerceIn(
                _state.value.bands.firstOrNull()?.minLevelMb ?: -1500,
                _state.value.bands.firstOrNull()?.maxLevelMb ?: 1500
            )
            eq.setBandLevel(bandIndex, clamped)
            prefs.edit()
                .putInt("${KEY_BAND_PREFIX}_${bandIndex.toInt()}", clamped.toInt())
                .putInt(KEY_PRESET, -1) // custom
                .apply()

            val updatedBands = _state.value.bands.map { band ->
                if (band.bandIndex == bandIndex) band.copy(currentLevelMb = clamped) else band
            }
            _state.value = _state.value.copy(
                bands = updatedBands,
                currentPreset = -1
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting band $bandIndex level to $levelMb", e)
        }
    }

    @Synchronized
    fun applyPreset(presetIndex: Short) {
        val eq = equalizer ?: return
        try {
            if (presetIndex >= 0 && presetIndex < _state.value.presets.size) {
                eq.usePreset(presetIndex)
                prefs.edit().putInt(KEY_PRESET, presetIndex.toInt()).apply()

                val updatedBands = _state.value.bands.map { band ->
                    val newLevel = try { eq.getBandLevel(band.bandIndex) } catch (_: Exception) { band.currentLevelMb }
                    // Also update persisted band levels
                    prefs.edit().putInt("${KEY_BAND_PREFIX}_${band.bandIndex.toInt()}", newLevel.toInt()).apply()
                    band.copy(currentLevelMb = newLevel)
                }

                _state.value = _state.value.copy(
                    bands = updatedBands,
                    currentPreset = presetIndex
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying preset $presetIndex", e)
        }
    }

    @Synchronized
    fun resetToFlat() {
        val eq = equalizer ?: return
        try {
            val updatedBands = _state.value.bands.map { band ->
                val zero: Short = 0
                try { eq.setBandLevel(band.bandIndex, zero) } catch (_: Exception) {}
                prefs.edit().putInt("${KEY_BAND_PREFIX}_${band.bandIndex.toInt()}", 0).apply()
                band.copy(currentLevelMb = zero)
            }
            prefs.edit().putInt(KEY_PRESET, -1).apply()
            _state.value = _state.value.copy(
                bands = updatedBands,
                currentPreset = -1
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting Equalizer to flat", e)
        }
    }

    @Synchronized
    fun setBassBoostStrength(strength: Short) {
        val bb = bassBoost ?: return
        try {
            val clamped = strength.coerceIn(0, 1000)
            bb.setStrength(clamped)
            prefs.edit().putInt(KEY_BASS_STRENGTH, clamped.toInt()).apply()
            _state.value = _state.value.copy(bassBoostStrength = clamped)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting BassBoost strength to $strength", e)
        }
    }

    @Synchronized
    fun setBassBoostEnabled(enabled: Boolean) {
        val bb = bassBoost ?: return
        try {
            bb.enabled = enabled
            prefs.edit().putBoolean(KEY_BASS_ENABLED, enabled).apply()
            _state.value = _state.value.copy(bassBoostEnabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling BassBoost enabled to $enabled", e)
        }
    }

    @Synchronized
    fun releaseHardwareEffects() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null

        activeSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    fun release() {
        releaseHardwareEffects()
    }

    companion object {
        private const val TAG = "AudioEqualizerEngine"
        private const val PREFS_NAME = "audio_aurora_equalizer_prefs"
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_PRESET = "eq_preset"
        private const val KEY_BAND_PREFIX = "eq_band"
        private const val KEY_BASS_ENABLED = "eq_bass_enabled"
        private const val KEY_BASS_STRENGTH = "eq_bass_strength"
    }
}

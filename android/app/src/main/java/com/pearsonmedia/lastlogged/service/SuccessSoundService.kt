package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays an opt-in success chime when the user logs a completion.
 *
 * Behavior:
 * - Gated by [SecureStorageService.getSuccessSoundEnabled] (off by default).
 * - Respects device silent / vibrate ringer modes — no sound if the user has
 *   silenced their phone.
 * - Prefers a bundled `res/raw/success_chime` asset via SoundPool. When the
 *   asset is not bundled (the resource id resolves to 0), falls back to a
 *   tasteful short system [ToneGenerator] ack tone so the feature still works.
 * - All operations wrapped in try/catch so audio failures never block a log.
 */
@Singleton
class SuccessSoundService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorageService: SecureStorageService
) {
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val soundPool: SoundPool by lazy {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()
    }

    private var chimeSoundId: Int = 0
    private var loaded: Boolean = false

    init {
        loadChimeIfAvailable()
    }

    private fun loadChimeIfAvailable() {
        try {
            val resId = context.resources.getIdentifier(
                "success_chime",
                "raw",
                context.packageName
            )
            if (resId != 0) {
                chimeSoundId = soundPool.load(context, resId, 1)
                soundPool.setOnLoadCompleteListener { _, _, status ->
                    loaded = (status == 0)
                }
            }
        } catch (_: Throwable) {
            // Ignore — feature silently disabled if asset can't be loaded.
        }
    }

    /**
     * Plays the success chime if the user has enabled it and the device is
     * not in silent mode. Safe to call from any thread.
     */
    fun playIfEnabled() {
        if (!secureStorageService.getSuccessSoundEnabled()) return
        if (!isAudible()) return

        try {
            if (loaded && chimeSoundId != 0) {
                soundPool.play(chimeSoundId, 0.8f, 0.8f, 1, 0, 1f)
            } else {
                // Fallback: short system ack tone (no asset required).
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                // Release after the tone finishes to avoid leaks.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { runCatching { tone.release() } },
                    250
                )
            }
        } catch (_: Throwable) {
            // Never let audio failures bubble up into the log flow.
        }
    }

    private fun isAudible(): Boolean {
        val am = audioManager ?: return false
        return am.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
}

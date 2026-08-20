package com.HrshD1eux.Gallery.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticUtil {

    enum class HapticType {
        LIGHT_TICK,
        SELECTION,
        CLICK,
        HEAVY_CLICK,
        SUCCESS,
        ERROR
    }

    fun performTick(context: Context) {
        vibrate(context, HapticType.LIGHT_TICK)
    }

    fun performSelection(context: Context) {
        vibrate(context, HapticType.SELECTION)
    }

    fun performClick(context: Context) {
        vibrate(context, HapticType.CLICK)
    }

    fun performLongPress(context: Context) {
        vibrate(context, HapticType.HEAVY_CLICK)
    }

    fun performSuccess(context: Context) {
        vibrate(context, HapticType.SUCCESS)
    }

    fun performError(context: Context) {
        vibrate(context, HapticType.ERROR)
    }

    fun vibrate(context: Context, type: HapticType) {
        try {
            val prefs = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("haptics_enabled", true)
            if (!isEnabled) return

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    HapticType.LIGHT_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticType.SELECTION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticType.HEAVY_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    HapticType.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 35, 50, 35), intArrayOf(0, 100, 0, 160), -1)
                    HapticType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 50, 40, 50), intArrayOf(0, 220, 0, 220), -1)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (type) {
                    HapticType.LIGHT_TICK -> 30
                    HapticType.SELECTION -> 60
                    HapticType.CLICK -> 80
                    HapticType.HEAVY_CLICK -> 180
                    HapticType.SUCCESS -> 140
                    HapticType.ERROR -> 220
                }
                val duration = when (type) {
                    HapticType.LIGHT_TICK -> 10L
                    HapticType.SELECTION -> 20L
                    HapticType.CLICK -> 25L
                    HapticType.HEAVY_CLICK -> 60L
                    HapticType.SUCCESS -> 70L
                    HapticType.ERROR -> 120L
                }
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                val duration = when (type) {
                    HapticType.LIGHT_TICK -> 10L
                    HapticType.SELECTION -> 20L
                    HapticType.CLICK -> 25L
                    HapticType.HEAVY_CLICK -> 60L
                    HapticType.SUCCESS -> 70L
                    HapticType.ERROR -> 120L
                }
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (_: Exception) {}
    }
}

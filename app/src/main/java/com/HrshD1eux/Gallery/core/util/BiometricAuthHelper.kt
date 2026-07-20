package com.HrshD1eux.Gallery.core.util

import android.app.Activity
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object BiometricAuthHelper {

    fun canAuthenticate(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val keyguardManager = activity.getSystemService(Activity.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        return keyguardManager?.isKeyguardSecure == true
    }

    fun authenticate(
        activity: Activity,
        title: String = "Unlock Hidden Vault",
        subtitle: String = "Use fingerprint or face unlock to access your hidden photos",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton(negativeButtonText, executor) { _, _ ->
                        onError("Use PIN")
                    }
                    .build()

                val cancellationSignal = CancellationSignal()
                biometricPrompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Biometric authentication failed. Try again.")
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            onError(errString?.toString() ?: "Biometric authentication cancelled.")
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Biometric error.")
            }
        } else {
            onError("Biometric hardware unavailable on this device version.")
        }
    }
}

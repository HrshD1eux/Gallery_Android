package com.hrshd1eux.imava.core.util

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    private fun findFragmentActivity(context: Context): FragmentActivity? {
        var currentContext: Context? = context
        while (currentContext is ContextWrapper) {
            if (currentContext is FragmentActivity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: Context,
        title: String = "Unlock Hidden Vault",
        subtitle: String = "Use fingerprint or face unlock to access your hidden photos",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val fragmentActivity = findFragmentActivity(activity)
        if (fragmentActivity == null) {
            onError("Biometric authentication requires FragmentActivity context.")
            return
        }

        if (!canAuthenticate(fragmentActivity)) {
            onError("Biometric hardware unavailable or not enrolled.")
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(fragmentActivity)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()

            val biometricPrompt = BiometricPrompt(
                fragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("Biometric authentication failed. Try again.")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString.toString())
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e.localizedMessage ?: "Biometric error.")
        }
    }
}

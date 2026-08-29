package com.hrshd1eux.imava.ui.vault

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.hrshd1eux.imava.core.util.VaultCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VaultStateHolder {

    private val _vaultConfigVersion = MutableStateFlow(0)
    val vaultConfigVersion: StateFlow<Int> = _vaultConfigVersion.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private var _isDecoyVault = mutableStateOf(false)
    val isDecoyVault: Boolean get() = _isDecoyVault.value

    private var backgroundTimestamp = 0L

    fun notifyVaultConfigChanged() {
        _vaultConfigVersion.value++
    }

    fun unlockVault() {
        _isDecoyVault.value = false
        _isVaultUnlocked.value = true
        backgroundTimestamp = 0L
        _vaultConfigVersion.value++
    }

    fun unlockDecoyVault() {
        _isDecoyVault.value = true
        _isVaultUnlocked.value = true
        backgroundTimestamp = 0L
        _vaultConfigVersion.value++
    }

    fun lockVault(context: Context) {
        _isVaultUnlocked.value = false
        _isDecoyVault.value = false
        backgroundTimestamp = 0L
        VaultCacheManager.clearCache(context)
        _vaultConfigVersion.value++
    }

    fun setUnlocked(unlocked: Boolean) {
        _isVaultUnlocked.value = unlocked
        _vaultConfigVersion.value++
    }

    fun onAppBackgrounded() {
        if (_isVaultUnlocked.value) {
            backgroundTimestamp = System.currentTimeMillis()
        }
    }

    fun onAppForegrounded(context: Context) {
        if (!_isVaultUnlocked.value || backgroundTimestamp == 0L) return

        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val timeoutMs = prefs.getLong("vault_autolock_timeout", 0L)

        if (timeoutMs >= 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            if (elapsed >= timeoutMs) {
                lockVault(context)
            }
        }
        backgroundTimestamp = 0L
    }
}

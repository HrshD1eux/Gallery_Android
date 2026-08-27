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

    fun notifyVaultConfigChanged() {
        _vaultConfigVersion.value++
    }

    fun unlockVault() {
        _isDecoyVault.value = false
        _isVaultUnlocked.value = true
        _vaultConfigVersion.value++
    }

    fun unlockDecoyVault() {
        _isDecoyVault.value = true
        _isVaultUnlocked.value = true
        _vaultConfigVersion.value++
    }

    fun lockVault(context: Context) {
        _isVaultUnlocked.value = false
        _isDecoyVault.value = false
        VaultCacheManager.clearCache(context)
        _vaultConfigVersion.value++
    }

    fun setUnlocked(unlocked: Boolean) {
        _isVaultUnlocked.value = unlocked
        _vaultConfigVersion.value++
    }
}

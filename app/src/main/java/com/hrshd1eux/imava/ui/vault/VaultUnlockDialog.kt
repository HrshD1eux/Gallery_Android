package com.hrshd1eux.imava.ui.vault

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.core.util.HapticUtil
import com.hrshd1eux.imava.core.util.VaultCrypto
import kotlinx.coroutines.delay

@Composable
fun VaultUnlockDialog(
    onDismiss: () -> Unit,
    onUnlockSuccess: () -> Unit,
    onUnlockDecoy: () -> Unit = onUnlockSuccess
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    
    val storedLockType = prefs.getString("vault_lock_type", "PIN") ?: "PIN"
    val storedPinHash = prefs.getString("vault_pin_hash", null)
    val storedDecoyHash = prefs.getString("vault_decoy_pin_hash", null)
    val storedSaltBase64 = prefs.getString("vault_salt", null)
    val legacyPin = prefs.getString("vault_pin", null)
    val isVaultDisabled = prefs.getBoolean("vault_disabled", false)
    val isPinConfigured = !isVaultDisabled && (storedPinHash != null || legacyPin != null)

    // Setup Wizard state
    var setupStage by remember { mutableIntStateOf(1) } // 1: Choose Type & Set Code, 2: Additional Security (Biometrics & Stealth)
    var setupLockType by remember { mutableStateOf("PIN") } // "PIN" or "PATTERN"
    var setupPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var patternSetupStep by remember { mutableIntStateOf(1) }
    var firstDrawnPattern by remember { mutableStateOf("") }
    var setupBiometrics by remember { mutableStateOf(false) }
    var setupStealthMode by remember { mutableStateOf(false) }
    var setupSecretTrigger by remember { mutableStateOf("#vault") }
    var chosenCodeToSave by remember { mutableStateOf("") }

    // Unlock state
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPatternError by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val initialLockoutUntil = prefs.getLong("vault_lockout_until_ms", 0L)
    val initialLockoutSecs = ((initialLockoutUntil - now) / 1000L).coerceAtLeast(0L).toInt()

    var failedAttempts by remember { mutableIntStateOf(prefs.getInt("vault_failed_attempts", 0)) }
    var lockoutRemainingSeconds by remember { mutableIntStateOf(initialLockoutSecs) }

    LaunchedEffect(lockoutRemainingSeconds) {
        if (lockoutRemainingSeconds > 0) {
            delay(1000L)
            lockoutRemainingSeconds -= 1
            if (lockoutRemainingSeconds == 0) {
                prefs.edit().remove("vault_lockout_until_ms").apply()
                errorMessage = null
            }
        }
    }

    fun finalizeSetup() {
        val saltBytes = VaultCrypto.generateSalt()
        val saltBase64 = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
        val pinHash = VaultCrypto.hashPin(chosenCodeToSave, saltBytes)
        val encryptedHash = VaultCrypto.encryptString(pinHash)

        prefs.edit()
            .putString("vault_pin_hash", encryptedHash)
            .putString("vault_salt", saltBase64)
            .putString("vault_lock_type", setupLockType)
            .putBoolean("vault_biometric_enabled", setupBiometrics)
            .putBoolean("vault_stealth_mode", setupStealthMode)
            .putString("vault_secret_trigger", setupSecretTrigger.trim().ifEmpty { "#vault" })
            .putBoolean("vault_disabled", false)
            .remove("vault_pin")
            .apply()

        HapticUtil.performSuccess(context)
        onUnlockSuccess()
    }

    fun verifyInput(input: String) {
        if (lockoutRemainingSeconds > 0) return

        var isValid = false
        var isDecoy = false
        var needsUpgrade = false

        if (storedPinHash != null && storedSaltBase64 != null) {
            val salt = android.util.Base64.decode(storedSaltBase64, android.util.Base64.NO_WRAP)
            val res = VaultCrypto.verifyPin(input, storedPinHash, salt)
            isValid = res.isValid
            needsUpgrade = res.needsUpgrade

            if (!isValid && storedDecoyHash != null) {
                val decoyRes = VaultCrypto.verifyPin(input, storedDecoyHash, salt)
                if (decoyRes.isValid) {
                    isDecoy = true
                }
            }
        } else if (legacyPin != null && input == legacyPin) {
            isValid = true
            needsUpgrade = true
        }

        if (isDecoy) {
            failedAttempts = 0
            lockoutRemainingSeconds = 0
            prefs.edit()
                .remove("vault_lockout_until_ms")
                .remove("vault_failed_attempts")
                .apply()
            HapticUtil.performSuccess(context)
            onUnlockDecoy()
            return
        }

        if (isValid) {
            failedAttempts = 0
            lockoutRemainingSeconds = 0
            prefs.edit()
                .remove("vault_lockout_until_ms")
                .remove("vault_failed_attempts")
                .apply()

            if (needsUpgrade) {
                // Seamlessly migrate legacy SHA-256 or plaintext PIN to PBKDF2WithHmacSHA256
                try {
                    val saltBytes = VaultCrypto.generateSalt()
                    val saltBase64 = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
                    val newHash = VaultCrypto.hashPin(input, saltBytes)
                    val encHash = VaultCrypto.encryptString(newHash)
                    prefs.edit()
                        .putString("vault_pin_hash", encHash)
                        .putString("vault_salt", saltBase64)
                        .remove("vault_pin")
                        .apply()
                } catch (_: Exception) {}
            }

            HapticUtil.performSuccess(context)
            onUnlockSuccess()
        } else {
            HapticUtil.performError(context)
            val newAttempts = failedAttempts + 1
            if (newAttempts >= 3) {
                val lockoutExpiry = System.currentTimeMillis() + 30_000L
                prefs.edit()
                    .putLong("vault_lockout_until_ms", lockoutExpiry)
                    .putInt("vault_failed_attempts", 0)
                    .apply()
                lockoutRemainingSeconds = 30
                failedAttempts = 0
                errorMessage = "Too many failed attempts. Try again in 30s."
            } else {
                failedAttempts = newAttempts
                prefs.edit().putInt("vault_failed_attempts", newAttempts).apply()
                errorMessage = "Incorrect code. Attempt $newAttempts of 3."
            }
            isPatternError = true
            pinInput = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (!isPinConfigured) {
                    if (setupStage == 1) "Set Up Vault Lock (1/2)" else "Vault Security Setup (2/2)"
                } else {
                    "Unlock Hidden Vault"
                },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (lockoutRemainingSeconds > 0) {
                    Text(
                        text = "Too many incorrect attempts.\nTry again in ${lockoutRemainingSeconds}s",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (!isPinConfigured) {
                    // --- SETUP WIZARD ---
                    if (setupStage == 1) {
                        // Stage 1: Choose Type & Code
                        Text(
                            text = "Choose how to protect your Hidden Vault:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Row(
                                modifier = Modifier.clickable { setupLockType = "PIN" },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = setupLockType == "PIN",
                                    onClick = { setupLockType = "PIN" }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PIN Lock")
                            }

                            Row(
                                modifier = Modifier.clickable { setupLockType = "PATTERN" },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = setupLockType == "PATTERN",
                                    onClick = { setupLockType = "PATTERN" }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pattern Lock")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        if (setupLockType == "PIN") {
                            Text(
                                text = "Create a 4-6 digit PIN:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = setupPinInput,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        setupPinInput = it
                                        errorMessage = null
                                    }
                                },
                                label = { Text("4-6 Digit PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirmPinInput,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        confirmPinInput = it
                                        errorMessage = null
                                    }
                                },
                                label = { Text("Confirm PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = if (patternSetupStep == 1) "Draw a pattern connecting at least 4 dots" else "Draw pattern again to confirm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PatternLockView(
                                onPatternComplete = { drawn ->
                                    if (patternSetupStep == 1) {
                                        if (drawn.split("-").size < 4) {
                                            errorMessage = "Connect at least 4 dots"
                                            isPatternError = true
                                        } else {
                                            firstDrawnPattern = drawn
                                            patternSetupStep = 2
                                            errorMessage = null
                                            isPatternError = false
                                        }
                                    } else {
                                        if (drawn == firstDrawnPattern) {
                                            chosenCodeToSave = drawn
                                            setupStage = 2
                                            errorMessage = null
                                            isPatternError = false
                                        } else {
                                            errorMessage = "Patterns do not match. Try again."
                                            patternSetupStep = 1
                                            isPatternError = true
                                        }
                                    }
                                },
                                isError = isPatternError,
                                modifier = Modifier.height(260.dp)
                            )
                        }
                    } else {
                        // Stage 2: Biometrics & Stealth Mode
                        Text(
                            text = "Configure optional security features:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Biometrics option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric Unlock", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Unlock vault using fingerprint or face",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = setupBiometrics,
                                onCheckedChange = { enabled ->
                                    val activity = context as? android.app.Activity
                                    if (enabled && activity != null) {
                                        com.hrshd1eux.imava.core.util.BiometricAuthHelper.authenticate(
                                            activity = activity,
                                            title = "Confirm Biometric",
                                            subtitle = "Verify fingerprint to enable",
                                            onSuccess = { setupBiometrics = true },
                                            onError = { setupBiometrics = false }
                                        )
                                    } else {
                                        setupBiometrics = enabled
                                    }
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        // Stealth Mode option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stealth Vault Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Hide Vault from Albums list. Access via search phrase.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = setupStealthMode,
                                onCheckedChange = { setupStealthMode = it }
                            )
                        }

                        if (setupStealthMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = setupSecretTrigger,
                                onValueChange = { setupSecretTrigger = it },
                                label = { Text("Secret Search Phrase") },
                                placeholder = { Text("#vault") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // --- NORMAL UNLOCK FLOW ---
                    if (storedLockType == "PATTERN") {
                        Text(
                            text = if (lockoutRemainingSeconds > 0) "Locked out temporarily" else "Draw your secret pattern to unlock",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (lockoutRemainingSeconds == 0) {
                            PatternLockView(
                                onPatternComplete = { patternStr ->
                                    isPatternError = false
                                    verifyInput(patternStr)
                                },
                                isError = isPatternError,
                                modifier = Modifier.height(260.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = pinInput,
                            enabled = lockoutRemainingSeconds == 0,
                            onValueChange = {
                                pinInput = it
                                errorMessage = null
                            },
                            label = { Text("Enter Vault PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isPinConfigured) {
                if (setupStage == 1) {
                    if (setupLockType == "PIN") {
                        Button(
                            onClick = {
                                if (setupPinInput.length < 4) {
                                    errorMessage = "PIN must be at least 4 digits."
                                    return@Button
                                }
                                if (setupPinInput != confirmPinInput) {
                                    errorMessage = "PINs do not match."
                                    return@Button
                                }
                                chosenCodeToSave = setupPinInput
                                setupStage = 2
                                errorMessage = null
                            }
                        ) {
                            Text("Next")
                        }
                    }
                } else {
                    Button(
                        onClick = { finalizeSetup() }
                    ) {
                        Text("Finish & Unlock")
                    }
                }
            } else {
                if (storedLockType == "PIN") {
                    Button(
                        onClick = {
                            if (pinInput.isNotBlank()) {
                                verifyInput(pinInput)
                            }
                        }
                    ) {
                        Text("Unlock")
                    }
                }
            }
        },
        dismissButton = {
            val isBiometricEnabled = prefs.getBoolean("vault_biometric_enabled", false)
            val activity = context as? android.app.Activity
            Row {
                if (isPinConfigured && isBiometricEnabled && activity != null) {
                    TextButton(
                        onClick = {
                            com.hrshd1eux.imava.core.util.BiometricAuthHelper.authenticate(
                                activity = activity,
                                title = "Unlock Hidden Vault",
                                subtitle = "Authenticate biometric sensor",
                                onSuccess = { onUnlockSuccess() },
                                onError = { _ -> }
                            )
                        }
                    ) {
                        Text("Use Biometric")
                    }
                }
                if (!isPinConfigured && setupStage == 2) {
                    TextButton(onClick = { setupStage = 1 }) {
                        Text("Back")
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    )
}

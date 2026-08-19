package com.HrshD1eux.Gallery.ui.vault

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.HrshD1eux.Gallery.core.util.VaultCrypto

@Composable
fun VaultUnlockDialog(
    onDismiss: () -> Unit,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    
    val lockType = remember(prefs) { prefs.getString("vault_lock_type", "PIN") ?: "PIN" }
    val storedPinHash = remember(prefs) { prefs.getString("vault_pin_hash", null) }
    val storedSaltBase64 = remember(prefs) { prefs.getString("vault_salt", null) }
    val legacyPin = remember(prefs) { prefs.getString("vault_pin", null) }
    val isPinConfigured = storedPinHash != null || legacyPin != null

    // Setup state
    var setupPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var patternSetupStep by remember { mutableIntStateOf(1) }
    var firstDrawnPattern by remember { mutableStateOf("") }

    // Unlock state
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPatternError by remember { mutableStateOf(false) }

    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutRemainingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(lockoutRemainingSeconds) {
        if (lockoutRemainingSeconds > 0) {
            delay(1000L)
            lockoutRemainingSeconds -= 1
        }
    }

    fun saveNewCredentials(rawCode: String, type: String) {
        val saltBytes = VaultCrypto.generateSalt()
        val saltBase64 = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
        val pinHash = VaultCrypto.hashPin(rawCode, saltBytes)
        val encryptedHash = VaultCrypto.encryptString(pinHash)

        prefs.edit()
            .putString("vault_pin_hash", encryptedHash)
            .putString("vault_salt", saltBase64)
            .putString("vault_lock_type", type)
            .remove("vault_pin")
            .apply()

        onUnlockSuccess()
    }

    fun verifyInput(input: String) {
        if (lockoutRemainingSeconds > 0) return

        var isValid = false
        if (storedPinHash != null && storedSaltBase64 != null) {
            val salt = android.util.Base64.decode(storedSaltBase64, android.util.Base64.NO_WRAP)
            val inputHash = VaultCrypto.hashPin(input, salt)
            val decryptedHash = try {
                VaultCrypto.decryptString(storedPinHash)
            } catch (_: Exception) {
                storedPinHash
            }
            isValid = (inputHash == decryptedHash || inputHash == storedPinHash)
        } else if (legacyPin != null && input == legacyPin) {
            isValid = true
        }

        if (isValid) {
            failedAttempts = 0
            onUnlockSuccess()
        } else {
            failedAttempts += 1
            if (failedAttempts >= 3) {
                lockoutRemainingSeconds = 30
                failedAttempts = 0
                errorMessage = "Too many failed attempts. Try again in 30s."
            } else {
                errorMessage = "Incorrect code. Attempt $failedAttempts of 3."
            }
            isPatternError = true
            pinInput = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (!isPinConfigured) "Set Up Vault Lock" else "Unlock Hidden Vault",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                    // First-time setup flow
                    if (lockType == "PATTERN") {
                        Text(
                            text = if (patternSetupStep == 1) "Draw a pattern connecting at least 4 dots" else "Draw the pattern again to confirm",
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
                                        saveNewCredentials(drawn, "PATTERN")
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
                    } else {
                        Text(
                            text = "Create a 4-6 digit numeric PIN to secure your Hidden Vault.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = setupPinInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                    setupPinInput = it
                                    errorMessage = null
                                }
                            },
                            label = { Text("New 4-6 Digit PIN") },
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
                    }
                } else {
                    // Normal unlock flow
                    if (lockType == "PATTERN") {
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
                if (lockType == "PIN") {
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
                            saveNewCredentials(setupPinInput, "PIN")
                        }
                    ) {
                        Text("Set PIN & Unlock")
                    }
                }
            } else {
                if (lockType == "PIN") {
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
                            com.HrshD1eux.Gallery.core.util.BiometricAuthHelper.authenticate(
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
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}


package com.HrshD1eux.Gallery.ui.vault

import android.content.Context
import androidx.compose.foundation.layout.Column
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

    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPatternError by remember { mutableStateOf(false) }

    var failedAttempts by remember { mutableStateOf(0) }
    var lockoutRemainingSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(lockoutRemainingSeconds) {
        if (lockoutRemainingSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            lockoutRemainingSeconds -= 1
        }
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
        },
        confirmButton = {
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
        },
        dismissButton = {
            val isBiometricEnabled = prefs.getBoolean("vault_biometric_enabled", false)
            val activity = context as? android.app.Activity
            androidx.compose.foundation.layout.Row {
                if (isBiometricEnabled && activity != null) {
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

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    fun verifyInput(input: String) {
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
            onUnlockSuccess()
        } else {
            errorMessage = "Incorrect code. Try again."
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
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (lockType == "PATTERN") {
                    Text(
                        text = "Draw your secret pattern to unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PatternLockView(
                        onPatternComplete = { patternStr ->
                            isPatternError = false
                            verifyInput(patternStr)
                        },
                        isError = isPatternError,
                        modifier = Modifier.height(260.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = pinInput,
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

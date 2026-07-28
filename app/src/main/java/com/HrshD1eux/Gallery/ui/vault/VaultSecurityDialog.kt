package com.HrshD1eux.Gallery.ui.vault

import android.content.Context
import androidx.compose.foundation.clickable
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
fun VaultSecurityDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }

    var lockType by remember { mutableStateOf(prefs.getString("vault_lock_type", "PIN") ?: "PIN") }
    var isBiometricEnabled by remember { mutableStateOf(prefs.getBoolean("vault_biometric_enabled", false)) }
    var isStealthMode by remember { mutableStateOf(prefs.getBoolean("vault_stealth_mode", false)) }
    var secretTrigger by remember { mutableStateOf(prefs.getString("vault_secret_trigger", "#vault") ?: "#vault") }

    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showPatternSetupDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Vault Security Settings", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Protection Type",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            lockType = "PIN"
                            prefs.edit().putString("vault_lock_type", "PIN").apply()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = lockType == "PIN",
                        onClick = {
                            lockType = "PIN"
                            prefs.edit().putString("vault_lock_type", "PIN").apply()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Numeric PIN", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            lockType = "PATTERN"
                            prefs.edit().putString("vault_lock_type", "PATTERN").apply()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = lockType == "PATTERN",
                        onClick = {
                            lockType = "PATTERN"
                            prefs.edit().putString("vault_lock_type", "PATTERN").apply()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Pattern Lock (3x3 Gesture)", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Configure PIN / Pattern
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (lockType == "PATTERN") {
                                showPatternSetupDialog = true
                            } else {
                                showPinChangeDialog = true
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lockType == "PATTERN") "Set / Change Pattern" else "Change Vault PIN",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Set or change lock code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Biometric Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Biometric Unlock", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Fingerprint or face unlock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            val activity = context as? android.app.Activity
                            if (enabled && activity != null) {
                                com.HrshD1eux.Gallery.core.util.BiometricAuthHelper.authenticate(
                                    activity = activity,
                                    title = "Confirm Biometric",
                                    subtitle = "Authenticate fingerprint to enable",
                                    onSuccess = {
                                        isBiometricEnabled = true
                                        prefs.edit().putBoolean("vault_biometric_enabled", true).apply()
                                    },
                                    onError = { _ ->
                                        isBiometricEnabled = false
                                        prefs.edit().putBoolean("vault_biometric_enabled", false).apply()
                                    }
                                )
                            } else {
                                isBiometricEnabled = false
                                prefs.edit().putBoolean("vault_biometric_enabled", false).apply()
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Stealth Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Stealth Vault Mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Hide Vault from Albums. Access via search phrase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isStealthMode,
                        onCheckedChange = { enabled ->
                            isStealthMode = enabled
                            prefs.edit().putBoolean("vault_stealth_mode", enabled).apply()
                        }
                    )
                }

                if (isStealthMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secretTrigger,
                        onValueChange = {
                            secretTrigger = it
                            prefs.edit().putString("vault_secret_trigger", it.trim()).apply()
                        },
                        label = { Text("Secret Search Phrase") },
                        placeholder = { Text("#vault") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    // Sub-dialog for Changing PIN
    if (showPinChangeDialog) {
        var currentPinInput by remember { mutableStateOf("") }
        var newPinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinChangeDialog = false },
            title = { Text("Change Vault PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) currentPinInput = it },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) newPinInput = it },
                        label = { Text("New 4-6 Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) confirmPinInput = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinError?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val storedPinHash = prefs.getString("vault_pin_hash", null)
                        val storedSaltBase64 = prefs.getString("vault_salt", null)
                        val legacyPin = prefs.getString("vault_pin", null)

                        var currentValid = false
                        if (storedPinHash != null && storedSaltBase64 != null) {
                            val salt = android.util.Base64.decode(storedSaltBase64, android.util.Base64.NO_WRAP)
                            val inputHash = VaultCrypto.hashPin(currentPinInput, salt)
                            val decryptedHash = try { VaultCrypto.decryptString(storedPinHash) } catch (_: Exception) { storedPinHash }
                            currentValid = (inputHash == decryptedHash || inputHash == storedPinHash)
                        } else if (legacyPin != null && currentPinInput == legacyPin) {
                            currentValid = true
                        } else if (storedPinHash == null && legacyPin == null) {
                            currentValid = true // First time setup
                        }

                        if (!currentValid) {
                            pinError = "Current PIN is incorrect."
                            return@Button
                        }

                        if (newPinInput.length < 4) {
                            pinError = "PIN must be at least 4 digits."
                            return@Button
                        }
                        if (newPinInput != confirmPinInput) {
                            pinError = "New PINs do not match."
                            return@Button
                        }

                        val saltBytes = VaultCrypto.generateSalt()
                        val saltBase64 = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
                        val pinHash = VaultCrypto.hashPin(newPinInput, saltBytes)
                        val encryptedHash = VaultCrypto.encryptString(pinHash)

                        prefs.edit()
                            .putString("vault_pin_hash", encryptedHash)
                            .putString("vault_salt", saltBase64)
                            .putString("vault_lock_type", "PIN")
                            .remove("vault_pin")
                            .apply()

                        lockType = "PIN"
                        showPinChangeDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinChangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sub-dialog for Setup / Change Pattern
    if (showPatternSetupDialog) {
        var setupStep by remember { mutableStateOf(1) }
        var firstPattern by remember { mutableStateOf("") }
        var patternError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPatternSetupDialog = false },
            title = { Text(if (setupStep == 1) "Draw New Pattern" else "Confirm Pattern") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (setupStep == 1) "Draw a pattern connecting at least 4 dots" else "Draw the pattern again to confirm",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    patternError?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PatternLockView(
                        onPatternComplete = { drawn ->
                            if (setupStep == 1) {
                                if (drawn.split("-").size < 4) {
                                    patternError = "Connect at least 4 dots"
                                } else {
                                    firstPattern = drawn
                                    setupStep = 2
                                    patternError = null
                                }
                            } else {
                                if (drawn == firstPattern) {
                                    val saltBytes = VaultCrypto.generateSalt()
                                    val saltBase64 = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
                                    val pinHash = VaultCrypto.hashPin(drawn, saltBytes)
                                    val encryptedHash = VaultCrypto.encryptString(pinHash)

                                    prefs.edit()
                                        .putString("vault_pin_hash", encryptedHash)
                                        .putString("vault_salt", saltBase64)
                                        .putString("vault_lock_type", "PATTERN")
                                        .remove("vault_pin")
                                        .apply()

                                    lockType = "PATTERN"
                                    showPatternSetupDialog = false
                                } else {
                                    patternError = "Patterns do not match. Try again."
                                    setupStep = 1
                                }
                            }
                        },
                        isError = patternError != null,
                        modifier = Modifier.height(260.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPatternSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

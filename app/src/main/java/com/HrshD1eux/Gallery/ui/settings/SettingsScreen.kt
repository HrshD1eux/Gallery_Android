package com.HrshD1eux.Gallery.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.HrshD1eux.Gallery.ui.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val prefs = remember(context) { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    var storedPinHash by remember { mutableStateOf(prefs.getString("vault_pin_hash", null)) }
    var storedSaltBase64 by remember { mutableStateOf(prefs.getString("vault_salt", null)) }
    var legacyPin by remember { mutableStateOf(prefs.getString("vault_pin", null)) }
    val isPinConfigured = storedPinHash != null || legacyPin != null

    var isBiometricEnabled by remember { mutableStateOf(prefs.getBoolean("vault_biometric_enabled", false)) }

    var showPinDialog by remember { mutableStateOf(false) }
    var currentPinInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }

    val appTheme = viewModel.appTheme

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // --- Appearance & Theme Section ---
        item {
            SettingsCategoryHeader(title = "Appearance & Theme", icon = Icons.Default.Palette)
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Choose App Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    ThemeOptionRow(
                        title = "System Default",
                        selected = appTheme == "system",
                        onClick = { viewModel.appTheme = "system" }
                    )
                    ThemeOptionRow(
                        title = "Dark Theme",
                        selected = appTheme == "dark",
                        onClick = { viewModel.appTheme = "dark" }
                    )
                    ThemeOptionRow(
                        title = "Light Theme",
                        selected = appTheme == "light",
                        onClick = { viewModel.appTheme = "light" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Privacy & Security Section ---
        item {
            SettingsCategoryHeader(title = "Privacy & Security", icon = Icons.Default.Security)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Vault PIN Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPinDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPinConfigured) "Change Hidden Vault PIN" else "Set Hidden Vault PIN",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isPinConfigured) "PIN protection active" else "No PIN set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Biometric Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable Biometric Unlock", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Use fingerprint or face unlock to access Hidden Vault",
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
                                        title = "Confirm Biometric Unlock",
                                        subtitle = "Authenticate fingerprint or face to enable biometric vault unlock",
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- About Section ---
        item {
            SettingsCategoryHeader(title = "About", icon = Icons.Default.Info)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Gallery App",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "A fast, private, and secure gallery for your photos and videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Vault PIN Setting Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                currentPinInput = ""
                pinInput = ""
                pinConfirmInput = ""
                pinErrorMessage = null
            },
            title = { Text(if (isPinConfigured) "Change Vault PIN" else "Set Vault PIN") },
            text = {
                Column {
                    if (isPinConfigured) {
                        OutlinedTextField(
                            value = currentPinInput,
                            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) currentPinInput = it },
                            label = { Text("Enter Current PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pinInput = it },
                        label = { Text(if (isPinConfigured) "Enter New 4-6 Digit PIN" else "Enter 4-6 Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pinConfirmInput = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinErrorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pinErrorMessage = null
                        if (isPinConfigured) {
                            if (currentPinInput.length < 4) {
                                pinErrorMessage = "Please enter your current 4-digit PIN."
                                return@Button
                            }
                            var currentValid = false
                            if (storedPinHash != null && storedSaltBase64 != null) {
                                val salt = android.util.Base64.decode(storedSaltBase64, android.util.Base64.NO_WRAP)
                                val inputHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.hashPin(currentPinInput, salt)
                                val decryptedHash = try {
                                    com.HrshD1eux.Gallery.core.util.VaultCrypto.decryptString(storedPinHash!!)
                                } catch (_: Exception) {
                                    storedPinHash
                                }
                                currentValid = (inputHash == decryptedHash || inputHash == storedPinHash)
                            } else if (legacyPin != null && currentPinInput == legacyPin) {
                                currentValid = true
                            }

                            if (!currentValid) {
                                pinErrorMessage = "Current PIN is incorrect."
                                return@Button
                            }
                        }

                        if (pinInput.length < 4) {
                            pinErrorMessage = "New PIN must be at least 4 digits."
                            return@Button
                        }
                        if (pinInput != pinConfirmInput) {
                            pinErrorMessage = "New PINs do not match."
                            return@Button
                        }
                        
                        val saltBytes = com.HrshD1eux.Gallery.core.util.VaultCrypto.generateSalt()
                        val saltBase64 = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
                        val pinHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.hashPin(pinInput, saltBytes)
                        val encryptedHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.encryptString(pinHash)

                        prefs.edit()
                            .putString("vault_pin_hash", encryptedHash)
                            .putString("vault_salt", saltBase64)
                            .remove("vault_pin")
                            .apply()

                        storedPinHash = encryptedHash
                        storedSaltBase64 = saltBase64
                        legacyPin = null
                        showPinDialog = false
                        currentPinInput = ""
                        pinInput = ""
                        pinConfirmInput = ""
                        pinErrorMessage = null
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    currentPinInput = ""
                    pinInput = ""
                    pinConfirmInput = ""
                    pinErrorMessage = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

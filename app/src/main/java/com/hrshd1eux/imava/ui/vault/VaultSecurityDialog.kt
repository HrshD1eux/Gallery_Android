package com.hrshd1eux.imava.ui.vault

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import com.hrshd1eux.imava.core.util.HapticUtil
import com.hrshd1eux.imava.core.util.VaultBackupManager
import com.hrshd1eux.imava.data.database.GalleryDatabase
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.core.util.VaultCrypto
import com.hrshd1eux.imava.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun VaultSecurityDialog(
    viewModel: MainViewModel? = null,
    onDismiss: () -> Unit,
    onVaultDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var lockType by remember { mutableStateOf(prefs.getString("vault_lock_type", "PIN") ?: "PIN") }
    var isBiometricEnabled by remember { mutableStateOf(prefs.getBoolean("vault_biometric_enabled", false)) }
    var isStealthMode by remember { mutableStateOf(prefs.getBoolean("vault_stealth_mode", false)) }
    var isVaultDisabled by remember { mutableStateOf(prefs.getBoolean("vault_disabled", false)) }
    var secretTrigger by remember { mutableStateOf(prefs.getString("vault_secret_trigger", "#vault") ?: "#vault") }
    var hasDecoyPin by remember { mutableStateOf(prefs.getString("vault_decoy_pin_hash", null) != null) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }

    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showPatternSetupDialog by remember { mutableStateOf(false) }
    var showDisableConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isProcessingDelete by remember { mutableStateOf(false) }

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var exportPassword by remember { mutableStateOf("") }

    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var isProcessingBackup by remember { mutableStateOf(false) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            showExportPasswordDialog = true
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePasswordDialog = true
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessingDelete) onDismiss() },
        title = {
            Text(text = "Vault Security Settings", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                val isRooted = remember { com.hrshd1eux.imava.core.util.RootDetectionUtil.isDeviceRooted() }
                if (isRooted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Device Root Detected: Superuser apps on this phone could bypass security protections.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

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
                            isVaultDisabled = false
                            prefs.edit().putString("vault_lock_type", "PIN").putBoolean("vault_disabled", false).apply()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = lockType == "PIN" && !isVaultDisabled,
                        onClick = {
                            lockType = "PIN"
                            isVaultDisabled = false
                            prefs.edit().putString("vault_lock_type", "PIN").putBoolean("vault_disabled", false).apply()
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
                            isVaultDisabled = false
                            prefs.edit().putString("vault_lock_type", "PATTERN").putBoolean("vault_disabled", false).apply()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = lockType == "PATTERN" && !isVaultDisabled,
                        onClick = {
                            lockType = "PATTERN"
                            isVaultDisabled = false
                            prefs.edit().putString("vault_lock_type", "PATTERN").putBoolean("vault_disabled", false).apply()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Pattern Lock (3x3 Gesture)", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


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
                                com.hrshd1eux.imava.core.util.BiometricAuthHelper.authenticate(
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
                            viewModel?.notifyVaultConfigChanged()
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDecoyPinDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Configure Decoy PIN 🎭",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (hasDecoyPin) "Decoy PIN active (opens empty fake vault)" else "Set a decoy PIN for plausible deniability",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val dateStr = java.time.LocalDate.now().toString()
                            createDocLauncher.launch("imava_vault_backup_$dateStr.imava")
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export Encrypted Backup 💾",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Save all vault media & metadata as a secure .imava archive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openDocLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Restore Vault Backup 📥",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Import and restore an encrypted .imava container",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDisableConfirmDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Disable Vault Lock",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Remove PIN/Pattern lock to open Vault directly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteConfirmDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Delete Vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Restore or wipe all hidden photos and delete Vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )


    if (showDecoyPinDialog) {
        var decoyInput by remember { mutableStateOf("") }
        var confirmDecoyInput by remember { mutableStateOf("") }
        var decoyError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showDecoyPinDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Decoy PIN 🎭") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "A decoy PIN provides plausible deniability. When typed at unlock, Imava unlocks an empty fake vault instead of revealing your private items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = decoyInput,
                        onValueChange = {
                            if (it.length <= 8 && it.all { c -> c.isDigit() }) decoyInput = it
                        },
                        label = { Text("Decoy PIN (4-8 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmDecoyInput,
                        onValueChange = {
                            if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmDecoyInput = it
                        },
                        label = { Text("Confirm Decoy PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (decoyError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = decoyError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (decoyInput.length < 4) {
                            decoyError = "Decoy PIN must be at least 4 digits"
                            return@Button
                        }
                        if (decoyInput != confirmDecoyInput) {
                            decoyError = "PINs do not match"
                            return@Button
                        }
                        val saltBase64 = prefs.getString("vault_salt", null)
                        val saltBytes = if (saltBase64 != null) {
                            android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP)
                        } else {
                            VaultCrypto.generateSalt()
                        }
                        val decoyHash = VaultCrypto.hashPin(decoyInput, saltBytes)
                        val encHash = VaultCrypto.encryptString(decoyHash)
                        prefs.edit().putString("vault_decoy_pin_hash", encHash).apply()
                        hasDecoyPin = true
                        showDecoyPinDialog = false
                        Toast.makeText(context, "Decoy PIN configured!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save Decoy PIN")
                }
            },
            dismissButton = {
                Row {
                    if (hasDecoyPin) {
                        TextButton(
                            onClick = {
                                prefs.edit().remove("vault_decoy_pin_hash").apply()
                                hasDecoyPin = false
                                showDecoyPinDialog = false
                                Toast.makeText(context, "Decoy PIN removed", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    TextButton(onClick = { showDecoyPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }


    if (showDisableConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisableConfirmDialog = false },
            icon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Disable Vault Lock?") },
            text = {
                Text("Disabling the lock will remove PIN, Pattern, and Biometric protection. Anyone with access to this device will be able to view hidden files in the Vault.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisableConfirmDialog = false
                        viewModel?.disableVault(context) ?: run {
                            prefs.edit()
                                .putBoolean("vault_disabled", true)
                                .remove("vault_pin_hash")
                                .remove("vault_salt")
                                .remove("vault_pin")
                                .remove("vault_biometric_enabled")
                                .remove("vault_stealth_mode")
                                .apply()
                        }
                        isVaultDisabled = true
                        Toast.makeText(context, "Vault lock disabled", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Disable Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingDelete) showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Vault") },
            text = {
                if (isProcessingDelete) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Deleting vault...")
                    }
                } else {
                    Column {
                        Text("Choose what to do with the files currently stored in the Vault:")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                isProcessingDelete = true
                                viewModel?.deleteVault(context, restoreMedia = true) {
                                    isProcessingDelete = false
                                    showDeleteConfirmDialog = false
                                    onVaultDeleted()
                                    onDismiss()
                                    Toast.makeText(context, "All media restored & Vault deleted", Toast.LENGTH_LONG).show()
                                } ?: run {
                                    isProcessingDelete = false
                                    showDeleteConfirmDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restore All Media & Delete Vault")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isProcessingDelete = true
                                viewModel?.deleteVault(context, restoreMedia = false) {
                                    isProcessingDelete = false
                                    showDeleteConfirmDialog = false
                                    onVaultDeleted()
                                    onDismiss()
                                    Toast.makeText(context, "Vault and files permanently deleted", Toast.LENGTH_LONG).show()
                                } ?: run {
                                    isProcessingDelete = false
                                    showDeleteConfirmDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Permanently Delete Vault & All Files")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (!isProcessingDelete) {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }


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
                            val res = VaultCrypto.verifyPin(currentPinInput, storedPinHash, salt)
                            currentValid = res.isValid
                        } else if (legacyPin != null && currentPinInput == legacyPin) {
                            currentValid = true
                        } else if (storedPinHash == null && legacyPin == null) {
                            currentValid = true
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
                            .putBoolean("vault_disabled", false)
                            .remove("vault_pin")
                            .apply()

                        lockType = "PIN"
                        isVaultDisabled = false
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
                                        .putBoolean("vault_disabled", false)
                                        .remove("vault_pin")
                                        .apply()

                                    lockType = "PATTERN"
                                    isVaultDisabled = false
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


    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingBackup) showExportPasswordDialog = false },
            title = { Text("Export Vault Backup 💾") },
            text = {
                Column {
                    Text(
                        text = "Set a strong passphrase to encrypt your backup container. You will need this passphrase to restore your vault on any device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("Backup Passphrase (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🔒 Security Tip: Avoid simple 4-digit PINs. A strong alphanumeric passphrase protects against offline brute-force attacks if the backup file is leaked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isProcessingBackup) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Encrypting & exporting...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = exportPassword.length >= 6 && !isProcessingBackup && pendingExportUri != null,
                    onClick = {
                        val targetUri = pendingExportUri ?: return@Button
                        isProcessingBackup = true
                        scope.launch {
                            try {
                                val outputStream = context.contentResolver.openOutputStream(targetUri)
                                if (outputStream != null) {
                                    val db = GalleryDatabase.getInstance(context)
                                    val result = VaultBackupManager.exportVaultBackup(
                                        context = context,
                                        database = db,
                                        targetOutputStream = outputStream,
                                        passphrase = exportPassword.toCharArray()
                                    )
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    if (result.success) {
                                        showExportPasswordDialog = false
                                        exportPassword = ""
                                        pendingExportUri = null
                                    }
                                } else {
                                    Toast.makeText(context, "Could not open target file.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                isProcessingBackup = false
                            }
                        }
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isProcessingBackup,
                    onClick = {
                        showExportPasswordDialog = false
                        exportPassword = ""
                        pendingExportUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }


    if (showRestorePasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingBackup) showRestorePasswordDialog = false },
            title = { Text("Restore Vault Backup 📥") },
            text = {
                Column {
                    Text(
                        text = "Enter the passphrase used when creating this .imava backup container.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Backup Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isProcessingBackup) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Decrypting & restoring vault...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = restorePassword.isNotBlank() && !isProcessingBackup && pendingRestoreUri != null,
                    onClick = {
                        val sourceUri = pendingRestoreUri ?: return@Button
                        isProcessingBackup = true
                        scope.launch {
                            try {
                                val inputStream = context.contentResolver.openInputStream(sourceUri)
                                if (inputStream != null) {
                                    val db = GalleryDatabase.getInstance(context)
                                    val result = VaultBackupManager.restoreVaultBackup(
                                        context = context,
                                        database = db,
                                        inputStream = inputStream,
                                        passphrase = restorePassword.toCharArray()
                                    )
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    if (result.success) {
                                        viewModel?.refreshAll()
                                        showRestorePasswordDialog = false
                                        restorePassword = ""
                                        pendingRestoreUri = null
                                    }
                                } else {
                                    Toast.makeText(context, "Could not open source file.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Restore error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                isProcessingBackup = false
                            }
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isProcessingBackup,
                    onClick = {
                        showRestorePasswordDialog = false
                        restorePassword = ""
                        pendingRestoreUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

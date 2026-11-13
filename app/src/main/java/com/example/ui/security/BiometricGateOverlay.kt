package com.example.ui.security

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.security.BiometricSecurityManager
import com.example.core.security.BiometricSecurityStatus
import com.example.ui.components.RawVideoAnimationBanner
import com.example.ui.theme.SeamBorderSubtle
import com.example.ui.theme.SeamCharcoal
import com.example.ui.theme.SeamElevatedSurface
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarmIvory

/**
 * Evaluates whether the device has both `USE_BIOMETRIC` permission and an active biometric hardware sensor.
 */
fun canDeviceAuthenticateWithBiometrics(context: Context): Boolean {
    return BiometricSecurityManager.assessSecurityLayer(context).canUseHardwareBiometricPrompt
}

/**
 * Evaluates whether the device physically has a biometric sensor.
 */
fun hasDeviceBiometricHardware(context: Context): Boolean {
    return BiometricSecurityManager.assessSecurityLayer(context).hasPhysicalBiometricSensor
}

/**
 * Triggers authentication through the centralized `BiometricSecurityManager`:
 * - Checks biometric permission (`USE_BIOMETRIC` / `USE_FINGERPRINT`) and hardware availability.
 * - If available, invokes the native Android `BiometricPrompt`.
 * - If the device lacks a biometric sensor (or lacks enrolled biometrics), routes to `onNoBiometricSensorAvailable()`
 *   so the caller presents the mandatory PIN / Password fallback entry.
 */
fun triggerHardwareBiometricPrompt(
    context: Context,
    title: String = "Device Biometric Authentication",
    subtitle: String = "Confirm with your Android device biometric sensor",
    description: String = "Authenticate with your device sensor to continue.",
    onAuthenticated: () -> Unit,
    onNoBiometricSensorAvailable: () -> Unit = {},
    onAuthenticationFailedOrCancelled: (String) -> Unit = {}
) {
    BiometricSecurityManager.authenticateWithSecurityLayer(
        context = context,
        title = title,
        subtitle = subtitle,
        description = description,
        onAuthenticated = onAuthenticated,
        onRequirePinOrPasswordFallback = {
            onNoBiometricSensorAvailable()
        },
        onAuthenticationErrorOrCancelled = onAuthenticationFailedOrCancelled
    )
}

private enum class FallbackCredentialInputMode {
    PIN_KEYPAD,
    PASSWORD_FIELD
}

/**
 * Security Layer Gate for Application Entry, Vault Tab Access, and Autofill:
 * 1. Inspects `BiometricSecurityManager.assessSecurityLayer(context)` for:
 *    - Biometric permission (`USE_BIOMETRIC` / `USE_FINGERPRINT`)
 *    - Biometric hardware sensor availability (`BiometricManager` + `PackageManager`)
 * 2. When biometric hardware & permission are active (`BIOMETRIC_READY`):
 *    - Directly calls the Android device's native `BiometricPrompt`.
 * 3. When the device lacks a biometric sensor (`NO_BIOMETRIC_HARDWARE_FALLBACK_REQUIRED` or fallback selected):
 *    - Enforces PIN / Password fallback entry (supporting both the 4-digit PIN keypad and alphanumeric Password entry,
 *      with NO misleading fingerprint icon on the keypad).
 */
@Composable
fun BiometricGateOverlay(
    expectedPasscode: String,
    userDisplayName: String,
    gateTitle: String = "SECURITY LAYER GATE",
    gateSubtitle: String? = null,
    autoTriggerBiometricOnLaunch: Boolean = true,
    onCancel: (() -> Unit)? = null,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val effectiveSecret = expectedPasscode.ifBlank { "2026" }

    var securityAssessment by remember {
        mutableStateOf(BiometricSecurityManager.assessSecurityLayer(context))
    }
    var usePinOrPasswordFallback by remember(securityAssessment) {
        mutableStateOf(!securityAssessment.canUseHardwareBiometricPrompt)
    }
    var fallbackInputMode by remember(effectiveSecret) {
        mutableStateOf(
            if (effectiveSecret.length == 4 && effectiveSecret.all { it.isDigit() }) {
                FallbackCredentialInputMode.PIN_KEYPAD
            } else {
                FallbackCredentialInputMode.PASSWORD_FIELD
            }
        )
    }

    var enteredPin by remember { mutableStateOf("") }
    var enteredPassword by remember { mutableStateOf("") }
    var showPasswordPlaintext by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var statusText by remember(gateSubtitle, securityAssessment, usePinOrPasswordFallback) {
        mutableStateOf(
            if (!usePinOrPasswordFallback && securityAssessment.canUseHardwareBiometricPrompt) {
                gateSubtitle ?: securityAssessment.statusDetail
            } else {
                securityAssessment.statusDetail
            }
        )
    }

    // Runtime permission launcher in case USE_BIOMETRIC permission needs to be requested
    val biometricPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        securityAssessment = BiometricSecurityManager.assessSecurityLayer(context)
        if (granted && securityAssessment.canUseHardwareBiometricPrompt) {
            usePinOrPasswordFallback = false
            BiometricSecurityManager.authenticateWithSecurityLayer(
                context = context,
                title = gateTitle,
                subtitle = gateSubtitle ?: "Confirm your identity with your Android device",
                onAuthenticated = onUnlocked,
                onRequirePinOrPasswordFallback = { updated ->
                    securityAssessment = updated
                    usePinOrPasswordFallback = true
                    statusText = updated.statusDetail
                },
                onAuthenticationErrorOrCancelled = { msg ->
                    hasError = true
                    statusText = msg
                }
            )
        } else {
            usePinOrPasswordFallback = true
            statusText = "Biometric permission unavailable. Enter your PIN or Password below."
        }
    }

    // Automatically check biometric permission & hardware availability on launch
    LaunchedEffect(autoTriggerBiometricOnLaunch) {
        val currentAssessment = BiometricSecurityManager.assessSecurityLayer(context)
        securityAssessment = currentAssessment

        if (currentAssessment.status == BiometricSecurityStatus.PERMISSION_REQUIRED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            biometricPermissionLauncher.launch(Manifest.permission.USE_BIOMETRIC)
            return@LaunchedEffect
        }

        if (currentAssessment.canUseHardwareBiometricPrompt && autoTriggerBiometricOnLaunch) {
            usePinOrPasswordFallback = false
            BiometricSecurityManager.authenticateWithSecurityLayer(
                context = context,
                title = gateTitle,
                subtitle = gateSubtitle ?: "Confirm your identity with your Android device",
                onAuthenticated = onUnlocked,
                onRequirePinOrPasswordFallback = { updated ->
                    securityAssessment = updated
                    usePinOrPasswordFallback = true
                    statusText = updated.statusDetail
                },
                onAuthenticationErrorOrCancelled = { msg ->
                    hasError = true
                    statusText = msg
                }
            )
        } else {
            usePinOrPasswordFallback = true
            statusText = currentAssessment.statusDetail
        }
    }

    // 4-Digit PIN verification in PIN_KEYPAD mode
    LaunchedEffect(enteredPin, usePinOrPasswordFallback, fallbackInputMode) {
        if (usePinOrPasswordFallback &&
            fallbackInputMode == FallbackCredentialInputMode.PIN_KEYPAD &&
            enteredPin.length == 4
        ) {
            if (enteredPin == effectiveSecret) {
                onUnlocked()
            } else {
                hasError = true
                statusText = "Incorrect 4-digit PIN. Please try again."
                enteredPin = ""
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("biometric_gate_overlay"),
        color = SeamNearBlack
    ) {
        if (!usePinOrPasswordFallback && securityAssessment.canUseHardwareBiometricPrompt) {
            // HARDWARE BIOMETRIC SENSOR ACTIVE VIEW:
            // Directly calls Android OS BiometricPrompt; no keypad and no misleading icons.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (onCancel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = SeamMutedGrey)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CircleShape)
                            .border(1.dp, SeamBorderSubtle, CircleShape)
                    ) {
                        RawVideoAnimationBanner(
                            rawResId = R.raw.vault_rings,
                            modifier = Modifier.fillMaxSize(),
                            overlayScrimAlpha = 0.2f
                        )
                    }

                    SecurityHardwareStatusBadge(
                        statusLabel = securityAssessment.statusLabel,
                        hasPermission = securityAssessment.hasBiometricPermission,
                        hasHardware = securityAssessment.hasPhysicalBiometricSensor
                    )

                    Text(
                        text = userDisplayName.ifBlank { "Device Biometric Verification" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = SeamWarmIvory,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasError) SeamErrorCoral else SeamMutedGrey,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            hasError = false
                            BiometricSecurityManager.authenticateWithSecurityLayer(
                                context = context,
                                title = gateTitle,
                                subtitle = gateSubtitle ?: "Confirm your identity with your Android device",
                                onAuthenticated = onUnlocked,
                                onRequirePinOrPasswordFallback = { updated ->
                                    securityAssessment = updated
                                    usePinOrPasswordFallback = true
                                    statusText = updated.statusDetail
                                },
                                onAuthenticationErrorOrCancelled = { msg ->
                                    hasError = true
                                    statusText = msg
                                }
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SeamWarmIvory,
                            contentColor = SeamNearBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("prompt_android_biometric_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verify with Android Biometric Sensor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(
                        onClick = {
                            usePinOrPasswordFallback = true
                            statusText = "Switched to PIN / Password fallback entry."
                        },
                        modifier = Modifier.testTag("switch_to_pin_password_fallback_button")
                    ) {
                        Text("Use PIN / Password Instead", color = SeamSilver)
                    }
                }
            }
        } else {
            // PIN / PASSWORD FALLBACK VIEW FOR DEVICES THAT LACK A BIOMETRIC SENSOR:
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onCancel != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onCancel) {
                                Text("Cancel", color = SeamMutedGrey)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .border(1.dp, SeamBorderSubtle, CircleShape)
                    ) {
                        RawVideoAnimationBanner(
                            rawResId = R.raw.vault_rings,
                            modifier = Modifier.fillMaxSize(),
                            overlayScrimAlpha = 0.2f
                        )
                    }

                    SecurityHardwareStatusBadge(
                        statusLabel = securityAssessment.statusLabel,
                        hasPermission = securityAssessment.hasBiometricPermission,
                        hasHardware = securityAssessment.hasPhysicalBiometricSensor
                    )

                    Text(
                        text = userDisplayName.ifBlank { "PIN / Password Authentication" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = SeamWarmIvory,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasError) SeamErrorCoral else SeamMutedGrey,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // Mode Toggle: 4-Digit PIN Keypad vs. Alphanumeric Password Input
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = fallbackInputMode == FallbackCredentialInputMode.PIN_KEYPAD,
                            onClick = {
                                hasError = false
                                fallbackInputMode = FallbackCredentialInputMode.PIN_KEYPAD
                            },
                            label = { Text("4-Digit PIN") },
                            modifier = Modifier.testTag("fallback_mode_pin_chip")
                        )
                        FilterChip(
                            selected = fallbackInputMode == FallbackCredentialInputMode.PASSWORD_FIELD,
                            onClick = {
                                hasError = false
                                fallbackInputMode = FallbackCredentialInputMode.PASSWORD_FIELD
                            },
                            label = { Text("Password Entry") },
                            modifier = Modifier.testTag("fallback_mode_password_chip")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (fallbackInputMode == FallbackCredentialInputMode.PIN_KEYPAD) {
                    // 4 PIN Dots + Pure Digits-Only Numpad (1-9, 0, Backspace — NO fingerprint icon)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            repeat(4) { index ->
                                val filled = index < enteredPin.length
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) SeamWarmIvory else SeamElevatedSurface)
                                        .border(
                                            1.dp,
                                            if (filled) SeamWarmIvory else SeamBorderSubtle,
                                            CircleShape
                                        )
                                )
                            }
                        }

                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9")
                        )
                        rows.forEach { rowDigits ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowDigits.forEach { digit ->
                                    PinKeyButton(
                                        label = digit,
                                        onClick = {
                                            hasError = false
                                            if (enteredPin.length < 4) enteredPin += digit
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Empty spacer — strictly no fingerprint button on the numpad
                            Spacer(modifier = Modifier.size(64.dp))

                            PinKeyButton(
                                label = "0",
                                onClick = {
                                    hasError = false
                                    if (enteredPin.length < 4) enteredPin += "0"
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(SeamCharcoal)
                                    .border(1.dp, SeamBorderSubtle, CircleShape)
                                    .clickable {
                                        if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                        }
                                    }
                                    .testTag("pin_key_backspace"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete digit",
                                    tint = SeamSilver,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Alphanumeric Password / PIN Text Input Fallback
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SeamCharcoal)
                            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "FALLBACK PASSWORD / PIN ENTRY",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamLavenderAccent
                        )
                        OutlinedTextField(
                            value = enteredPassword,
                            onValueChange = {
                                hasError = false
                                enteredPassword = it
                            },
                            label = { Text("Enter Vault PIN or Password") },
                            singleLine = true,
                            visualTransformation = if (showPasswordPlaintext) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPasswordPlaintext = !showPasswordPlaintext }) {
                                    Icon(
                                        imageVector = if (showPasswordPlaintext) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = SeamSilver
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fallback_password_input")
                        )

                        Button(
                            onClick = {
                                if (enteredPassword.trim() == effectiveSecret) {
                                    onUnlocked()
                                } else {
                                    hasError = true
                                    statusText = "Incorrect PIN or Password. Please try again."
                                }
                            },
                            enabled = enteredPassword.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SeamWarmIvory,
                                contentColor = SeamNearBlack
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("fallback_password_submit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock with PIN / Password", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityHardwareStatusBadge(
    statusLabel: String,
    hasPermission: Boolean,
    hasHardware: Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SeamCharcoal)
            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (hasHardware && hasPermission) Icons.Default.Shield else Icons.Default.Lock,
            contentDescription = null,
            tint = if (hasHardware && hasPermission) SeamSuccessSage else SeamLavenderAccent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = SeamWarmIvory
        )
    }
}

/**
 * Fallback PIN / Password Verification Dialog used when a device lacks a biometric sensor
 * and the user needs to authorize Autofill or change their Vault PIN/Password in Settings.
 */
@Composable
fun PinOnlyVerificationDialog(
    title: String,
    subtitle: String,
    expectedPasscode: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val effectiveSecret = expectedPasscode.ifBlank { "2026" }
    var usePasswordField by remember(effectiveSecret) {
        mutableStateOf(!(effectiveSecret.length == 4 && effectiveSecret.all { it.isDigit() }))
    }
    var enteredPin by remember { mutableStateOf("") }
    var enteredPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enteredPin, usePasswordField) {
        if (!usePasswordField && enteredPin.length == 4) {
            if (enteredPin == effectiveSecret) {
                onVerified()
            } else {
                errorText = "Incorrect 4-digit PIN."
                enteredPin = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeamCharcoal,
        titleContentColor = SeamWarmIvory,
        textContentColor = SeamSilver,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorText ?: subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorText != null) SeamErrorCoral else SeamMutedGrey,
                    textAlign = TextAlign.Center
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !usePasswordField,
                        onClick = {
                            errorText = null
                            usePasswordField = false
                        },
                        label = { Text("PIN Keypad") }
                    )
                    FilterChip(
                        selected = usePasswordField,
                        onClick = {
                            errorText = null
                            usePasswordField = true
                        },
                        label = { Text("Password") }
                    )
                }

                if (!usePasswordField) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { index ->
                            val filled = index < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) SeamWarmIvory else SeamElevatedSurface)
                                    .border(1.dp, if (filled) SeamWarmIvory else SeamBorderSubtle, CircleShape)
                            )
                        }
                    }

                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )
                    rows.forEach { rowDigits ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            rowDigits.forEach { digit ->
                                PinKeyButton(
                                    label = digit,
                                    sizeDp = 50,
                                    onClick = {
                                        errorText = null
                                        if (enteredPin.length < 4) enteredPin += digit
                                    }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Spacer(modifier = Modifier.size(50.dp))
                        PinKeyButton(
                            label = "0",
                            sizeDp = 50,
                            onClick = {
                                errorText = null
                                if (enteredPin.length < 4) enteredPin += "0"
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(SeamElevatedSurface)
                                .border(1.dp, SeamBorderSubtle, CircleShape)
                                .clickable {
                                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = SeamSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = {
                            errorText = null
                            enteredPassword = it
                        },
                        label = { Text("Enter Vault PIN or Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (usePasswordField) {
                Button(
                    onClick = {
                        if (enteredPassword.trim() == effectiveSecret) {
                            onVerified()
                        } else {
                            errorText = "Incorrect PIN or Password."
                        }
                    },
                    enabled = enteredPassword.isNotBlank()
                ) {
                    Text("Verify")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SeamMutedGrey)
            }
        }
    )
}

/**
 * Asks the Android device for permission to enable Seam's Autofill & Auto-Save Password service
 * (`Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE`).
 */
@Composable
fun DeviceAutofillPermissionRequestDialog(
    onDismiss: () -> Unit,
    onPermissionResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = context.getSystemService(AutofillManager::class.java)
            afm?.hasEnabledAutofillServices() == true
        } else {
            false
        }
        onPermissionResult(enabled)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                    Uri.parse("package:${context.packageName}")
                )
                launcher.launch(intent)
            } catch (_: Exception) {
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeamCharcoal,
        titleContentColor = SeamWarmIvory,
        textContentColor = SeamSilver,
        icon = {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = SeamLavenderAccent
            )
        },
        title = {
            Text(
                text = "Allow Autofill & Auto-Save Passwords",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "To automatically autofill logins and save passwords across apps on your device, grant Seam by PaperFlow permission as your Android Autofill service.",
                style = MaterialTheme.typography.bodyMedium,
                color = SeamSilver
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                                Uri.parse("package:${context.packageName}")
                            )
                            launcher.launch(intent)
                        } catch (e: Exception) {
                            try {
                                launcher.launch(Intent(Settings.ACTION_SETTINGS))
                            } catch (_: Exception) {
                                onDismiss()
                            }
                        }
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeamWarmIvory,
                    contentColor = SeamNearBlack
                ),
                modifier = Modifier.testTag("grant_device_autofill_permission_button")
            ) {
                Text("Grant Device Permission", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("skip_device_autofill_permission_button")
            ) {
                Text("Later", color = SeamMutedGrey)
            }
        }
    )
}

/**
 * Requirement 5:
 * One-Time Fallback Vault PIN / Password Setup at Sign-In.
 */
@Composable
fun FirstTimeVaultPinSetupScreen(
    userDisplayName: String,
    userEmail: String,
    onPinCreated: (String) -> Unit
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activePin = if (isConfirmStep) confirmPin else firstPin

    LaunchedEffect(firstPin, confirmPin, isConfirmStep) {
        if (!isConfirmStep && firstPin.length == 4) {
            isConfirmStep = true
        } else if (isConfirmStep && confirmPin.length == 4) {
            if (confirmPin == firstPin) {
                onPinCreated(firstPin)
            } else {
                errorMessage = "PINs did not match. Please enter a new 4-digit Vault PIN."
                firstPin = ""
                confirmPin = ""
                isConfirmStep = false
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("first_time_pin_setup_screen"),
        color = SeamNearBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SeamCharcoal)
                        .border(1.dp, SeamLavenderAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SeamWarmIvory,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "ONE-TIME VAULT PIN SETUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeamLavenderAccent
                )

                Text(
                    text = if (!isConfirmStep) "Create your 4-digit Vault PIN" else "Confirm your 4-digit Vault PIN",
                    style = MaterialTheme.typography.headlineLarge,
                    color = SeamWarmIvory,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (errorMessage != null) {
                        errorMessage!!
                    } else if (!isConfirmStep) {
                        "Signed in as ${userDisplayName.ifBlank { userEmail }}. Set your fallback Vault PIN once now (required on devices that lack a biometric sensor)."
                    } else {
                        "Re-enter the 4 digits to confirm and lock your Vault PIN."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) SeamErrorCoral else SeamMutedGrey,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // 4 PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    repeat(4) { index ->
                        val filled = index < activePin.length
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(if (filled) SeamWarmIvory else SeamElevatedSurface)
                                .border(1.dp, if (filled) SeamWarmIvory else SeamBorderSubtle, CircleShape)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                rows.forEach { rowDigits ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowDigits.forEach { digit ->
                            PinKeyButton(
                                label = digit,
                                onClick = {
                                    errorMessage = null
                                    if (!isConfirmStep) {
                                        if (firstPin.length < 4) firstPin += digit
                                    } else {
                                        if (confirmPin.length < 4) confirmPin += digit
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(64.dp))
                    PinKeyButton(
                        label = "0",
                        onClick = {
                            errorMessage = null
                            if (!isConfirmStep) {
                                if (firstPin.length < 4) firstPin += "0"
                            } else {
                                if (confirmPin.length < 4) confirmPin += "0"
                            }
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SeamCharcoal)
                            .border(1.dp, SeamBorderSubtle, CircleShape)
                            .clickable {
                                if (!isConfirmStep && firstPin.isNotEmpty()) {
                                    firstPin = firstPin.dropLast(1)
                                } else if (isConfirmStep && confirmPin.isNotEmpty()) {
                                    confirmPin = confirmPin.dropLast(1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Delete digit",
                            tint = SeamSilver,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    sizeDp: Int = 64,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(SeamCharcoal)
            .border(1.dp, SeamBorderSubtle, CircleShape)
            .clickable(onClick = onClick)
            .testTag("pin_key_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = SeamWarmIvory,
            fontWeight = FontWeight.SemiBold
        )
    }
}

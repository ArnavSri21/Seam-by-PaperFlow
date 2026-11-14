package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.security.BiometricSecurityManager
import com.example.core.security.KeystoreVaultCrypto
import com.example.data.local.PasswordStrengthTier
import com.example.data.local.VaultCredentialEntity
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.AiExecutionMode
import com.example.data.preferences.ThemeModePreference
import com.example.data.preferences.UserVaultPreferences
import com.example.ui.components.RawVideoAnimationBanner
import com.example.ui.components.SeamGlassBackdrop
import com.example.ui.components.SeamGlassBadge
import com.example.ui.components.SeamTopHeader
import com.example.ui.components.seamGlassSurface
import com.example.ui.components.seamPressableGlassCard
import com.example.ui.security.PinOnlyVerificationDialog
import com.example.ui.security.triggerHardwareBiometricPrompt
import com.example.ui.theme.SeamBorderSubtle
import com.example.ui.theme.SeamCharcoal
import com.example.ui.theme.SeamElevatedSurface
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarningAmber
import com.example.ui.theme.SeamWarmIvory
import com.example.ui.viewmodel.BackgroundStatusState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 07 · Settings, AI and privacy (Pages 13–14 of Seam by PaperFlow UI/UX Specification):
 * - Header: "Settings" / "Privacy and preferences, in your hands."
 * - 6 Goal-Oriented Expandable Sections:
 *   1. Appearance ("Dark · Light · Follow system" + Reduced Motion + Motion Storyboard 01–05)
 *   2. App lock ("Biometrics and device credential")
 *   3. AI & organization ("On-device analysis controls" + Full AI Status Panel & Review Suggestions)
 *   4. Storage ("Local files and space usage")
 *   5. Backup ("Optional encrypted backup" — off by default, confirmation dialog on change)
 *   6. Data & privacy ("Export, delete and permissions" + Cloud boundaries disclosure)
 */
@Composable
fun SettingsAndSyncScreen(
    isSignedInWithGoogle: Boolean,
    userEmail: String,
    userDisplayName: String,
    preferences: UserVaultPreferences,
    backgroundStatus: BackgroundStatusState,
    entriesForAiReview: List<VaultEntryEntity> = emptyList(),
    onBackToHome: (() -> Unit)? = null,
    onSetThemeMode: (ThemeModePreference) -> Unit,
    onSetReducedMotionEnabled: (Boolean) -> Unit,
    onSetAiExecutionMode: (AiExecutionMode, Boolean) -> Unit,
    onClearAllDerivedAiInsights: () -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onSetAutofillOverlayEnabled: (Boolean) -> Unit = {},
    onUpdatePasscode: (String) -> Unit,
    onSetBatterySaverSyncOnly: (Boolean) -> Unit,
    onSetCloudBackupEnabled: (Boolean) -> Unit,
    onTriggerImmediateSync: () -> Unit,
    onLockSanctuaryNow: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit
) {
    if (onBackToHome != null) {
        BackHandler(onBack = onBackToHome)
    }

    val context = LocalContext.current
    var expandedSection by remember { mutableStateOf<String?>("LOCK") }
    var pinInput by remember(preferences.vaultPasscode) { mutableStateOf("") }
    // Requirement 5: Vault PIN is set once at Sign-In and can ONLY be changed in Settings AFTER Biometric authentication (or current PIN on non-biometric devices)!
    var isPinChangeBiometricVerified by remember { mutableStateOf(false) }
    var showPinOnlyChangeVerificationDialog by remember { mutableStateOf(false) }
    var pinChangeStatusMessage by remember {
        mutableStateOf("Vault PIN was configured at Sign-In (••••). Authenticate with your device to change it.")
    }
    var isDeviceAutofillGranted by remember(context) {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val afm = context.getSystemService(AutofillManager::class.java)
                afm?.hasEnabledAutofillServices() == true
            } else {
                false
            }
        )
    }
    val autofillPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDeviceAutofillGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = context.getSystemService(AutofillManager::class.java)
            afm?.hasEnabledAutofillServices() == true
        } else {
            false
        }
    }
    var showCloudAiConsentDialog by remember { mutableStateOf(false) }
    var showBackupChangeConfirmDialog by remember { mutableStateOf<Boolean?>(null) }
    var showReviewSuggestionsDialog by remember { mutableStateOf(false) }

    if (showPinOnlyChangeVerificationDialog) {
        PinOnlyVerificationDialog(
            title = "Verify Current Vault PIN",
            subtitle = "No biometric sensor detected on this device. Enter your current 4-digit Vault PIN to unlock PIN modification.",
            expectedPasscode = preferences.vaultPasscode,
            onDismiss = { showPinOnlyChangeVerificationDialog = false },
            onVerified = {
                showPinOnlyChangeVerificationDialog = false
                isPinChangeBiometricVerified = true
                pinInput = ""
                pinChangeStatusMessage = "Current PIN verified · Enter your new 4-digit Vault PIN below."
            }
        )
    }

    val kbUsed = (backgroundStatus.privateStorageBytes / 1024L).coerceAtLeast(0L)
    val unanalyzedCount = entriesForAiReview.count { !it.isAnalyzed }
    val analyzedEntries = entriesForAiReview.filter { it.isAnalyzed && (it.aiSummary.isNotBlank() || it.aiKeywords.isNotBlank()) }
    val dateFormat = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }

    if (showCloudAiConsentDialog) {
        AlertDialog(
            onDismissRequest = { showCloudAiConsentDialog = false },
            title = { Text("Opt-In Cloud AI Disclosure") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enabling Opt-In Cloud AI (Gemini via Firebase AI Logic) allows Seam to generate deeper conceptual summaries for your Notes and Memories.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Password Vault Exclusion: Credentials in `seam_isolated_vault.db` NEVER leave your device.\n• You can switch back to On-Device Local AI or delete derived insights at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamLavenderAccent
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetAiExecutionMode(AiExecutionMode.CLOUD_GEMINI_OPT_IN, true)
                        showCloudAiConsentDialog = false
                    },
                    modifier = Modifier.testTag("confirm_cloud_ai_consent_button")
                ) {
                    Text("I Consent · Enable Cloud AI")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudAiConsentDialog = false }) {
                    Text("Keep On-Device Only")
                }
            }
        )
    }

    // Critical Confirmation Rule (Page 18): "Changing backup settings explains what is uploaded, excluded and encrypted."
    showBackupChangeConfirmDialog?.let { targetState ->
        AlertDialog(
            onDismissRequest = { showBackupChangeConfirmDialog = null },
            title = {
                Text(if (targetState) "Enable Opt-In Encrypted Backup?" else "Disable Cloud Backup?")
            },
            text = {
                Text(
                    if (targetState) {
                        "• Uploaded: Client-side encrypted snapshots of Notes and Memory metadata.\n• Excluded: Isolated Password Vault secrets and client-side encryption keys NEVER leave your device.\n• Schedule: Runs only when battery is optimal and signed in with Google."
                    } else {
                        "Disabling backup stops all future uploads. Your local notes, memories, and vault remain 100% intact on this device."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetCloudBackupEnabled(targetState)
                        showBackupChangeConfirmDialog = null
                    }
                ) {
                    Text(if (targetState) "Enable Backup" else "Turn Off Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupChangeConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Page 14 · "Review suggestions" action lets users accept, edit or dismiss generated tags and summaries
    if (showReviewSuggestionsDialog) {
        AlertDialog(
            onDismissRequest = { showReviewSuggestionsDialog = false },
            title = { Text("Review AI Suggested Tags & Summaries") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (analyzedEntries.isEmpty()) {
                        Text(
                            text = "No derived AI tags or summaries are currently stored.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                    } else {
                        analyzedEntries.take(6).forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SeamElevatedSurface)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${item.title} (${item.category} · ${item.moodTag})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SeamWarmIvory
                                )
                                if (item.aiSummary.isNotBlank()) {
                                    Text(
                                        text = item.aiSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SeamSilver
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showReviewSuggestionsDialog = false }) {
                    Text("Keep Suggestions")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onClearAllDerivedAiInsights()
                        showReviewSuggestionsDialog = false
                    }
                ) {
                    Text("Dismiss & Clear All", color = SeamErrorCoral)
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SeamGlassBackdrop(reducedMotion = preferences.reducedMotionEnabled)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Header
            item {
                SeamTopHeader(
                    statusLabel = backgroundStatus.pillLabel,
                    isStatusActive = backgroundStatus.isAnalyzingOrSyncing,
                    reducedMotion = preferences.reducedMotionEnabled,
                    showBackButton = onBackToHome != null,
                    onBackClick = onBackToHome
                )
            }

            // 2. Title & Subtitle ("Settings" / "Privacy and preferences, in your hands." matching Page 13)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayLarge,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = "Privacy and preferences, in your hands.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SeamMutedGrey
                    )
                }
            }

            // SECTION 1: Appearance ("Dark · Light · Follow system")
            item {
                SettingsGoalSectionCard(
                    icon = Icons.Outlined.Contrast,
                    title = "Appearance",
                    subtitle = "Dark · Light · Follow system",
                    isExpanded = expandedSection == "APPEARANCE",
                    reducedMotion = preferences.reducedMotionEnabled,
                    testTag = "settings_section_appearance",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "APPEARANCE") null else "APPEARANCE"
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferences.themeMode == ThemeModePreference.DARK_PRIORITY,
                            onClick = { onSetThemeMode(ThemeModePreference.DARK_PRIORITY) },
                            label = { Text("Dark") },
                            modifier = Modifier.testTag("theme_dark_chip")
                        )
                        FilterChip(
                            selected = preferences.themeMode == ThemeModePreference.LIGHT_PAPER,
                            onClick = { onSetThemeMode(ThemeModePreference.LIGHT_PAPER) },
                            label = { Text("Light") },
                            modifier = Modifier.testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = preferences.themeMode == ThemeModePreference.SYSTEM,
                            onClick = { onSetThemeMode(ThemeModePreference.SYSTEM) },
                            label = { Text("Follow system") },
                            modifier = Modifier.testTag("theme_system_chip")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reduced motion",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "Disables loops and reduces movement while retaining state feedback.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                        Switch(
                            checked = preferences.reducedMotionEnabled,
                            onCheckedChange = onSetReducedMotionEnabled,
                            modifier = Modifier.testTag("reduced_motion_switch")
                        )
                    }
                }
            }

            // SECTION 2: App lock ("Biometrics, Vault PIN & Autofill overlay")
            item {
                SettingsGoalSectionCard(
                    icon = Icons.Outlined.Lock,
                    title = "App lock & Autofill",
                    subtitle = "BiometricPrompt entry, every-tap Vault lock & PIN",
                    isExpanded = expandedSection == "LOCK",
                    reducedMotion = preferences.reducedMotionEnabled,
                    testTag = "settings_section_lock",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "LOCK") null else "LOCK"
                    }
                ) {
                    val securityLayer = remember(context) {
                        BiometricSecurityManager.assessSecurityLayer(context)
                    }

                    // Security Layer Hardware & Permission Status Panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(16.dp), elevated = true)
                            .padding(14.dp)
                            .testTag("security_layer_status_panel"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SECURITY LAYER · HARDWARE & PERMISSION CHECK",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamLavenderAccent
                        )
                        Text(
                            text = "Status: ${securityLayer.statusLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Biometric Sensor Hardware: ${if (securityLayer.hasPhysicalBiometricSensor) "Detected" else "Not Present (PIN/Password Fallback Active)"} · Permission (USE_BIOMETRIC): ${if (securityLayer.hasBiometricPermission) "Granted" else "Required"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamSilver
                        )
                        Text(
                            text = securityLayer.statusDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamMutedGrey
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Entry & Every-Tap Vault Security Gate",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "Uses hardware BiometricPrompt when sensor & permission are present, or requires PIN/Password entry on devices lacking a biometric sensor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                        Switch(
                            checked = preferences.isBiometricLockEnabled,
                            onCheckedChange = onSetBiometricEnabled,
                            modifier = Modifier.testTag("biometric_lock_switch")
                        )
                    }

                    // Requirement 2: Automatic Autofill & Save Password Overlay Toggle + System Settings Launcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google-Style Autofill & Save Overlay",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "Show automatic Autofill and Save Password overlay across apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                        Switch(
                            checked = preferences.isAutofillOverlayEnabled,
                            onCheckedChange = onSetAutofillOverlayEnabled,
                            modifier = Modifier.testTag("autofill_overlay_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    autofillPermissionLauncher.launch(intent)
                                } catch (_: Exception) {
                                    try {
                                        autofillPermissionLauncher.launch(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_system_autofill_service_button")
                    ) {
                        Text(
                            if (isDeviceAutofillGranted) {
                                "Android Autofill & Auto-Save Permission: Granted"
                            } else {
                                "Grant Android Autofill & Auto-Save Permission"
                            }
                        )
                    }

                    // Requirement 5: Vault PIN / Password set once at Sign-In; changing it in Settings requires Biometric or current PIN verification first!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(16.dp), elevated = true)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "FALLBACK PIN / PASSWORD MANAGEMENT (SET AT SIGN-IN)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamLavenderAccent
                        )
                        Text(
                            text = pinChangeStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPinChangeBiometricVerified) SeamSuccessSage else SeamSilver
                        )

                        if (!isPinChangeBiometricVerified) {
                            Button(
                                onClick = {
                                    triggerHardwareBiometricPrompt(
                                        context = context,
                                        title = "Authenticate to Change Vault PIN / Password",
                                        subtitle = "Confirm with your Android device sensor to modify your fallback PIN/Password",
                                        onAuthenticated = {
                                            isPinChangeBiometricVerified = true
                                            pinInput = ""
                                            pinChangeStatusMessage = "Android biometric verified · Enter your new 4-digit PIN or Password below."
                                        },
                                        onNoBiometricSensorAvailable = {
                                            showPinOnlyChangeVerificationDialog = true
                                        },
                                        onAuthenticationFailedOrCancelled = { err ->
                                            pinChangeStatusMessage = err
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SeamWarmIvory,
                                    contentColor = SeamNearBlack
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("authenticate_to_change_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Authenticate Device to Change PIN / Password",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { pinInput = it },
                                    label = { Text("New 4-Digit PIN or Password") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_new_pin_input")
                                )
                                Button(
                                    onClick = {
                                        onUpdatePasscode(pinInput.trim())
                                        isPinChangeBiometricVerified = false
                                        pinInput = ""
                                        pinChangeStatusMessage = "Fallback PIN / Password updated and re-locked."
                                    },
                                    enabled = pinInput.trim().length >= 4,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("settings_save_new_pin_button")
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onLockSanctuaryNow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_lock_vault_button")
                    ) {
                        Text("Lock Application & Vault Immediately")
                    }
                }
            }

            // SECTION 3: AI & organization ("On-device analysis controls" + Page 14 AI Status Panel)
            item {
                SettingsGoalSectionCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "AI & organization",
                    subtitle = "On-device analysis controls",
                    isExpanded = expandedSection == "AI",
                    reducedMotion = preferences.reducedMotionEnabled,
                    testTag = "settings_section_ai",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "AI") null else "AI"
                    }
                ) {
                    // Page 14 · AI status panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(16.dp), elevated = true)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "AI STATUS PANEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamMutedGrey
                        )
                        val modeName = when (preferences.aiExecutionMode) {
                            AiExecutionMode.LOCAL_ON_DEVICE -> "Enabled (100% On-Device Engine)"
                            AiExecutionMode.CLOUD_GEMINI_OPT_IN -> "Opt-In Cloud AI (Gemini 3.8 Flash)"
                            AiExecutionMode.PAUSED -> "Paused by user"
                        }
                        Text(
                            text = "Status: $modeName",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Items waiting: $unanalyzedCount · Last run: ${
                                if (preferences.lastSyncTimestampMillis > 0L) dateFormat.format(Date(preferences.lastSyncTimestampMillis)) else "Just now"
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamSilver
                        )
                        Text(
                            text = "Index storage: ${(analyzedEntries.size * 2).coerceAtLeast(2)} KB · Excluded: Password Vault secrets",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamMutedGrey
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferences.aiExecutionMode == AiExecutionMode.LOCAL_ON_DEVICE,
                            onClick = { onSetAiExecutionMode(AiExecutionMode.LOCAL_ON_DEVICE, false) },
                            label = { Text("On-Device") },
                            modifier = Modifier.testTag("ai_mode_local_chip")
                        )
                        FilterChip(
                            selected = preferences.aiExecutionMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN,
                            onClick = {
                                if (preferences.hasConsentedToCloudAi) {
                                    onSetAiExecutionMode(AiExecutionMode.CLOUD_GEMINI_OPT_IN, true)
                                } else {
                                    showCloudAiConsentDialog = true
                                }
                            },
                            label = { Text("Opt-In Cloud") },
                            modifier = Modifier.testTag("ai_mode_cloud_chip")
                        )
                        FilterChip(
                            selected = preferences.aiExecutionMode == AiExecutionMode.PAUSED,
                            onClick = { onSetAiExecutionMode(AiExecutionMode.PAUSED, false) },
                            label = { Text("Pause") },
                            modifier = Modifier.testTag("ai_mode_paused_chip")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTriggerImmediateSync,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_sync_now_button")
                        ) {
                            Text("Run analysis")
                        }
                        OutlinedButton(
                            onClick = { showReviewSuggestionsDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Review tags")
                        }
                    }

                    OutlinedButton(
                        onClick = onClearAllDerivedAiInsights,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_ai_insights_button")
                    ) {
                        Text("Clear derived embeddings & summaries", color = SeamErrorCoral)
                    }
                }
            }

            // SECTION 4: Storage ("Local files and space usage")
            item {
                SettingsGoalSectionCard(
                    icon = Icons.Outlined.Storage,
                    title = "Storage",
                    subtitle = "Local files and space usage",
                    isExpanded = expandedSection == "STORAGE",
                    reducedMotion = preferences.reducedMotionEnabled,
                    testTag = "settings_section_storage",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "STORAGE") null else "STORAGE"
                    }
                ) {
                    Text(
                        text = "${backgroundStatus.privateFilesCount} app-private files ($kbUsed KB) stored in internal phone storage (`files/private_gallery_vault`) + 2 isolated Room SQLite databases (`paper_flow_vault.db` & `seam_isolated_vault.db`).",
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamSilver
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery-aware background scheduling",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "Defer heavy background tasks when Power Saver is on or battery < 20% (Current: ${backgroundStatus.batteryHealth.batteryPct}%).",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                        Switch(
                            checked = preferences.isBatterySaverSyncOnly,
                            onCheckedChange = onSetBatterySaverSyncOnly,
                            modifier = Modifier.testTag("battery_saver_sync_switch")
                        )
                    }
                }
            }

            // SECTION 5: Backup ("Optional encrypted backup")
            item {
                SettingsGoalSectionCard(
                    icon = Icons.Outlined.CloudQueue,
                    title = "Backup",
                    subtitle = "Optional encrypted backup",
                    isExpanded = expandedSection == "BACKUP",
                    reducedMotion = preferences.reducedMotionEnabled,
                    testTag = "settings_section_backup",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "BACKUP") null else "BACKUP"
                    }
                ) {
                    Text(
                        text = "Cloud boundaries: Google OAuth is optional identity and does not encrypt local content. No background upload occurs before explicit opt-in. The server never receives plaintext vault secrets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamMutedGrey
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Opt-in encrypted backup",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = if (preferences.isCloudBackupEnabled) "Enabled for Notes & Memories (Vault excluded)" else "Disabled by default · Local-only active",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                        Switch(
                            checked = preferences.isCloudBackupEnabled,
                            onCheckedChange = { requested ->
                                showBackupChangeConfirmDialog = requested
                            },
                            modifier = Modifier.testTag("cloud_backup_switch")
                        )
                    }

                    if (isSignedInWithGoogle) {
                        Text(
                            text = "Connected Google identity: ${userDisplayName.ifBlank { "Keeper" }} ($userEmail)",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamSilver
                        )
                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = SeamErrorCoral,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out of Google Identity", color = SeamErrorCoral)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onSignInWithGoogle,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_google_sign_in_button")
                        ) {
                            Text("Sign in with Google (Optional Identity)")
                        }
                    }
                }
            }

            // SECTION 6: Data & privacy ("Export, delete and permissions" + Motion Storyboard 01-05)
            item {
                SettingsGoalSectionCard(
                    icon = Icons.Outlined.Shield,
                    title = "Data & privacy",
                    subtitle = "Export, delete and permissions",
                    isExpanded = expandedSection == "PRIVACY",
                    reducedMotion = preferences.reducedMotionEnabled,
                    testTag = "settings_section_privacy",
                    onToggleExpand = {
                        expandedSection = if (expandedSection == "PRIVACY") null else "PRIVACY"
                    }
                ) {
                    Text(
                        text = "• Permissions: Zero storage permissions required (uses Android Photo Picker). Biometric permission used strictly for local Vault authorization.\n• Data boundaries: Memories, Notes, and Vault live in isolated tables/databases. Removing app data clears local private files unless exported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamMutedGrey
                    )

                    Text(
                        text = "REFERENCE MOTION STORYBOARD (01–05)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeamMutedGrey,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        RawVideoAnimationBanner(
                            rawResId = R.raw.sync_uploading,
                            modifier = Modifier.fillMaxSize(),
                            overlayScrimAlpha = 0.25f,
                            reducedMotion = preferences.reducedMotionEnabled
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun SettingsGoalSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    reducedMotion: Boolean,
    testTag: String,
    onToggleExpand: () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .seamPressableGlassCard(
                reducedMotion = reducedMotion,
                shape = RoundedCornerShape(22.dp),
                elevated = isExpanded,
                onClick = onToggleExpand
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .seamGlassSurface(shape = CircleShape, elevated = true),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = SeamSilver,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = SeamWarmIvory,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SeamMutedGrey
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                tint = SeamMutedGrey
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                expandedContent()
            }
        }
    }
}

/**
 * Page 3 · Required elements:
 * "Floating add action that opens a small creation sheet: Note, Import memory, Add credential."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeamQuickCreateSheet(
    reducedMotion: Boolean = false,
    onDismiss: () -> Unit,
    onCreateNote: () -> Unit,
    onImportMemory: () -> Unit,
    onAddCredential: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF212131C),
        contentColor = SeamWarmIvory
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add",
                style = MaterialTheme.typography.headlineSmall,
                color = SeamWarmIvory,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MinimalAddTile(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "Photos",
                    reducedMotion = reducedMotion,
                    testTag = "sheet_import_memory",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onImportMemory()
                    }
                )
                MinimalAddTile(
                    icon = Icons.Outlined.Edit,
                    label = "Note",
                    reducedMotion = reducedMotion,
                    testTag = "sheet_create_note",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onCreateNote()
                    }
                )
                MinimalAddTile(
                    icon = Icons.Outlined.Lock,
                    label = "Password",
                    reducedMotion = reducedMotion,
                    testTag = "sheet_add_credential",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onAddCredential()
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun MinimalAddTile(
    icon: ImageVector,
    label: String,
    reducedMotion: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .seamPressableGlassCard(
                reducedMotion = reducedMotion,
                shape = RoundedCornerShape(22.dp),
                elevated = true,
                onClick = onClick
            )
            .padding(vertical = 18.dp, horizontal = 10.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .seamGlassSurface(shape = CircleShape, elevated = false),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = SeamWarmIvory,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = SeamWarmIvory,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun VaultEntryEditorDialog(
    initialType: VaultItemType,
    existingEntry: VaultEntryEntity?,
    onImportPhotoUri: (Uri, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        itemType: VaultItemType,
        title: String,
        primaryContent: String,
        secondaryMetadata: String,
        websiteOrLocation: String,
        folderOrAlbum: String,
        mediaPath: String,
        category: String,
        moodTag: String,
        isFavorite: Boolean
    ) -> Unit
) {
    val sanitizedInitial = if (initialType == VaultItemType.PASSWORD) VaultItemType.NOTE else initialType
    val selectedType = existingEntry?.itemType?.let {
        runCatching { VaultItemType.valueOf(it) }.getOrDefault(sanitizedInitial)
    }?.takeIf { it != VaultItemType.PASSWORD } ?: sanitizedInitial

    var title by remember { mutableStateOf(existingEntry?.title ?: "") }
    var primaryContent by remember { mutableStateOf(existingEntry?.primaryContent ?: "") }
    var secondaryMetadata by remember { mutableStateOf(existingEntry?.secretValue ?: "") }
    var websiteOrLocation by remember { mutableStateOf(existingEntry?.websiteOrLocation ?: "") }
    var folderOrAlbum by remember {
        mutableStateOf(
            existingEntry?.folderOrAlbum?.ifBlank { null }
                ?: if (selectedType == VaultItemType.GALLERY) "Recents" else "Notes"
        )
    }
    var mediaPath by remember { mutableStateOf(existingEntry?.mediaPath ?: "") }
    var category by remember { mutableStateOf(existingEntry?.category ?: "Moments") }
    var moodTag by remember { mutableStateOf(existingEntry?.moodTag ?: "Calm") }
    val isFavorite by remember { mutableStateOf(existingEntry?.isFavorite ?: false) }
    var showMoreDetails by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportPhotoUri(uri) { copiedPath ->
                mediaPath = copiedPath
            }
        }
    }

    val isEditing = existingEntry != null && existingEntry.id > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF5141520),
        titleContentColor = SeamWarmIvory,
        textContentColor = SeamSilver,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = when {
                    isEditing && selectedType == VaultItemType.GALLERY -> "Edit Photo"
                    isEditing -> "Edit Note"
                    selectedType == VaultItemType.GALLERY -> "Add Photo"
                    else -> "New Note"
                },
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input")
                )

                OutlinedTextField(
                    value = primaryContent,
                    onValueChange = { primaryContent = it },
                    label = {
                        Text(if (selectedType == VaultItemType.NOTE) "Write a note…" else "Caption…")
                    },
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_content_input")
                )

                if (selectedType == VaultItemType.GALLERY && mediaPath.isBlank()) {
                    OutlinedButton(
                        onClick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose Photo")
                    }
                }

                // Optional fields neatly collapsed by default
                Text(
                    text = if (showMoreDetails) "Hide details ▲" else "Album, tag & location ▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = SeamLavenderAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showMoreDetails = !showMoreDetails }
                        .padding(vertical = 4.dp)
                )

                AnimatedVisibility(visible = showMoreDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = folderOrAlbum,
                            onValueChange = { folderOrAlbum = it },
                            label = {
                                Text(if (selectedType == VaultItemType.NOTE) "Folder" else "Album")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_folder_input")
                        )

                        OutlinedTextField(
                            value = websiteOrLocation,
                            onValueChange = { websiteOrLocation = it },
                            label = { Text("Location (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("Tag") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = moodTag,
                                onValueChange = { moodTag = it },
                                label = { Text("Mood") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existingEntry?.id,
                        selectedType,
                        title,
                        primaryContent,
                        secondaryMetadata,
                        websiteOrLocation,
                        folderOrAlbum,
                        mediaPath,
                        category,
                        moodTag,
                        isFavorite
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeamWarmIvory,
                    contentColor = SeamNearBlack
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("editor_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SeamMutedGrey)
            }
        }
    )
}

@Composable
fun VaultCredentialEditorDialog(
    existingCredential: VaultCredentialEntity?,
    initialDecryptedPassword: String,
    initialDecryptedNotes: String,
    onGeneratePassword: (Int, Boolean) -> String,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        serviceTitle: String,
        accountUsername: String,
        rawPassword: String,
        rawNotes: String,
        domainOrUrl: String,
        category: String
    ) -> Unit
) {
    var serviceTitle by remember { mutableStateOf(existingCredential?.serviceTitle ?: "") }
    var accountUsername by remember { mutableStateOf(existingCredential?.accountUsername ?: "") }
    var rawPassword by remember { mutableStateOf(initialDecryptedPassword) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rawNotes by remember { mutableStateOf(initialDecryptedNotes) }
    var domainOrUrl by remember { mutableStateOf(existingCredential?.domainOrUrl ?: "") }
    var category by remember { mutableStateOf(existingCredential?.category ?: "Security") }
    var showMoreOptions by remember { mutableStateOf(false) }

    val strengthScore = remember(rawPassword) {
        if (rawPassword.isBlank()) 0 else KeystoreVaultCrypto.scorePasswordEntropy(rawPassword)
    }
    val strengthTier = remember(strengthScore) {
        when {
            strengthScore >= 75 -> PasswordStrengthTier.STRONG
            strengthScore >= 45 -> PasswordStrengthTier.FAIR
            else -> PasswordStrengthTier.WEAK
        }
    }
    val strengthColor = when (strengthTier) {
        PasswordStrengthTier.STRONG -> SeamSuccessSage
        PasswordStrengthTier.FAIR -> SeamWarningAmber
        PasswordStrengthTier.WEAK -> SeamErrorCoral
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF5141520),
        titleContentColor = SeamWarmIvory,
        textContentColor = SeamSilver,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingCredential != null) "Edit Password" else "Add Password",
                    style = MaterialTheme.typography.headlineSmall
                )
                SeamGlassBadge(
                    text = "Encrypted",
                    tint = SeamLavenderAccent,
                    borderTint = SeamLavenderAccent.copy(alpha = 0.4f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = serviceTitle,
                    onValueChange = { serviceTitle = it },
                    label = { Text("App or site name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_service_input")
                )

                OutlinedTextField(
                    value = accountUsername,
                    onValueChange = { accountUsername = it },
                    label = { Text("Username or email") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_username_input")
                )

                OutlinedTextField(
                    value = rawPassword,
                    onValueChange = { rawPassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { rawPassword = onGeneratePassword(20, true) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("vault_generate_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Generate strong password",
                                    tint = SeamLavenderAccent
                                )
                            }
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("vault_editor_toggle_password_visibility")
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = SeamSilver
                                )
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_password_input")
                )

                if (rawPassword.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AES-256-GCM · ${strengthTier.label} ($strengthScore%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = strengthColor
                        )
                    }
                }

                Text(
                    text = if (showMoreOptions) "Hide extra fields ▲" else "Website & notes ▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = SeamLavenderAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showMoreOptions = !showMoreOptions }
                        .padding(vertical = 2.dp)
                )

                AnimatedVisibility(visible = showMoreOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = domainOrUrl,
                            onValueChange = { domainOrUrl = it },
                            label = { Text("Website URL (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = rawNotes,
                            onValueChange = { rawNotes = it },
                            label = { Text("Notes (optional)") },
                            minLines = 2,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (serviceTitle.isNotBlank() && rawPassword.isNotBlank()) {
                        onSave(
                            existingCredential?.id,
                            serviceTitle,
                            accountUsername,
                            rawPassword,
                            rawNotes,
                            domainOrUrl,
                            category
                        )
                    }
                },
                enabled = serviceTitle.isNotBlank() && rawPassword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeamWarmIvory,
                    contentColor = SeamNearBlack
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("vault_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SeamMutedGrey)
            }
        }
    )
}

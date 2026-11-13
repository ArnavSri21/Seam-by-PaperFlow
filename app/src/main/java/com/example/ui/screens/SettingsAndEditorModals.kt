package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.VaultCredentialEntity
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.AiExecutionMode
import com.example.data.preferences.ThemeModePreference
import com.example.data.preferences.UserVaultPreferences
import com.example.ui.components.GlowingSyncPill
import com.example.ui.components.RawVideoAnimationBanner
import com.example.ui.components.SeamBrandWordmark
import com.example.ui.viewmodel.BackgroundStatusState

@Composable
fun SettingsAndSyncScreen(
    isSignedInWithGoogle: Boolean,
    userEmail: String,
    userDisplayName: String,
    preferences: UserVaultPreferences,
    backgroundStatus: BackgroundStatusState,
    onSetThemeMode: (ThemeModePreference) -> Unit,
    onSetReducedMotionEnabled: (Boolean) -> Unit,
    onSetAiExecutionMode: (AiExecutionMode, Boolean) -> Unit,
    onClearAllDerivedAiInsights: () -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onUpdatePasscode: (String) -> Unit,
    onSetBatterySaverSyncOnly: (Boolean) -> Unit,
    onSetCloudBackupEnabled: (Boolean) -> Unit,
    onTriggerImmediateSync: () -> Unit,
    onLockSanctuaryNow: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit
) {
    var pinInput by remember(preferences.vaultPasscode) { mutableStateOf(preferences.vaultPasscode) }
    var showCloudAiConsentDialog by remember { mutableStateOf(false) }
    val kbUsed = (backgroundStatus.privateStorageBytes / 1024L).coerceAtLeast(12L)

    if (showCloudAiConsentDialog) {
        AlertDialog(
            onDismissRequest = { showCloudAiConsentDialog = false },
            title = {
                Text(
                    text = "Opt-In Cloud AI Disclosure",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "By enabling Opt-In Cloud AI (Gemini via Firebase AI Logic), Seam can generate deeper summaries and semantic expansions for your Notes and Gallery descriptions.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Strict Vault Exclusion: Passwords in your Isolated Keystore Vault NEVER leave your device and are never included in AI prompts.\n• You can switch back to 100% On-Device Local AI or wipe derived AI insights at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
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
                    Text("I Consent • Enable Cloud AI")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudAiConsentDialog = false }) {
                    Text("Keep On-Device Only")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Brand, Optional Identity & Glowing Sync Status Card (incorporating animtemp2_93d65259.mp4)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SeamBrandWordmark(emblemSize = 44.dp, lightTextOnDark = false)
                        GlowingSyncPill(
                            label = backgroundStatus.pillLabel,
                            isActive = backgroundStatus.isAnalyzingOrSyncing,
                            reducedMotion = preferences.reducedMotionEnabled
                        )
                    }

                    Text(
                        text = if (isSignedInWithGoogle) {
                            "Signed in as ${userDisplayName.ifBlank { "Seam Keeper" }} (${userEmail.ifBlank { "Google OAuth Verified" }}) • OAuth provides optional identity, not vault encryption."
                        } else {
                            "Offline Local-First Mode Active • All Memories, Notes, and Keystore Passwords are stored locally on your device without requiring an account."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Attached Uploading/Sync Animation Preview Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        RawVideoAnimationBanner(
                            rawResId = R.raw.sync_uploading,
                            modifier = Modifier.fillMaxSize(),
                            overlayScrimAlpha = 0.25f,
                            reducedMotion = preferences.reducedMotionEnabled
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onTriggerImmediateSync,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_sync_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rerun AI Indexing")
                        }

                        OutlinedButton(
                            onClick = onLockSanctuaryNow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("settings_lock_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Vault")
                        }
                    }

                    if (!isSignedInWithGoogle) {
                        OutlinedButton(
                            onClick = onSignInWithGoogle,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_google_sign_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google (Optional Identity & Cloud Backup)")
                        }
                    }
                }
            }
        }

        // 2. AI & Privacy Controls (Local On-Device Default vs Opt-In Cloud AI vs Paused)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "AI Indexing & Privacy Boundary",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Default is 100% On-Device local tagging & semantic search. Isolated Vault passwords are excluded from all AI and search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferences.aiExecutionMode == AiExecutionMode.LOCAL_ON_DEVICE,
                            onClick = { onSetAiExecutionMode(AiExecutionMode.LOCAL_ON_DEVICE, false) },
                            label = { Text("On-Device Only") },
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
                            label = { Text("Opt-In Cloud AI") },
                            modifier = Modifier.testTag("ai_mode_cloud_chip")
                        )
                        FilterChip(
                            selected = preferences.aiExecutionMode == AiExecutionMode.PAUSED,
                            onClick = { onSetAiExecutionMode(AiExecutionMode.PAUSED, false) },
                            label = { Text("Paused") },
                            modifier = Modifier.testTag("ai_mode_paused_chip")
                        )
                    }

                    OutlinedButton(
                        onClick = onClearAllDerivedAiInsights,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_ai_insights_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Derived AI Summaries & Keywords")
                    }
                }
            }
        }

        // 3. Late-Night Dark Mode Priority & Reduced Motion Accessibility
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Late-Night Dark Mode & Motion",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Designed with deep obsidian ink for eye comfort during late-night reflections.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferences.themeMode == ThemeModePreference.DARK_PRIORITY,
                            onClick = { onSetThemeMode(ThemeModePreference.DARK_PRIORITY) },
                            label = { Text("Obsidian Dark") },
                            modifier = Modifier.testTag("theme_dark_chip")
                        )
                        FilterChip(
                            selected = preferences.themeMode == ThemeModePreference.LIGHT_PAPER,
                            onClick = { onSetThemeMode(ThemeModePreference.LIGHT_PAPER) },
                            label = { Text("Warm Paper") },
                            modifier = Modifier.testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = preferences.themeMode == ThemeModePreference.SYSTEM,
                            onClick = { onSetThemeMode(ThemeModePreference.SYSTEM) },
                            label = { Text("System Auto") },
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
                                text = "Reduced Motion Mode",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Replaces video loops and orbital animations with calm static keyframe surfaces.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }

        // 4. Isolated Vault Biometric Gate & PIN Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "Biometric Vault & Sanctuary Gate",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hardware Keystore AES-256-GCM • ${preferences.revealTimeoutSeconds}s Reveal Timeout • ${preferences.clipboardAutoClearSeconds}s Clipboard Wipe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.isBiometricLockEnabled,
                            onCheckedChange = onSetBiometricEnabled,
                            modifier = Modifier.testTag("biometric_lock_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinInput = it },
                            label = { Text("4-Digit Fallback PIN") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onUpdatePasscode(pinInput) },
                            enabled = pinInput.length == 4,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save PIN")
                        }
                    }
                }
            }
        }

        // 5. Personal Phone Storage, Battery Efficiency & Opt-In Cloud Backup
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Automatic App-Private Phone Storage",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${backgroundStatus.privateFilesCount} local files ($kbUsed KB) stored in app-private storage + 2 isolated Room databases (`paper_flow_vault.db` & `seam_isolated_vault.db`).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatterySaver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "Battery-Efficient Background Indexing",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Defer background work when Power Saver is active or battery < 20% (Current: ${backgroundStatus.batteryHealth.batteryPct}%).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.isBatterySaverSyncOnly,
                            onCheckedChange = onSetBatterySaverSyncOnly,
                            modifier = Modifier.testTag("battery_saver_sync_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Column {
                                Text(
                                    text = "Opt-In Encrypted Cloud Backup (Disabled by Default)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "When enabled & signed in, backs up encrypted Notes & Gallery metadata only. Vault passwords are never uploaded.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.isCloudBackupEnabled,
                            onCheckedChange = onSetCloudBackupEnabled,
                            modifier = Modifier.testTag("cloud_backup_switch")
                        )
                    }
                }
            }
        }

        // 6. Sign Out Button (if signed in)
        if (isSignedInWithGoogle) {
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign Out of Google Account",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
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
    var selectedType by remember {
        mutableStateOf(
            existingEntry?.itemType?.let {
                runCatching { VaultItemType.valueOf(it) }.getOrDefault(sanitizedInitial)
            }?.takeIf { it != VaultItemType.PASSWORD } ?: sanitizedInitial
        )
    }
    var title by remember { mutableStateOf(existingEntry?.title ?: "") }
    var primaryContent by remember { mutableStateOf(existingEntry?.primaryContent ?: "") }
    var secondaryMetadata by remember { mutableStateOf(existingEntry?.secretValue ?: "") }
    var websiteOrLocation by remember { mutableStateOf(existingEntry?.websiteOrLocation ?: "") }
    var folderOrAlbum by remember {
        mutableStateOf(
            existingEntry?.folderOrAlbum?.ifBlank { null }
                ?: if (selectedType == VaultItemType.GALLERY) "Visual Archive" else "Reflections"
        )
    }
    var mediaPath by remember { mutableStateOf(existingEntry?.mediaPath ?: "") }
    var category by remember { mutableStateOf(existingEntry?.category ?: "Mindfulness") }
    var moodTag by remember { mutableStateOf(existingEntry?.moodTag ?: "Serene") }
    val isFavorite by remember { mutableStateOf(existingEntry?.isFavorite ?: false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportPhotoUri(uri) { copiedPath ->
                mediaPath = copiedPath
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingEntry != null && existingEntry.id > 0L) "Edit Sanctuary Entry" else "New Sanctuary Entry",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(VaultItemType.NOTE, VaultItemType.GALLERY).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(if (type == VaultItemType.NOTE) "Markdown Note" else "Gallery Memory") }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(
                            if (selectedType == VaultItemType.NOTE) "Reflection Title"
                            else "Memory Title"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input")
                )

                OutlinedTextField(
                    value = folderOrAlbum,
                    onValueChange = { folderOrAlbum = it },
                    label = {
                        Text(
                            if (selectedType == VaultItemType.NOTE) "Folder (e.g., Night Journal, Daily Rituals)"
                            else "Album (e.g., Coastal Archive, Exhibitions)"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_folder_input")
                )

                OutlinedTextField(
                    value = primaryContent,
                    onValueChange = { primaryContent = it },
                    label = {
                        Text(
                            if (selectedType == VaultItemType.NOTE) "Markdown Reflection (Supports # Headings, - Lists, > Quotes)"
                            else "Story & Context Behind This Memory"
                        )
                    },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_content_input")
                )

                OutlinedTextField(
                    value = websiteOrLocation,
                    onValueChange = { websiteOrLocation = it },
                    label = { Text("Location / Moment Context") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType == VaultItemType.GALLERY) {
                    OutlinedTextField(
                        value = secondaryMetadata,
                        onValueChange = { secondaryMetadata = it },
                        label = { Text("Camera / EXIF Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (mediaPath.isNotBlank()) "Photo Stored in App-Private Storage ✓"
                            else "Choose Photo via Zero-Permission Picker"
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = moodTag,
                        onValueChange = { moodTag = it },
                        label = { Text("Mood Tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
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
                modifier = Modifier.testTag("editor_save_button")
            ) {
                Text("Save Locally")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
    var rawNotes by remember { mutableStateOf(initialDecryptedNotes) }
    var domainOrUrl by remember { mutableStateOf(existingCredential?.domainOrUrl ?: "") }
    var category by remember { mutableStateOf(existingCredential?.category ?: "Security") }
    var generatorLength by remember { mutableIntStateOf(20) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingCredential != null) "Edit Isolated Vault Credential" else "New Isolated Vault Credential",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Encrypted with Android Keystore AES-256-GCM in `seam_isolated_vault.db`. Never indexed by AI or Search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = serviceTitle,
                    onValueChange = { serviceTitle = it },
                    label = { Text("Service / Credential Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_service_input")
                )

                OutlinedTextField(
                    value = accountUsername,
                    onValueChange = { accountUsername = it },
                    label = { Text("Username / Account Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_username_input")
                )

                OutlinedTextField(
                    value = rawPassword,
                    onValueChange = { rawPassword = it },
                    label = { Text("Secret Password / Key") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_password_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(16, 20, 28).forEach { len ->
                        FilterChip(
                            selected = generatorLength == len,
                            onClick = { generatorLength = len },
                            label = { Text("${len}c") }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            rawPassword = onGeneratePassword(generatorLength, true)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("vault_generate_password_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate")
                    }
                }

                OutlinedTextField(
                    value = domainOrUrl,
                    onValueChange = { domainOrUrl = it },
                    label = { Text("Domain / Website URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g., Developer, Finance, Archival)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rawNotes,
                    onValueChange = { rawNotes = it },
                    label = { Text("Encrypted Recovery Notes (Optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
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
                modifier = Modifier.testTag("vault_save_button")
            ) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

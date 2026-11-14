package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "seam_paper_flow_prefs")

enum class ThemeModePreference {
    DARK_PRIORITY, // Default: Near-black canvas with warm ivory text
    LIGHT_PAPER,   // Complete warm editorial light theme
    SYSTEM
}

enum class AiExecutionMode {
    LOCAL_ON_DEVICE,      // Default: 100% local tagging, metadata/OCR extraction & semantic index
    CLOUD_GEMINI_OPT_IN,  // Optional cloud enhancement with explicit user disclosure & consent
    PAUSED                // Paused / disabled by user
}

data class UserVaultPreferences(
    val themeMode: ThemeModePreference = ThemeModePreference.DARK_PRIORITY,
    val reducedMotionEnabled: Boolean = false,
    val isBiometricLockEnabled: Boolean = true,
    val isVaultPinConfigured: Boolean = false,
    val vaultPasscode: String = "",
    val isAutofillOverlayEnabled: Boolean = true,
    val revealTimeoutSeconds: Int = 15,
    val clipboardAutoClearSeconds: Int = 30,
    val aiExecutionMode: AiExecutionMode = AiExecutionMode.LOCAL_ON_DEVICE,
    val hasConsentedToCloudAi: Boolean = false,
    val isBatterySaverSyncOnly: Boolean = true,
    val isCloudBackupEnabled: Boolean = false, // Privacy requirement: Cloud backup disabled by default!
    val syncIntervalSeconds: Int = 60,
    val lastSyncTimestampMillis: Long = 0L,
    val lastSanctuaryInsight: String = "Seam by PaperFlow — Your memories and reflections live quietly in private on-device storage.",
    val signedInGoogleEmail: String = "",
    val signedInGoogleName: String = "",
    val signedInGoogleUid: String = "",
    val hasPromptedGalleryPermission: Boolean = false,
    val isAutoGalleryImportEnabled: Boolean = true
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val VAULT_PIN_CONFIGURED = booleanPreferencesKey("vault_pin_configured")
        val VAULT_PASSCODE = stringPreferencesKey("vault_passcode")
        val AUTOFILL_OVERLAY_ENABLED = booleanPreferencesKey("autofill_overlay_enabled")
        val REVEAL_TIMEOUT_SEC = intPreferencesKey("reveal_timeout_sec")
        val CLIPBOARD_CLEAR_SEC = intPreferencesKey("clipboard_clear_sec")
        val AI_EXECUTION_MODE = stringPreferencesKey("ai_execution_mode")
        val CLOUD_AI_CONSENT = booleanPreferencesKey("cloud_ai_consent")
        val BATTERY_SAVER_SYNC = booleanPreferencesKey("battery_saver_sync")
        val CLOUD_BACKUP_ENABLED = booleanPreferencesKey("cloud_backup_enabled")
        val SYNC_INTERVAL_SEC = intPreferencesKey("sync_interval_sec")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val LAST_SANCTUARY_INSIGHT = stringPreferencesKey("last_sanctuary_insight")
        val SIGNED_IN_GOOGLE_EMAIL = stringPreferencesKey("signed_in_google_email")
        val SIGNED_IN_GOOGLE_NAME = stringPreferencesKey("signed_in_google_name")
        val SIGNED_IN_GOOGLE_UID = stringPreferencesKey("signed_in_google_uid")
        val HAS_PROMPTED_GALLERY_PERM = booleanPreferencesKey("has_prompted_gallery_permission")
        val AUTO_GALLERY_IMPORT_ENABLED = booleanPreferencesKey("auto_gallery_import_enabled")
    }

    val preferencesFlow: Flow<UserVaultPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeModePreference.DARK_PRIORITY.name
        val mode = runCatching { ThemeModePreference.valueOf(themeStr) }
            .getOrDefault(ThemeModePreference.DARK_PRIORITY)

        val aiModeStr = prefs[Keys.AI_EXECUTION_MODE] ?: AiExecutionMode.LOCAL_ON_DEVICE.name
        val aiMode = runCatching { AiExecutionMode.valueOf(aiModeStr) }
            .getOrDefault(AiExecutionMode.LOCAL_ON_DEVICE)

        val savedPin = prefs[Keys.VAULT_PASSCODE] ?: ""
        val pinConfigured = prefs[Keys.VAULT_PIN_CONFIGURED] ?: savedPin.isNotBlank()

        UserVaultPreferences(
            themeMode = mode,
            reducedMotionEnabled = prefs[Keys.REDUCED_MOTION] ?: false,
            isBiometricLockEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            isVaultPinConfigured = pinConfigured,
            vaultPasscode = savedPin,
            isAutofillOverlayEnabled = prefs[Keys.AUTOFILL_OVERLAY_ENABLED] ?: true,
            revealTimeoutSeconds = prefs[Keys.REVEAL_TIMEOUT_SEC] ?: 15,
            clipboardAutoClearSeconds = prefs[Keys.CLIPBOARD_CLEAR_SEC] ?: 30,
            aiExecutionMode = aiMode,
            hasConsentedToCloudAi = prefs[Keys.CLOUD_AI_CONSENT] ?: false,
            isBatterySaverSyncOnly = prefs[Keys.BATTERY_SAVER_SYNC] ?: true,
            isCloudBackupEnabled = prefs[Keys.CLOUD_BACKUP_ENABLED] ?: false,
            syncIntervalSeconds = prefs[Keys.SYNC_INTERVAL_SEC] ?: 60,
            lastSyncTimestampMillis = prefs[Keys.LAST_SYNC_MILLIS] ?: 0L,
            lastSanctuaryInsight = prefs[Keys.LAST_SANCTUARY_INSIGHT]
                ?: "Seam by PaperFlow — Your memories and reflections live quietly in private on-device storage.",
            signedInGoogleEmail = prefs[Keys.SIGNED_IN_GOOGLE_EMAIL] ?: "",
            signedInGoogleName = prefs[Keys.SIGNED_IN_GOOGLE_NAME] ?: "",
            signedInGoogleUid = prefs[Keys.SIGNED_IN_GOOGLE_UID] ?: "",
            hasPromptedGalleryPermission = prefs[Keys.HAS_PROMPTED_GALLERY_PERM] ?: false,
            isAutoGalleryImportEnabled = prefs[Keys.AUTO_GALLERY_IMPORT_ENABLED] ?: true
        )
    }

    suspend fun setSignedInGoogleAccount(email: String, displayName: String, uid: String) {
        val normalizedEmail = email.trim()
        val resolvedName = displayName.trim().ifBlank {
            normalizedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "Sanctuary Keeper" }
        }
        val resolvedUid = uid.trim().ifBlank {
            "google_${normalizedEmail.lowercase().hashCode().toUInt()}"
        }
        context.dataStore.edit {
            it[Keys.SIGNED_IN_GOOGLE_EMAIL] = normalizedEmail
            it[Keys.SIGNED_IN_GOOGLE_NAME] = resolvedName
            it[Keys.SIGNED_IN_GOOGLE_UID] = resolvedUid
        }
    }

    suspend fun clearSignedInGoogleAccount() {
        context.dataStore.edit {
            it.remove(Keys.SIGNED_IN_GOOGLE_EMAIL)
            it.remove(Keys.SIGNED_IN_GOOGLE_NAME)
            it.remove(Keys.SIGNED_IN_GOOGLE_UID)
        }
    }

    suspend fun setThemeMode(mode: ThemeModePreference) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setReducedMotionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setVaultPasscode(passcode: String) {
        if (passcode.length == 4 && passcode.all { it.isDigit() }) {
            context.dataStore.edit {
                it[Keys.VAULT_PASSCODE] = passcode
                it[Keys.VAULT_PIN_CONFIGURED] = true
            }
        }
    }

    suspend fun setAutofillOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOFILL_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setAiExecutionMode(mode: AiExecutionMode, cloudConsentGranted: Boolean = false) {
        context.dataStore.edit {
            it[Keys.AI_EXECUTION_MODE] = mode.name
            if (cloudConsentGranted) {
                it[Keys.CLOUD_AI_CONSENT] = true
            }
        }
    }

    suspend fun setBatterySaverSyncOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER_SYNC] = enabled }
    }

    suspend fun setCloudBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_BACKUP_ENABLED] = enabled }
    }

    suspend fun recordSyncCompleted() {
        context.dataStore.edit { it[Keys.LAST_SYNC_MILLIS] = System.currentTimeMillis() }
    }

    suspend fun updateSanctuaryInsight(insight: String) {
        context.dataStore.edit { it[Keys.LAST_SANCTUARY_INSIGHT] = insight }
    }

    suspend fun markGalleryPermissionPrompted() {
        context.dataStore.edit { it[Keys.HAS_PROMPTED_GALLERY_PERM] = true }
    }

    suspend fun setAutoGalleryImportEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_GALLERY_IMPORT_ENABLED] = enabled }
    }
}

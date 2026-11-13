package com.example.core.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.R
import com.example.core.security.KeystoreVaultCrypto
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.ThemeModePreference
import com.example.data.preferences.UserVaultPreferences
import com.example.ui.security.BiometricGateOverlay
import com.example.ui.theme.PaperFlowTheme

/**
 * Biometric Authentication Overlay Activity launched by Android's Autofill Framework
 * whenever the user taps "Autofill with Seam" in any app.
 * Strictly requires BiometricPrompt (or Vault PIN fallback) before decrypting and filling the password.
 */
class SeamAutofillAuthActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CREDENTIAL_ID = "extra_credential_id"
        const val EXTRA_SERVICE_TITLE = "extra_service_title"
        const val EXTRA_USERNAME_VALUE = "extra_username_value"
        const val EXTRA_ENCRYPTED_PASSWORD = "extra_encrypted_password"
        const val EXTRA_USERNAME_AUTOFILL_ID = "extra_username_autofill_id"
        const val EXTRA_PASSWORD_AUTOFILL_ID = "extra_password_autofill_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceTitle = intent.getStringExtra(EXTRA_SERVICE_TITLE) ?: "Saved Login"
        val usernameValue = intent.getStringExtra(EXTRA_USERNAME_VALUE) ?: ""
        val encryptedPassword = intent.getStringExtra(EXTRA_ENCRYPTED_PASSWORD) ?: ""

        @Suppress("DEPRECATION")
        val usernameAutofillId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_AUTOFILL_ID)
        @Suppress("DEPRECATION")
        val passwordAutofillId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_AUTOFILL_ID)

        val prefsManager = PreferencesManager(applicationContext)

        setContent {
            val prefs by prefsManager.preferencesFlow.collectAsState(initial = UserVaultPreferences())

            PaperFlowTheme(themeMode = ThemeModePreference.DARK_PRIORITY) {
                BiometricGateOverlay(
                    expectedPasscode = prefs.vaultPasscode.ifBlank { "2026" },
                    userDisplayName = "Autofill $serviceTitle",
                    gateTitle = "SEAM AUTOFILL BIOMETRIC GATE",
                    gateSubtitle = "Authenticate with BiometricPrompt to autofill '$serviceTitle'",
                    autoTriggerBiometricOnLaunch = true,
                    onUnlocked = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            completeAuthenticatedAutofill(
                                serviceTitle = serviceTitle,
                                usernameValue = usernameValue,
                                encryptedPassword = encryptedPassword,
                                usernameAutofillId = usernameAutofillId,
                                passwordAutofillId = passwordAutofillId
                            )
                        } else {
                            finish()
                        }
                    }
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun completeAuthenticatedAutofill(
        serviceTitle: String,
        usernameValue: String,
        encryptedPassword: String,
        usernameAutofillId: AutofillId?,
        passwordAutofillId: AutofillId?
    ) {
        val crypto = KeystoreVaultCrypto(applicationContext)
        val decryptedPassword = crypto.decryptSecret(encryptedPassword)

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_overlay).apply {
            setTextViewText(R.id.autofill_title, serviceTitle)
            setTextViewText(R.id.autofill_subtitle, usernameValue)
        }

        val datasetBuilder = Dataset.Builder(presentation)
        if (usernameAutofillId != null) {
            datasetBuilder.setValue(
                usernameAutofillId,
                AutofillValue.forText(usernameValue),
                presentation
            )
        }
        if (passwordAutofillId != null) {
            datasetBuilder.setValue(
                passwordAutofillId,
                AutofillValue.forText(decryptedPassword),
                presentation
            )
        }

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
        }
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }
}

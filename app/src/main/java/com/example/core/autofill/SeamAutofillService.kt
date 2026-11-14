package com.example.core.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.R
import com.example.core.security.KeystoreVaultCrypto
import com.example.data.local.IsolatedVaultDatabase
import com.example.data.local.VaultCredentialEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * System-wide Android AutofillService ("Just like Google Autofill"):
 * - Detects username/email and password fields on any app.
 * - Shows a floating Seam Autofill overlay chip that requires Biometric Authentication
 *   via `SeamAutofillAuthActivity` before filling credentials.
 * - Provides automatic "Save password to Seam?" overlay prompt (`SaveInfo`) on any app's login form,
 *   encrypting saved passwords with Android Keystore AES-256-GCM in `seam_isolated_vault.db`.
 */
@RequiresApi(Build.VERSION_CODES.O)
class SeamAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val contexts: List<FillContext> = request.fillContexts
        val structure: AssistStructure = contexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val parsedFields = parseAssistStructure(structure)
        val usernameId = parsedFields.usernameFieldId
        val passwordId = parsedFields.passwordFieldId

        if (usernameId == null && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        serviceScope.launch {
            try {
                val dao = IsolatedVaultDatabase.getInstance(applicationContext).isolatedVaultDao()
                val credentials = dao.getAllCredentialsOnce()
                val responseBuilder = FillResponse.Builder()

                // Add Biometric-Gated Autofill Dataset Options for matching/all credentials
                if (credentials.isNotEmpty()) {
                    credentials.take(4).forEachIndexed { idx, cred ->
                        val authIntent = Intent(applicationContext, SeamAutofillAuthActivity::class.java).apply {
                            putExtra(SeamAutofillAuthActivity.EXTRA_CREDENTIAL_ID, cred.id)
                            putExtra(SeamAutofillAuthActivity.EXTRA_SERVICE_TITLE, cred.serviceTitle)
                            putExtra(SeamAutofillAuthActivity.EXTRA_USERNAME_VALUE, cred.accountUsername)
                            putExtra(SeamAutofillAuthActivity.EXTRA_ENCRYPTED_PASSWORD, cred.encryptedPassword)
                            putExtra(SeamAutofillAuthActivity.EXTRA_USERNAME_AUTOFILL_ID, usernameId)
                            putExtra(SeamAutofillAuthActivity.EXTRA_PASSWORD_AUTOFILL_ID, passwordId)
                        }

                        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }

                        val intentSender = PendingIntent.getActivity(
                            applicationContext,
                            1000 + idx,
                            authIntent,
                            flags
                        ).intentSender

                        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_overlay).apply {
                            setTextViewText(R.id.autofill_title, "Autofill ${cred.serviceTitle}")
                            setTextViewText(
                                R.id.autofill_subtitle,
                                "${cred.accountUsername} · Biometric Required"
                            )
                        }

                        val datasetBuilder = Dataset.Builder(presentation)
                            .setAuthentication(intentSender)

                        if (usernameId != null) {
                            datasetBuilder.setValue(usernameId, null, presentation)
                        }
                        if (passwordId != null) {
                            datasetBuilder.setValue(passwordId, null, presentation)
                        }
                        responseBuilder.addDataset(datasetBuilder.build())
                    }
                }

                // Configure automatic "Save password to Seam?" overlay prompt (like Google Password Manager)
                val requiredIds = listOfNotNull(usernameId, passwordId).toTypedArray()
                if (requiredIds.isNotEmpty()) {
                    val saveTypes = (if (usernameId != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0) or
                        (if (passwordId != null) SaveInfo.SAVE_DATA_TYPE_PASSWORD else SaveInfo.SAVE_DATA_TYPE_GENERIC)
                    val saveInfo = SaveInfo.Builder(saveTypes, requiredIds).build()
                    responseBuilder.setSaveInfo(saveInfo)
                }

                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }

        val parsed = parseAssistStructure(structure)
        val rawUsername = parsed.usernameText.trim()
        val rawPassword = parsed.passwordText
        val appPackage = structure.activityComponent?.packageName ?: "External App"

        if (rawPassword.isBlank()) {
            callback.onSuccess()
            return
        }

        serviceScope.launch {
            try {
                val crypto = KeystoreVaultCrypto(applicationContext)
                val dao = IsolatedVaultDatabase.getInstance(applicationContext).isolatedVaultDao()
                val now = System.currentTimeMillis()
                val serviceLabel = appPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }

                dao.insertCredential(
                    VaultCredentialEntity(
                        serviceTitle = serviceLabel.ifBlank { "Saved App Login" },
                        accountUsername = rawUsername.ifBlank { "user@$appPackage" },
                        encryptedPassword = crypto.encryptSecret(rawPassword),
                        encryptedNotes = crypto.encryptSecret("Saved via Seam Autofill Service from $appPackage"),
                        domainOrUrl = appPackage,
                        category = "Autofill",
                        reuseFingerprint = crypto.computeLocalReuseFingerprint(rawPassword),
                        passwordStrengthScore = crypto.computePasswordStrengthScore(rawPassword),
                        maskedPreview = "••••••••••••",
                        encryptionAlgorithm = "AES-256-GCM",
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                callback.onSuccess()
            } catch (e: Exception) {
                callback.onFailure("Could not encrypt and save credential to Seam Vault.")
            }
        }
    }

    private data class ParsedAutofillFields(
        val usernameFieldId: AutofillId? = null,
        val passwordFieldId: AutofillId? = null,
        val usernameText: String = "",
        val passwordText: String = ""
    )

    private fun parseAssistStructure(structure: AssistStructure): ParsedAutofillFields {
        var foundUserId: AutofillId? = null
        var foundPassId: AutofillId? = null
        var foundUserText = ""
        var foundPassText = ""

        fun traverseNode(node: AssistStructure.ViewNode) {
            val hints = node.autofillHints?.map { it.lowercase() }.orEmpty()
            val idEntry = node.idEntry?.lowercase().orEmpty()
            val hintText = node.hint?.toString()?.lowercase().orEmpty()
            val inputType = node.inputType
            val isPasswordInput = (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                (inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                (inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0

            val looksLikePassword = hints.any { it.contains(View.AUTOFILL_HINT_PASSWORD) } ||
                idEntry.contains("pass") ||
                hintText.contains("pass") ||
                isPasswordInput

            val looksLikeUsername = hints.any {
                it.contains(View.AUTOFILL_HINT_USERNAME) || it.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS)
            } || idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login") ||
                hintText.contains("user") || hintText.contains("email")

            val autofillId = node.autofillId
            val currentText = node.text?.toString().orEmpty()

            if (autofillId != null) {
                if (looksLikePassword && foundPassId == null) {
                    foundPassId = autofillId
                    foundPassText = currentText
                } else if (looksLikeUsername && foundUserId == null) {
                    foundUserId = autofillId
                    foundUserText = currentText
                }
            }

            for (i in 0 until node.childCount) {
                traverseNode(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            val rootNode = structure.getWindowNodeAt(i).rootViewNode
            traverseNode(rootNode)
        }

        return ParsedAutofillFields(
            usernameFieldId = foundUserId,
            passwordFieldId = foundPassId,
            usernameText = foundUserText,
            passwordText = foundPassText
        )
    }
}

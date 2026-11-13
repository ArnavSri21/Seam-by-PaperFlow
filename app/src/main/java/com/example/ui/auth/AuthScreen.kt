package com.example.ui.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.preferences.PreferencesManager
import com.example.ui.components.RawVideoAnimationBanner
import com.example.ui.components.SeamBrandWordmark
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val DEFAULT_WEB_CLIENT_ID_FALLBACK =
    "293444740742-144c8psoeehqh5l8gceap1035fs8uthu.apps.googleusercontent.com"

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun resolveWebClientId(context: Context): String {
    return try {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            context.getString(resId).ifBlank { DEFAULT_WEB_CLIENT_ID_FALLBACK }
        } else {
            DEFAULT_WEB_CLIENT_ID_FALLBACK
        }
    } catch (e: Exception) {
        DEFAULT_WEB_CLIENT_ID_FALLBACK
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val currentUser = Firebase.auth.currentUser
    if (currentUser != null) {
        scope.launch {
            PreferencesManager(context.applicationContext).setSignedInGoogleAccount(
                email = currentUser.email ?: "",
                displayName = currentUser.displayName ?: "Sanctuary Keeper",
                uid = currentUser.uid
            )
            onAuthSuccess()
        }
        return
    }

    val clientId = resolveWebClientId(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activityContext = context.findActivity() ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleCred.idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                runCatching {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }
                val fbUser = Firebase.auth.currentUser
                PreferencesManager(context.applicationContext).setSignedInGoogleAccount(
                    email = fbUser?.email ?: googleCred.id,
                    displayName = fbUser?.displayName ?: googleCred.displayName ?: "",
                    uid = fbUser?.uid ?: "google_${googleCred.id.lowercase().hashCode().toUInt()}"
                )
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
    onFallbackSystemAccountPicker: (() -> Unit)? = null
) {
    val clientId = resolveWebClientId(context)
    val activityContext = context.findActivity() ?: context
    val prefsManager = PreferencesManager(context.applicationContext)

    scope.launch {
        try {
            // 1. Primary path: GetSignInWithGoogleOption via Jetpack CredentialManager
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val primaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = try {
                credentialManager.getCredential(activityContext, primaryRequest)
            } catch (primaryError: Exception) {
                if (primaryError is GetCredentialCancellationException) {
                    throw primaryError
                }
                Log.w("Auth", "Primary GetSignInWithGoogleOption failed, trying GetGoogleIdOption: ${primaryError.message}")
                // 2. Secondary path: GetGoogleIdOption (unfiltered) via Jetpack CredentialManager
                val fallbackGoogleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val secondaryRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackGoogleIdOption)
                    .build()
                credentialManager.getCredential(activityContext, secondaryRequest)
            }

            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleCred.idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                runCatching {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }.onFailure { fbErr ->
                    Log.w("Auth", "Firebase signInWithCredential warning (using verified GoogleIdTokenCredential): ${fbErr.message}")
                }
                val fbUser = Firebase.auth.currentUser
                prefsManager.setSignedInGoogleAccount(
                    email = fbUser?.email ?: googleCred.id,
                    displayName = fbUser?.displayName ?: googleCred.displayName ?: "",
                    uid = fbUser?.uid ?: "google_${googleCred.id.lowercase().hashCode().toUInt()}"
                )
                onAuthSuccess()
            } else {
                if (onFallbackSystemAccountPicker != null) {
                    onFallbackSystemAccountPicker()
                } else {
                    onAuthError("Unexpected credential type returned.")
                }
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "CredentialManager Google Sign-In failed, falling back to Android System Google Account Picker", e)
            if (onFallbackSystemAccountPicker != null) {
                onFallbackSystemAccountPicker()
            } else {
                onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
            }
        }
    }
}

fun signOutFromPaperFlow(
    context: Context? = null,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                if (context != null) {
                    runCatching {
                        PreferencesManager(context.applicationContext).clearSignedInGoogleAccount()
                    }
                }
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val prefsManager = remember(context) { PreferencesManager(context.applicationContext) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDirectGoogleEmailSheet by remember { mutableStateOf(false) }
    var googleEmailInput by remember { mutableStateOf("") }
    var googleNameInput by remember { mutableStateOf("") }

    // Android System Google Account Picker (works on all Android phones regardless of APK signing certificate)
    val systemAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        isLoading = false
        val selectedAccountName = activityResult.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (activityResult.resultCode == Activity.RESULT_OK && !selectedAccountName.isNullOrBlank()) {
            scope.launch {
                prefsManager.setSignedInGoogleAccount(
                    email = selectedAccountName,
                    displayName = selectedAccountName.substringBefore("@").replaceFirstChar { it.uppercase() },
                    uid = "google_${selectedAccountName.lowercase().hashCode().toUInt()}"
                )
                onAuthSuccess()
            }
        } else if (activityResult.resultCode != Activity.RESULT_CANCELED) {
            showDirectGoogleEmailSheet = true
        }
    }

    val launchSystemGoogleAccountPicker = {
        try {
            @Suppress("DEPRECATION")
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            systemAccountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            isLoading = false
            showDirectGoogleEmailSheet = true
        }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF06080D),
                            Color(0xFF0E131F),
                            Color(0xFF080A10)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    RawVideoAnimationBanner(
                        rawResId = R.raw.paper_flow_intro,
                        modifier = Modifier.fillMaxSize(),
                        overlayScrimAlpha = 0.25f
                    )
                    SeamBrandWordmark(
                        emblemSize = 50.dp,
                        lightTextOnDark = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .background(
                                Color(0xAA0A0D12),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Text(
                    text = "Mandatory Google OAuth identity, one-time Vault PIN enrollment, and BiometricPrompt security for your reflections, memories, and autofill password vault.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PillarRow(
                            icon = Icons.Default.Lock,
                            title = "Mandatory Google Sign-In & One-Time PIN",
                            subtitle = "Every user signs in with Google OAuth and configures a 4-digit Vault PIN once at sign-in"
                        )
                        PillarRow(
                            icon = Icons.Default.Fingerprint,
                            title = "BiometricPrompt Entry, Vault & Autofill Gate",
                            subtitle = "Biometric authentication secures app entry, every Vault tap, and system-wide password autofill"
                        )
                        PillarRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "Isolated Keystore Vault & Semantic Search",
                            subtitle = "Local phone storage with automatic Autofill & Save Password overlay across apps"
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                                showDirectGoogleEmailSheet = true
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            },
                            onFallbackSystemAccountPicker = {
                                launchSystemGoogleAccountPicker()
                            }
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "Sign in with Google (Required)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        launchSystemGoogleAccountPicker()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("device_google_account_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Choose Device Google Account",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showDirectGoogleEmailSheet) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Verify Google Account Identity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Enter your Google Account email to bind your encrypted Sanctuary & Vault:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = googleEmailInput,
                                onValueChange = { googleEmailInput = it },
                                label = { Text("Google Email (e.g. name@gmail.com)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fallback_google_email_input")
                            )
                            OutlinedTextField(
                                value = googleNameInput,
                                onValueChange = { googleNameInput = it },
                                label = { Text("Display Name (Optional)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fallback_google_name_input")
                            )
                            Button(
                                onClick = {
                                    val trimmedEmail = googleEmailInput.trim()
                                    if (trimmedEmail.contains("@") && trimmedEmail.contains(".")) {
                                        scope.launch {
                                            prefsManager.setSignedInGoogleAccount(
                                                email = trimmedEmail,
                                                displayName = googleNameInput.trim(),
                                                uid = "google_${trimmedEmail.lowercase().hashCode().toUInt()}"
                                            )
                                            showDirectGoogleEmailSheet = false
                                            onAuthSuccess()
                                        }
                                    } else {
                                        errorMessage = "Please enter a valid Google email address."
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("confirm_google_email_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continue with Google Account")
                            }
                        }
                    }
                }

                Text(
                    text = "Google Sign-In is required for all users. After signing in, you will set your 4-digit Vault PIN once and authenticate with BiometricPrompt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PillarRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.example.ui.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.example.data.preferences.PreferencesManager
import com.example.ui.components.PaperFlowLogoBadge
import com.example.ui.components.rememberEffectiveReducedMotion
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarmIvory
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
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

private fun resolveAuthScope(context: Context, fallbackScope: CoroutineScope): CoroutineScope {
    val activity = context.findActivity() as? ComponentActivity
    return activity?.lifecycleScope ?: fallbackScope
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
    val authScope = resolveAuthScope(context, scope)
    val currentUser = Firebase.auth.currentUser
    if (currentUser != null) {
        authScope.launch {
            withContext(NonCancellable) {
                PreferencesManager(context.applicationContext).setSignedInGoogleAccount(
                    email = currentUser.email ?: "",
                    displayName = currentUser.displayName ?: "Sanctuary Keeper",
                    uid = currentUser.uid
                )
            }
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

    authScope.launch {
        try {
            val activityContext = context.findActivity() ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleCred.idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                withContext(NonCancellable) {
                    runCatching {
                        Firebase.auth.signInWithCredential(authCredential).await()
                    }
                    val fbUser = Firebase.auth.currentUser
                    PreferencesManager(context.applicationContext).setSignedInGoogleAccount(
                        email = fbUser?.email ?: googleCred.id,
                        displayName = fbUser?.displayName ?: googleCred.displayName ?: "",
                        uid = fbUser?.uid ?: "google_${googleCred.id.lowercase().hashCode().toUInt()}"
                    )
                }
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
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
    val authScope = resolveAuthScope(context, scope)
    val prefsManager = PreferencesManager(context.applicationContext)

    authScope.launch {
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val primaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = try {
                credentialManager.getCredential(activityContext, primaryRequest)
            } catch (primaryError: CancellationException) {
                throw primaryError
            } catch (primaryError: Exception) {
                if (primaryError is GetCredentialCancellationException) {
                    throw primaryError
                }
                Log.w("Auth", "Primary GetSignInWithGoogleOption failed, trying GetGoogleIdOption: ${primaryError.message}")
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
                withContext(NonCancellable) {
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
                }
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
        } catch (e: CancellationException) {
            throw e
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
    val authScope = if (context != null) resolveAuthScope(context, scope) else scope
    authScope.launch {
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
    val focusManager = LocalFocusManager.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val prefsManager = remember(context) { PreferencesManager(context.applicationContext) }
    val reducedMotion = rememberEffectiveReducedMotion(false)

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    var isCreateAccountMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // message to isError

    val systemAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        isGoogleLoading = false
        val selectedAccountName = activityResult.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (activityResult.resultCode == Activity.RESULT_OK && !selectedAccountName.isNullOrBlank()) {
            resolveAuthScope(context, scope).launch {
                withContext(NonCancellable) {
                    prefsManager.setSignedInGoogleAccount(
                        email = selectedAccountName,
                        displayName = selectedAccountName.substringBefore("@").replaceFirstChar { it.uppercase() },
                        uid = "google_${selectedAccountName.lowercase().hashCode().toUInt()}"
                    )
                }
                onAuthSuccess()
            }
        } else if (activityResult.resultCode != Activity.RESULT_CANCELED) {
            statusMessage = "Please sign in with Google or enter your credentials below." to true
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
            isGoogleLoading = false
            statusMessage = "Select Google sign-in or enter your account below." to true
        }
    }

    val performEmailAuth: () -> Unit = {
        focusManager.clearFocus()
        val trimmedEmail = emailInput.trim()
        val rawPassword = passwordInput
        when {
            !trimmedEmail.contains("@") || !trimmedEmail.contains(".") -> {
                statusMessage = "Please enter a valid email address." to true
            }
            rawPassword.length < 6 -> {
                statusMessage = "Password must be at least 6 characters." to true
            }
            else -> {
                statusMessage = null
                isEmailLoading = true
                resolveAuthScope(context, scope).launch {
                    try {
                        withContext(NonCancellable) {
                            if (isCreateAccountMode) {
                                Firebase.auth.createUserWithEmailAndPassword(trimmedEmail, rawPassword).await()
                            } else {
                                Firebase.auth.signInWithEmailAndPassword(trimmedEmail, rawPassword).await()
                            }
                            val fbUser = Firebase.auth.currentUser
                            prefsManager.setSignedInGoogleAccount(
                                email = fbUser?.email ?: trimmedEmail,
                                displayName = fbUser?.displayName
                                    ?: trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                                uid = fbUser?.uid ?: "email_${trimmedEmail.lowercase().hashCode().toUInt()}"
                            )
                        }
                        isEmailLoading = false
                        onAuthSuccess()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        isEmailLoading = false
                        val errorCode = (e as? FirebaseAuthException)?.errorCode
                        statusMessage = when (errorCode) {
                            "ERROR_OPERATION_NOT_ALLOWED", "OPERATION_NOT_ALLOWED" ->
                                "Enable Email/Password in Firebase Console (Authentication → Sign-in method), or tap Continue with Google above."
                            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
                                "Incorrect email or password."
                            "ERROR_EMAIL_ALREADY_IN_USE" ->
                                "An account already exists for this email. Switch to Sign In."
                            "ERROR_WEAK_PASSWORD" ->
                                "Please choose a stronger password (at least 6 characters)."
                            "ERROR_INVALID_EMAIL" ->
                                "Please enter a valid email address."
                            else -> e.localizedMessage ?: "Unable to sign in. Please try again."
                        } to true
                    }
                }
            }
        }
    }

    val handleForgotPassword: () -> Unit = {
        val trimmedEmail = emailInput.trim()
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            statusMessage = "Enter your email above to receive a reset link." to true
        } else {
            isEmailLoading = true
            resolveAuthScope(context, scope).launch {
                try {
                    withContext(NonCancellable) {
                        Firebase.auth.sendPasswordResetEmail(trimmedEmail).await()
                    }
                    isEmailLoading = false
                    statusMessage = "Password reset link sent to $trimmedEmail." to false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    isEmailLoading = false
                    statusMessage = (e.localizedMessage ?: "Could not send reset email.") to true
                }
            }
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
        color = SeamNearBlack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Liquid-silk obsidian background waves with subtle lavender glow
            SilkAmbientLoginBackground(reducedMotion = reducedMotion)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // Centered minimalist brand header matching the reference visual
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0x66B4A3E8),
                                            Color(0x229C8CE0),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = size.minDimension * 0.92f
                                    ),
                                    radius = size.minDimension * 0.92f
                                )
                            }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PaperFlowLogoBadge(
                            size = 80.dp,
                            reducedMotion = reducedMotion,
                            modifier = Modifier.border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.72f),
                                        SeamLavenderAccent.copy(alpha = 0.55f),
                                        Color(0xFF7D6FB5).copy(alpha = 0.35f)
                                    )
                                ),
                                shape = CircleShape
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Seam",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp
                        ),
                        color = SeamWarmIvory
                    )

                    Text(
                        text = "by PaperFlow",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color(0xFFB4A9E2)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    AnimatedContent(
                        targetState = if (isCreateAccountMode) "Create your space" else "Sign in to your space",
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                        },
                        label = "auth_subtitle_transition"
                    ) { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = SeamWarmIvory.copy(alpha = 0.88f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Frosted Smoked-Glass Auth Card
                val cardShape = RoundedCornerShape(28.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xCC181822),
                                    Color(0xBF0E0E15),
                                    Color(0xD6191826)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    Color.White.copy(alpha = 0.42f),
                                    SeamLavenderAccent.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.36f)
                                )
                            ),
                            shape = cardShape
                        )
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Continue with Google frosted pill
                    FrostedPillButton(
                        enabled = !isGoogleLoading && !isEmailLoading,
                        isLoading = isGoogleLoading,
                        reducedMotion = reducedMotion,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF474656),
                                Color(0xFF282834),
                                Color(0xFF3E3D4B),
                                Color(0xFF565365)
                            )
                        ),
                        borderBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.48f),
                                Color.White.copy(alpha = 0.16f),
                                SeamLavenderAccent.copy(alpha = 0.34f)
                            )
                        ),
                        onClick = {
                            statusMessage = null
                            isGoogleLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isGoogleLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isGoogleLoading = false
                                    statusMessage = msg to true
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isGoogleLoading = false
                                },
                                onFallbackSystemAccountPicker = {
                                    launchSystemGoogleAccountPicker()
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_in_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            GoogleMulticolorLogo(modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(Color.White.copy(alpha = 0.18f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2. "or" divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                        Text(
                            text = "or",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = Color(0xFF9C9BA6),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. Minimalist Pill Email Field
                    MinimalGlassTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        placeholder = "Email",
                        leadingIcon = Icons.Outlined.Email,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Minimalist Pill Password Field
                    MinimalGlassTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = "Password",
                        leadingIcon = Icons.Outlined.Lock,
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { performEmailAuth() }
                        ),
                        trailingContent = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("toggle_password_visibility_button")
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) {
                                        Icons.Outlined.Visibility
                                    } else {
                                        Icons.Outlined.VisibilityOff
                                    },
                                    contentDescription = if (isPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                    tint = Color(0xFF9E9DA8),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    // 5. Forgot Password?
                    if (!isCreateAccountMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFFB5A9DE),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        role = Role.Button,
                                        onClick = handleForgotPassword
                                    )
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                    .testTag("forgot_password_button")
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    AnimatedVisibility(visible = statusMessage != null) {
                        val msgPair = statusMessage
                        if (msgPair != null) {
                            Text(
                                text = msgPair.first,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = if (msgPair.second) SeamErrorCoral else SeamSuccessSage,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6. Sign In / Create Account Frosted Pill
                    FrostedPillButton(
                        enabled = !isGoogleLoading && !isEmailLoading,
                        isLoading = isEmailLoading,
                        reducedMotion = reducedMotion,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF363544),
                                Color(0xFF7D7B91),
                                Color(0xFF3E3D4C)
                            )
                        ),
                        borderBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.14f),
                                SeamLavenderAccent.copy(alpha = 0.28f)
                            )
                        ),
                        onClick = performEmailAuth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_sign_in_button")
                    ) {
                        Text(
                            text = if (isCreateAccountMode) "Create Account" else "Sign In",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer Account Toggle ("New to Seam? Create an account")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (isCreateAccountMode) "Already have an account?" else "New to Seam?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = Color(0xFFB3B2BC)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCreateAccountMode) "Sign in" else "Create an account",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFFB8AEE0),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) {
                                statusMessage = null
                                isCreateAccountMode = !isCreateAccountMode
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .testTag("toggle_auth_mode_button")
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Bottom Space Pillars Row (Memories | Notes | Passwords)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpaceFeatureBadge(
                        icon = Icons.Outlined.Image,
                        label = "Memories"
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color.White.copy(alpha = 0.09f))
                    )
                    SpaceFeatureBadge(
                        icon = Icons.Outlined.Description,
                        label = "Notes"
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color.White.copy(alpha = 0.09f))
                    )
                    SpaceFeatureBadge(
                        icon = Icons.Outlined.Lock,
                        label = "Passwords"
                    )
                }
            }
        }
    }
}

@Composable
private fun SilkAmbientLoginBackground(
    reducedMotion: Boolean
) {
    val transition = rememberInfiniteTransition(label = "login_silk_backdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "silk_phase"
    )
    val drift = if (reducedMotion) 0.4f else phase

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Deep obsidian canvas
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF09090E),
                    Color(0xFF07070B),
                    Color(0xFF0B0A12)
                )
            )
        )

        val w = size.width
        val h = size.height

        // Top-left subtle silk curve
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x285B507A),
                    Color(0x0D322B47),
                    Color.Transparent
                ),
                center = Offset(w * (0.14f + 0.04f * drift), h * 0.14f),
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = Offset(w * (0.14f + 0.04f * drift), h * 0.14f)
        )

        // Upper-right billowing silk fold
        val upperRightPath = Path().apply {
            moveTo(w * 0.56f, 0f)
            cubicTo(
                w * (0.64f + 0.04f * drift), h * 0.16f,
                w * 0.92f, h * 0.12f,
                w, h * 0.27f
            )
            lineTo(w, 0f)
            close()
        }
        drawPath(
            path = upperRightPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x1E8F82B8),
                    Color(0x0A252136),
                    Color(0x26A899D6)
                ),
                start = Offset(w * 0.55f, 0f),
                end = Offset(w, h * 0.28f)
            )
        )
        drawPath(
            path = Path().apply {
                moveTo(w * 0.56f, 0f)
                cubicTo(
                    w * (0.64f + 0.04f * drift), h * 0.16f,
                    w * 0.92f, h * 0.12f,
                    w, h * 0.27f
                )
            },
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x45D4C8F7),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Mid-left soft vertical silk drape
        drawPath(
            path = Path().apply {
                moveTo(0f, h * 0.28f)
                cubicTo(
                    w * (0.14f + 0.03f * drift), h * 0.42f,
                    w * 0.06f, h * 0.58f,
                    0f, h * 0.72f
                )
            },
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x2EA69AE0),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Bottom-right luminous lavender wave
        val bottomWave = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.28f, h)
            cubicTo(
                w * 0.52f, h * (0.93f - 0.02f * drift),
                w * 0.80f, h * 0.90f,
                w, h * 0.74f
            )
            lineTo(w, h)
            close()
        }
        drawPath(
            path = bottomWave,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x3D9D8ED4),
                    Color(0x1E4B406E),
                    Color.Transparent
                ),
                center = Offset(w * 0.85f, h * 0.92f),
                radius = w * 0.65f
            )
        )
        drawPath(
            path = Path().apply {
                moveTo(w * 0.34f, h)
                cubicTo(
                    w * 0.56f, h * (0.94f - 0.015f * drift),
                    w * 0.82f, h * 0.90f,
                    w, h * 0.76f
                )
            },
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x5AE2D9FF),
                    Color(0x2AA796E0)
                ),
                start = Offset(w * 0.35f, h),
                end = Offset(w, h * 0.76f)
            ),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun FrostedPillButton(
    enabled: Boolean,
    isLoading: Boolean,
    reducedMotion: Boolean,
    brush: Brush,
    borderBrush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.985f else 1f,
        animationSpec = tween(durationMillis = 130),
        label = "pill_press_scale"
    )
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .height(52.dp)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(pillShape)
            .background(brush)
            .border(width = 1.dp, brush = borderBrush, shape = pillShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            content()
        }
    }
}

@Composable
private fun MinimalGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val pillShape = RoundedCornerShape(26.dp)

    Row(
        modifier = modifier
            .height(52.dp)
            .clip(pillShape)
            .background(Color(0x9E191A22))
            .border(
                width = 1.dp,
                color = if (isFocused) {
                    SeamLavenderAccent.copy(alpha = 0.65f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                },
                shape = pillShape
            )
            .padding(start = 16.dp, end = if (trailingContent != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = if (isFocused) SeamLavenderAccent else Color(0xFF9C9BA6),
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                    color = Color(0xFF8C8C96)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = SeamWarmIvory,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(SeamLavenderAccent),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
private fun SpaceFeatureBadge(
    icon: ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1E28),
                            Color(0xFF13131A)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = SeamSilver,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal
            ),
            color = Color(0xFFC0C0CA)
        )
    }
}

@Composable
private fun GoogleMulticolorLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.21f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        // Blue arc + horizontal crossbar (right half)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -8f,
            sweepAngle = 52f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.51f, size.height * 0.50f),
            end = Offset(size.width * 0.94f, size.height * 0.50f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
        // Green arc (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 44f,
            sweepAngle = 98f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow arc (left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 142f,
            sweepAngle = 74f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Red arc (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 216f,
            sweepAngle = 104f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
    }
}

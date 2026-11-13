package com.example

import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.security.KeystoreVaultCrypto
import com.example.data.ai.MindfulAiService
import com.example.data.ai.configureAppCheck
import com.example.data.cloud.CloudSyncRepository
import com.example.data.local.IsolatedVaultDatabase
import com.example.data.local.PaperFlowDatabase
import com.example.data.local.PrivatePhoneStorageManager
import com.example.data.local.VaultCredentialEntity
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.ThemeModePreference
import com.example.data.repository.PaperFlowRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOutFromPaperFlow
import com.example.ui.components.rememberEffectiveReducedMotion
import com.example.ui.screens.GalleryVaultScreen
import com.example.ui.screens.PasswordsVaultScreen
import com.example.ui.screens.ReflectionsNotesScreen
import com.example.ui.screens.SanctuaryScreen
import com.example.ui.screens.SeamQuickCreateSheet
import com.example.ui.screens.SemanticSearchScreen
import com.example.ui.screens.SettingsAndSyncScreen
import com.example.ui.screens.VaultCredentialEditorDialog
import com.example.ui.screens.VaultEntryEditorDialog
import com.example.ui.security.BiometricGateOverlay
import com.example.ui.security.DeviceAutofillPermissionRequestDialog
import com.example.ui.security.FirstTimeVaultPinSetupScreen
import com.example.ui.theme.PaperFlowTheme
import com.example.ui.theme.SeamBorderSubtle
import com.example.ui.theme.SeamCharcoal
import com.example.ui.theme.SeamElevatedSurface
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamWarmIvory
import com.example.ui.viewmodel.PaperFlowTab
import com.example.ui.viewmodel.PaperFlowViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            PaperFlowAppRoot()
        }
    }
}

@Composable
fun PaperFlowAppRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Requirement 4: Google Sign-In is mandatory for every user to use the app.
    val signedInUser = currentUser
    if (signedInUser == null) {
        PaperFlowTheme(themeMode = ThemeModePreference.DARK_PRIORITY) {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
    } else {
        val activeUserId = signedInUser.uid
        key(activeUserId) {
            AuthenticatedSanctuaryHost(
                currentUserId = activeUserId,
                isSignedInWithGoogle = true,
                userEmail = signedInUser.email ?: "",
                userDisplayName = signedInUser.displayName ?: "Sanctuary Keeper"
            )
        }
    }
}

@Composable
fun AuthenticatedSanctuaryHost(
    currentUserId: String,
    isSignedInWithGoogle: Boolean,
    userEmail: String,
    userDisplayName: String,
    onShowAuthScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PaperFlowViewModel = viewModel(
        key = "$currentUserId-$isSignedInWithGoogle",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val roomDb = PaperFlowDatabase.getInstance(app)
                val isolatedVaultDb = IsolatedVaultDatabase.getInstance(app)
                val keystoreCrypto = KeystoreVaultCrypto(app)
                val storageManager = PrivatePhoneStorageManager(app)
                val cloudRepo = CloudSyncRepository(firestore)
                val aiService = MindfulAiService()
                val prefsManager = PreferencesManager(app)
                val repository = PaperFlowRepository(
                    context = app,
                    vaultDao = roomDb.vaultDao(),
                    isolatedVaultDao = isolatedVaultDb.isolatedVaultDao(),
                    keystoreCrypto = keystoreCrypto,
                    storageManager = storageManager,
                    cloudSyncRepository = cloudRepo,
                    aiService = aiService
                )
                PaperFlowViewModel(
                    repository = repository,
                    preferencesManager = prefsManager,
                    currentUserId = currentUserId,
                    isSignedInWithGoogle = isSignedInWithGoogle
                )
            }
        }
    )

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isAppEntryAuthenticated by viewModel.isAppEntryAuthenticated.collectAsStateWithLifecycle()
    val isVaultTabAuthenticated by viewModel.isVaultTabAuthenticated.collectAsStateWithLifecycle()
    val isBiometricUnlocked by viewModel.isBiometricUnlocked.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val vaultCredentials by viewModel.vaultCredentials.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val searchFilter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val backgroundStatus by viewModel.backgroundStatus.collectAsStateWithLifecycle()
    val revealedPasswordCountdowns by viewModel.revealedPasswordCountdowns.collectAsStateWithLifecycle()
    val clipboardClearCountdown by viewModel.clipboardClearCountdown.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.uiBannerMessage.collectAsStateWithLifecycle()
    val canUndoDelete by viewModel.canUndoDelete.collectAsStateWithLifecycle()

    val effectiveReducedMotion = rememberEffectiveReducedMotion(preferences.reducedMotionEnabled)

    var showQuickCreateSheet by remember { mutableStateOf(false) }
    var showBiometricPromptModal by remember { mutableStateOf(false) }
    var pendingRevealCredentialId by remember { mutableStateOf<Long?>(null) }
    var sanctuaryEditorState by remember { mutableStateOf<Pair<VaultItemType, VaultEntryEntity?>?>(null) }
    var credentialEditorState by remember { mutableStateOf<Pair<Boolean, VaultCredentialEntity?>>(false to null) }

    // Ask the Android device for Autofill & Auto-Save Password permission if not already enabled
    var showAutofillPermissionPrompt by remember(context) {
        val alreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = context.getSystemService(AutofillManager::class.java)
            afm?.hasEnabledAutofillServices() == true
        } else {
            true
        }
        mutableStateOf(!alreadyGranted)
    }

    // BackHandler on secondary tabs returns smoothly to Home
    BackHandler(enabled = currentTab != PaperFlowTab.SANCTUARY) {
        viewModel.selectTab(PaperFlowTab.SANCTUARY)
    }

    PaperFlowTheme(themeMode = preferences.themeMode) {
        // Requirement 5: The PIN for Vault is set at the time of Google Sign-In ONLY ONCE.
        if (!preferences.isVaultPinConfigured) {
            FirstTimeVaultPinSetupScreen(
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                onPinCreated = { newPin ->
                    viewModel.updateVaultPasscode(newPin)
                }
            )
            return@PaperFlowTheme
        }

        // Requirement 1: Secure application entry with Android device BiometricPrompt (or PIN-only on devices without a biometric sensor)
        if (!isAppEntryAuthenticated) {
            BiometricGateOverlay(
                expectedPasscode = preferences.vaultPasscode,
                userDisplayName = userDisplayName,
                gateTitle = "APPLICATION ENTRY LOCK",
                gateSubtitle = "Authenticate with your Android device to access your personal reflections, memories, and passwords",
                autoTriggerBiometricOnLaunch = true,
                onUnlocked = {
                    viewModel.markAppEntryAuthenticated()
                }
            )
            return@PaperFlowTheme
        }

        // Ask the Android device for permission to auto-save passwords and autofill them across apps
        if (showAutofillPermissionPrompt) {
            DeviceAutofillPermissionRequestDialog(
                onDismiss = {
                    showAutofillPermissionPrompt = false
                },
                onPermissionResult = { granted ->
                    showAutofillPermissionPrompt = false
                    if (granted) {
                        viewModel.showBannerMessage("Android Autofill & Auto-Save Password permission granted.")
                    }
                }
            )
        }

        // Requirement 3: Biometric authentication is required when tapping Vault EVERY TIME.
        if (currentTab == PaperFlowTab.PASSWORDS && !isVaultTabAuthenticated) {
            BiometricGateOverlay(
                expectedPasscode = preferences.vaultPasscode,
                userDisplayName = userDisplayName,
                gateTitle = "VAULT ACCESS BIOMETRIC LOCK",
                gateSubtitle = "BiometricPrompt authentication is required every time you open the Password Vault",
                autoTriggerBiometricOnLaunch = true,
                onUnlocked = {
                    viewModel.markVaultTabAuthenticated()
                },
                onCancel = {
                    viewModel.selectTab(PaperFlowTab.SANCTUARY)
                }
            )
            return@PaperFlowTheme
        }

        if (showBiometricPromptModal) {
            BiometricGateOverlay(
                expectedPasscode = preferences.vaultPasscode,
                userDisplayName = userDisplayName,
                gateTitle = "CREDENTIAL REVEAL BIOMETRIC LOCK",
                gateSubtitle = "Verify with BiometricPrompt to reveal or copy this encrypted credential",
                autoTriggerBiometricOnLaunch = true,
                onUnlocked = {
                    viewModel.markBiometricUnlocked()
                    showBiometricPromptModal = false
                    pendingRevealCredentialId?.let { credId ->
                        viewModel.toggleCredentialRevealWithTimeout(credId)
                        pendingRevealCredentialId = null
                    }
                },
                onCancel = {
                    showBiometricPromptModal = false
                    pendingRevealCredentialId = null
                }
            )
            return@PaperFlowTheme
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            floatingActionButton = {
                if (currentTab != PaperFlowTab.SETTINGS) {
                    FloatingActionButton(
                        onClick = { showQuickCreateSheet = true },
                        containerColor = SeamWarmIvory,
                        contentColor = SeamNearBlack,
                        modifier = Modifier.testTag("main_fab_add")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "One-tap capture: Note, Import memory, Add credential"
                        )
                    }
                }
            },
            bottomBar = {
                // 5-Tab Bottom Navigation Bar matching Pages 3, 5, 7, 9, 11, 13:
                // Home | Memories | Notes | Vault | Search
                NavigationBar(
                    containerColor = SeamCharcoal,
                    modifier = Modifier.border(width = 1.dp, color = SeamBorderSubtle)
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SeamWarmIvory,
                        selectedTextColor = SeamWarmIvory,
                        indicatorColor = SeamElevatedSurface,
                        unselectedIconColor = SeamMutedGrey,
                        unselectedTextColor = SeamMutedGrey
                    )

                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.SANCTUARY,
                        onClick = { viewModel.selectTab(PaperFlowTab.SANCTUARY) },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_sanctuary")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.GALLERY,
                        onClick = { viewModel.selectTab(PaperFlowTab.GALLERY) },
                        icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Memories") },
                        label = { Text("Memories") },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_gallery")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.NOTES,
                        onClick = { viewModel.selectTab(PaperFlowTab.NOTES) },
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = "Notes") },
                        label = { Text("Notes") },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_notes")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.PASSWORDS,
                        onClick = { viewModel.selectTab(PaperFlowTab.PASSWORDS) },
                        icon = { Icon(Icons.Outlined.Lock, contentDescription = "Vault") },
                        label = { Text("Vault") },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_passwords")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.SEARCH,
                        onClick = { viewModel.selectTab(PaperFlowTab.SEARCH) },
                        icon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_search")
                    )
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Section 09 · Success confirmation / Undo banner (180–260 ms transition)
                    AnimatedVisibility(
                        visible = bannerMessage != null,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(180))
                    ) {
                        bannerMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SeamElevatedSurface)
                                    .border(1.dp, SeamBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SeamWarmIvory,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (canUndoDelete && msg.startsWith("Removed")) {
                                        Text(
                                            text = "Undo",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = SeamLavenderAccent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { viewModel.undoLastDeletedEntry() }
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                                .testTag("banner_undo_button")
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "Dismiss",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SeamSilver,
                                        modifier = Modifier
                                            .clickable { viewModel.dismissBannerMessage() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Section 09 · Screen transition:
                    // Navigation | 180–280 ms | Fade plus 8–16 dp movement; preserve state and scroll.
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                if (effectiveReducedMotion) {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                } else {
                                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { 24 })
                                        .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -16 })
                                }
                            },
                            label = "seam_screen_transition"
                        ) { targetTab ->
                            when (targetTab) {
                                PaperFlowTab.SANCTUARY -> {
                                    SanctuaryScreen(
                                        entries = allEntries,
                                        isolatedVaultCount = vaultCredentials.size,
                                        preferences = preferences,
                                        backgroundStatus = backgroundStatus,
                                        onNavigateToTab = { viewModel.selectTab(it) },
                                        onOpenNewEntryDialog = { type ->
                                            if (type == VaultItemType.PASSWORD) {
                                                viewModel.selectTab(PaperFlowTab.PASSWORDS)
                                                credentialEditorState = true to null
                                            } else {
                                                sanctuaryEditorState = type to null
                                            }
                                        },
                                        onSelectEntry = { entry ->
                                            val type = runCatching { VaultItemType.valueOf(entry.itemType) }
                                                .getOrDefault(VaultItemType.NOTE)
                                            sanctuaryEditorState = type to entry
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onTriggerBackgroundSyncAndAi = { viewModel.triggerImmediateBackgroundSyncAndAi() },
                                        onLockSanctuary = {
                                            viewModel.lockVaultNow()
                                            viewModel.selectTab(PaperFlowTab.PASSWORDS)
                                        }
                                    )
                                }

                                PaperFlowTab.GALLERY -> {
                                    GalleryVaultScreen(
                                        galleryEntries = allEntries.filter { it.itemType == VaultItemType.GALLERY.name },
                                        reducedMotion = preferences.reducedMotionEnabled,
                                        onPhotoPickedForVault = { uri ->
                                            viewModel.importGalleryPhotoToPhoneStorage(uri) { copiedPath ->
                                                sanctuaryEditorState = VaultItemType.GALLERY to VaultEntryEntity(
                                                    itemType = VaultItemType.GALLERY.name,
                                                    mediaPath = copiedPath,
                                                    folderOrAlbum = "Visual Archive",
                                                    category = "Visual Memory",
                                                    moodTag = "Luminous"
                                                )
                                            }
                                        },
                                        onOpenNewGalleryDialog = {
                                            sanctuaryEditorState = VaultItemType.GALLERY to null
                                        },
                                        onSelectEntry = { entry ->
                                            sanctuaryEditorState = VaultItemType.GALLERY to entry
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onAnalyzeEntryWithAi = { viewModel.analyzeSingleEntryWithAi(it) },
                                        onDeleteEntry = { viewModel.deleteEntry(it) },
                                        onOpenSettings = { viewModel.selectTab(PaperFlowTab.SETTINGS) }
                                    )
                                }

                                PaperFlowTab.NOTES -> {
                                    ReflectionsNotesScreen(
                                        noteEntries = allEntries.filter { it.itemType == VaultItemType.NOTE.name },
                                        reducedMotion = preferences.reducedMotionEnabled,
                                        onOpenNewNoteDialog = {
                                            sanctuaryEditorState = VaultItemType.NOTE to null
                                        },
                                        onSelectEntry = { entry ->
                                            sanctuaryEditorState = VaultItemType.NOTE to entry
                                        },
                                        onSaveNoteInline = { existingId, title, body, folder, category, mood, isFav ->
                                            viewModel.saveSanctuaryEntry(
                                                existingId = existingId,
                                                itemType = VaultItemType.NOTE,
                                                title = title,
                                                primaryContent = body,
                                                secondaryMetadata = "Markdown",
                                                websiteOrLocation = "",
                                                folderOrAlbum = folder,
                                                mediaPath = "",
                                                category = category,
                                                moodTag = mood,
                                                isFavorite = isFav
                                            )
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onAnalyzeEntryWithAi = { viewModel.analyzeSingleEntryWithAi(it) },
                                        onExportMarkdown = { viewModel.exportNoteToMarkdown(it) },
                                        onDeleteEntry = { viewModel.deleteEntry(it) },
                                        onOpenSettings = { viewModel.selectTab(PaperFlowTab.SETTINGS) }
                                    )
                                }

                                PaperFlowTab.PASSWORDS -> {
                                    PasswordsVaultScreen(
                                        credentials = vaultCredentials,
                                        revealedCountdowns = revealedPasswordCountdowns,
                                        clipboardClearCountdown = clipboardClearCountdown,
                                        expectedPasscode = preferences.vaultPasscode,
                                        isVaultSessionUnlocked = isVaultTabAuthenticated && (!preferences.isBiometricLockEnabled || isBiometricUnlocked),
                                        isAutofillOverlayEnabled = preferences.isAutofillOverlayEnabled,
                                        reducedMotion = preferences.reducedMotionEnabled,
                                        onRequestBiometricUnlock = {
                                            showBiometricPromptModal = true
                                        },
                                        onDecryptSecret = { viewModel.decryptCredentialSecret(it) },
                                        onToggleRevealPassword = { credId ->
                                            if (preferences.isBiometricLockEnabled && !isBiometricUnlocked) {
                                                pendingRevealCredentialId = credId
                                                showBiometricPromptModal = true
                                            } else {
                                                viewModel.toggleCredentialRevealWithTimeout(credId)
                                            }
                                        },
                                        onCopySecretWithAutoClear = { label, secret ->
                                            if (preferences.isBiometricLockEnabled && !isBiometricUnlocked) {
                                                showBiometricPromptModal = true
                                            } else {
                                                viewModel.copyCredentialSecretWithAutoClear(label, secret)
                                            }
                                        },
                                        onOpenNewCredentialDialog = {
                                            credentialEditorState = true to null
                                        },
                                        onSaveQuickAutofillCredential = { serviceTitle, accountUsername, rawPassword, domainOrUrl ->
                                            viewModel.saveVaultCredential(
                                                existingId = null,
                                                serviceTitle = serviceTitle,
                                                accountUsername = accountUsername,
                                                rawPassword = rawPassword,
                                                rawNotes = "Saved via Seam Autofill Overlay",
                                                domainOrUrl = domainOrUrl,
                                                category = "Autofill"
                                            )
                                        },
                                        onEditCredential = { cred ->
                                            if (preferences.isBiometricLockEnabled && !isBiometricUnlocked) {
                                                showBiometricPromptModal = true
                                            } else {
                                                credentialEditorState = true to cred
                                            }
                                        },
                                        onDeleteCredential = { cred ->
                                            viewModel.deleteVaultCredential(cred.id, cred.serviceTitle)
                                        },
                                        onLockVaultNow = {
                                            viewModel.lockVaultNow()
                                        },
                                        onOpenSettings = { viewModel.selectTab(PaperFlowTab.SETTINGS) }
                                    )
                                }

                                PaperFlowTab.SEARCH -> {
                                    val categories = remember(allEntries) {
                                        allEntries.map { it.category }.filter { it.isNotBlank() }.distinct()
                                    }
                                    val moods = remember(allEntries) {
                                        allEntries.map { it.moodTag }.filter { it.isNotBlank() }.distinct()
                                    }
                                    val foldersAndAlbums = remember(allEntries) {
                                        allEntries.map { it.folderOrAlbum }.filter { it.isNotBlank() }.distinct()
                                    }
                                    SemanticSearchScreen(
                                        searchFilter = searchFilter,
                                        filteredEntries = filteredEntries,
                                        allFoldersAndAlbums = foldersAndAlbums,
                                        allCategories = categories,
                                        allMoods = moods,
                                        reducedMotion = preferences.reducedMotionEnabled,
                                        onExplainMatchRelevance = { entry -> viewModel.explainMatchRelevance(entry) },
                                        onQueryChange = { viewModel.updateSearchQuery(it) },
                                        onRunDeepAiSearch = { viewModel.runDeepAiSemanticSearch() },
                                        onSelectTypeFilter = { viewModel.setFilterType(it) },
                                        onSelectFolderFilter = { viewModel.setFilterFolderOrAlbum(it) },
                                        onSelectCategoryFilter = { viewModel.setFilterCategory(it) },
                                        onSelectMoodFilter = { viewModel.setFilterMood(it) },
                                        onSelectDateRange = { viewModel.setFilterDateRange(it) },
                                        onToggleFavoritesOnly = { viewModel.toggleFilterFavoritesOnly() },
                                        onToggleAnalyzedOnly = { viewModel.toggleFilterAnalyzedOnly() },
                                        onClearFilters = { viewModel.clearAllSearchFilters() },
                                        onSelectEntry = { entry ->
                                            val type = runCatching { VaultItemType.valueOf(entry.itemType) }
                                                .getOrDefault(VaultItemType.NOTE)
                                            sanctuaryEditorState = type to entry
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onOpenSettings = { viewModel.selectTab(PaperFlowTab.SETTINGS) }
                                    )
                                }

                                PaperFlowTab.SETTINGS -> {
                                    SettingsAndSyncScreen(
                                        isSignedInWithGoogle = isSignedInWithGoogle,
                                        userEmail = userEmail,
                                        userDisplayName = userDisplayName,
                                        preferences = preferences,
                                        backgroundStatus = backgroundStatus,
                                        entriesForAiReview = allEntries,
                                        onBackToHome = { viewModel.selectTab(PaperFlowTab.SANCTUARY) },
                                        onSetThemeMode = { viewModel.setThemeMode(it) },
                                        onSetReducedMotionEnabled = { viewModel.setReducedMotionEnabled(it) },
                                        onSetAiExecutionMode = { mode, consent ->
                                            viewModel.setAiExecutionMode(mode, consent)
                                        },
                                        onClearAllDerivedAiInsights = { viewModel.clearAllDerivedAiInsights() },
                                        onSetBiometricEnabled = { viewModel.setBiometricLockEnabled(it) },
                                        onSetAutofillOverlayEnabled = { viewModel.setAutofillOverlayEnabled(it) },
                                        onUpdatePasscode = { viewModel.updateVaultPasscode(it) },
                                        onSetBatterySaverSyncOnly = { viewModel.setBatterySaverSyncOnly(it) },
                                        onSetCloudBackupEnabled = { viewModel.setCloudBackupEnabled(it) },
                                        onTriggerImmediateSync = { viewModel.triggerImmediateBackgroundSyncAndAi() },
                                        onLockSanctuaryNow = {
                                            viewModel.lockAppEntryNow()
                                        },
                                        onSignInWithGoogle = {
                                            onGoogleSignInClicked(
                                                context = context,
                                                credentialManager = credentialManager,
                                                onAuthSuccess = {},
                                                onAuthError = { onShowAuthScreen() },
                                                scope = scope
                                            )
                                        },
                                        onSignOut = {
                                            signOutFromPaperFlow(
                                                credentialManager = credentialManager,
                                                scope = scope
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // One-Tap Capture Sheet ("Note", "Import memory", "Add credential" per Page 3)
        if (showQuickCreateSheet) {
            SeamQuickCreateSheet(
                reducedMotion = preferences.reducedMotionEnabled,
                onDismiss = { showQuickCreateSheet = false },
                onCreateNote = {
                    sanctuaryEditorState = VaultItemType.NOTE to null
                },
                onImportMemory = {
                    sanctuaryEditorState = VaultItemType.GALLERY to null
                },
                onAddCredential = {
                    credentialEditorState = true to null
                }
            )
        }

        sanctuaryEditorState?.let { (initialType, entryToEdit) ->
            VaultEntryEditorDialog(
                initialType = initialType,
                existingEntry = entryToEdit,
                onImportPhotoUri = { uri, onDone ->
                    viewModel.importGalleryPhotoToPhoneStorage(uri, onDone)
                },
                onDismiss = { sanctuaryEditorState = null },
                onSave = { existingId, itemType, title, primaryContent, secondaryMetadata, websiteOrLocation, folderOrAlbum, mediaPath, category, moodTag, isFavorite ->
                    viewModel.saveSanctuaryEntry(
                        existingId = existingId,
                        itemType = itemType,
                        title = title,
                        primaryContent = primaryContent,
                        secondaryMetadata = secondaryMetadata,
                        websiteOrLocation = websiteOrLocation,
                        folderOrAlbum = folderOrAlbum,
                        mediaPath = mediaPath,
                        category = category,
                        moodTag = moodTag,
                        isFavorite = isFavorite
                    )
                    sanctuaryEditorState = null
                }
            )
        }

        if (credentialEditorState.first) {
            val credToEdit = credentialEditorState.second
            val initialDecryptedPass = credToEdit?.let { viewModel.decryptCredentialSecret(it.encryptedPassword) } ?: ""
            val initialDecryptedNotes = credToEdit?.let { viewModel.decryptCredentialSecret(it.encryptedNotes) } ?: ""
            VaultCredentialEditorDialog(
                existingCredential = credToEdit,
                initialDecryptedPassword = initialDecryptedPass,
                initialDecryptedNotes = initialDecryptedNotes,
                onGeneratePassword = { len, symbols ->
                    viewModel.generateStrongPassword(length = len, includeSymbols = symbols)
                },
                onDismiss = { credentialEditorState = false to null },
                onSave = { existingId, serviceTitle, accountUsername, rawPassword, rawNotes, domainOrUrl, category ->
                    viewModel.saveVaultCredential(
                        existingId = existingId,
                        serviceTitle = serviceTitle,
                        accountUsername = accountUsername,
                        rawPassword = rawPassword,
                        rawNotes = rawNotes,
                        domainOrUrl = domainOrUrl,
                        category = category
                    )
                    credentialEditorState = false to null
                }
            )
        }
    }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.example.ui.screens.GalleryVaultScreen
import com.example.ui.screens.PasswordsVaultScreen
import com.example.ui.screens.ReflectionsNotesScreen
import com.example.ui.screens.SanctuaryScreen
import com.example.ui.screens.SemanticSearchScreen
import com.example.ui.screens.SettingsAndSyncScreen
import com.example.ui.screens.VaultCredentialEditorDialog
import com.example.ui.screens.VaultEntryEditorDialog
import com.example.ui.security.BiometricGateOverlay
import com.example.ui.theme.PaperFlowTheme
import com.example.ui.viewmodel.PaperFlowTab
import com.example.ui.viewmodel.PaperFlowViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
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
    var isOfflineSanctuaryActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null && !isOfflineSanctuaryActive) {
        PaperFlowTheme(themeMode = ThemeModePreference.DARK_PRIORITY) {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                },
                onContinueOffline = {
                    isOfflineSanctuaryActive = true
                }
            )
        }
    } else {
        val activeUserId = currentUser?.uid ?: "local_offline_keeper"
        val isSignedInWithGoogle = currentUser != null
        key(activeUserId) {
            AuthenticatedSanctuaryHost(
                currentUserId = activeUserId,
                isSignedInWithGoogle = isSignedInWithGoogle,
                userEmail = currentUser?.email ?: "",
                userDisplayName = currentUser?.displayName ?: "Local Sanctuary Keeper",
                onShowAuthScreen = {
                    isOfflineSanctuaryActive = false
                }
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

    var sanctuaryEditorState by remember { mutableStateOf<Pair<VaultItemType, VaultEntryEntity?>?>(null) }
    var credentialEditorState by remember { mutableStateOf<Pair<Boolean, VaultCredentialEntity?>>(false to null) }

    // BackHandler on secondary tabs returns smoothly to Sanctuary home
    BackHandler(enabled = currentTab != PaperFlowTab.SANCTUARY) {
        viewModel.selectTab(PaperFlowTab.SANCTUARY)
    }

    PaperFlowTheme(themeMode = preferences.themeMode) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            floatingActionButton = {
                if (currentTab != PaperFlowTab.SETTINGS) {
                    FloatingActionButton(
                        onClick = {
                            if (currentTab == PaperFlowTab.PASSWORDS) {
                                credentialEditorState = true to null
                            } else {
                                val defaultType = when (currentTab) {
                                    PaperFlowTab.GALLERY -> VaultItemType.GALLERY
                                    else -> VaultItemType.NOTE
                                }
                                sanctuaryEditorState = defaultType to null
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("main_fab_add")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Entry"
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.SANCTUARY,
                        onClick = { viewModel.selectTab(PaperFlowTab.SANCTUARY) },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Sanctuary") },
                        label = { Text("Sanctuary") },
                        modifier = Modifier.testTag("nav_sanctuary")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.GALLERY,
                        onClick = { viewModel.selectTab(PaperFlowTab.GALLERY) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery") },
                        label = { Text("Gallery") },
                        modifier = Modifier.testTag("nav_gallery")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.NOTES,
                        onClick = { viewModel.selectTab(PaperFlowTab.NOTES) },
                        icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                        label = { Text("Notes") },
                        modifier = Modifier.testTag("nav_notes")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.PASSWORDS,
                        onClick = { viewModel.selectTab(PaperFlowTab.PASSWORDS) },
                        icon = { Icon(Icons.Default.Key, contentDescription = "Vault") },
                        label = { Text("Vault") },
                        modifier = Modifier.testTag("nav_passwords")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.SEARCH,
                        onClick = { viewModel.selectTab(PaperFlowTab.SEARCH) },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        modifier = Modifier.testTag("nav_search")
                    )
                    NavigationBarItem(
                        selected = currentTab == PaperFlowTab.SETTINGS,
                        onClick = { viewModel.selectTab(PaperFlowTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_settings")
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
                    AnimatedVisibility(visible = bannerMessage != null) {
                        bannerMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { viewModel.dismissBannerMessage() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Dismiss",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
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
                                    onDeleteEntry = { viewModel.deleteEntry(it) }
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
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAnalyzeEntryWithAi = { viewModel.analyzeSingleEntryWithAi(it) },
                                    onExportMarkdown = { viewModel.exportNoteToMarkdown(it) },
                                    onDeleteEntry = { viewModel.deleteEntry(it) }
                                )
                            }

                            PaperFlowTab.PASSWORDS -> {
                                // Dedicated Biometric / PIN Gate for the Isolated Keystore Password Vault
                                if (preferences.isBiometricLockEnabled && !isBiometricUnlocked) {
                                    BiometricGateOverlay(
                                        expectedPasscode = preferences.vaultPasscode,
                                        userDisplayName = userDisplayName,
                                        onUnlocked = { viewModel.markBiometricUnlocked() }
                                    )
                                } else {
                                    PasswordsVaultScreen(
                                        credentials = vaultCredentials,
                                        revealedCountdowns = revealedPasswordCountdowns,
                                        clipboardClearCountdown = clipboardClearCountdown,
                                        reducedMotion = preferences.reducedMotionEnabled,
                                        onDecryptSecret = { viewModel.decryptCredentialSecret(it) },
                                        onToggleRevealPassword = { viewModel.toggleCredentialRevealWithTimeout(it) },
                                        onCopySecretWithAutoClear = { label, secret ->
                                            viewModel.copyCredentialSecretWithAutoClear(label, secret)
                                        },
                                        onOpenNewCredentialDialog = {
                                            credentialEditorState = true to null
                                        },
                                        onEditCredential = { cred ->
                                            credentialEditorState = true to cred
                                        },
                                        onDeleteCredential = { cred ->
                                            viewModel.deleteVaultCredential(cred.id, cred.serviceTitle)
                                        },
                                        onLockVaultNow = {
                                            viewModel.lockVaultNow()
                                        }
                                    )
                                }
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
                                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                                )
                            }

                            PaperFlowTab.SETTINGS -> {
                                SettingsAndSyncScreen(
                                    isSignedInWithGoogle = isSignedInWithGoogle,
                                    userEmail = userEmail,
                                    userDisplayName = userDisplayName,
                                    preferences = preferences,
                                    backgroundStatus = backgroundStatus,
                                    onSetThemeMode = { viewModel.setThemeMode(it) },
                                    onSetReducedMotionEnabled = { viewModel.setReducedMotionEnabled(it) },
                                    onSetAiExecutionMode = { mode, consent ->
                                        viewModel.setAiExecutionMode(mode, consent)
                                    },
                                    onClearAllDerivedAiInsights = { viewModel.clearAllDerivedAiInsights() },
                                    onSetBiometricEnabled = { viewModel.setBiometricLockEnabled(it) },
                                    onUpdatePasscode = { viewModel.updateVaultPasscode(it) },
                                    onSetBatterySaverSyncOnly = { viewModel.setBatterySaverSyncOnly(it) },
                                    onSetCloudBackupEnabled = { viewModel.setCloudBackupEnabled(it) },
                                    onTriggerImmediateSync = { viewModel.triggerImmediateBackgroundSyncAndAi() },
                                    onLockSanctuaryNow = {
                                        viewModel.lockVaultNow()
                                        viewModel.selectTab(PaperFlowTab.PASSWORDS)
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

package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.VaultCredentialEntity
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.AiExecutionMode
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.ThemeModePreference
import com.example.data.preferences.UserVaultPreferences
import com.example.data.repository.DeviceBatteryHealth
import com.example.data.repository.PaperFlowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PaperFlowTab {
    SANCTUARY,
    GALLERY,
    NOTES,
    PASSWORDS,
    SEARCH,
    SETTINGS
}

enum class DateRangeFilter(val label: String) {
    ALL_TIME("All Time"),
    LAST_24_HOURS("Last 24h"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days")
}

data class SearchFilterState(
    val query: String = "",
    val selectedType: VaultItemType? = null, // null = All Sanctuary types (Notes + Gallery; Passwords NEVER included)
    val selectedFolderOrAlbum: String = "All",
    val selectedCategory: String = "All",
    val selectedMood: String = "All",
    val dateRange: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val onlyFavorites: Boolean = false,
    val onlyAnalyzed: Boolean = false,
    val aiExpandedTerms: List<String> = emptyList(),
    val aiMatchedIds: Set<Long> = emptySet(),
    val aiSearchInsight: String = "",
    val isAiSearching: Boolean = false
)

data class BackgroundStatusState(
    val isAnalyzingOrSyncing: Boolean = false,
    val pillLabel: String = "Local Sanctuary Ready",
    val detailStatus: String = "On-device storage active • Password Vault isolated.",
    val batteryHealth: DeviceBatteryHealth = DeviceBatteryHealth(88, isCharging = true, isPowerSaveMode = false),
    val privateFilesCount: Int = 0,
    val privateStorageBytes: Long = 0L
)

class PaperFlowViewModel(
    private val repository: PaperFlowRepository,
    private val preferencesManager: PreferencesManager,
    val currentUserId: String,
    val isSignedInWithGoogle: Boolean
) : ViewModel() {

    private val _currentTab = MutableStateFlow(PaperFlowTab.SANCTUARY)
    val currentTab: StateFlow<PaperFlowTab> = _currentTab.asStateFlow()

    // Requirement 1: BiometricPrompt authentication required at application entry before accessing reflections & passwords
    private val _isAppEntryAuthenticated = MutableStateFlow(false)
    val isAppEntryAuthenticated: StateFlow<Boolean> = _isAppEntryAuthenticated.asStateFlow()

    // Requirement 3: Biometric authentication required EVERY TIME the user taps the Vault tab
    private val _isVaultTabAuthenticated = MutableStateFlow(false)
    val isVaultTabAuthenticated: StateFlow<Boolean> = _isVaultTabAuthenticated.asStateFlow()

    // Dedicated Biometric / PIN Gate specifically for revealing/copying secrets inside the Password Vault
    private val _isBiometricUnlocked = MutableStateFlow(false)
    val isBiometricUnlocked: StateFlow<Boolean> = _isBiometricUnlocked.asStateFlow()

    // Map of Credential ID -> remaining seconds before automatic re-masking
    private val _revealedPasswordCountdowns = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val revealedPasswordCountdowns: StateFlow<Map<Long, Int>> = _revealedPasswordCountdowns.asStateFlow()

    // Countdown in seconds before clipboard is automatically wiped
    private val _clipboardClearCountdown = MutableStateFlow(0)
    val clipboardClearCountdown: StateFlow<Int> = _clipboardClearCountdown.asStateFlow()

    private var revealTimerJobs = mutableMapOf<Long, Job>()
    private var clipboardClearJob: Job? = null

    private val _searchFilter = MutableStateFlow(SearchFilterState())
    val searchFilter: StateFlow<SearchFilterState> = _searchFilter.asStateFlow()

    private val _backgroundStatus = MutableStateFlow(BackgroundStatusState())
    val backgroundStatus: StateFlow<BackgroundStatusState> = _backgroundStatus.asStateFlow()

    private val _uiBannerMessage = MutableStateFlow<String?>(null)
    val uiBannerMessage: StateFlow<String?> = _uiBannerMessage.asStateFlow()

    private var lastDeletedEntry: VaultEntryEntity? = null
    private val _canUndoDelete = MutableStateFlow(false)
    val canUndoDelete: StateFlow<Boolean> = _canUndoDelete.asStateFlow()

    val preferences: StateFlow<UserVaultPreferences> = preferencesManager.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserVaultPreferences()
        )

    // Sanctuary entries: Notes & Gallery Memories ONLY
    val allEntries: StateFlow<List<VaultEntryEntity>> = repository.observeSanctuaryEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Isolated Password Vault credentials from separate Room DB (`seam_isolated_vault.db`)
    val vaultCredentials: StateFlow<List<VaultCredentialEntity>> = repository.observeIsolatedVaultCredentials()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val localConceptThesaurus: Map<String, List<String>> = mapOf(
        "calm" to listOf("serene", "peace", "peaceful", "stillness", "quiet", "meditation", "mindful", "tranquil", "relax", "midnight", "breath"),
        "sleep" to listOf("midnight", "night", "rest", "insomnia", "dream", "quiet", "stillness"),
        "work" to listOf("sprint", "focus", "morning", "routine", "architecture", "lecture", "studio"),
        "photo" to listOf("gallery", "image", "picture", "visual", "ocean", "sunset", "zürich", "nature", "art", "memory", "constellation"),
        "sea" to listOf("ocean", "water", "tide", "coast", "waves", "blue", "pacific", "beach"),
        "nature" to listOf("ocean", "forest", "bamboo", "sunset", "coast", "water", "earth", "luminous")
    )

    // PRIVACY INVARIANT: Search filters ONLY Sanctuary entries (`allEntries`), strictly excluding `vaultCredentials`.
    val filteredEntries: StateFlow<List<VaultEntryEntity>> = combine(
        allEntries,
        _searchFilter
    ) { entries, filter ->
        val now = System.currentTimeMillis()
        entries.filter { item ->
            // Extra defense-in-depth: never include PASSWORD items in search
            if (item.itemType == VaultItemType.PASSWORD.name) return@filter false

            // 1. Type filter (Note vs Gallery)
            if (filter.selectedType != null && item.itemType != filter.selectedType.name) {
                return@filter false
            }
            // 2. Folder / Album filter
            if (filter.selectedFolderOrAlbum != "All" && !item.folderOrAlbum.equals(filter.selectedFolderOrAlbum, ignoreCase = true)) {
                return@filter false
            }
            // 3. Category filter
            if (filter.selectedCategory != "All" && !item.category.equals(filter.selectedCategory, ignoreCase = true)) {
                return@filter false
            }
            // 4. Mood filter
            if (filter.selectedMood != "All" && !item.moodTag.equals(filter.selectedMood, ignoreCase = true)) {
                return@filter false
            }
            // 5. Date range filter
            val cutoff = when (filter.dateRange) {
                DateRangeFilter.ALL_TIME -> 0L
                DateRangeFilter.LAST_24_HOURS -> now - 86_400_000L
                DateRangeFilter.LAST_7_DAYS -> now - 86_400_000L * 7
                DateRangeFilter.LAST_30_DAYS -> now - 86_400_000L * 30
            }
            if (item.updatedAtMillis < cutoff) return@filter false

            // 6. Favorites / Analyzed toggles
            if (filter.onlyFavorites && !item.isFavorite) return@filter false
            if (filter.onlyAnalyzed && !item.isAnalyzed) return@filter false

            // 7. Lexical FTS + Semantic Keyword matching
            val rawQuery = filter.query.trim().lowercase()
            if (rawQuery.isBlank()) return@filter true

            if (filter.aiMatchedIds.contains(item.id)) return@filter true

            val searchableBlob = buildString {
                append(item.title.lowercase()).append(" ")
                append(item.primaryContent.lowercase()).append(" ")
                append(item.websiteOrLocation.lowercase()).append(" ")
                append(item.folderOrAlbum.lowercase()).append(" ")
                append(item.ocrExtractedText.lowercase()).append(" ")
                append(item.category.lowercase()).append(" ")
                append(item.moodTag.lowercase()).append(" ")
                append(item.aiSummary.lowercase()).append(" ")
                append(item.aiKeywords.lowercase())
            }

            val queryTokens = rawQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
            val candidateTerms = mutableSetOf<String>()
            candidateTerms.add(rawQuery)
            candidateTerms.addAll(queryTokens)
            filter.aiExpandedTerms.forEach { candidateTerms.add(it.lowercase()) }

            queryTokens.forEach { token ->
                localConceptThesaurus.forEach { (key, synonyms) ->
                    if (key.contains(token) || token.contains(key) || synonyms.any { it == token }) {
                        candidateTerms.add(key)
                        candidateTerms.addAll(synonyms)
                    }
                }
            }

            candidateTerms.any { term -> term.length >= 2 && searchableBlob.contains(term) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    init {
        initializeLocalFirstSanctuary()
    }

    private fun initializeLocalFirstSanctuary() {
        viewModelScope.launch {
            try {
                refreshStorageAndBatteryMetrics()
                repository.ensureInitialSanctuarySeeded(currentUserId)
                refreshStorageAndBatteryMetrics()

                // Run a single incremental on-device check on launch (no aggressive polling)
                val prefs = preferences.value
                performIncrementalBackgroundWork(
                    aiMode = prefs.aiExecutionMode,
                    hasCloudConsent = prefs.hasConsentedToCloudAi,
                    respectBattery = prefs.isBatterySaverSyncOnly,
                    enableCloud = prefs.isCloudBackupEnabled,
                    forceManual = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SeamVM", "Initialization error", e)
            }
        }
    }

    private suspend fun performIncrementalBackgroundWork(
        aiMode: AiExecutionMode,
        hasCloudConsent: Boolean,
        respectBattery: Boolean,
        enableCloud: Boolean,
        forceManual: Boolean
    ) {
        val battery = repository.getDeviceBatteryHealth()
        val (filesCount, bytesCount) = repository.getPrivateStorageStats()
        _backgroundStatus.value = _backgroundStatus.value.copy(
            isAnalyzingOrSyncing = true,
            pillLabel = if (aiMode == AiExecutionMode.PAUSED) "AI Paused" else "Indexing Sanctuary...",
            batteryHealth = battery,
            privateFilesCount = filesCount,
            privateStorageBytes = bytesCount
        )

        val summary = repository.runBackgroundAnalysisAndSync(
            userId = currentUserId,
            aiMode = aiMode,
            hasCloudAiConsent = hasCloudConsent,
            respectBatterySaver = respectBattery,
            enableCloudBackup = enableCloud,
            isSignedInWithGoogle = isSignedInWithGoogle,
            forceManualRun = forceManual
        )

        if (!summary.skippedDueToBatterySaver) {
            preferencesManager.recordSyncCompleted()
        }

        val (updatedFiles, updatedBytes) = repository.getPrivateStorageStats()
        val finalLabel = when {
            aiMode == AiExecutionMode.PAUSED -> "AI Paused • Local Vault"
            summary.skippedDueToBatterySaver -> "Eco Battery Guard (${battery.batteryPct}%)"
            summary.analyzedCount > 0 -> "Indexed +${summary.analyzedCount} Local"
            summary.cloudSyncedCount > 0 -> "Opt-In Cloud Synced"
            else -> "Local-First • ${battery.batteryPct}%"
        }

        _backgroundStatus.value = BackgroundStatusState(
            isAnalyzingOrSyncing = false,
            pillLabel = finalLabel,
            detailStatus = summary.statusMessage,
            batteryHealth = battery,
            privateFilesCount = updatedFiles,
            privateStorageBytes = updatedBytes
        )
    }

    private fun refreshStorageAndBatteryMetrics() {
        val battery = repository.getDeviceBatteryHealth()
        val (files, bytes) = repository.getPrivateStorageStats()
        _backgroundStatus.value = _backgroundStatus.value.copy(
            batteryHealth = battery,
            privateFilesCount = files,
            privateStorageBytes = bytes
        )
    }

    fun selectTab(tab: PaperFlowTab) {
        // Requirement 3: Biometric authentication is required EVERY TIME the user taps Vault!
        // Reset _isVaultTabAuthenticated whenever Vault is tapped or when leaving Vault.
        if (tab == PaperFlowTab.PASSWORDS || _currentTab.value == PaperFlowTab.PASSWORDS) {
            _isVaultTabAuthenticated.value = false
            _isBiometricUnlocked.value = false
            revealTimerJobs.values.forEach { it.cancel() }
            revealTimerJobs.clear()
            _revealedPasswordCountdowns.value = emptyMap()
        }
        _currentTab.value = tab
    }

    fun markAppEntryAuthenticated() {
        _isAppEntryAuthenticated.value = true
    }

    fun lockAppEntryNow() {
        _isAppEntryAuthenticated.value = false
        lockVaultNow()
    }

    fun markVaultTabAuthenticated() {
        _isVaultTabAuthenticated.value = true
        _isBiometricUnlocked.value = true
    }

    fun markBiometricUnlocked() {
        _isBiometricUnlocked.value = true
    }

    fun lockVaultNow() {
        _isVaultTabAuthenticated.value = false
        _isBiometricUnlocked.value = false
        revealTimerJobs.values.forEach { it.cancel() }
        revealTimerJobs.clear()
        _revealedPasswordCountdowns.value = emptyMap()
    }

    /**
     * Reveals a password with an automatic countdown timer (default 15s) that re-masks the secret
     * when the timer expires.
     */
    fun toggleCredentialRevealWithTimeout(credentialId: Long) {
        val currentMap = _revealedPasswordCountdowns.value
        if (currentMap.containsKey(credentialId)) {
            revealTimerJobs.remove(credentialId)?.cancel()
            _revealedPasswordCountdowns.value = currentMap - credentialId
            return
        }

        val timeoutSec = preferences.value.revealTimeoutSeconds.coerceIn(5, 60)
        val job = viewModelScope.launch {
            for (remaining in timeoutSec downTo 1) {
                _revealedPasswordCountdowns.value = _revealedPasswordCountdowns.value + (credentialId to remaining)
                delay(1000L)
            }
            _revealedPasswordCountdowns.value = _revealedPasswordCountdowns.value - credentialId
            revealTimerJobs.remove(credentialId)
        }
        revealTimerJobs[credentialId] = job
    }

    /**
     * Copies a password to the system clipboard with Android 13+ IS_SENSITIVE flag and automatically
     * wipes the clipboard after `clipboardAutoClearSeconds` (default 30s).
     */
    fun copyCredentialSecretWithAutoClear(serviceTitle: String, encodedSecret: String) {
        val plain = repository.decryptVaultCredentialSecret(encodedSecret)
        if (plain.isBlank()) return
        repository.copyToClipboardSensitive(serviceTitle, plain)
        val clearSec = preferences.value.clipboardAutoClearSeconds.coerceIn(10, 120)
        _uiBannerMessage.value = "Copied '$serviceTitle' secret. Clipboard auto-clears in ${clearSec}s."

        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            for (remaining in clearSec downTo 1) {
                _clipboardClearCountdown.value = remaining
                delay(1000L)
            }
            repository.clearClipboard()
            _clipboardClearCountdown.value = 0
            _uiBannerMessage.value = "Clipboard automatically cleared for your privacy."
        }
    }

    fun decryptCredentialSecret(encodedSecret: String): String =
        repository.decryptVaultCredentialSecret(encodedSecret)

    fun generateStrongPassword(
        length: Int = 20,
        includeUppercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String = repository.generateStrongPassword(length, includeUppercase, includeDigits, includeSymbols)

    fun explainMatchRelevance(entry: VaultEntryEntity): String {
        val filter = _searchFilter.value
        return repository.explainMatchRelevance(filter.query, entry, filter.aiExpandedTerms)
    }

    fun saveSanctuaryEntry(
        existingId: Long? = null,
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
    ) {
        if (title.isBlank()) {
            _uiBannerMessage.value = "Please enter a title."
            return
        }
        viewModelScope.launch {
            try {
                repository.saveSanctuaryEntry(
                    userId = currentUserId,
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
                refreshStorageAndBatteryMetrics()
                _uiBannerMessage.value = "Saved to private on-device storage & indexed locally."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiBannerMessage.value = "Failed to save entry: ${e.localizedMessage}"
            }
        }
    }

    fun saveVaultCredential(
        existingId: Long? = null,
        serviceTitle: String,
        accountUsername: String,
        rawPassword: String,
        rawNotes: String,
        domainOrUrl: String,
        category: String
    ) {
        if (serviceTitle.isBlank() || rawPassword.isBlank()) {
            _uiBannerMessage.value = "Service title and password are required."
            return
        }
        viewModelScope.launch {
            try {
                repository.saveIsolatedVaultCredential(
                    existingId = existingId,
                    serviceTitle = serviceTitle,
                    accountUsername = accountUsername,
                    rawPassword = rawPassword,
                    rawNotes = rawNotes,
                    domainOrUrl = domainOrUrl,
                    category = category
                )
                _uiBannerMessage.value = "Encrypted with Android Keystore AES-256-GCM in isolated Vault DB."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiBannerMessage.value = "Failed to encrypt credential: ${e.localizedMessage}"
            }
        }
    }

    fun deleteVaultCredential(id: Long, title: String) {
        viewModelScope.launch {
            repository.deleteIsolatedVaultCredential(id)
            _uiBannerMessage.value = "Removed '$title' from isolated Password Vault."
        }
    }

    fun exportNoteToMarkdown(entry: VaultEntryEntity) {
        viewModelScope.launch {
            val path = repository.exportNoteAsMarkdownFile(entry)
            refreshStorageAndBatteryMetrics()
            _uiBannerMessage.value = "Exported Markdown to private storage: ${path.substringAfterLast("/")}"
        }
    }

    fun clearAllDerivedAiInsights() {
        viewModelScope.launch {
            repository.clearAllDerivedAiInsights()
            _uiBannerMessage.value = "Cleared all derived AI tags, summaries, and semantic keywords."
        }
    }

    fun importGalleryPhotoToPhoneStorage(
        uri: Uri,
        onCopied: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val internalPath = repository.copyPickedMediaToPrivateStorage(uri)
                refreshStorageAndBatteryMetrics()
                onCopied(internalPath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiBannerMessage.value = "Could not copy image to phone storage: ${e.localizedMessage}"
            }
        }
    }

    fun toggleFavorite(entry: VaultEntryEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(entry)
        }
    }

    fun deleteEntry(entry: VaultEntryEntity) {
        viewModelScope.launch {
            lastDeletedEntry = entry
            _canUndoDelete.value = true
            repository.deleteEntry(entry, isSignedInWithGoogle)
            refreshStorageAndBatteryMetrics()
            _uiBannerMessage.value = "Removed '${entry.title}' from local storage."
        }
    }

    fun undoLastDeletedEntry() {
        val restored = lastDeletedEntry ?: return
        lastDeletedEntry = null
        _canUndoDelete.value = false
        viewModelScope.launch {
            val type = runCatching { VaultItemType.valueOf(restored.itemType) }.getOrDefault(VaultItemType.NOTE)
            repository.saveSanctuaryEntry(
                userId = currentUserId,
                existingId = null,
                itemType = type,
                title = restored.title,
                primaryContent = restored.primaryContent,
                secondaryMetadata = restored.secretValue,
                websiteOrLocation = restored.websiteOrLocation,
                folderOrAlbum = restored.folderOrAlbum,
                mediaPath = restored.mediaPath,
                category = restored.category,
                moodTag = restored.moodTag,
                isFavorite = restored.isFavorite
            )
            refreshStorageAndBatteryMetrics()
            _uiBannerMessage.value = "Restored '${restored.title}'."
        }
    }

    fun triggerImmediateBackgroundSyncAndAi() {
        viewModelScope.launch {
            val prefs = preferences.value
            performIncrementalBackgroundWork(
                aiMode = prefs.aiExecutionMode,
                hasCloudConsent = prefs.hasConsentedToCloudAi,
                respectBattery = false,
                enableCloud = prefs.isCloudBackupEnabled,
                forceManual = true
            )
        }
    }

    fun analyzeSingleEntryWithAi(entry: VaultEntryEntity) {
        viewModelScope.launch {
            val prefs = preferences.value
            if (prefs.aiExecutionMode == AiExecutionMode.PAUSED) {
                _uiBannerMessage.value = "AI analysis is paused. Enable Local or Cloud AI in Settings."
                return@launch
            }
            _backgroundStatus.value = _backgroundStatus.value.copy(
                isAnalyzingOrSyncing = true,
                pillLabel = "Analyzing '${entry.title.take(14)}'..."
            )
            val result = repository.analyzeSingleEntryNow(
                entry = entry,
                aiMode = prefs.aiExecutionMode,
                hasCloudAiConsent = prefs.hasConsentedToCloudAi
            )
            if (result.isSuccess) {
                val modeDesc = if (prefs.aiExecutionMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN && prefs.hasConsentedToCloudAi) {
                    "Opt-In Cloud Gemini"
                } else {
                    "Local On-Device Engine"
                }
                _uiBannerMessage.value = "$modeDesc organized '${entry.title}'."
            }
            refreshStorageAndBatteryMetrics()
            _backgroundStatus.value = _backgroundStatus.value.copy(
                isAnalyzingOrSyncing = false,
                pillLabel = "Local Sanctuary Ready"
            )
        }
    }

    // Search & Advanced Filtering Actions
    fun updateSearchQuery(newQuery: String) {
        _searchFilter.value = _searchFilter.value.copy(
            query = newQuery,
            aiExpandedTerms = if (newQuery.isBlank()) emptyList() else _searchFilter.value.aiExpandedTerms,
            aiMatchedIds = if (newQuery.isBlank()) emptySet() else _searchFilter.value.aiMatchedIds,
            aiSearchInsight = if (newQuery.isBlank()) "" else _searchFilter.value.aiSearchInsight
        )
    }

    fun setFilterType(type: VaultItemType?) {
        // Never allow filtering by PASSWORD in Search
        val safeType = if (type == VaultItemType.PASSWORD) null else type
        _searchFilter.value = _searchFilter.value.copy(selectedType = safeType)
    }

    fun setFilterFolderOrAlbum(folder: String) {
        _searchFilter.value = _searchFilter.value.copy(selectedFolderOrAlbum = folder)
    }

    fun setFilterCategory(category: String) {
        _searchFilter.value = _searchFilter.value.copy(selectedCategory = category)
    }

    fun setFilterMood(mood: String) {
        _searchFilter.value = _searchFilter.value.copy(selectedMood = mood)
    }

    fun setFilterDateRange(range: DateRangeFilter) {
        _searchFilter.value = _searchFilter.value.copy(dateRange = range)
    }

    fun toggleFilterFavoritesOnly() {
        _searchFilter.value = _searchFilter.value.copy(
            onlyFavorites = !_searchFilter.value.onlyFavorites
        )
    }

    fun toggleFilterAnalyzedOnly() {
        _searchFilter.value = _searchFilter.value.copy(
            onlyAnalyzed = !_searchFilter.value.onlyAnalyzed
        )
    }

    fun clearAllSearchFilters() {
        _searchFilter.value = SearchFilterState()
    }

    fun runDeepAiSemanticSearch() {
        val q = _searchFilter.value.query.trim()
        if (q.isBlank()) {
            _uiBannerMessage.value = "Type a keyword, concept, or feeling to run Semantic Search."
            return
        }
        viewModelScope.launch {
            val prefs = preferences.value
            _searchFilter.value = _searchFilter.value.copy(isAiSearching = true)
            val result = repository.runSemanticSearch(
                query = q,
                entries = allEntries.value,
                aiMode = prefs.aiExecutionMode,
                hasCloudAiConsent = prefs.hasConsentedToCloudAi
            )
            if (result.isSuccess) {
                val expansion = result.getOrThrow()
                _searchFilter.value = _searchFilter.value.copy(
                    aiExpandedTerms = expansion.expandedTerms,
                    aiMatchedIds = expansion.matchedEntryIds.toSet(),
                    aiSearchInsight = expansion.mindfulSearchInsight,
                    isAiSearching = false
                )
            } else {
                _searchFilter.value = _searchFilter.value.copy(
                    isAiSearching = false,
                    aiSearchInsight = "Matched using local on-device FTS & concept vectors (Vault excluded)."
                )
            }
        }
    }

    // Preferences updates
    fun setThemeMode(mode: ThemeModePreference) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setReducedMotionEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setReducedMotionEnabled(enabled) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBiometricLockEnabled(enabled)
            if (enabled) _isBiometricUnlocked.value = false
        }
    }

    fun updateVaultPasscode(newPin: String) {
        viewModelScope.launch {
            preferencesManager.setVaultPasscode(newPin)
            _uiBannerMessage.value = "Updated 4-digit Vault PIN."
        }
    }

    fun setAiExecutionMode(mode: AiExecutionMode, cloudConsentGranted: Boolean = false) {
        viewModelScope.launch {
            preferencesManager.setAiExecutionMode(mode, cloudConsentGranted)
            val msg = when (mode) {
                AiExecutionMode.LOCAL_ON_DEVICE -> "Switched to 100% On-Device AI Tagging & Search."
                AiExecutionMode.CLOUD_GEMINI_OPT_IN -> "Opt-In Cloud AI enabled for Notes & Gallery (Vault passwords never leave device)."
                AiExecutionMode.PAUSED -> "AI analysis paused."
            }
            _uiBannerMessage.value = msg
        }
    }

    fun setBatterySaverSyncOnly(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBatterySaverSyncOnly(enabled) }
    }

    fun setCloudBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCloudBackupEnabled(enabled)
            _uiBannerMessage.value = if (enabled) {
                "Opt-in encrypted Cloud Backup enabled for Notes & Memories (Passwords remain local-only)."
            } else {
                "Cloud Backup disabled. All data remains strictly on-device."
            }
        }
    }

    fun setAutofillOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutofillOverlayEnabled(enabled)
            _uiBannerMessage.value = if (enabled) {
                "Seam Autofill & Save Password overlay enabled."
            } else {
                "Seam Autofill & Save Password overlay paused."
            }
        }
    }

    fun showBannerMessage(message: String) {
        _uiBannerMessage.value = message
    }

    fun dismissBannerMessage() {
        _uiBannerMessage.value = null
    }
}

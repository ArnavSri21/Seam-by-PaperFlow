package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import com.example.core.security.KeystoreVaultCrypto
import com.example.data.ai.LocalAiAnalyzer
import com.example.data.ai.MindfulAiService
import com.example.data.ai.SemanticSearchExpansion
import com.example.data.cloud.CloudSyncRepository
import com.example.data.local.IsolatedVaultDao
import com.example.data.local.PrivatePhoneStorageManager
import com.example.data.local.VaultCredentialEntity
import com.example.data.local.VaultDao
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.AiExecutionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DeviceBatteryHealth(
    val batteryPct: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean
) {
    val isBatteryOptimalForBackgroundWork: Boolean
        get() = !isPowerSaveMode && (isCharging || batteryPct >= 20)
}

data class BackgroundSyncSummary(
    val analyzedCount: Int,
    val cloudSyncedCount: Int,
    val localSnapshotSaved: Boolean,
    val skippedDueToBatterySaver: Boolean,
    val statusMessage: String
)

/**
 * Coordinates Seam by PaperFlow's strict two-boundary architecture:
 * 1. Sanctuary Database (`VaultDao` + `PrivatePhoneStorageManager`): Notes/Reflections & Gallery Memories.
 * 2. Isolated Password Vault Database (`IsolatedVaultDao` + `KeystoreVaultCrypto`): Android Keystore AES-GCM
 *    encrypted credentials that NEVER touch AI, cloud sync, or semantic search indexes.
 */
class PaperFlowRepository(
    private val context: Context,
    private val vaultDao: VaultDao,
    private val isolatedVaultDao: IsolatedVaultDao,
    private val keystoreCrypto: KeystoreVaultCrypto,
    private val storageManager: PrivatePhoneStorageManager,
    private val cloudSyncRepository: CloudSyncRepository,
    private val aiService: MindfulAiService,
    private val localAiAnalyzer: LocalAiAnalyzer = LocalAiAnalyzer()
) {

    // Sanctuary stream: Notes & Gallery ONLY (Passwords live in IsolatedVaultDao)
    fun observeSanctuaryEntries(): Flow<List<VaultEntryEntity>> =
        vaultDao.observeEntriesForSanctuary()

    // Isolated Password Vault stream
    fun observeIsolatedVaultCredentials(): Flow<List<VaultCredentialEntity>> =
        isolatedVaultDao.observeAllCredentials()

    fun getDeviceBatteryHealth(): DeviceBatteryHealth {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 1..100 } ?: 88

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val isPowerSave = pm?.isPowerSaveMode ?: false

        return DeviceBatteryHealth(
            batteryPct = pct,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSave
        )
    }

    fun getPrivateStorageStats(): Pair<Int, Long> = storageManager.getPrivateStorageStats()

    fun decryptVaultCredentialSecret(encodedSecret: String): String =
        keystoreCrypto.decryptSecret(encodedSecret)

    fun generateStrongPassword(
        length: Int = 20,
        includeUppercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String = keystoreCrypto.generateStrongPassword(
        length = length,
        includeUppercase = includeUppercase,
        includeDigits = includeDigits,
        includeSymbols = includeSymbols
    )

    fun copyToClipboardSensitive(label: String, secret: String) {
        keystoreCrypto.copySecretWithSensitiveFlag(label, secret)
    }

    fun clearClipboard() {
        keystoreCrypto.clearClipboard()
    }

    fun explainMatchRelevance(
        query: String,
        entry: VaultEntryEntity,
        aiExpandedTerms: List<String>
    ): String = localAiAnalyzer.explainMatchRelevance(query, entry, aiExpandedTerms)

    suspend fun copyPickedMediaToPrivateStorage(uri: Uri): String =
        storageManager.copyPickedUriToPrivateStorage(uri)

    /**
     * Seeds initial mindful entries into local phone storage on first run so the app works
     * 100% offline immediately without requiring Google sign-in.
     */
    suspend fun ensureInitialSanctuarySeeded(userId: String) = withContext(Dispatchers.IO) {
        val existingSanctuary = vaultDao.getEntriesOnce()
        if (existingSanctuary.isEmpty()) {
            val artPath1 = storageManager.ensureMindfulArtworkFile(
                fileName = "seam_constellation_01.jpg",
                primaryColor = 0xFF0A0E17.toInt(),
                secondaryColor = 0xFF161F30.toInt(),
                accentColor = 0xFFB8B5FF.toInt(),
                styleIndex = 1
            )
            val artPath2 = storageManager.ensureMindfulArtworkFile(
                fileName = "seam_editorial_solstice_02.jpg",
                primaryColor = 0xFF121016.toInt(),
                secondaryColor = 0xFF282332.toInt(),
                accentColor = 0xFFE5C185.toInt(),
                styleIndex = 2
            )

            val now = System.currentTimeMillis()
            val sampleSanctuaryEntries = listOf(
                VaultEntryEntity(
                    cloudId = "seed_note_${UUID.randomUUID().toString().take(8)}",
                    userId = userId,
                    itemType = VaultItemType.NOTE.name,
                    title = "Midnight Stillness & Creative Clarity",
                    primaryContent = "# Midnight Reflection\nWhen the room settles after midnight, ideas stop competing for attention and begin to flow like ink on warm paper.\n\n- Breathe before opening the editor\n- Keep personal reflections local-first",
                    secretValue = "Markdown • 42 words",
                    websiteOrLocation = "Home Study • 01:15 AM",
                    folderOrAlbum = "Night Journal",
                    mediaPath = "raw://liquid_reflection",
                    ocrExtractedText = "Lectures Swiss National Museum Zürich HGK Basel",
                    category = "Mindfulness",
                    moodTag = "Serene",
                    aiSummary = "A late-night Markdown reflection on quietude, breathwork, and local-first creative focus.",
                    aiKeywords = "midnight, stillness, calm, meditation, peace, insomnia, night, focus, writing, journal, creativity, breath, tranquil, silence",
                    isFavorite = true,
                    isBiometricProtected = false,
                    isAnalyzed = true,
                    isCloudSynced = false,
                    createdAtMillis = now - 86_400_000L * 2,
                    updatedAtMillis = now - 86_400_000L * 2
                ),
                VaultEntryEntity(
                    cloudId = "seed_note_${UUID.randomUUID().toString().take(8)}",
                    userId = userId,
                    itemType = VaultItemType.NOTE.name,
                    title = "Architecture of a Slower Morning",
                    primaryContent = "Replaced the morning scroll with twenty minutes of tea, sketching concentric wire rings, and writing three calm intentions.",
                    secretValue = "Markdown • 26 words",
                    websiteOrLocation = "Sunroom Terrace",
                    folderOrAlbum = "Daily Rituals",
                    mediaPath = "",
                    ocrExtractedText = "",
                    category = "Gratitude",
                    moodTag = "Grounded",
                    aiSummary = "Establishing an intentional morning ritual centered on tea, sketching, and digital minimalism.",
                    aiKeywords = "morning, routine, ritual, tea, habit, digital detox, intention, gratitude, slow living, mindfulness, breakfast, peace",
                    isFavorite = false,
                    isBiometricProtected = false,
                    isAnalyzed = true,
                    isCloudSynced = false,
                    createdAtMillis = now - 86_400_000L,
                    updatedAtMillis = now - 86_400_000L
                ),
                VaultEntryEntity(
                    cloudId = "seed_gallery_${UUID.randomUUID().toString().take(8)}",
                    userId = userId,
                    itemType = VaultItemType.GALLERY.name,
                    title = "Cerulean Tide & Orbital Constellation",
                    primaryContent = "Captured the feeling of ocean ripples converging under moonlight. Stored automatically in app-private storage.",
                    secretValue = "900×700px • ISO 400 • 35mm f/1.8",
                    websiteOrLocation = "Pacific Coastline",
                    folderOrAlbum = "Coastal Archive",
                    mediaPath = artPath1,
                    ocrExtractedText = "900×700px • On-Device Indexed (Cerulean Tide)",
                    category = "Nature",
                    moodTag = "Luminous",
                    aiSummary = "Visual study of deep ocean blues and concentric harmonic rings evoking coastal calm.",
                    aiKeywords = "ocean, sea, water, waves, coast, blue, moonlight, tide, beach, horizon, photography, visual, memory, calm, nature",
                    isFavorite = true,
                    isBiometricProtected = false,
                    isAnalyzed = true,
                    isCloudSynced = false,
                    createdAtMillis = now - 43_200_000L,
                    updatedAtMillis = now - 43_200_000L
                ),
                VaultEntryEntity(
                    cloudId = "seed_gallery_${UUID.randomUUID().toString().take(8)}",
                    userId = userId,
                    itemType = VaultItemType.GALLERY.name,
                    title = "Zürich Monochrome Exhibition Index",
                    primaryContent = "Warm parchment light intersecting dark stone textures and Swiss typographic posters.",
                    secretValue = "900×700px • Golden Hour Archive",
                    websiteOrLocation = "Zürich Design Pavilion",
                    folderOrAlbum = "Exhibitions",
                    mediaPath = artPath2,
                    ocrExtractedText = "Swiss National Museum Zürich 7. Oct HGK Basel",
                    category = "Architecture",
                    moodTag = "Contemplative",
                    aiSummary = "Editorial monochrome exhibition study inspired by Swiss museum typography and twilight geometry.",
                    aiKeywords = "sunset, autumn, gold, amber, architecture, design, zürich, basel, geometry, warm, light, dusk, art, gallery, photo",
                    isFavorite = false,
                    isBiometricProtected = false,
                    isAnalyzed = true,
                    isCloudSynced = false,
                    createdAtMillis = now - 21_600_000L,
                    updatedAtMillis = now - 21_600_000L
                )
            )
            sampleSanctuaryEntries.forEach { vaultDao.insertEntry(it) }
            writeLocalAutoSnapshot(userId)
        }

        // Seed Isolated Password Vault (Separate DB boundary, Keystore AES-GCM encrypted)
        val existingCredentials = isolatedVaultDao.getAllCredentialsOnce()
        if (existingCredentials.isEmpty()) {
            val now = System.currentTimeMillis()
            val pass1 = "Seam#Obsidian!9942-Key"
            val pass2 = "Zürich$7721-PaperFlow*"
            val sampleCredentials = listOf(
                VaultCredentialEntity(
                    serviceTitle = "GitHub Production Signing Key",
                    accountUsername = "architect@paperflow.dev",
                    encryptedPassword = keystoreCrypto.encryptSecret(pass1),
                    encryptedNotes = keystoreCrypto.encryptSecret("Hardware security key backup token stored offline."),
                    domainOrUrl = "https://github.com/settings/keys",
                    category = "Developer",
                    reuseFingerprint = keystoreCrypto.computeLocalReuseFingerprint(pass1),
                    createdAtMillis = now - 14_400_000L,
                    updatedAtMillis = now - 14_400_000L
                ),
                VaultCredentialEntity(
                    serviceTitle = "Zürich Private Archive Portal",
                    accountUsername = "seam_keeper_8841",
                    encryptedPassword = keystoreCrypto.encryptSecret(pass2),
                    encryptedNotes = keystoreCrypto.encryptSecret("Primary archival vault credential."),
                    domainOrUrl = "https://archive.paperflow.ch",
                    category = "Archival",
                    reuseFingerprint = keystoreCrypto.computeLocalReuseFingerprint(pass2),
                    createdAtMillis = now - 7_200_000L,
                    updatedAtMillis = now - 7_200_000L
                )
            )
            sampleCredentials.forEach { isolatedVaultDao.insertCredential(it) }
        }
    }

    suspend fun saveSanctuaryEntry(
        userId: String,
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
    ): Long = withContext(Dispatchers.IO) {
        require(itemType != VaultItemType.PASSWORD) {
            "Passwords must be saved via saveIsolatedVaultCredential in the separate Vault DB boundary."
        }
        val now = System.currentTimeMillis()
        val existing = if (existingId != null && existingId > 0L) {
            vaultDao.getEntryById(existingId)
        } else null

        val extractedOcr = if (itemType == VaultItemType.GALLERY) {
            localAiAnalyzer.extractLocalMediaMetadataAndOcr(mediaPath, title)
        } else {
            existing?.ocrExtractedText ?: ""
        }

        val draftEntry = VaultEntryEntity(
            id = existing?.id ?: 0L,
            cloudId = existing?.cloudId?.ifBlank { "seam_${UUID.randomUUID().toString().replace("-", "_")}" }
                ?: "seam_${UUID.randomUUID().toString().replace("-", "_")}",
            userId = userId,
            itemType = itemType.name,
            title = title.trim(),
            primaryContent = primaryContent.trim(),
            secretValue = secondaryMetadata.trim(),
            websiteOrLocation = websiteOrLocation.trim(),
            folderOrAlbum = folderOrAlbum.trim().ifBlank {
                if (itemType == VaultItemType.GALLERY) "Timeline" else "Reflections"
            },
            mediaPath = mediaPath,
            ocrExtractedText = extractedOcr,
            category = category.ifBlank { "Mindfulness" },
            moodTag = moodTag.ifBlank { "Serene" },
            aiSummary = existing?.aiSummary ?: "",
            aiKeywords = existing?.aiKeywords ?: "",
            isFavorite = isFavorite,
            isBiometricProtected = false,
            isAnalyzed = false,
            isCloudSynced = false,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now
        )

        // Run fast local on-device tagging immediately so offline search works right away
        val localAnalysis = localAiAnalyzer.analyzeLocally(draftEntry)
        val enrichedEntry = draftEntry.copy(
            category = localAnalysis.category,
            moodTag = localAnalysis.moodTag,
            aiSummary = localAnalysis.aiSummary,
            aiKeywords = localAnalysis.aiKeywords,
            isAnalyzed = true
        )

        val savedId = vaultDao.insertEntry(enrichedEntry)
        writeLocalAutoSnapshot(userId)
        savedId
    }

    suspend fun saveIsolatedVaultCredential(
        existingId: Long?,
        serviceTitle: String,
        accountUsername: String,
        rawPassword: String,
        rawNotes: String,
        domainOrUrl: String,
        category: String
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val encryptedPass = keystoreCrypto.encryptSecret(rawPassword)
        val encryptedNotes = keystoreCrypto.encryptSecret(rawNotes)
        val fingerprint = keystoreCrypto.computeLocalReuseFingerprint(rawPassword)

        val entity = VaultCredentialEntity(
            id = existingId ?: 0L,
            serviceTitle = serviceTitle.trim(),
            accountUsername = accountUsername.trim(),
            encryptedPassword = encryptedPass,
            encryptedNotes = encryptedNotes,
            domainOrUrl = domainOrUrl.trim(),
            category = category.trim().ifBlank { "Security" },
            reuseFingerprint = fingerprint,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        isolatedVaultDao.insertCredential(entity)
    }

    suspend fun deleteIsolatedVaultCredential(id: Long) = withContext(Dispatchers.IO) {
        isolatedVaultDao.deleteCredentialById(id)
    }

    suspend fun toggleFavorite(entry: VaultEntryEntity) = withContext(Dispatchers.IO) {
        vaultDao.updateEntry(
            entry.copy(
                isFavorite = !entry.isFavorite,
                isCloudSynced = false,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        writeLocalAutoSnapshot(entry.userId)
    }

    suspend fun deleteEntry(entry: VaultEntryEntity, isSignedInWithGoogle: Boolean) = withContext(Dispatchers.IO) {
        vaultDao.deleteEntryById(entry.id)
        if (isSignedInWithGoogle && entry.cloudId.isNotBlank() && entry.isCloudSynced) {
            cloudSyncRepository.deleteVaultItem(entry.cloudId)
        }
        writeLocalAutoSnapshot(entry.userId)
    }

    suspend fun clearAllDerivedAiInsights() = withContext(Dispatchers.IO) {
        vaultDao.clearAllDerivedAiInsights()
    }

    suspend fun exportNoteAsMarkdownFile(entry: VaultEntryEntity): String = withContext(Dispatchers.IO) {
        val md = buildString {
            appendLine("# ${entry.title}")
            appendLine()
            appendLine("- **Folder**: ${entry.folderOrAlbum}")
            appendLine("- **Category**: ${entry.category} • **Mood**: ${entry.moodTag}")
            if (entry.websiteOrLocation.isNotBlank()) {
                appendLine("- **Context**: ${entry.websiteOrLocation}")
            }
            appendLine()
            appendLine(entry.primaryContent)
            if (entry.aiSummary.isNotBlank()) {
                appendLine()
                appendLine("---")
                appendLine("> *Derived Summary*: ${entry.aiSummary}")
            }
        }
        storageManager.writeAutoSnapshotJson("markdown_export_${entry.id}", md)
    }

    /**
     * Incremental, resumable background analysis & optional opt-in cloud sync.
     * Strictly respects:
     * - AI Execution Mode (Paused vs Local On-Device vs Opt-In Cloud Gemini)
     * - Cloud Backup Opt-In (Disabled by default; never uploads passwords)
     * - Battery constraints
     */
    suspend fun runBackgroundAnalysisAndSync(
        userId: String,
        aiMode: AiExecutionMode,
        hasCloudAiConsent: Boolean,
        respectBatterySaver: Boolean,
        enableCloudBackup: Boolean,
        isSignedInWithGoogle: Boolean,
        forceManualRun: Boolean = false
    ): BackgroundSyncSummary = withContext(Dispatchers.IO) {
        val battery = getDeviceBatteryHealth()
        if (!forceManualRun && respectBatterySaver && !battery.isBatteryOptimalForBackgroundWork) {
            return@withContext BackgroundSyncSummary(
                analyzedCount = 0,
                cloudSyncedCount = 0,
                localSnapshotSaved = true,
                skippedDueToBatterySaver = true,
                statusMessage = "Paused background work to preserve battery (${battery.batteryPct}%)."
            )
        }

        var analyzedCount = 0
        if (aiMode != AiExecutionMode.PAUSED) {
            val targetEntries = if (forceManualRun) {
                vaultDao.getEntriesOnce().take(5)
            } else {
                vaultDao.getUnanalyzedEntries().take(4)
            }

            for (item in targetEntries) {
                // Privacy Hardening: Never analyze PASSWORD items
                if (item.itemType == VaultItemType.PASSWORD.name) continue

                val analysis = if (aiMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN && hasCloudAiConsent) {
                    val cloudRes = aiService.analyzeEntry(item)
                    cloudRes.getOrElse { localAiAnalyzer.analyzeLocally(item) }
                } else {
                    localAiAnalyzer.analyzeLocally(item)
                }

                analyzedCount++
                vaultDao.updateEntry(
                    item.copy(
                        category = analysis.category.ifBlank { item.category },
                        moodTag = analysis.moodTag.ifBlank { item.moodTag },
                        aiSummary = analysis.aiSummary.ifBlank { item.aiSummary },
                        aiKeywords = analysis.aiKeywords.ifBlank { item.aiKeywords },
                        isAnalyzed = true,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        writeLocalAutoSnapshot(userId)

        // Optional E2EE Cloud Backup for Notes/Gallery ONLY when explicitly enabled AND signed in
        var syncedCount = 0
        if (enableCloudBackup && isSignedInWithGoogle) {
            val unsynced = vaultDao.getUnsyncedEntries()
            for (item in unsynced.take(5)) {
                if (item.itemType == VaultItemType.PASSWORD.name) continue
                val encryptedCloudPayload = storageManager.encryptSecret(
                    "${item.primaryContent}|${item.websiteOrLocation}",
                    userId
                )
                val cloudRes = cloudSyncRepository.createVaultItem(
                    itemId = item.cloudId.ifBlank { "seam_${item.id}" },
                    itemType = item.itemType,
                    title = item.title.take(190).ifBlank { "Untitled" },
                    category = item.category.take(90).ifBlank { "Mindful" },
                    aiSummary = item.aiSummary.take(1900),
                    aiKeywords = item.aiKeywords.take(1900),
                    moodTag = item.moodTag.take(90).ifBlank { "Serene" },
                    encryptedPayload = encryptedCloudPayload.take(19000),
                    isAnalyzed = item.isAnalyzed
                )
                if (cloudRes.isSuccess) {
                    syncedCount++
                    vaultDao.updateEntry(item.copy(isCloudSynced = true))
                }
            }
        }

        val modeLabel = when (aiMode) {
            AiExecutionMode.PAUSED -> "AI Paused"
            AiExecutionMode.LOCAL_ON_DEVICE -> "On-Device AI"
            AiExecutionMode.CLOUD_GEMINI_OPT_IN -> "Opt-In Cloud AI"
        }

        val statusMsg = when {
            aiMode == AiExecutionMode.PAUSED ->
                "AI indexing is paused by user preference. All entries stored locally."
            analyzedCount > 0 && syncedCount > 0 ->
                "$modeLabel indexed $analyzedCount entries & synced $syncedCount encrypted backups."
            analyzedCount > 0 ->
                "$modeLabel organized $analyzedCount entries in private phone storage."
            else ->
                "$modeLabel ready • Vault isolated & encrypted on device."
        }

        BackgroundSyncSummary(
            analyzedCount = analyzedCount,
            cloudSyncedCount = syncedCount,
            localSnapshotSaved = true,
            skippedDueToBatterySaver = false,
            statusMessage = statusMsg
        )
    }

    suspend fun analyzeSingleEntryNow(
        entry: VaultEntryEntity,
        aiMode: AiExecutionMode,
        hasCloudAiConsent: Boolean
    ): Result<VaultEntryEntity> = withContext(Dispatchers.IO) {
        if (entry.itemType == VaultItemType.PASSWORD.name) {
            return@withContext Result.failure(IllegalArgumentException("Passwords are never sent to AI."))
        }
        val analysis = if (aiMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN && hasCloudAiConsent) {
            val cloudResult = aiService.analyzeEntry(entry)
            cloudResult.getOrElse { localAiAnalyzer.analyzeLocally(entry) }
        } else {
            localAiAnalyzer.analyzeLocally(entry)
        }
        val updated = entry.copy(
            category = analysis.category.ifBlank { entry.category },
            moodTag = analysis.moodTag.ifBlank { entry.moodTag },
            aiSummary = analysis.aiSummary,
            aiKeywords = analysis.aiKeywords,
            isAnalyzed = true,
            updatedAtMillis = System.currentTimeMillis()
        )
        vaultDao.updateEntry(updated)
        writeLocalAutoSnapshot(entry.userId)
        Result.success(updated)
    }

    suspend fun runSemanticSearch(
        query: String,
        entries: List<VaultEntryEntity>,
        aiMode: AiExecutionMode,
        hasCloudAiConsent: Boolean
    ): Result<SemanticSearchExpansion> = withContext(Dispatchers.IO) {
        val safeEntries = entries.filter { it.itemType != VaultItemType.PASSWORD.name }
        if (aiMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN && hasCloudAiConsent) {
            val cloudRes = aiService.performSemanticSearch(query, safeEntries)
            if (cloudRes.isSuccess) return@withContext cloudRes
        }

        // Local semantic search expansion (100% offline)
        val q = query.trim().lowercase()
        val scored = safeEntries.map { entry ->
            entry to localAiAnalyzer.computeLocalSemanticSimilarity(q, entry)
        }.filter { it.second > 0.08f }
            .sortedByDescending { it.second }

        val localExpandedTerms = safeEntries
            .flatMap { it.aiKeywords.split(",") }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && (it.contains(q) || q.contains(it)) }
            .distinct()
            .take(8)

        Result.success(
            SemanticSearchExpansion(
                expandedTerms = localExpandedTerms.ifEmpty { listOf(q, "mindful", "reflection", "memory") },
                matchedEntryIds = scored.map { it.first.id },
                mindfulSearchInsight = "Local on-device FTS & trigram vector index matched ${scored.size} sanctuary entries (Password Vault strictly excluded)."
            )
        )
    }

    private suspend fun writeLocalAutoSnapshot(userId: String) {
        val allEntries = vaultDao.getEntriesOnce()
        val array = JSONArray()
        allEntries.forEach { item ->
            if (item.itemType != VaultItemType.PASSWORD.name) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("itemType", item.itemType)
                    put("title", item.title)
                    put("folderOrAlbum", item.folderOrAlbum)
                    put("category", item.category)
                    put("moodTag", item.moodTag)
                    put("aiSummary", item.aiSummary)
                    put("aiKeywords", item.aiKeywords)
                    put("updatedAtMillis", item.updatedAtMillis)
                }
                array.put(obj)
            }
        }
        storageManager.writeAutoSnapshotJson(userId.ifBlank { "local_keeper" }, array.toString())
    }
}

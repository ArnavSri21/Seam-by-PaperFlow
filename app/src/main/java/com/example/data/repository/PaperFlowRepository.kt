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
import com.example.data.local.DevicePhotoAsset
import com.example.data.local.IsolatedVaultDao
import com.example.data.local.PrivatePhoneStorageManager
import com.example.data.local.VaultCredentialEntity
import com.example.data.local.VaultDao
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.AiExecutionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

data class AutoImportResult(
    val newlyImportedCount: Int,
    val aiTaggedCount: Int,
    val skippedDuplicateCount: Int,
    val aiErrorMessage: String? = null
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
     * Launch-ready initialization: never seeds demo/preloaded notes, gallery items, or passwords.
     * Also cleans up any legacy `seed_` entries or sample artwork files if present from earlier builds.
     */
    suspend fun ensureInitialSanctuarySeeded(userId: String) = withContext(Dispatchers.IO) {
        storageManager.cleanupPreloadedSampleFiles()
        val existingSanctuary = vaultDao.getEntriesOnce()
        existingSanctuary
            .filter { it.cloudId.startsWith("seed_note_") || it.cloudId.startsWith("seed_gallery_") }
            .forEach { vaultDao.deleteEntryById(it.id) }

        val existingCredentials = isolatedVaultDao.getAllCredentialsOnce()
        existingCredentials
            .filter {
                it.serviceTitle == "GitHub Production Signing Key" ||
                    it.serviceTitle == "Zürich Private Archive Portal"
            }
            .forEach { isolatedVaultDao.deleteCredentialById(it.id) }
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
        val strengthScore = keystoreCrypto.computePasswordStrengthScore(rawPassword)

        val entity = VaultCredentialEntity(
            id = existingId ?: 0L,
            serviceTitle = serviceTitle.trim(),
            accountUsername = accountUsername.trim(),
            encryptedPassword = encryptedPass,
            encryptedNotes = encryptedNotes,
            domainOrUrl = domainOrUrl.trim(),
            category = category.trim().ifBlank { "Security" },
            reuseFingerprint = fingerprint,
            passwordStrengthScore = strengthScore,
            maskedPreview = "••••••••••••",
            encryptionAlgorithm = "AES-256-GCM",
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
            aiKeywords = mergeKeywords(analysis.aiKeywords, localAiAnalyzer.analyzeLocally(entry).aiKeywords),
            isAnalyzed = true,
            updatedAtMillis = System.currentTimeMillis()
        )
        vaultDao.updateEntry(updated)
        writeLocalAutoSnapshot(entry.userId)
        Result.success(updated)
    }

    suspend fun autoImportDevicePhotos(
        userId: String,
        assets: List<DevicePhotoAsset>,
        runGeminiVision: Boolean,
        onProgress: (processed: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): AutoImportResult = withContext(Dispatchers.IO) {
        val existingGallery = vaultDao.getEntriesOnce()
            .filter { it.itemType == VaultItemType.GALLERY.name }
        val existingKeys = existingGallery.mapNotNull { entry ->
            entry.secretValue.takeIf { it.startsWith("mediastore:") }
        }.toHashSet()
        val existingTitleDates = existingGallery.map { "${it.title}|${it.createdAtMillis}" }.toHashSet()

        val newAssets = assets.filter { asset ->
            !existingKeys.contains(asset.dedupeKey) &&
                !existingTitleDates.contains("${cleanPhotoTitle(asset.displayName, asset.mediaStoreId)}|${asset.dateTakenMillis}")
        }
        val skippedCount = assets.size - newAssets.size
        if (newAssets.isEmpty()) {
            return@withContext AutoImportResult(
                newlyImportedCount = 0,
                aiTaggedCount = 0,
                skippedDuplicateCount = skippedCount
            )
        }

        var imported = 0
        var aiTagged = 0
        var lastAiError: String? = null
        val total = newAssets.size

        newAssets.forEachIndexed { idx, asset ->
            val title = cleanPhotoTitle(asset.displayName, asset.mediaStoreId)
            onProgress(idx + 1, total, "Importing & AI-tagging $title…")
            try {
                val copiedPath = storageManager.copyPickedUriToPrivateStorage(asset.contentUri)
                val destFile = File(copiedPath)
                if (!destFile.exists() || destFile.length() == 0L) return@forEachIndexed

                val ocrMeta = localAiAnalyzer.extractLocalMediaMetadataAndOcr(copiedPath, title)
                val baseEntry = VaultEntryEntity(
                    cloudId = "seam_${UUID.randomUUID().toString().replace("-", "_")}",
                    userId = userId,
                    itemType = VaultItemType.GALLERY.name,
                    title = title,
                    primaryContent = "",
                    secretValue = asset.dedupeKey,
                    websiteOrLocation = "",
                    folderOrAlbum = asset.albumName.ifBlank { "Recents" },
                    mediaPath = copiedPath,
                    ocrExtractedText = ocrMeta,
                    category = "Moments",
                    moodTag = "Luminous",
                    createdAtMillis = asset.dateTakenMillis,
                    updatedAtMillis = System.currentTimeMillis()
                )

                val localAnalysis = localAiAnalyzer.analyzeLocally(baseEntry)
                var finalEntry = baseEntry.copy(
                    category = localAnalysis.category,
                    moodTag = localAnalysis.moodTag,
                    aiSummary = localAnalysis.aiSummary,
                    aiKeywords = localAnalysis.aiKeywords,
                    isAnalyzed = true
                )

                if (runGeminiVision) {
                    val cloudResult = aiService.analyzeEntry(finalEntry)
                    if (cloudResult.isSuccess) {
                        val ai = cloudResult.getOrThrow()
                        finalEntry = finalEntry.copy(
                            category = ai.category.ifBlank { finalEntry.category },
                            moodTag = ai.moodTag.ifBlank { finalEntry.moodTag },
                            aiSummary = ai.aiSummary.ifBlank { finalEntry.aiSummary },
                            aiKeywords = mergeKeywords(ai.aiKeywords, localAnalysis.aiKeywords),
                            isAnalyzed = true
                        )
                        aiTagged++
                    } else {
                        lastAiError = cloudResult.exceptionOrNull()?.localizedMessage
                    }
                } else {
                    aiTagged++
                }

                vaultDao.insertEntry(finalEntry)
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Skip unreadable/corrupt system URI cleanly
            }
        }

        if (imported > 0) {
            writeLocalAutoSnapshot(userId)
        }

        AutoImportResult(
            newlyImportedCount = imported,
            aiTaggedCount = aiTagged,
            skippedDuplicateCount = skippedCount,
            aiErrorMessage = lastAiError
        )
    }

    suspend fun importPickedUrisWithAi(
        userId: String,
        uris: List<Uri>,
        runGeminiVision: Boolean,
        onProgress: (processed: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): AutoImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var aiTagged = 0
        var lastAiError: String? = null
        val total = uris.size

        uris.forEachIndexed { idx, uri ->
            val label = uri.lastPathSegment?.substringAfterLast("/")?.substringBeforeLast(".")
                ?.takeIf { it.isNotBlank() } ?: "Photo ${idx + 1}"
            onProgress(idx + 1, total, "Adding & AI-tagging $label…")
            try {
                val copiedPath = storageManager.copyPickedUriToPrivateStorage(uri)
                val destFile = File(copiedPath)
                if (!destFile.exists() || destFile.length() == 0L) return@forEachIndexed

                val now = System.currentTimeMillis()
                val baseEntry = VaultEntryEntity(
                    cloudId = "seam_${UUID.randomUUID().toString().replace("-", "_")}",
                    userId = userId,
                    itemType = VaultItemType.GALLERY.name,
                    title = label,
                    primaryContent = "",
                    secretValue = "picker:${uri.toString().hashCode()}",
                    folderOrAlbum = "Visual Archive",
                    mediaPath = copiedPath,
                    ocrExtractedText = localAiAnalyzer.extractLocalMediaMetadataAndOcr(copiedPath, label),
                    category = "Moments",
                    moodTag = "Luminous",
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                val localAnalysis = localAiAnalyzer.analyzeLocally(baseEntry)
                var finalEntry = baseEntry.copy(
                    category = localAnalysis.category,
                    moodTag = localAnalysis.moodTag,
                    aiSummary = localAnalysis.aiSummary,
                    aiKeywords = localAnalysis.aiKeywords,
                    isAnalyzed = true
                )

                if (runGeminiVision) {
                    val cloudRes = aiService.analyzeEntry(finalEntry)
                    if (cloudRes.isSuccess) {
                        val ai = cloudRes.getOrThrow()
                        finalEntry = finalEntry.copy(
                            category = ai.category.ifBlank { finalEntry.category },
                            moodTag = ai.moodTag.ifBlank { finalEntry.moodTag },
                            aiSummary = ai.aiSummary.ifBlank { finalEntry.aiSummary },
                            aiKeywords = mergeKeywords(ai.aiKeywords, localAnalysis.aiKeywords)
                        )
                        aiTagged++
                    } else {
                        lastAiError = cloudRes.exceptionOrNull()?.localizedMessage
                    }
                } else {
                    aiTagged++
                }

                vaultDao.insertEntry(finalEntry)
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Continue batch gracefully
            }
        }

        if (imported > 0) {
            writeLocalAutoSnapshot(userId)
        }

        AutoImportResult(
            newlyImportedCount = imported,
            aiTaggedCount = aiTagged,
            skippedDuplicateCount = 0,
            aiErrorMessage = lastAiError
        )
    }

    suspend fun batchAiTagMemories(
        userId: String,
        useGeminiVision: Boolean,
        onProgress: (processed: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): AutoImportResult = withContext(Dispatchers.IO) {
        val galleryItems = vaultDao.getEntriesOnce()
            .filter { it.itemType == VaultItemType.GALLERY.name }
        if (galleryItems.isEmpty()) {
            return@withContext AutoImportResult(0, 0, 0)
        }

        var tagged = 0
        var lastAiError: String? = null
        val total = galleryItems.size

        galleryItems.forEachIndexed { idx, item ->
            onProgress(idx + 1, total, "AI indexing '${item.title.take(18)}'…")
            val localAnalysis = localAiAnalyzer.analyzeLocally(item)
            var updated = item.copy(
                category = localAnalysis.category.ifBlank { item.category },
                moodTag = localAnalysis.moodTag.ifBlank { item.moodTag },
                aiSummary = item.aiSummary.ifBlank { localAnalysis.aiSummary },
                aiKeywords = mergeKeywords(item.aiKeywords, localAnalysis.aiKeywords),
                isAnalyzed = true,
                updatedAtMillis = System.currentTimeMillis()
            )

            if (useGeminiVision) {
                val cloudRes = aiService.analyzeEntry(updated)
                if (cloudRes.isSuccess) {
                    val ai = cloudRes.getOrThrow()
                    updated = updated.copy(
                        category = ai.category.ifBlank { updated.category },
                        moodTag = ai.moodTag.ifBlank { updated.moodTag },
                        aiSummary = ai.aiSummary.ifBlank { updated.aiSummary },
                        aiKeywords = mergeKeywords(ai.aiKeywords, localAnalysis.aiKeywords)
                    )
                } else {
                    lastAiError = cloudRes.exceptionOrNull()?.localizedMessage
                }
            }
            vaultDao.updateEntry(updated)
            tagged++
        }

        writeLocalAutoSnapshot(userId)
        AutoImportResult(
            newlyImportedCount = 0,
            aiTaggedCount = tagged,
            skippedDuplicateCount = 0,
            aiErrorMessage = lastAiError
        )
    }

    private fun cleanPhotoTitle(rawName: String, fallbackId: Long): String {
        val base = rawName.substringBeforeLast(".").replace("_", " ").trim()
        return if (base.isNotBlank()) base else "Memory $fallbackId"
    }

    private fun mergeKeywords(primary: String, secondary: String): String {
        val merged = linkedSetOf<String>()
        listOf(primary, secondary).forEach { source ->
            source.split(",")
                .map { it.trim().lowercase() }
                .filter { it.length >= 2 }
                .forEach { merged.add(it) }
        }
        return merged.take(28).joinToString(", ")
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

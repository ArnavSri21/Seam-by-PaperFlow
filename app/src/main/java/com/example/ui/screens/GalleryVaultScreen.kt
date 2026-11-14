package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DeviceGalleryAutoImporter
import com.example.data.local.GalleryPermissionStatus
import com.example.data.local.VaultEntryEntity
import com.example.data.preferences.AiExecutionMode
import com.example.data.preferences.UserVaultPreferences
import com.example.ui.components.SeamGlassBackdrop
import com.example.ui.components.SeamGlassBadge
import com.example.ui.components.SeamGlassSearchField
import com.example.ui.components.seamGlassSurface
import com.example.ui.components.seamPressableGlassCard
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarmIvory
import com.example.ui.viewmodel.GallerySyncUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ApplePhotosTimelineMode(val label: String) {
    YEARS("Years"),
    MONTHS("Months"),
    ALL_PHOTOS("All Photos")
}

/**
 * Apple Photos-inspired Memories screen inside Smoked Glass UI:
 * - Automatically requests Android gallery permission on entry and auto-imports device photos.
 * - Generates searchable AI keywords & tokens (on-device pixel/temporal tokenizer + Gemini Vision).
 * - Clean Apple Photos visual mosaic (Years / Months / All Photos segmented frosted glass pill,
 *   Collections album strip, trending AI keyword chips, and full-bleed Apple Photos detail viewer).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryVaultScreen(
    galleryEntries: List<VaultEntryEntity>,
    preferences: UserVaultPreferences,
    gallerySyncState: GallerySyncUiState,
    onAutoSyncDeviceGallery: (enableGeminiVision: Boolean) -> Unit,
    onImportMultiplePickedPhotos: (List<Uri>, Boolean) -> Unit,
    onRunAiTagAllMemories: (Boolean) -> Unit,
    onMarkGalleryPermissionPrompted: () -> Unit,
    onOpenGlobalSearchWithToken: (String) -> Unit,
    onPhotoPickedForVault: (Uri) -> Unit,
    onOpenNewGalleryDialog: () -> Unit,
    onSelectEntry: (VaultEntryEntity) -> Unit,
    onToggleFavorite: (VaultEntryEntity) -> Unit,
    onAnalyzeEntryWithAi: (VaultEntryEntity) -> Unit,
    onDeleteEntry: (VaultEntryEntity) -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val reducedMotion = preferences.reducedMotionEnabled

    var localSearchQuery by remember { mutableStateOf("") }
    var selectedAlbum by remember { mutableStateOf("All") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var timelineMode by remember { mutableStateOf(ApplePhotosTimelineMode.MONTHS) }

    var activeDetailMemoryId by remember { mutableStateOf<Long?>(null) }
    var showAiConsentOrRunDialog by remember { mutableStateOf(false) }

    var permissionStatus by remember {
        mutableStateOf(DeviceGalleryAutoImporter.getGalleryPermissionStatus(context))
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val updated = DeviceGalleryAutoImporter.getGalleryPermissionStatus(context)
        permissionStatus = updated
        if (updated != GalleryPermissionStatus.DENIED) {
            val useGemini = preferences.aiExecutionMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN ||
                preferences.hasConsentedToCloudAi
            onAutoSyncDeviceGallery(useGemini)
        }
    }

    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 40)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val useGemini = preferences.aiExecutionMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN ||
                preferences.hasConsentedToCloudAi
            onImportMultiplePickedPhotos(uris, useGemini)
        }
    }

    // Single photo picker retained for direct detail relink/import
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onPhotoPickedForVault(uri)
        }
    }

    // Automatically ask gallery permission on first Memories visit OR auto-sync new photos when already granted
    LaunchedEffect(Unit) {
        val currentStatus = DeviceGalleryAutoImporter.getGalleryPermissionStatus(context)
        permissionStatus = currentStatus
        val useGemini = preferences.aiExecutionMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN ||
            preferences.hasConsentedToCloudAi
        if (currentStatus != GalleryPermissionStatus.DENIED) {
            onAutoSyncDeviceGallery(useGemini)
        } else if (!preferences.hasPromptedGalleryPermission) {
            onMarkGalleryPermissionPrompted()
            galleryPermissionLauncher.launch(DeviceGalleryAutoImporter.requiredGalleryPermissions())
        }
    }

    // Extract trending AI tokens across all gallery items so user can tap any token to filter
    val trendingAiTokens = remember(galleryEntries) {
        galleryEntries
            .flatMap { entry ->
                entry.aiKeywords.split(",").map { it.trim().lowercase() }
            }
            .filter { it.length in 3..20 && it !in setOf("photo", "memory", "image") }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(6)
    }

    // Derive Apple Photos-style Albums / Collections with cover thumbnails
    val albumCollections = remember(galleryEntries) {
        galleryEntries
            .groupBy { it.folderOrAlbum.ifBlank { "Recents" } }
            .entries
            .sortedByDescending { it.value.size }
            .map { (name, items) ->
                Triple(name, items.size, items.firstOrNull()?.mediaPath.orEmpty())
            }
    }

    val visibleMemories = remember(galleryEntries, localSearchQuery, selectedAlbum, onlyFavorites) {
        galleryEntries.filter { item ->
            val albumMatch = selectedAlbum == "All" ||
                item.folderOrAlbum.equals(selectedAlbum, ignoreCase = true) ||
                item.category.equals(selectedAlbum, ignoreCase = true)
            val favMatch = !onlyFavorites || item.isFavorite
            val q = localSearchQuery.trim().lowercase()
            val queryMatch = if (q.isBlank()) {
                true
            } else {
                val blob = "${item.title} ${item.primaryContent} ${item.websiteOrLocation} ${item.folderOrAlbum} ${item.category} ${item.moodTag} ${item.aiSummary} ${item.aiKeywords} ${item.ocrExtractedText}".lowercase()
                q.split(Regex("\\s+")).filter { it.isNotBlank() }.all { token ->
                    blob.contains(token)
                }
            }
            albumMatch && favMatch && queryMatch
        }
    }

    val activeDetailIndex = remember(visibleMemories, activeDetailMemoryId) {
        visibleMemories.indexOfFirst { it.id == activeDetailMemoryId }
    }

    if (showAiConsentOrRunDialog) {
        AlertDialog(
            onDismissRequest = { showAiConsentOrRunDialog = false },
            containerColor = Color(0xF2151521),
            title = {
                Text(
                    text = "AI Keyword & Token Indexer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SeamWarmIvory
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose how Seam should generate searchable objects, colors, lighting, scenes, and keyword tokens for your ${galleryEntries.size} photos:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SeamSilver
                    )
                    Text(
                        text = "• Gemini Vision AI inspects actual photo pixels for specific objects & scenes.\n• On-Device Engine runs 100% offline using color palette, lighting, album & date tokens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamMutedGrey
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiConsentOrRunDialog = false
                        onRunAiTagAllMemories(true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SeamLavenderAccent,
                        contentColor = SeamNearBlack
                    )
                ) {
                    Text("Run Gemini Vision AI")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAiConsentOrRunDialog = false
                        onRunAiTagAllMemories(false)
                    }
                ) {
                    Text("On-Device Only", color = SeamSilver)
                }
            }
        )
    }

    // Full-screen Apple Photos detail viewer
    if (activeDetailIndex >= 0) {
        val currentMemory = visibleMemories[activeDetailIndex]
        ApplePhotosDetailViewer(
            memory = currentMemory,
            allVisibleMemories = visibleMemories,
            currentIndex = activeDetailIndex,
            reducedMotion = reducedMotion,
            onBack = { activeDetailMemoryId = null },
            onNavigateIndex = { newIdx ->
                visibleMemories.getOrNull(newIdx)?.let { activeDetailMemoryId = it.id }
            },
            onToggleFavorite = { onToggleFavorite(currentMemory) },
            onEditMemory = { onSelectEntry(currentMemory) },
            onAnalyzeWithAi = { onAnalyzeEntryWithAi(currentMemory) },
            onTokenClicked = { token ->
                activeDetailMemoryId = null
                onOpenGlobalSearchWithToken(token)
            },
            onDeleteMemory = {
                activeDetailMemoryId = null
                onDeleteEntry(currentMemory)
            },
            onReimportMissingMedia = {
                singlePhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
        return
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val yearFormat = remember { SimpleDateFormat("yyyy", Locale.getDefault()) }

    val groupedSections = remember(visibleMemories, timelineMode) {
        when (timelineMode) {
            ApplePhotosTimelineMode.YEARS -> visibleMemories.groupBy {
                yearFormat.format(Date(it.createdAtMillis))
            }
            ApplePhotosTimelineMode.MONTHS -> visibleMemories.groupBy {
                monthFormat.format(Date(it.createdAtMillis))
            }
            ApplePhotosTimelineMode.ALL_PHOTOS -> {
                if (visibleMemories.isEmpty()) emptyMap()
                else mapOf("All Photos" to visibleMemories)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SeamGlassBackdrop(reducedMotion = reducedMotion)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gallery_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Minimalist Apple Photos Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Photos",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SeamWarmIvory
                        )
                        Text(
                            text = if (gallerySyncState.isSyncing) {
                                gallerySyncState.progressLabel.ifBlank { "AI indexing…" }
                            } else {
                                "${visibleMemories.size} items"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (gallerySyncState.isSyncing) SeamLavenderAccent else SeamMutedGrey
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick AI Tag All Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .seamPressableGlassCard(
                                    reducedMotion = reducedMotion,
                                    shape = CircleShape,
                                    elevated = true,
                                    accentBorder = SeamLavenderAccent.copy(alpha = 0.5f),
                                    onClick = { showAiConsentOrRunDialog = true }
                                )
                                .testTag("ai_tag_all_photos_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "AI tag photos",
                                tint = SeamLavenderAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Sync Gallery Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .seamPressableGlassCard(
                                    reducedMotion = reducedMotion,
                                    shape = CircleShape,
                                    elevated = true,
                                    onClick = {
                                        if (permissionStatus == GalleryPermissionStatus.DENIED) {
                                            galleryPermissionLauncher.launch(
                                                DeviceGalleryAutoImporter.requiredGalleryPermissions()
                                            )
                                        } else {
                                            val useGemini = preferences.aiExecutionMode == AiExecutionMode.CLOUD_GEMINI_OPT_IN ||
                                                preferences.hasConsentedToCloudAi
                                            onAutoSyncDeviceGallery(useGemini)
                                        }
                                    }
                                )
                                .testTag("sync_gallery_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = "Sync photos",
                                tint = SeamWarmIvory,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Direct 1-tap Add Photo Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .seamPressableGlassCard(
                                    reducedMotion = reducedMotion,
                                    shape = CircleShape,
                                    elevated = true,
                                    onClick = {
                                        multiPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                .testTag("pick_photo_vault_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddPhotoAlternate,
                                contentDescription = "Add photos",
                                tint = SeamWarmIvory,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 3. Live AI Import / Tagging Progress Bar
            item {
                AnimatedVisibility(visible = gallerySyncState.isSyncing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(
                                shape = RoundedCornerShape(20.dp),
                                elevated = true,
                                accentBorder = SeamLavenderAccent.copy(alpha = 0.6f)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = SeamLavenderAccent
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = gallerySyncState.progressLabel.ifBlank { "Scanning gallery & generating AI tokens…" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = SeamWarmIvory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (gallerySyncState.totalCount > 0) {
                                Text(
                                    text = "${gallerySyncState.processedCount} of ${gallerySyncState.totalCount} photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SeamLavenderAccent
                                )
                            }
                        }
                    }
                }
            }

            // 4. Gallery Permission & Auto-Import Glass Prompt (when permission not full)
            if (permissionStatus != GalleryPermissionStatus.GRANTED_FULL) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(
                                shape = RoundedCornerShape(22.dp),
                                elevated = true,
                                accentBorder = SeamLavenderAccent.copy(alpha = 0.45f)
                            )
                            .padding(16.dp)
                            .testTag("gallery_permission_card"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .seamGlassSurface(shape = CircleShape, elevated = true),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    tint = SeamLavenderAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (permissionStatus == GalleryPermissionStatus.GRANTED_SELECTED_ONLY) {
                                        "Selected Photos Access"
                                    } else {
                                        "Sync Device Photos"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SeamWarmIvory,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (permissionStatus == GalleryPermissionStatus.GRANTED_SELECTED_ONLY) {
                                        "Allow full access to auto-add photos with AI search tags."
                                    } else {
                                        "Allow gallery access to auto-import photos and add AI search tags."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SeamMutedGrey
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onMarkGalleryPermissionPrompted()
                                    galleryPermissionLauncher.launch(
                                        DeviceGalleryAutoImporter.requiredGalleryPermissions()
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SeamWarmIvory,
                                    contentColor = SeamNearBlack
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_gallery_permission_button")
                            ) {
                                Text(
                                    text = if (permissionStatus == GalleryPermissionStatus.GRANTED_SELECTED_ONLY) {
                                        "Allow Full"
                                    } else {
                                        "Allow"
                                    },
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    multiPhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pick", color = SeamWarmIvory, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 5. Apple Photos Search Pill ("Objects, colors, places, or AI tokens")
            item {
                SeamGlassSearchField(
                    value = localSearchQuery,
                    onValueChange = { localSearchQuery = it },
                    placeholder = "Search objects, colors, albums, or AI tokens",
                    leadingIcon = Icons.Outlined.Search,
                    trailingContent = {
                        if (localSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { localSearchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear search",
                                    tint = SeamSilver
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memories_search_input")
                )
            }

            // 6. Trending AI Search Tokens Ribbon (Tap any token to filter instantly!)
            if (trendingAiTokens.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (onlyFavorites) SeamLavenderAccent.copy(alpha = 0.25f) else Color(0x9E191924)
                                )
                                .border(
                                    1.dp,
                                    if (onlyFavorites) SeamLavenderAccent else Color.White.copy(alpha = 0.16f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onlyFavorites = !onlyFavorites }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (onlyFavorites) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (onlyFavorites) SeamErrorCoral else SeamSilver,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Favorites",
                                style = MaterialTheme.typography.labelMedium,
                                color = SeamWarmIvory
                            )
                        }

                        trendingAiTokens.forEach { token ->
                            val isSelected = localSearchQuery.equals(token, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) SeamLavenderAccent.copy(alpha = 0.28f) else Color(0x9E191924)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) SeamLavenderAccent else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        localSearchQuery = if (isSelected) "" else token
                                    }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tag,
                                    contentDescription = null,
                                    tint = if (isSelected) SeamWarmIvory else SeamLavenderAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = token,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) SeamWarmIvory else SeamSilver
                                )
                            }
                        }
                    }
                }
            }

            // 7. Apple Photos-Style Segmented Glass Pill Switcher: Years · Months · All Photos
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamGlassSurface(shape = RoundedCornerShape(24.dp), elevated = true)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ApplePhotosTimelineMode.entries.forEach { mode ->
                        val selected = timelineMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0x669C98B8),
                                                Color(0x4D6D698D)
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                    }
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.34f),
                                            RoundedCornerShape(20.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { timelineMode = mode },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) SeamWarmIvory else SeamMutedGrey,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 8. Apple Photos Collections / Albums Horizontal Strip
            if (albumCollections.size > 1 && localSearchQuery.isBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Collections",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = SeamWarmIvory
                            )
                            if (selectedAlbum != "All") {
                                Text(
                                    text = "Show All",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SeamLavenderAccent,
                                    modifier = Modifier.clickable { selectedAlbum = "All" }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            albumCollections.forEach { (albumName, count, coverPath) ->
                                val isAlbumSelected = selectedAlbum.equals(albumName, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(width = 122.dp, height = 104.dp)
                                        .seamPressableGlassCard(
                                            reducedMotion = reducedMotion,
                                            shape = RoundedCornerShape(18.dp),
                                            accentBorder = if (isAlbumSelected) SeamLavenderAccent else null,
                                            onClick = {
                                                selectedAlbum = if (isAlbumSelected) "All" else albumName
                                            }
                                        )
                                ) {
                                    if (coverPath.isNotBlank() && File(coverPath).exists()) {
                                        AsyncImage(
                                            model = File(coverPath),
                                            contentDescription = albumName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0xCC090910)
                                                    )
                                                )
                                            )
                                    )
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = albumName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = SeamWarmIvory,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SeamSilver
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 9. Empty State OR Apple Photos Mosaic Grid
            if (visibleMemories.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(24.dp), elevated = true)
                            .padding(24.dp)
                            .testTag("gallery_empty_state_card"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .seamGlassSurface(shape = CircleShape, elevated = true),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = SeamLavenderAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = if (galleryEntries.isEmpty()) "No photos yet" else "No matching photos",
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (galleryEntries.isEmpty()) {
                                "Tap + to pick photos or allow gallery access above."
                            } else {
                                "Try another keyword or collection."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                    }
                }
            } else {
                groupedSections.forEach { (sectionHeading, sectionItems) ->
                    stickyHeader(key = "header_$sectionHeading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xD907070B))
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sectionHeading,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "${sectionItems.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SeamMutedGrey
                            )
                        }
                    }

                    when (timelineMode) {
                        ApplePhotosTimelineMode.MONTHS -> {
                            val rows = sectionItems.chunked(3)
                            items(rows, key = { row -> "m3_${sectionHeading}_${row.first().id}" }) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        ApplePhotoMosaicTile(
                                            entry = item,
                                            cornerRadiusDp = 10,
                                            modifier = Modifier.weight(1f),
                                            onClick = { activeDetailMemoryId = item.id }
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        ApplePhotosTimelineMode.YEARS, ApplePhotosTimelineMode.ALL_PHOTOS -> {
                            val cols = if (timelineMode == ApplePhotosTimelineMode.YEARS) 4 else 3
                            val rows = sectionItems.chunked(cols)
                            items(rows, key = { row -> "grid_${cols}_${sectionHeading}_${row.first().id}" }) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        ApplePhotoMosaicTile(
                                            entry = item,
                                            cornerRadiusDp = if (cols == 4) 6 else 10,
                                            modifier = Modifier.weight(1f),
                                            onClick = { activeDetailMemoryId = item.id }
                                        )
                                    }
                                    repeat(cols - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom clearance for floating pill glass taskbar + FAB
            item {
                Spacer(modifier = Modifier.height(92.dp))
            }
        }
    }
}

/**
 * Pure Apple Photos square edge-to-edge photo tile with minimal glass badges (heart / AI-indexed dot).
 */
@Composable
private fun ApplePhotoMosaicTile(
    entry: VaultEntryEntity,
    cornerRadiusDp: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fileExists = remember(entry.mediaPath) {
        entry.mediaPath.isNotBlank() && File(entry.mediaPath).exists()
    }
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Color(0xFF141520))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .testTag("gallery_card_${entry.id}")
    ) {
        if (fileExists) {
            AsyncImage(
                model = File(entry.mediaPath),
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = SeamMutedGrey.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (entry.isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0x88090A10)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = SeamWarmIvory,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        if (entry.isAnalyzed && entry.aiKeywords.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x8A0A0B12))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "AI keyword indexed",
                    tint = SeamLavenderAccent,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Full-bleed Apple Photos-style Detail Viewer with floating frosted glass controls and interactive AI keyword/token cloud.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplePhotosDetailViewer(
    memory: VaultEntryEntity,
    allVisibleMemories: List<VaultEntryEntity>,
    currentIndex: Int,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onNavigateIndex: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditMemory: () -> Unit,
    onAnalyzeWithAi: () -> Unit,
    onTokenClicked: (String) -> Unit,
    onDeleteMemory: () -> Unit,
    onReimportMissingMedia: () -> Unit
) {
    BackHandler(onBack = onBack)

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()) }
    val fileExists = remember(memory.mediaPath) {
        memory.mediaPath.isNotBlank() && File(memory.mediaPath).exists()
    }
    var horizontalDragDelta by remember { mutableFloatStateOf(0f) }

    val tokens = remember(memory.aiKeywords) {
        memory.aiKeywords
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SeamGlassBackdrop(reducedMotion = reducedMotion)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("memory_detail_view"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Apple Photos Floating Glass Top Pill Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamGlassSurface(shape = RoundedCornerShape(28.dp), elevated = true)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back to Photos",
                            tint = SeamWarmIvory
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dateFormat.format(Date(memory.createdAtMillis)),
                            style = MaterialTheme.typography.titleSmall,
                            color = SeamWarmIvory,
                            maxLines = 1
                        )
                        Text(
                            text = "${memory.folderOrAlbum.ifBlank { "Recents" }} · ${currentIndex + 1} of ${allVisibleMemories.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamMutedGrey,
                            maxLines = 1
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { if (currentIndex > 0) onNavigateIndex(currentIndex - 1) },
                            enabled = currentIndex > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Photo",
                                tint = if (currentIndex > 0) SeamSilver else SeamMutedGrey.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(
                            onClick = { if (currentIndex < allVisibleMemories.lastIndex) onNavigateIndex(currentIndex + 1) },
                            enabled = currentIndex < allVisibleMemories.lastIndex
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Photo",
                                tint = if (currentIndex < allVisibleMemories.lastIndex) SeamSilver else SeamMutedGrey.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }

            // 2. Full-bleed Photo Canvas with Swipe Navigation
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .seamGlassSurface(shape = RoundedCornerShape(24.dp), elevated = true)
                        .pointerInput(currentIndex, allVisibleMemories.size) {
                            detectHorizontalDragGestures(
                                onDragStart = { horizontalDragDelta = 0f },
                                onHorizontalDrag = { _, dragAmount -> horizontalDragDelta += dragAmount },
                                onDragEnd = {
                                    if (horizontalDragDelta < -85f && currentIndex < allVisibleMemories.lastIndex) {
                                        onNavigateIndex(currentIndex + 1)
                                    } else if (horizontalDragDelta > 85f && currentIndex > 0) {
                                        onNavigateIndex(currentIndex - 1)
                                    }
                                }
                            )
                        }
                ) {
                    if (fileExists) {
                        AsyncImage(
                            model = File(memory.mediaPath),
                            contentDescription = memory.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF07080D))
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Photo file unavailable",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SeamMutedGrey
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = onReimportMissingMedia) {
                                Text("Relink Photo")
                            }
                        }
                    }
                }
            }

            // 3. Apple Photos Frosted Glass Bottom Action Pill Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamGlassSurface(shape = RoundedCornerShape(28.dp), elevated = true)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (memory.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite photo",
                            tint = if (memory.isFavorite) SeamErrorCoral else SeamWarmIvory
                        )
                    }
                    IconButton(onClick = onAnalyzeWithAi) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Refresh AI keywords",
                            tint = SeamLavenderAccent
                        )
                    }
                    IconButton(onClick = onEditMemory) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit title, caption & album",
                            tint = SeamWarmIvory
                        )
                    }
                    IconButton(onClick = onDeleteMemory) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete photo",
                            tint = SeamErrorCoral
                        )
                    }
                }
            }

            // 4. AI Keywords & Searchable Tokens Glass Panel
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamGlassSurface(
                            shape = RoundedCornerShape(24.dp),
                            elevated = true,
                            accentBorder = SeamLavenderAccent.copy(alpha = 0.42f)
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = SeamLavenderAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AI Search Keywords & Tokens",
                                style = MaterialTheme.typography.titleMedium,
                                color = SeamWarmIvory,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        SeamGlassBadge(
                            text = memory.category,
                            tint = SeamLavenderAccent
                        )
                    }

                    if (memory.aiSummary.isNotBlank()) {
                        Text(
                            text = memory.aiSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamSilver
                        )
                    }

                    if (tokens.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tokens.forEach { token ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xB8212132))
                                        .border(
                                            1.dp,
                                            SeamLavenderAccent.copy(alpha = 0.35f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onTokenClicked(token) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tag,
                                        contentDescription = null,
                                        tint = SeamLavenderAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = token,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SeamWarmIvory
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Tap any token to search matching photos across Seam.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamMutedGrey
                        )
                    } else {
                        OutlinedButton(
                            onClick = onAnalyzeWithAi,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate AI Keywords")
                        }
                    }

                    if (memory.ocrExtractedText.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = SeamMutedGrey,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = memory.ocrExtractedText,
                                style = MaterialTheme.typography.labelSmall,
                                color = SeamMutedGrey
                            )
                        }
                    }
                }
            }

            // 5. Title, Caption & Collection Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamPressableGlassCard(
                            reducedMotion = reducedMotion,
                            shape = RoundedCornerShape(22.dp),
                            onClick = onEditMemory
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .testTag("memory_add_to_collection_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .seamGlassSurface(shape = CircleShape, elevated = true),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = null,
                            tint = SeamSilver,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = memory.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (memory.primaryContent.isNotBlank()) {
                                memory.primaryContent
                            } else {
                                "Album: ${memory.folderOrAlbum.ifBlank { "Recents" }} · Tap to add caption or move album"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamMutedGrey,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = SeamMutedGrey
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(92.dp))
            }
        }
    }
}

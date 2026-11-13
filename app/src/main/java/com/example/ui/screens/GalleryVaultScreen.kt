package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.VaultEntryEntity
import com.example.ui.components.AmbientLightBackdrop
import com.example.ui.components.SeamTopHeader
import com.example.ui.components.seamPressableCard
import com.example.ui.theme.SeamBorderSubtle
import com.example.ui.theme.SeamCharcoal
import com.example.ui.theme.SeamElevatedSurface
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamWarmIvory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GalleryViewDensity {
    COMFORTABLE_GRID,
    COMPACT_GRID,
    ACCESSIBLE_LIST
}

/**
 * 03 · Memories and gallery (Pages 5–6 of Seam by PaperFlow UI/UX Specification):
 * - Left: Memory timeline grouped by month/year with sticky date headings, grid density toggle
 *   (Comfortable 2-col / Compact 3-col / Accessible List), filters, "Recently imported" card,
 *   and explicit choice before copying vs linking media into Seam's private storage.
 * - Right: Individual Memory Detail with full-width media viewer, swipe navigation, opt-in location,
 *   "Add to collection / Choose an album or tag", storage mode status, and dismissible Related Items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryVaultScreen(
    galleryEntries: List<VaultEntryEntity>,
    reducedMotion: Boolean = false,
    onPhotoPickedForVault: (Uri) -> Unit,
    onOpenNewGalleryDialog: () -> Unit,
    onSelectEntry: (VaultEntryEntity) -> Unit,
    onToggleFavorite: (VaultEntryEntity) -> Unit,
    onAnalyzeEntryWithAi: (VaultEntryEntity) -> Unit,
    onDeleteEntry: (VaultEntryEntity) -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    var localSearchQuery by remember { mutableStateOf("") }
    var selectedAlbum by remember { mutableStateOf("All") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var viewDensity by remember { mutableStateOf(GalleryViewDensity.COMFORTABLE_GRID) }
    var showFiltersRow by remember { mutableStateOf(false) }

    // Selected memory for full-screen Memory Detail view (Page 5 right screenshot)
    var activeDetailMemoryId by remember { mutableStateOf<Long?>(null) }

    // Ask before copying vs linking media modal (Page 5 requirement)
    var showImportChoiceDialog by remember { mutableStateOf(false) }
    var copyToPrivateStorageMode by remember { mutableStateOf(true) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onPhotoPickedForVault(uri)
        }
    }

    val albums = remember(galleryEntries) {
        listOf("All") + galleryEntries.map { it.folderOrAlbum.ifBlank { "Timeline" } }.distinct()
    }

    val visibleMemories = remember(galleryEntries, localSearchQuery, selectedAlbum, onlyFavorites) {
        galleryEntries.filter { item ->
            val albumMatch = selectedAlbum == "All" || item.folderOrAlbum.equals(selectedAlbum, ignoreCase = true)
            val favMatch = !onlyFavorites || item.isFavorite
            val q = localSearchQuery.trim().lowercase()
            val queryMatch = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.primaryContent.lowercase().contains(q) ||
                item.websiteOrLocation.lowercase().contains(q) ||
                item.category.lowercase().contains(q) ||
                item.aiKeywords.lowercase().contains(q)
            albumMatch && favMatch && queryMatch
        }
    }

    val activeDetailIndex = remember(visibleMemories, activeDetailMemoryId) {
        visibleMemories.indexOfFirst { it.id == activeDetailMemoryId }
    }

    if (showImportChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showImportChoiceDialog = false },
            title = {
                Text(
                    text = "Import into Memories",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how Seam should store your selected photo:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (copyToPrivateStorageMode) SeamElevatedSurface else SeamCharcoal)
                            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(14.dp))
                            .clickable { copyToPrivateStorageMode = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Private local copy (Recommended)",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "Copies the file into Seam's app-private folder (`files/private_gallery_vault`) so it remains available even if the original URI expires.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (!copyToPrivateStorageMode) SeamElevatedSurface else SeamCharcoal)
                            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(14.dp))
                            .clickable { copyToPrivateStorageMode = false }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Linked media reference",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "Saves storage space by referencing the system URI, but access may expire if the source file is moved or deleted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportChoiceDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("confirm_photo_picker_import")
                ) {
                    Text("Select photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportChoiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // If a memory is selected, show the full-screen Memory Detail view (Page 5 Right Screenshot)
    if (activeDetailIndex >= 0) {
        val currentMemory = visibleMemories[activeDetailIndex]
        MemoryDetailView(
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
            onDeleteMemory = {
                activeDetailMemoryId = null
                onDeleteEntry(currentMemory)
            },
            onReimportMissingMedia = {
                showImportChoiceDialog = true
            }
        )
        return
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val groupedByMonth = remember(visibleMemories) {
        visibleMemories.groupBy { entry ->
            monthFormat.format(Date(entry.createdAtMillis)).uppercase(Locale.getDefault())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SeamNearBlack)
    ) {
        AmbientLightBackdrop(reducedMotion = reducedMotion)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gallery_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Header
            item {
                SeamTopHeader(
                    statusLabel = "${galleryEntries.size} memories",
                    reducedMotion = reducedMotion,
                    onSettingsClick = onOpenSettings
                )
            }

            // 2. Title & Subtitle ("Memories" / "Moments, gathered in one place.")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Memories",
                            style = MaterialTheme.typography.displayLarge,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Moments, gathered in one place.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SeamMutedGrey
                        )
                    }

                    // Grid density toggle (Comfortable / Compact / Accessible List)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(SeamCharcoal)
                            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { viewDensity = GalleryViewDensity.COMFORTABLE_GRID },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = "Comfortable Grid",
                                tint = if (viewDensity == GalleryViewDensity.COMFORTABLE_GRID) SeamWarmIvory else SeamMutedGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewDensity = GalleryViewDensity.COMPACT_GRID },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ViewModule,
                                contentDescription = "Compact Grid",
                                tint = if (viewDensity == GalleryViewDensity.COMPACT_GRID) SeamWarmIvory else SeamMutedGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewDensity = GalleryViewDensity.ACCESSIBLE_LIST },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ViewList,
                                contentDescription = "Accessible List View",
                                tint = if (viewDensity == GalleryViewDensity.ACCESSIBLE_LIST) SeamWarmIvory else SeamMutedGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 3. Search Bar ("Search places, dates or moments")
            item {
                OutlinedTextField(
                    value = localSearchQuery,
                    onValueChange = { localSearchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search places, dates or moments",
                            color = SeamMutedGrey
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search Memories",
                            tint = SeamMutedGrey
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showFiltersRow = !showFiltersRow },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Toggle album & favorite filters",
                                tint = if (showFiltersRow || selectedAlbum != "All" || onlyFavorites) SeamLavenderAccent else SeamSilver
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memories_search_input")
                )
            }

            // 4. Filter Chips (Albums & Favorites)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onlyFavorites = !onlyFavorites },
                        label = { Text("Favorites") }
                    )
                    albums.forEach { album ->
                        FilterChip(
                            selected = selectedAlbum == album,
                            onClick = { selectedAlbum = album },
                            label = { Text(album) }
                        )
                    }
                }
            }

            // 5. Sticky Month/Year Timeline Sections (matching Page 5 left screenshot)
            groupedByMonth.forEach { (monthHeading, monthItems) ->
                stickyHeader(key = "header_$monthHeading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SeamNearBlack.copy(alpha = 0.94f))
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = monthHeading,
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamMutedGrey
                        )
                    }
                }

                when (viewDensity) {
                    GalleryViewDensity.COMFORTABLE_GRID -> {
                        val rows = monthItems.chunked(2)
                        items(rows, key = { row -> "grid2_${row.first().id}" }) { rowPair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowPair.forEach { item ->
                                    MemoryGridTile(
                                        entry = item,
                                        reducedMotion = reducedMotion,
                                        compact = false,
                                        modifier = Modifier.weight(1f),
                                        onClick = { activeDetailMemoryId = item.id }
                                    )
                                }
                                if (rowPair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    GalleryViewDensity.COMPACT_GRID -> {
                        val rows = monthItems.chunked(3)
                        items(rows, key = { row -> "grid3_${row.first().id}" }) { rowTriple ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowTriple.forEach { item ->
                                    MemoryGridTile(
                                        entry = item,
                                        reducedMotion = reducedMotion,
                                        compact = true,
                                        modifier = Modifier.weight(1f),
                                        onClick = { activeDetailMemoryId = item.id }
                                    )
                                }
                                repeat(3 - rowTriple.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    GalleryViewDensity.ACCESSIBLE_LIST -> {
                        items(monthItems, key = { "list_${it.id}" }) { item ->
                            MemoryAccessibleListRow(
                                entry = item,
                                reducedMotion = reducedMotion,
                                onClick = { activeDetailMemoryId = item.id }
                            )
                        }
                    }
                }
            }

            // 6. "Recently imported" / Import Card (matching bottom of Page 5 left screenshot)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamPressableCard(
                            reducedMotion = reducedMotion,
                            shape = RoundedCornerShape(22.dp),
                            onClick = { showImportChoiceDialog = true }
                        )
                        .background(SeamCharcoal)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                        .testTag("pick_photo_vault_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SeamElevatedSurface)
                            .border(1.dp, SeamBorderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "Import memory",
                            tint = SeamSilver,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Recently imported",
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "${galleryEntries.size} items in private storage · Tap to import photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
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
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun MemoryGridTile(
    entry: VaultEntryEntity,
    reducedMotion: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fileExists = remember(entry.mediaPath) {
        entry.mediaPath.isNotBlank() && File(entry.mediaPath).exists()
    }

    Column(
        modifier = modifier
            .seamPressableCard(
                reducedMotion = reducedMotion,
                shape = RoundedCornerShape(20.dp),
                onClick = onClick
            )
            .background(SeamCharcoal)
            .padding(10.dp)
            .testTag("gallery_card_${entry.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SeamElevatedSurface)
        ) {
            if (fileExists) {
                AsyncImage(
                    model = File(entry.mediaPath),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Clean mountain/moon geometric silhouette matching Page 5 wireframe aesthetic
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 44.dp else 72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF565B66).copy(alpha = 0.55f))
                    )
                }
            }

            if (entry.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = SeamWarmIvory,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }

        Text(
            text = entry.title,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            color = SeamWarmIvory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MemoryAccessibleListRow(
    entry: VaultEntryEntity,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val fileExists = remember(entry.mediaPath) {
        entry.mediaPath.isNotBlank() && File(entry.mediaPath).exists()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .seamPressableCard(
                reducedMotion = reducedMotion,
                shape = RoundedCornerShape(18.dp),
                onClick = onClick
            )
            .background(SeamCharcoal)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SeamElevatedSurface)
        ) {
            if (fileExists) {
                AsyncImage(
                    model = File(entry.mediaPath),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = SeamWarmIvory
            )
            Text(
                text = "${entry.folderOrAlbum} · ${entry.category} · Local private copy",
                style = MaterialTheme.typography.bodySmall,
                color = SeamMutedGrey
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SeamMutedGrey
        )
    }
}

/**
 * Page 5 Right Screenshot · Individual Memory Detail:
 * - Full-width media viewer with swipe navigation and a visible back action.
 * - Metadata: date, optional opt-in location, source and storage mode.
 * - Actions: favorite, add to collection, tag, edit caption, share/export, delete.
 * - Dismissible Related Items suggestions.
 */
@Composable
private fun MemoryDetailView(
    memory: VaultEntryEntity,
    allVisibleMemories: List<VaultEntryEntity>,
    currentIndex: Int,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onNavigateIndex: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditMemory: () -> Unit,
    onAnalyzeWithAi: () -> Unit,
    onDeleteMemory: () -> Unit,
    onReimportMissingMedia: () -> Unit
) {
    BackHandler(onBack = onBack)

    val dateFormat = remember { SimpleDateFormat("EEEE · h:mm a · MMM d", Locale.getDefault()) }
    val fileExists = remember(memory.mediaPath) {
        memory.mediaPath.isNotBlank() && File(memory.mediaPath).exists()
    }
    var optInShowLocation by remember { mutableStateOf(false) }
    var dismissedRelatedIds by remember { mutableStateOf(setOf<Long>()) }
    var horizontalDragDelta by remember { mutableFloatStateOf(0f) }

    val relatedItems = remember(memory, allVisibleMemories, dismissedRelatedIds) {
        allVisibleMemories.filter { candidate ->
            candidate.id != memory.id &&
                !dismissedRelatedIds.contains(candidate.id) &&
                (candidate.category == memory.category || candidate.folderOrAlbum == memory.folderOrAlbum)
        }.take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SeamNearBlack)
            .testTag("memory_detail_view"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Top Header with visible Back action & Prev/Next navigation
        item {
            SeamTopHeader(
                statusLabel = "${currentIndex + 1} of ${allVisibleMemories.size}",
                reducedMotion = reducedMotion,
                showBackButton = true,
                onBackClick = onBack,
                rightActionContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (currentIndex > 0) onNavigateIndex(currentIndex - 1) },
                            enabled = currentIndex > 0,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Memory",
                                tint = if (currentIndex > 0) SeamWarmIvory else SeamMutedGrey.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (currentIndex < allVisibleMemories.lastIndex) {
                                    onNavigateIndex(currentIndex + 1)
                                }
                            },
                            enabled = currentIndex < allVisibleMemories.lastIndex,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Memory",
                                tint = if (currentIndex < allVisibleMemories.lastIndex) SeamWarmIvory else SeamMutedGrey.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            )
        }

        // 2. Full-width media viewer with horizontal swipe navigation
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(SeamCharcoal)
                    .border(1.dp, SeamBorderSubtle, RoundedCornerShape(26.dp))
                    .pointerInput(currentIndex, allVisibleMemories.size) {
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDragDelta = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                horizontalDragDelta += dragAmount
                            },
                            onDragEnd = {
                                if (horizontalDragDelta < -90f && currentIndex < allVisibleMemories.lastIndex) {
                                    onNavigateIndex(currentIndex + 1)
                                } else if (horizontalDragDelta > 90f && currentIndex > 0) {
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
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF565B66).copy(alpha = 0.45f))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Source media unavailable or linked URI expired",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onReimportMissingMedia) {
                            Text("Relink or Import Copy")
                        }
                    }
                }
            }
        }

        // 3. Title & Date/Storage Mode Metadata
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = memory.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = SeamWarmIvory
                )
                Text(
                    text = "${dateFormat.format(Date(memory.createdAtMillis))} · ${if (fileExists) "Local private copy" else "Linked reference"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SeamMutedGrey
                )
                if (memory.primaryContent.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = memory.primaryContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SeamSilver
                    )
                }
            }
        }

        // 4. "Add to collection / Choose an album or tag" Card (matching Page 5 right screenshot)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .seamPressableCard(
                        reducedMotion = reducedMotion,
                        shape = RoundedCornerShape(22.dp),
                        onClick = onEditMemory
                    )
                    .background(SeamCharcoal)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .testTag("memory_add_to_collection_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SeamElevatedSurface)
                        .border(1.dp, SeamBorderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CollectionsBookmark,
                        contentDescription = null,
                        tint = SeamSilver,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to collection",
                        style = MaterialTheme.typography.titleLarge,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = "Album: ${memory.folderOrAlbum.ifBlank { "Timeline" }} · Tag: ${memory.category}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SeamMutedGrey
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SeamMutedGrey
                )
            }
        }

        // 5. Actions Bar (Favorite, Edit caption, Local AI Tag, Delete)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleFavorite,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (memory.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (memory.isFavorite) "Favorited" else "Favorite")
                }

                OutlinedButton(
                    onClick = onEditMemory,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit caption",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }

                OutlinedButton(
                    onClick = onAnalyzeWithAi,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "Local AI tag",
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedButton(
                    onClick = onDeleteMemory,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Memory",
                        tint = SeamErrorCoral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 6. Opt-In Location Metadata Toggle (Page 5: "Location is opt-in and should not be inferred or displayed without permission.")
        if (memory.websiteOrLocation.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SeamCharcoal)
                        .border(1.dp, SeamBorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = SeamSilver,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (optInShowLocation) memory.websiteOrLocation else "Location hidden by default (Opt-in)",
                                style = MaterialTheme.typography.titleSmall,
                                color = SeamWarmIvory
                            )
                            Text(
                                text = "Location metadata is only displayed when explicitly enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }
                    }
                    Switch(
                        checked = optInShowLocation,
                        onCheckedChange = { optInShowLocation = it }
                    )
                }
            }
        }

        // 7. Related Items Suggested by Shared Dates / Tags (with Dismiss button per Page 6)
        if (relatedItems.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RELATED BY COLLECTION OR LOCAL TAGS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeamMutedGrey
                    )
                    relatedItems.forEach { related ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .seamPressableCard(
                                    reducedMotion = reducedMotion,
                                    shape = RoundedCornerShape(16.dp),
                                    onClick = {
                                        val idx = allVisibleMemories.indexOfFirst { it.id == related.id }
                                        if (idx >= 0) onNavigateIndex(idx)
                                    }
                                )
                                .background(SeamCharcoal)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = related.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SeamWarmIvory
                                )
                                Text(
                                    text = "Suggested · Shared tag '${related.category}'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SeamMutedGrey
                                )
                            }
                            IconButton(
                                onClick = { dismissedRelatedIds = dismissedRelatedIds + related.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss suggestion",
                                    tint = SeamMutedGrey,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.ui.components.SeamGlassBackdrop
import com.example.ui.components.SeamGlassSearchField
import com.example.ui.components.SeamTopHeader
import com.example.ui.components.seamGlassSurface
import com.example.ui.components.seamPressableGlassCard
import com.example.ui.theme.SeamBorderSubtle
import com.example.ui.theme.SeamCharcoal
import com.example.ui.theme.SeamElevatedSurface
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamWarmIvory
import com.example.ui.viewmodel.DateRangeFilter
import com.example.ui.viewmodel.SearchFilterState
import kotlinx.coroutines.delay

/**
 * 06 · Search and discovery (Pages 11–12 of Seam by PaperFlow UI/UX Specification):
 * - Header: "Find your thread." / "Search by words, ideas or remembered details."
 * - Debounced search input (150–250ms per Motion Spec Page 17).
 * - FILTERS 2×2 Bento Grid (Type, Date, Tags, Collection).
 * - BEST MATCHES result cards with Title, Match Reason subtitle ("Matched by meaning · Notes",
 *   "Related words: ...", "Matched by phrase"), highlighted query excerpt, and AI transparency prompt.
 * - Strict exclusion of Vault passwords & secrets.
 */
@Composable
fun SemanticSearchScreen(
    searchFilter: SearchFilterState,
    filteredEntries: List<VaultEntryEntity>,
    allFoldersAndAlbums: List<String>,
    allCategories: List<String>,
    allMoods: List<String>,
    reducedMotion: Boolean = false,
    onExplainMatchRelevance: (VaultEntryEntity) -> String,
    onQueryChange: (String) -> Unit,
    onRunDeepAiSearch: () -> Unit,
    onSelectTypeFilter: (VaultItemType?) -> Unit,
    onSelectFolderFilter: (String) -> Unit,
    onSelectCategoryFilter: (String) -> Unit,
    onSelectMoodFilter: (String) -> Unit,
    onSelectDateRange: (DateRangeFilter) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleAnalyzedOnly: () -> Unit,
    onClearFilters: () -> Unit,
    onSelectEntry: (VaultEntryEntity) -> Unit,
    onToggleFavorite: (VaultEntryEntity) -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    // 180ms debounced typing feedback per Section 09 Motion Specification (150–250 ms)
    var localTypingQuery by remember(searchFilter.query) { mutableStateOf(searchFilter.query) }
    var isDebouncing by remember { mutableStateOf(false) }
    val recentSearches = remember { mutableStateListOf<String>() }
    var showCloudAiSearchConfirm by remember { mutableStateOf(false) }
    var showExtendedTagChips by remember { mutableStateOf(false) }

    LaunchedEffect(localTypingQuery) {
        if (localTypingQuery == searchFilter.query) {
            isDebouncing = false
            return@LaunchedEffect
        }
        isDebouncing = true
        delay(190L)
        onQueryChange(localTypingQuery)
        if (localTypingQuery.trim().length >= 3 && !recentSearches.contains(localTypingQuery.trim())) {
            recentSearches.add(0, localTypingQuery.trim())
            if (recentSearches.size > 5) recentSearches.removeAt(recentSearches.lastIndex)
        }
        isDebouncing = false
    }

    if (showCloudAiSearchConfirm) {
        AlertDialog(
            onDismissRequest = { showCloudAiSearchConfirm = false },
            title = { Text("Ask Cloud AI to expand this search?") },
            text = {
                Text(
                    "Only your search phrase (\"${localTypingQuery.ifBlank { searchFilter.query }}\") and non-sensitive note/memory titles will be used to rank conceptual matches. Vault passwords are never indexed or sent."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudAiSearchConfirm = false
                        onRunDeepAiSearch()
                    },
                    modifier = Modifier.testTag("confirm_cloud_ai_search_button")
                ) {
                    Text("Confirm & Expand")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudAiSearchConfirm = false }) {
                    Text("Use Local Search Only")
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SeamGlassBackdrop(reducedMotion = reducedMotion)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("semantic_search_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Header
            item {
                SeamTopHeader(
                    statusLabel = if (isDebouncing || searchFilter.isAiSearching) "Searching…" else "${filteredEntries.size} matches",
                    isStatusActive = isDebouncing || searchFilter.isAiSearching,
                    reducedMotion = reducedMotion,
                    onSettingsClick = onOpenSettings
                )
            }

            // 2. Title & Subtitle ("Find your thread." / "Search by words, ideas or remembered details.")
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Find your thread.",
                        style = MaterialTheme.typography.displayLarge,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = "Search by words, ideas or remembered details.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SeamMutedGrey
                    )
                }
            }

            // 3. Debounced Search Field ("that idea about starting again")
            item {
                SeamGlassSearchField(
                    value = localTypingQuery,
                    onValueChange = { localTypingQuery = it },
                    placeholder = "that idea about starting again",
                    leadingIcon = Icons.Outlined.Search,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (localTypingQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        localTypingQuery = ""
                                        onQueryChange("")
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Clear query",
                                        tint = SeamMutedGrey
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showExtendedTagChips = !showExtendedTagChips },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_semantic_search_button")
                            ) {
                                if (searchFilter.isAiSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = SeamLavenderAccent
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = "Toggle extended filters",
                                        tint = SeamSilver
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("semantic_search_input")
                )
            }

            // 4. FILTERS 2×2 Bento Grid (matching Page 11 screenshot!)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FILTERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamMutedGrey
                        )
                        if (searchFilter.selectedType != null ||
                            searchFilter.dateRange != DateRangeFilter.ALL_TIME ||
                            searchFilter.selectedCategory != "All" ||
                            searchFilter.selectedFolderOrAlbum != "All" ||
                            searchFilter.onlyFavorites
                        ) {
                            Text(
                                text = "Reset filters",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamLavenderAccent,
                                modifier = Modifier
                                    .clickable {
                                        localTypingQuery = ""
                                        onClearFilters()
                                    }
                                    .testTag("clear_filters_button")
                            )
                        }
                    }

                    // Row 1 of 2x2 Bento Grid: Type | Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val typeLabel = when (searchFilter.selectedType) {
                            null -> "All content"
                            VaultItemType.NOTE -> "Notes"
                            VaultItemType.GALLERY -> "Memories"
                            VaultItemType.TASK -> "Tasks"
                            VaultItemType.PASSWORD -> "All content"
                        }
                        FilterBentoBox(
                            label = "Type",
                            value = typeLabel,
                            reducedMotion = reducedMotion,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("filter_bento_type"),
                            onClick = {
                                val nextType = when (searchFilter.selectedType) {
                                    null -> VaultItemType.NOTE
                                    VaultItemType.NOTE -> VaultItemType.GALLERY
                                    VaultItemType.GALLERY -> VaultItemType.TASK
                                    else -> null
                                }
                                onSelectTypeFilter(nextType)
                            }
                        )

                        val dateLabel = if (searchFilter.dateRange == DateRangeFilter.ALL_TIME) {
                            "Any time"
                        } else {
                            searchFilter.dateRange.label
                        }
                        FilterBentoBox(
                            label = "Date",
                            value = dateLabel,
                            reducedMotion = reducedMotion,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("filter_bento_date"),
                            onClick = {
                                val entries = DateRangeFilter.entries
                                val nextIdx = (entries.indexOf(searchFilter.dateRange) + 1) % entries.size
                                onSelectDateRange(entries[nextIdx])
                            }
                        )
                    }

                    // Row 2 of 2x2 Bento Grid: Tags | Collection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val tagLabel = if (searchFilter.selectedCategory == "All") {
                            "All tags"
                        } else {
                            searchFilter.selectedCategory
                        }
                        FilterBentoBox(
                            label = "Tags",
                            value = tagLabel,
                            reducedMotion = reducedMotion,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("filter_bento_tags"),
                            onClick = {
                                val tagOptions = listOf("All") + allCategories
                                val nextIdx = (tagOptions.indexOf(searchFilter.selectedCategory) + 1) % tagOptions.size
                                onSelectCategoryFilter(tagOptions[nextIdx])
                            }
                        )

                        val collectionLabel = if (searchFilter.selectedFolderOrAlbum == "All") {
                            "Everywhere"
                        } else {
                            searchFilter.selectedFolderOrAlbum
                        }
                        FilterBentoBox(
                            label = "Collection",
                            value = collectionLabel,
                            reducedMotion = reducedMotion,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("filter_bento_collection"),
                            onClick = {
                                val colOptions = listOf("All") + allFoldersAndAlbums
                                val nextIdx = (colOptions.indexOf(searchFilter.selectedFolderOrAlbum) + 1) % colOptions.size
                                onSelectFolderFilter(colOptions[nextIdx])
                            }
                        )
                    }
                }
            }

            // Optional Extended Chips & Clearable Local Search History
            if (showExtendedTagChips || localTypingQuery.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = searchFilter.onlyFavorites,
                            onClick = onToggleFavoritesOnly,
                            label = { Text("Favorites only") }
                        )
                        FilterChip(
                            selected = searchFilter.onlyAnalyzed,
                            onClick = onToggleAnalyzedOnly,
                            label = { Text("Local AI indexed") }
                        )
                        if (localTypingQuery.isNotBlank()) {
                            FilterChip(
                                selected = searchFilter.aiExpandedTerms.isNotEmpty(),
                                onClick = { showCloudAiSearchConfirm = true },
                                label = { Text("Ask AI to expand meaning") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (localTypingQuery.isBlank() && recentSearches.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NATURAL QUERY EXAMPLES & HISTORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = SeamMutedGrey
                            )
                            Text(
                                text = "Clear history",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey,
                                modifier = Modifier.clickable { recentSearches.clear() }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.forEach { past ->
                                Row(
                                    modifier = Modifier
                                        .seamGlassSurface(shape = RoundedCornerShape(12.dp))
                                        .clickable {
                                            localTypingQuery = past
                                            onQueryChange(past)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = SeamMutedGrey,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = past,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SeamSilver
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. BEST MATCHES Section Header (matching Page 11 screenshot!)
            item {
                Text(
                    text = "BEST MATCHES",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeamMutedGrey
                )
            }

            if (filteredEntries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(22.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No matching notes or memories",
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Try searching by a remembered concept, place, or feeling. Password vault secrets are strictly excluded from search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                    }
                }
            } else {
                items(filteredEntries, key = { it.id }) { item ->
                    val isGallery = item.itemType == VaultItemType.GALLERY.name
                    val rawReason = onExplainMatchRelevance(item)
                    val typeSuffix = if (isGallery) "Memories" else "Notes"
                    val formattedReason = if (localTypingQuery.isBlank()) {
                        "Matched by date and context · $typeSuffix"
                    } else {
                        "$rawReason · $typeSuffix"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamPressableGlassCard(
                                reducedMotion = reducedMotion,
                                shape = RoundedCornerShape(22.dp),
                                onClick = { onSelectEntry(item) }
                            )
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                            .testTag("search_result_${item.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .seamGlassSurface(shape = CircleShape, elevated = true),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isGallery) Icons.Outlined.PhotoLibrary else Icons.Outlined.Edit,
                                contentDescription = typeSuffix,
                                tint = SeamSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = highlightQueryTerms(item.title, localTypingQuery),
                                style = MaterialTheme.typography.titleLarge,
                                color = SeamWarmIvory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formattedReason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SeamMutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (localTypingQuery.isNotBlank() && item.primaryContent.isNotBlank()) {
                                Text(
                                    text = highlightQueryTerms(item.primaryContent.replace("\n", " "), localTypingQuery),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SeamSilver,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open result",
                            tint = SeamMutedGrey
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun FilterBentoBox(
    label: String,
    value: String,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .seamPressableGlassCard(
                reducedMotion = reducedMotion,
                shape = RoundedCornerShape(18.dp),
                elevated = true,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SeamMutedGrey
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = SeamWarmIvory,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Page 12 · Result design:
 * "Highlight query terms without changing the underlying content."
 */
@Composable
private fun highlightQueryTerms(text: String, query: String) = buildAnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.length < 2) {
        append(text)
        return@buildAnnotatedString
    }
    val lowerText = text.lowercase()
    val lowerToken = cleanQuery.lowercase()
    var startIndex = 0
    while (startIndex < text.length) {
        val matchIdx = lowerText.indexOf(lowerToken, startIndex)
        if (matchIdx == -1) {
            append(text.substring(startIndex))
            break
        }
        if (matchIdx > startIndex) {
            append(text.substring(startIndex, matchIdx))
        }
        withStyle(
            SpanStyle(
                color = SeamWarmIvory,
                fontWeight = FontWeight.Bold,
                background = SeamLavenderAccent.copy(alpha = 0.24f)
            )
        ) {
            append(text.substring(matchIdx, matchIdx + lowerToken.length))
        }
        startIndex = matchIdx + lowerToken.length
    }
}

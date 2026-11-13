package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.ui.viewmodel.DateRangeFilter
import com.example.ui.viewmodel.SearchFilterState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SemanticSearchScreen(
    searchFilter: SearchFilterState,
    filteredEntries: List<VaultEntryEntity>,
    allFoldersAndAlbums: List<String>,
    allCategories: List<String>,
    allMoods: List<String>,
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
    onToggleFavorite: (VaultEntryEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("semantic_search_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Lexical FTS & Semantic Search",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Searches Memories & Reflections with match relevance explanations. Password Vault is strictly excluded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search input + Semantic Expand button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchFilter.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("semantic_search_input"),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = {
                        Text("Search by keyword, similar concept, OCR text, or mood (e.g. 'calm', 'sea', 'zürich')...")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchFilter.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRunDeepAiSearch,
                        enabled = !searchFilter.isAiSearching,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ai_semantic_expand_button")
                    ) {
                        if (searchFilter.isAiSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Computing Semantic Vectors...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Expand Similar Concepts",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = onClearFilters,
                        modifier = Modifier.testTag("reset_filters_button")
                    ) {
                        Text("Reset All")
                    }
                }
            }
        }

        // Quick Try Similar Keyword Suggestions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TRY SIMILAR CONCEPT QUERIES:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sampleQueries = listOf("calm", "sea", "sleep", "morning", "zürich", "design")
                    sampleQueries.forEach { sample ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    RoundedCornerShape(50)
                                )
                                .clickable { onQueryChange(sample) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("sample_query_chip_$sample")
                        ) {
                            Text(
                                text = "\"$sample\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Expanded Terms & Insight Card
        if (searchFilter.aiExpandedTerms.isNotEmpty() || searchFilter.aiSearchInsight.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "SEMANTIC INDEX EXPANSION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (searchFilter.aiSearchInsight.isNotBlank()) {
                            Text(
                                text = searchFilter.aiSearchInsight,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (searchFilter.aiExpandedTerms.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                searchFilter.aiExpandedTerms.forEach { term ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "≈ $term",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Advanced Multi-Faceted Filters Panel (Content Type, Folder/Album, Category, Date, Pinned)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "FILTERS (VAULT EXCLUDED BY DESIGN)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Filter 1: Sanctuary Content Type (Notes vs Memories)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = searchFilter.selectedType == null,
                            onClick = { onSelectTypeFilter(null) },
                            label = { Text("All Sanctuary Items") }
                        )
                        FilterChip(
                            selected = searchFilter.selectedType == VaultItemType.NOTE,
                            onClick = { onSelectTypeFilter(VaultItemType.NOTE) },
                            label = { Text("Reflections / Notes") }
                        )
                        FilterChip(
                            selected = searchFilter.selectedType == VaultItemType.GALLERY,
                            onClick = { onSelectTypeFilter(VaultItemType.GALLERY) },
                            label = { Text("Gallery Memories") }
                        )
                    }

                    // Filter 2: Folder / Album
                    if (allFoldersAndAlbums.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (listOf("All") + allFoldersAndAlbums).distinct().forEach { folder ->
                                FilterChip(
                                    selected = searchFilter.selectedFolderOrAlbum.equals(folder, ignoreCase = true),
                                    onClick = { onSelectFolderFilter(folder) },
                                    label = { Text("Folder: $folder") }
                                )
                            }
                        }
                    }

                    // Filter 3: Category & Mood
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (listOf("All") + allCategories).distinct().forEach { cat ->
                            FilterChip(
                                selected = searchFilter.selectedCategory.equals(cat, ignoreCase = true),
                                onClick = { onSelectCategoryFilter(cat) },
                                label = { Text("Tag: $cat") }
                            )
                        }
                    }

                    // Filter 4: Date Range & Pinned
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DateRangeFilter.entries.forEach { range ->
                            FilterChip(
                                selected = searchFilter.dateRange == range,
                                onClick = { onSelectDateRange(range) },
                                label = { Text(range.label) }
                            )
                        }
                        FilterChip(
                            selected = searchFilter.onlyFavorites,
                            onClick = onToggleFavoritesOnly,
                            label = { Text("★ Pinned / Favorites") }
                        )
                        FilterChip(
                            selected = searchFilter.onlyAnalyzed,
                            onClick = onToggleAnalyzedOnly,
                            label = { Text("Indexed Only") }
                        )
                    }

                    if (allMoods.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (listOf("All") + allMoods).distinct().forEach { mood ->
                                FilterChip(
                                    selected = searchFilter.selectedMood.equals(mood, ignoreCase = true),
                                    onClick = { onSelectMoodFilter(mood) },
                                    label = { Text("Mood: $mood") }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Results Header
        item {
            Text(
                text = "Matched Sanctuary Entries (${filteredEntries.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(filteredEntries, key = { it.id }) { entry ->
            val explanation = if (searchFilter.query.isNotBlank()) {
                onExplainMatchRelevance(entry)
            } else null

            VaultStreamItemCard(
                entry = entry,
                matchExplanation = explanation,
                onClick = { onSelectEntry(entry) },
                onToggleFavorite = { onToggleFavorite(entry) }
            )
        }
    }
}

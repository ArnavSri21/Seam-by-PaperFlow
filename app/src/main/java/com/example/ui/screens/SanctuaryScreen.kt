package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.AiExecutionMode
import com.example.data.preferences.UserVaultPreferences
import com.example.ui.components.MemoryConstellationHeroCanvas
import com.example.ui.components.SeamGlassBackdrop
import com.example.ui.components.SeamGlassBadge
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
import com.example.ui.viewmodel.BackgroundStatusState
import com.example.ui.viewmodel.PaperFlowTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 02 · Home dashboard (Pages 3–4 of Seam by PaperFlow UI/UX Specification):
 * - Brand mark and wordmark at top, with settings shortcut.
 * - Two-line greeting / positioning statement ("Your space," / "A quieter place for the things that matter.").
 * - Global search field with visible filter affordance.
 * - One featured memory / constellation card (700–1200ms draw-in then settle; never autoplays video or blocks scroll).
 * - Three core feature cards: Memories, Notes & reflections, Password vault.
 * - Recent items list with title, type, last-updated time and optional tag (never exposing vault secrets).
 */
@Composable
fun SanctuaryScreen(
    entries: List<VaultEntryEntity>,
    isolatedVaultCount: Int,
    preferences: UserVaultPreferences,
    backgroundStatus: BackgroundStatusState,
    onNavigateToTab: (PaperFlowTab) -> Unit,
    onOpenNewEntryDialog: (VaultItemType) -> Unit,
    onSelectEntry: (VaultEntryEntity) -> Unit,
    onToggleFavorite: (VaultEntryEntity) -> Unit,
    onTriggerBackgroundSyncAndAi: () -> Unit,
    onLockSanctuary: () -> Unit
) {
    val notesCount = entries.count { it.itemType == VaultItemType.NOTE.name }
    val galleryCount = entries.count { it.itemType == VaultItemType.GALLERY.name }
    val dateFormat = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SeamGlassBackdrop(reducedMotion = preferences.reducedMotionEnabled)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("sanctuary_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Top Brand Header with Settings shortcut & honest system status
            item {
                SeamTopHeader(
                    statusLabel = backgroundStatus.pillLabel,
                    isStatusActive = backgroundStatus.isAnalyzingOrSyncing,
                    reducedMotion = preferences.reducedMotionEnabled,
                    onSettingsClick = { onNavigateToTab(PaperFlowTab.SETTINGS) }
                )
            }

            // 2. Two-line greeting / positioning statement (no forced motivational copy)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Your space,",
                        style = MaterialTheme.typography.displayLarge,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = "A quieter place for the things that matter.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SeamMutedGrey
                    )
                }
            }

            // 3. Global search field near the top with visible filter affordance
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .seamPressableGlassCard(
                            reducedMotion = preferences.reducedMotionEnabled,
                            shape = RoundedCornerShape(22.dp),
                            onClick = { onNavigateToTab(PaperFlowTab.SEARCH) }
                        )
                        .padding(horizontal = 16.dp)
                        .testTag("home_global_search_bar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = SeamMutedGrey,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Search memories, thoughts and ideas",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SeamMutedGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onNavigateToTab(PaperFlowTab.SEARCH) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_search_filter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Search filters",
                            tint = SeamSilver,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 4. Featured Memory Constellation Card (700–1200ms draw-in then settle; no video autoplay!)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .seamPressableGlassCard(
                            reducedMotion = preferences.reducedMotionEnabled,
                            shape = RoundedCornerShape(24.dp),
                            elevated = true,
                            accentBorder = SeamLavenderAccent.copy(alpha = 0.55f),
                            onClick = { onNavigateToTab(PaperFlowTab.GALLERY) }
                        )
                        .testTag("home_constellation_hero_card")
                ) {
                    MemoryConstellationHeroCanvas(
                        memoryCount = galleryCount.coerceAtLeast(4),
                        reducedMotion = preferences.reducedMotionEnabled,
                        modifier = Modifier.fillMaxSize()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "A constellation\nof your moments.",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 25.sp,
                                lineHeight = 31.sp
                            ),
                            color = SeamWarmIvory
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bring the little pieces together.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                    }
                }
            }

            // 5. Section: YOUR SPACE — Three Core Feature Cards with full-row tap targets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "YOUR SPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeamMutedGrey,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )

                    HomeFeatureRowCard(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Memories",
                        subtitle = if (galleryCount > 0) {
                            "Photos, moments and places · $galleryCount saved"
                        } else {
                            "Photos, moments and places"
                        },
                        reducedMotion = preferences.reducedMotionEnabled,
                        testTag = "feature_card_memories",
                        onClick = { onNavigateToTab(PaperFlowTab.GALLERY) }
                    )

                    HomeFeatureRowCard(
                        icon = Icons.Outlined.Edit,
                        title = "Notes & reflections",
                        subtitle = if (notesCount > 0) {
                            "Thoughts worth keeping · $notesCount notes"
                        } else {
                            "Thoughts worth keeping"
                        },
                        reducedMotion = preferences.reducedMotionEnabled,
                        testTag = "feature_card_notes",
                        onClick = { onNavigateToTab(PaperFlowTab.NOTES) }
                    )

                    HomeFeatureRowCard(
                        icon = Icons.Outlined.Lock,
                        title = "Password vault",
                        subtitle = if (isolatedVaultCount > 0) {
                            "Credentials, kept private · $isolatedVaultCount protected"
                        } else {
                            "Credentials, kept private"
                        },
                        reducedMotion = preferences.reducedMotionEnabled,
                        testTag = "feature_card_vault",
                        onClick = { onNavigateToTab(PaperFlowTab.PASSWORDS) }
                    )
                }
            }

            // 6. Honest System Status Row (AI paused / indexing / local storage status)
            item {
                val kbUsed = (backgroundStatus.privateStorageBytes / 1024L).coerceAtLeast(0L)
                val statusSummary = when {
                    preferences.aiExecutionMode == AiExecutionMode.PAUSED ->
                        "AI indexing paused · Local storage active (${backgroundStatus.privateFilesCount} files, $kbUsed KB)"
                    backgroundStatus.isAnalyzingOrSyncing ->
                        "Indexing local metadata in background · Vault excluded"
                    else ->
                        "Saved locally on device ($kbUsed KB) · Cloud backup ${if (preferences.isCloudBackupEnabled) "opt-in enabled" else "off by default"}"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamGlassSurface(shape = RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamMutedGrey,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (preferences.aiExecutionMode == AiExecutionMode.PAUSED) "Settings" else "Index now",
                        style = MaterialTheme.typography.labelLarge,
                        color = SeamLavenderAccent,
                        modifier = Modifier
                            .seamPressableGlassCard(
                                reducedMotion = preferences.reducedMotionEnabled,
                                shape = RoundedCornerShape(10.dp),
                                elevated = true,
                                onClick = {
                                    if (preferences.aiExecutionMode == AiExecutionMode.PAUSED) {
                                        onNavigateToTab(PaperFlowTab.SETTINGS)
                                    } else {
                                        onTriggerBackgroundSyncAndAi()
                                    }
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // 7. Recent items list (compact, never exposing sensitive vault secrets) or First-Run Empty State
            if (entries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(22.dp), elevated = true)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Start your local space",
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Tap the + button below to write a note, import a photo memory, or add a Keystore-encrypted credential. Everything stays on your device by default.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onOpenNewEntryDialog(VaultItemType.NOTE) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SeamWarmIvory,
                                    contentColor = SeamNearBlack
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("New note")
                            }
                            Button(
                                onClick = { onOpenNewEntryDialog(VaultItemType.GALLERY) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SeamElevatedSurface,
                                    contentColor = SeamWarmIvory
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Import memory")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "RECENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeamMutedGrey,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                    )
                }

                items(entries.take(5), key = { it.id }) { entry ->
                    val isGallery = entry.itemType == VaultItemType.GALLERY.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamPressableGlassCard(
                                reducedMotion = preferences.reducedMotionEnabled,
                                shape = RoundedCornerShape(20.dp),
                                onClick = { onSelectEntry(entry) }
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("sanctuary_entry_${entry.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .seamGlassSurface(shape = CircleShape, elevated = true),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isGallery) Icons.Outlined.PhotoLibrary else Icons.Outlined.Description,
                                contentDescription = if (isGallery) "Memory" else "Note",
                                tint = SeamSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = SeamWarmIvory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${if (isGallery) "Memory" else "Note"} · ${dateFormat.format(Date(entry.updatedAtMillis))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey
                            )
                        }

                        if (entry.category.isNotBlank()) {
                            SeamGlassBadge(
                                text = entry.category,
                                tint = SeamSilver
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SeamMutedGrey,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(56.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeFeatureRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    reducedMotion: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .seamPressableGlassCard(
                reducedMotion = reducedMotion,
                shape = RoundedCornerShape(22.dp),
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .seamGlassSurface(shape = CircleShape, elevated = true),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = SeamSilver,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = SeamWarmIvory,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SeamMutedGrey
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open $title",
            tint = SeamMutedGrey,
            modifier = Modifier.size(20.dp)
        )
    }
}

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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.GraphicEq
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
    isVoiceProcessing: Boolean = false,
    onNavigateToTab: (PaperFlowTab) -> Unit,
    onOpenVoiceCapture: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    onOpenLiveVoice: () -> Unit = {},
    onOpenNewEntryDialog: (VaultItemType) -> Unit,
    onSelectEntry: (VaultEntryEntity) -> Unit,
    onToggleFavorite: (VaultEntryEntity) -> Unit,
    onTriggerBackgroundSyncAndAi: () -> Unit,
    onLockSanctuary: () -> Unit
) {
    val notesCount = entries.count { it.itemType == VaultItemType.NOTE.name }
    val galleryCount = entries.count { it.itemType == VaultItemType.GALLERY.name }
    val pendingTasksCount = entries.count { it.itemType == VaultItemType.TASK.name && !it.isTaskCompleted }
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
            // 1. Minimalist Top Bar ("Seam" + Settings shortcut)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seam",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = SeamWarmIvory
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onNavigateToTab(PaperFlowTab.SEARCH) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("nav_search")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = SeamSilver
                            )
                        }
                        SeamTopHeader(
                            statusLabel = "",
                            isStatusActive = false,
                            reducedMotion = preferences.reducedMotionEnabled,
                            onSettingsClick = { onNavigateToTab(PaperFlowTab.SETTINGS) }
                        )
                    }
                }
            }

            // 2. Clean Greeting
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Your space",
                        style = MaterialTheme.typography.displayLarge,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = "Speak a task, organize thoughts, or save memories.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SeamMutedGrey
                    )
                }
            }

            // 3. 1-Tap Voice Upload & Gemini Live / Chat Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .seamPressableGlassCard(
                                reducedMotion = preferences.reducedMotionEnabled,
                                shape = RoundedCornerShape(26.dp),
                                elevated = true,
                                accentBorder = SeamLavenderAccent.copy(alpha = 0.52f),
                                onClick = onOpenVoiceCapture
                            )
                            .padding(horizontal = 16.dp)
                            .testTag("home_voice_upload_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice upload task or schedule",
                            tint = SeamLavenderAccent,
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = if (isVoiceProcessing) "Organizing voice…" else "Speak task or schedule",
                            style = MaterialTheme.typography.titleSmall,
                            color = SeamWarmIvory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .seamPressableGlassCard(
                                reducedMotion = preferences.reducedMotionEnabled,
                                shape = RoundedCornerShape(22.dp),
                                elevated = true,
                                onClick = onOpenAiChat
                            )
                            .testTag("home_ai_chat_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Gemini Chat",
                            tint = SeamSilver,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .seamPressableGlassCard(
                                reducedMotion = preferences.reducedMotionEnabled,
                                shape = RoundedCornerShape(22.dp),
                                elevated = true,
                                accentBorder = SeamLavenderAccent.copy(alpha = 0.4f),
                                onClick = onOpenLiveVoice
                            )
                            .testTag("home_live_ai_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = "Gemini Live Voice",
                            tint = SeamLavenderAccent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // 4. Minimal Global Search Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .seamPressableGlassCard(
                            reducedMotion = preferences.reducedMotionEnabled,
                            shape = RoundedCornerShape(24.dp),
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
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = "Search tasks, photos, notes or tags",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SeamMutedGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 4. Featured Constellation Hero Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .seamPressableGlassCard(
                            reducedMotion = preferences.reducedMotionEnabled,
                            shape = RoundedCornerShape(24.dp),
                            elevated = true,
                            accentBorder = SeamLavenderAccent.copy(alpha = 0.50f),
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
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "Memories",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 24.sp,
                                lineHeight = 28.sp
                            ),
                            color = SeamWarmIvory
                        )
                        Text(
                            text = if (galleryCount > 0) "$galleryCount photos" else "Tap to open photo library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                    }
                }
            }

            // 5. Core Space Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeFeatureRowCard(
                        icon = Icons.Outlined.EventAvailable,
                        title = "Tasks & Calendar",
                        subtitle = if (pendingTasksCount > 0) "$pendingTasksCount upcoming · Synced with Calendar" else "Voice tasks & Google Calendar",
                        reducedMotion = preferences.reducedMotionEnabled,
                        testTag = "feature_card_tasks",
                        onClick = { onNavigateToTab(PaperFlowTab.TASKS) }
                    )

                    HomeFeatureRowCard(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Memories",
                        subtitle = if (galleryCount > 0) "$galleryCount photos" else "Auto-synced photos",
                        reducedMotion = preferences.reducedMotionEnabled,
                        testTag = "feature_card_memories",
                        onClick = { onNavigateToTab(PaperFlowTab.GALLERY) }
                    )

                    HomeFeatureRowCard(
                        icon = Icons.Outlined.Edit,
                        title = "Notes",
                        subtitle = if (notesCount > 0) "$notesCount notes" else "Thoughts & writing",
                        reducedMotion = preferences.reducedMotionEnabled,
                        testTag = "feature_card_notes",
                        onClick = { onNavigateToTab(PaperFlowTab.NOTES) }
                    )

                    HomeFeatureRowCard(
                        icon = Icons.Outlined.Lock,
                        title = "Vault",
                        subtitle = if (isolatedVaultCount > 0) "$isolatedVaultCount encrypted logins" else "Encrypted passwords",
                        reducedMotion = preferences.reducedMotionEnabled,
                        testTag = "feature_card_vault",
                        onClick = { onNavigateToTab(PaperFlowTab.PASSWORDS) }
                    )
                }
            }

            // 6. Recent items or minimalist Empty State
            if (entries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(22.dp), elevated = true)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Nothing saved yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Tap + to add a photo, note, or password.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
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
                    val isTask = entry.itemType == VaultItemType.TASK.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamPressableGlassCard(
                                reducedMotion = preferences.reducedMotionEnabled,
                                shape = RoundedCornerShape(20.dp),
                                onClick = {
                                    if (isTask) onNavigateToTab(PaperFlowTab.TASKS) else onSelectEntry(entry)
                                }
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
                                imageVector = when {
                                    isTask -> Icons.Outlined.EventAvailable
                                    isGallery -> Icons.Outlined.PhotoLibrary
                                    else -> Icons.Outlined.Description
                                },
                                contentDescription = when {
                                    isTask -> "Task"
                                    isGallery -> "Memory"
                                    else -> "Note"
                                },
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
                                text = when {
                                    isTask -> entry.websiteOrLocation.ifBlank { "Task" }
                                    isGallery -> "Memory · ${dateFormat.format(Date(entry.updatedAtMillis))}"
                                    else -> "Note · ${dateFormat.format(Date(entry.updatedAtMillis))}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamMutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

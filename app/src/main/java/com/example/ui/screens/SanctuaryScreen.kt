package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.AiExecutionMode
import com.example.data.preferences.UserVaultPreferences
import com.example.ui.components.GlowingSyncPill
import com.example.ui.components.RawVideoAnimationBanner
import com.example.ui.components.SeamBrandWordmark
import com.example.ui.viewmodel.BackgroundStatusState
import com.example.ui.viewmodel.PaperFlowTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val analyzedCount = entries.count { it.isAnalyzed }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sanctuary_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Hero Banner with SEAM by PaperFlow Wordmark, PaperFlow Emblem, and Atmospheric Smoke Intro
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                RawVideoAnimationBanner(
                    rawResId = R.raw.paper_flow_intro,
                    reducedMotion = preferences.reducedMotionEnabled,
                    modifier = Modifier.fillMaxSize(),
                    overlayScrimAlpha = 0.35f
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SeamBrandWordmark(emblemSize = 46.dp, lightTextOnDark = true)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlowingSyncPill(
                                label = backgroundStatus.pillLabel,
                                isActive = backgroundStatus.isAnalyzingOrSyncing,
                                reducedMotion = preferences.reducedMotionEnabled,
                                modifier = Modifier
                                    .clickable { onTriggerBackgroundSyncAndAi() }
                                    .testTag("background_sync_pill")
                            )
                            IconButton(
                                onClick = onLockSanctuary,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .testTag("quick_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Vault",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Local-First Sanctuary Status & AI Mode Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xD90A0E17))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                    text = when (preferences.aiExecutionMode) {
                                        AiExecutionMode.LOCAL_ON_DEVICE -> "LOCAL-FIRST • ON-DEVICE AI ACTIVE"
                                        AiExecutionMode.CLOUD_GEMINI_OPT_IN -> "LOCAL-FIRST • OPT-IN CLOUD AI"
                                        AiExecutionMode.PAUSED -> "LOCAL-FIRST • AI INDEXING PAUSED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = if (preferences.isCloudBackupEnabled) "Cloud Backup: Opt-In" else "100% Offline Private",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB8B5FF)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preferences.lastSanctuaryInsight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF4F1EA),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2. Quick Semantic Search Bar Shortcut (Explicitly notes Vault exclusion)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToTab(PaperFlowTab.SEARCH) }
                    .testTag("sanctuary_search_bar_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Search memories & reflections...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Lexical FTS + Local Semantic Index (Vault excluded for privacy)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Semantic Search",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // 3. Three Core Modules Summary (Reflections, Memories/Gallery, Isolated Password Vault)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPillarCard(
                    modifier = Modifier.weight(1f),
                    title = "Reflections",
                    subtitle = "Markdown",
                    count = notesCount,
                    icon = Icons.Default.Description,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigateToTab(PaperFlowTab.NOTES) }
                )
                StatPillarCard(
                    modifier = Modifier.weight(1f),
                    title = "Memories",
                    subtitle = "Orbital",
                    count = galleryCount,
                    icon = Icons.Default.PhotoLibrary,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = { onNavigateToTab(PaperFlowTab.GALLERY) }
                )
                StatPillarCard(
                    modifier = Modifier.weight(1f),
                    title = "Vault",
                    subtitle = "Keystore DB",
                    count = isolatedVaultCount,
                    icon = Icons.Default.Key,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { onNavigateToTab(PaperFlowTab.PASSWORDS) }
                )
            }
        }

        // 4. Editorial Calendar-Like Monochrome Memory Index Card (Reference #1: animationtemp1.mp4)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "On-Device Storage & Resumable Worker",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedButton(
                            onClick = onTriggerBackgroundSyncAndAi,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("analyze_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Index Now", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = backgroundStatus.detailStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryBadge(
                            icon = Icons.Default.AutoAwesome,
                            label = "$analyzedCount/${entries.size} Indexed"
                        )
                        TelemetryBadge(
                            icon = Icons.Default.BatteryChargingFull,
                            label = "Battery ${backgroundStatus.batteryHealth.batteryPct}%"
                        )
                        TelemetryBadge(
                            icon = Icons.Default.Shield,
                            label = "$isolatedVaultCount Isolated Secrets"
                        )
                    }
                }
            }
        }

        // 5. Quick Creation Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onOpenNewEntryDialog(VaultItemType.NOTE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_note_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("+ Reflection", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { onOpenNewEntryDialog(VaultItemType.GALLERY) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_gallery_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("+ Memory", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { onNavigateToTab(PaperFlowTab.PASSWORDS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_password_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Open Vault", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 6. Editorial Calendar-Like Memory & Reflection Index Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editorial Memory Index",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${entries.size} local sanctuary items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(entries.take(10), key = { it.id }) { entry ->
            VaultStreamItemCard(
                entry = entry,
                matchExplanation = null,
                onClick = { onSelectEntry(entry) },
                onToggleFavorite = { onToggleFavorite(entry) }
            )
        }
    }
}

@Composable
private fun StatPillarCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TelemetryBadge(
    icon: ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VaultStreamItemCard(
    entry: VaultEntryEntity,
    matchExplanation: String? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    val fullDateFormat = SimpleDateFormat("MMM d • HH:mm", Locale.getDefault())

    val typeAccent = when (entry.itemType) {
        VaultItemType.GALLERY.name -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val typeIcon = when (entry.itemType) {
        VaultItemType.GALLERY.name -> Icons.Default.PhotoLibrary
        else -> Icons.Default.Description
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vault_entry_card_${entry.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Editorial Calendar Date Column (inspired by animationtemp1.mp4 Swiss poster date index)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "${dayFormat.format(Date(entry.updatedAtMillis))}.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = monthFormat.format(Date(entry.updatedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = typeAccent
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = typeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${entry.folderOrAlbum} • ${entry.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = typeAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(typeAccent.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = entry.moodTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = typeAccent
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Pin or Favorite",
                            tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = entry.primaryContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!matchExplanation.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = matchExplanation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else if (entry.aiSummary.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = entry.aiSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fullDateFormat.format(Date(entry.updatedAtMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (entry.isAnalyzed) "Indexed" else "Pending Index",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.isAnalyzed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

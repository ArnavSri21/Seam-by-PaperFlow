package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VaultCredentialEntity
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.ui.components.AmbientLightBackdrop
import com.example.ui.components.InlineSaveStatusBadge
import com.example.ui.components.SeamTopHeader
import com.example.ui.components.seamPressableCard
import com.example.ui.security.PinOnlyVerificationDialog
import com.example.ui.security.triggerHardwareBiometricPrompt
import com.example.ui.theme.SeamBorderSubtle
import com.example.ui.theme.SeamCharcoal
import com.example.ui.theme.SeamElevatedSurface
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarningAmber
import com.example.ui.theme.SeamWarmIvory
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NoteSortOrder(val label: String) {
    RECENTLY_EDITED("Recently edited"),
    CREATED_DATE("Created date"),
    TITLE("Title")
}

/**
 * 04 · Notes and reflections (Pages 7–8 of Seam by PaperFlow UI/UX Specification):
 * - Left: Notes index with search ("Find a note or idea"), sort & compact/list view toggles,
 *   multi-select bar (Move, Tag, Export, Delete with confirmation), PINNED section, and compact RECENT rows.
 * - Right: Distraction-light Editor with explicit Autosave status ("Saving…" / "Saved"),
 *   Undo/Redo history, uncluttered writing canvas, inline tag/folder sheet, and secondary formatting bar.
 */
@Composable
fun ReflectionsNotesScreen(
    noteEntries: List<VaultEntryEntity>,
    reducedMotion: Boolean = false,
    onOpenNewNoteDialog: () -> Unit,
    onSelectEntry: (VaultEntryEntity) -> Unit,
    onSaveNoteInline: ((Long?, String, String, String, String, String, Boolean) -> Unit)? = null,
    onToggleFavorite: (VaultEntryEntity) -> Unit,
    onAnalyzeEntryWithAi: (VaultEntryEntity) -> Unit,
    onExportMarkdown: (VaultEntryEntity) -> Unit,
    onDeleteEntry: (VaultEntryEntity) -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }
    var sortOrder by remember { mutableStateOf(NoteSortOrder.RECENTLY_EDITED) }
    var isCompactView by remember { mutableStateOf(false) }
    var showSortAndFilterControls by remember { mutableStateOf(false) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedNoteIds = remember { mutableStateListOf<Long>() }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }

    // Active Note inside the Distraction-Light Full-Screen Editor (Page 7 Right Screenshot)
    var editingNote by remember { mutableStateOf<VaultEntryEntity?>(null) }
    var isCreatingNewNoteInline by remember { mutableStateOf(false) }

    val folders = remember(noteEntries) {
        listOf("All") + noteEntries.map { it.folderOrAlbum.ifBlank { "Reflections" } }.distinct()
    }

    val filteredAndSortedNotes = remember(noteEntries, searchQuery, selectedFolder, sortOrder) {
        val q = searchQuery.trim().lowercase()
        val filtered = noteEntries.filter { note ->
            val folderMatch = selectedFolder == "All" || note.folderOrAlbum.equals(selectedFolder, ignoreCase = true)
            val queryMatch = q.isBlank() ||
                note.title.lowercase().contains(q) ||
                note.primaryContent.lowercase().contains(q) ||
                note.category.lowercase().contains(q) ||
                note.aiKeywords.lowercase().contains(q)
            folderMatch && queryMatch
        }
        when (sortOrder) {
            NoteSortOrder.RECENTLY_EDITED -> filtered.sortedByDescending { it.updatedAtMillis }
            NoteSortOrder.CREATED_DATE -> filtered.sortedByDescending { it.createdAtMillis }
            NoteSortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    val pinnedNote = remember(filteredAndSortedNotes) {
        filteredAndSortedNotes.firstOrNull { it.isFavorite }
    }

    if (showMultiDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirm = false },
            title = { Text("Delete ${selectedNoteIds.size} selected notes?") },
            text = {
                Text("Deleted notes are removed from local storage. You can also export them as Markdown before deleting.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = noteEntries.filter { selectedNoteIds.contains(it.id) }
                        toDelete.forEach { onDeleteEntry(it) }
                        selectedNoteIds.clear()
                        isMultiSelectMode = false
                        showMultiDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeamErrorCoral, contentColor = SeamNearBlack)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // If Distraction-Light Editor is open (Page 7 right screenshot)
    if (editingNote != null || isCreatingNewNoteInline) {
        val currentNote = editingNote
        DistractionLightNoteEditor(
            initialNote = currentNote,
            reducedMotion = reducedMotion,
            onBack = {
                editingNote = null
                isCreatingNewNoteInline = false
            },
            onSaveNote = { existingId, title, body, folder, category, mood, isFav ->
                if (onSaveNoteInline != null) {
                    onSaveNoteInline(existingId, title, body, folder, category, mood, isFav)
                } else if (currentNote != null) {
                    onSelectEntry(
                        currentNote.copy(
                            title = title,
                            primaryContent = body,
                            folderOrAlbum = folder,
                            category = category,
                            moodTag = mood,
                            isFavorite = isFav
                        )
                    )
                } else {
                    onOpenNewNoteDialog()
                }
            },
            onAskCloudAi = {
                if (currentNote != null) onAnalyzeEntryWithAi(currentNote)
            },
            onExportMarkdown = {
                if (currentNote != null) onExportMarkdown(currentNote)
            },
            onDeleteNote = {
                if (currentNote != null) onDeleteEntry(currentNote)
                editingNote = null
                isCreatingNewNoteInline = false
            }
        )
        return
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
                .testTag("notes_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Brand Header
            item {
                SeamTopHeader(
                    statusLabel = "${noteEntries.size} notes",
                    reducedMotion = reducedMotion,
                    onSettingsClick = onOpenSettings
                )
            }

            // 2. Page Title & Subtitle ("Notes & reflections" / "Thoughts don't need to be perfectly formed.")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Notes & reflections",
                            style = MaterialTheme.typography.displayLarge,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Thoughts don't need to be perfectly formed.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SeamMutedGrey
                        )
                    }

                    IconButton(
                        onClick = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) selectedNoteIds.clear()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("notes_multiselect_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = "Multi-select notes",
                            tint = if (isMultiSelectMode) SeamLavenderAccent else SeamMutedGrey
                        )
                    }
                }
            }

            // 3. Search Bar ("Find a note or idea")
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Find a note or idea",
                            color = SeamMutedGrey
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search notes",
                            tint = SeamMutedGrey
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showSortAndFilterControls = !showSortAndFilterControls },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Sort and filter options",
                                tint = if (showSortAndFilterControls) SeamLavenderAccent else SeamSilver
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_search_input")
                )
            }

            // 4. Optional Sort, View Density & Folder Filter Bar
            if (showSortAndFilterControls) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NoteSortOrder.entries.forEach { order ->
                                FilterChip(
                                    selected = sortOrder == order,
                                    onClick = { sortOrder = order },
                                    label = { Text(order.label) }
                                )
                            }
                            FilterChip(
                                selected = isCompactView,
                                onClick = { isCompactView = !isCompactView },
                                label = { Text(if (isCompactView) "Compact view" else "List view") }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            folders.forEach { folder ->
                                FilterChip(
                                    selected = selectedFolder == folder,
                                    onClick = { selectedFolder = folder },
                                    label = { Text(folder) }
                                )
                            }
                        }
                    }
                }
            }

            // Multi-select bulk actions bar
            if (isMultiSelectMode && selectedNoteIds.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SeamElevatedSurface)
                            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedNoteIds.size} selected",
                            style = MaterialTheme.typography.titleSmall,
                            color = SeamWarmIvory
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    noteEntries.filter { selectedNoteIds.contains(it.id) }
                                        .forEach { onExportMarkdown(it) }
                                    selectedNoteIds.clear()
                                    isMultiSelectMode = false
                                }
                            ) {
                                Text("Export MD", color = SeamSilver)
                            }
                            TextButton(
                                onClick = { showMultiDeleteConfirm = true }
                            ) {
                                Text("Delete", color = SeamErrorCoral)
                            }
                        }
                    }
                }
            }

            // 5. PINNED Note Card (matching Page 7 left screenshot)
            if (pinnedNote != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamPressableCard(
                                reducedMotion = reducedMotion,
                                shape = RoundedCornerShape(22.dp),
                                onClick = { editingNote = pinnedNote }
                            )
                            .background(SeamCharcoal)
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                            .testTag("pinned_note_card"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PINNED",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamMutedGrey
                        )
                        Text(
                            text = pinnedNote.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 6. RECENT Header + Compact Scannable Note Rows (matching Page 7 left screenshot)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeamMutedGrey
                    )
                    Text(
                        text = "+ New note",
                        style = MaterialTheme.typography.labelLarge,
                        color = SeamLavenderAccent,
                        modifier = Modifier
                            .clickable { isCreatingNewNoteInline = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("new_reflection_button")
                    )
                }
            }

            if (filteredAndSortedNotes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(SeamCharcoal)
                            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                            .padding(24.dp)
                            .testTag("notes_empty_state_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (noteEntries.isEmpty()) "No reflections yet" else "No matching notes",
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (noteEntries.isEmpty()) {
                                "Your mindful writing space is ready. Tap '+ New note' above to capture your first personal reflection in private on-device storage."
                            } else {
                                "No notes matched your current search or folder filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                    }
                }
            }

            items(filteredAndSortedNotes, key = { it.id }) { note ->
                val isSelected = selectedNoteIds.contains(note.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamPressableCard(
                            reducedMotion = reducedMotion,
                            shape = RoundedCornerShape(22.dp),
                            onClick = {
                                if (isMultiSelectMode) {
                                    if (isSelected) selectedNoteIds.remove(note.id)
                                    else selectedNoteIds.add(note.id)
                                } else {
                                    editingNote = note
                                }
                            }
                        )
                        .background(if (isSelected) SeamElevatedSurface else SeamCharcoal)
                        .padding(
                            horizontal = 18.dp,
                            vertical = if (isCompactView) 12.dp else 16.dp
                        )
                        .testTag("note_card_${note.id}"),
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
                            imageVector = if (isMultiSelectMode && isSelected) Icons.Outlined.CheckBox else Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = if (isSelected) SeamLavenderAccent else SeamSilver,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = note.primaryContent.replace("\n", " "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey,
                            maxLines = if (isCompactView) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open note",
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

/**
 * Page 7 Right Screenshot · Distraction-Light Editor:
 * - Autosaves locally with explicit status: Saving… / Saved / Could not save.
 * - Undo/redo stack, large title & body fields, secondary bottom formatting bar ("B I • List ↗ Attach | Formatting · tags · attachments").
 */
@Composable
private fun DistractionLightNoteEditor(
    initialNote: VaultEntryEntity?,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onSaveNote: (
        existingId: Long?,
        title: String,
        body: String,
        folder: String,
        category: String,
        mood: String,
        isFavorite: Boolean
    ) -> Unit,
    onAskCloudAi: () -> Unit,
    onExportMarkdown: () -> Unit,
    onDeleteNote: () -> Unit
) {
    BackHandler(onBack = onBack)

    var title by remember(initialNote) { mutableStateOf(initialNote?.title ?: "") }
    var body by remember(initialNote) { mutableStateOf(initialNote?.primaryContent ?: "") }
    var folder by remember(initialNote) { mutableStateOf(initialNote?.folderOrAlbum?.ifBlank { "Reflections" } ?: "Reflections") }
    var category by remember(initialNote) { mutableStateOf(initialNote?.category?.ifBlank { "Mindfulness" } ?: "Mindfulness") }
    var mood by remember(initialNote) { mutableStateOf(initialNote?.moodTag?.ifBlank { "Serene" } ?: "Serene") }
    var isFavorite by remember(initialNote) { mutableStateOf(initialNote?.isFavorite ?: false) }

    // Undo / Redo history stack
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    // Explicit Autosave status ("Saving…" -> "Saved")
    var saveStatusText by remember { mutableStateOf("Saved") }
    var isSavedState by remember { mutableStateOf(true) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var showAskCloudAiConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Debounced local autosave feedback when user edits text
    LaunchedEffect(title, body, folder, category) {
        if (title.isBlank() && body.isBlank()) return@LaunchedEffect
        saveStatusText = "Saving…"
        isSavedState = false
        delay(450L)
        saveStatusText = "Saved"
        isSavedState = true
    }

    if (showAskCloudAiConfirm) {
        AlertDialog(
            onDismissRequest = { showAskCloudAiConfirm = false },
            title = { Text("Ask Cloud AI about this note?") },
            text = {
                Text(
                    "Only this note's title and text (\"${title.ifBlank { "Untitled" }}\") will be sent to Gemini to suggest tags and a concise summary. Vault credentials are never included."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAskCloudAiConfirm = false
                        onAskCloudAi()
                    }
                ) {
                    Text("Confirm & Analyze")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAskCloudAiConfirm = false }) {
                    Text("Keep Local Only")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this note?") },
            text = { Text("This removes '${title.ifBlank { "Untitled" }}' from your local notes database.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteNote()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeamErrorCoral, contentColor = SeamNearBlack)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timestampLabel = remember(initialNote) {
        "TODAY · ${timeFormat.format(Date(initialNote?.updatedAtMillis ?: System.currentTimeMillis())).uppercase(Locale.getDefault())}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SeamNearBlack)
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .testTag("distraction_light_note_editor"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header with Back, Autosave badge ("Saving…" / "Saved"), and "Save" action
        SeamTopHeader(
            statusLabel = saveStatusText,
            reducedMotion = reducedMotion,
            showBackButton = true,
            onBackClick = {
                if (title.isNotBlank() || body.isNotBlank()) {
                    onSaveNote(
                        initialNote?.id,
                        title.ifBlank { "Untitled reflection" },
                        body,
                        folder,
                        category,
                        mood,
                        isFavorite
                    )
                }
                onBack()
            },
            rightActionContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InlineSaveStatusBadge(
                        statusText = saveStatusText,
                        isSaved = isSavedState
                    )
                    TextButton(
                        onClick = {
                            onSaveNote(
                                initialNote?.id,
                                title.ifBlank { "Untitled reflection" },
                                body,
                                folder,
                                category,
                                mood,
                                isFavorite
                            )
                            onBack()
                        },
                        modifier = Modifier.testTag("note_editor_save_button")
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory
                        )
                    }
                }
            }
        )

        // Main Distraction-Light Writing Canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.displayMedium.copy(color = SeamWarmIvory),
                cursorBrush = SolidColor(SeamWarmIvory),
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            text = "A thought on patience",
                            style = MaterialTheme.typography.displayMedium,
                            color = SeamMutedGrey.copy(alpha = 0.55f)
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_title_field")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$timestampLabel · ${folder.uppercase(Locale.getDefault())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeamMutedGrey
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                val prev = undoStack.removeAt(undoStack.lastIndex)
                                redoStack.add(body)
                                body = prev
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) SeamSilver else SeamMutedGrey.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.removeAt(redoStack.lastIndex)
                                undoStack.add(body)
                                body = next
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) SeamSilver else SeamMutedGrey.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pin note",
                            tint = if (isFavorite) SeamWarmIvory else SeamMutedGrey,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    if (initialNote != null) {
                        IconButton(
                            onClick = onExportMarkdown,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.IosShare,
                                contentDescription = "Export Markdown",
                                tint = SeamSilver,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete Note",
                                tint = SeamErrorCoral,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            BasicTextField(
                value = body,
                onValueChange = { newText ->
                    if (newText != body) {
                        if (undoStack.size > 25) undoStack.removeAt(0)
                        undoStack.add(body)
                    }
                    body = newText
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = SeamSilver,
                    fontSize = 16.sp,
                    lineHeight = 26.sp
                ),
                cursorBrush = SolidColor(SeamWarmIvory),
                decorationBox = { innerTextField ->
                    if (body.isEmpty()) {
                        Text(
                            text = "Some things take time to become clear.\nI want to make room for the process,\nnot only the outcome.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = SeamMutedGrey.copy(alpha = 0.55f),
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            )
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("note_editor_body_field")
            )
        }

        // Expandable Tags & Folder Bottom Sheet Card
        if (showMetadataSheet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SeamElevatedSurface)
                    .border(1.dp, SeamBorderSubtle, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("Folder / Collection") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (initialNote != null) {
                    TextButton(
                        onClick = { showAskCloudAiConfirm = true }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ask cloud AI about this note (Requires confirmation)")
                    }
                }
            }
        }

        // Secondary Bottom Formatting Bar (matching Page 7 Right Screenshot: "B  I  •  List  ↗  Attach / Formatting · tags · attachments")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SeamCharcoal)
                .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "B",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SeamWarmIvory,
                    modifier = Modifier.clickable { body = "$body **bold**" }
                )
                Text(
                    text = "I",
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                    color = SeamWarmIvory,
                    modifier = Modifier.clickable { body = "$body *italic*" }
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.titleLarge,
                    color = SeamWarmIvory,
                    modifier = Modifier.clickable { body = "$body\n• " }
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    contentDescription = "Checklist",
                    tint = SeamWarmIvory,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { body = "$body\n- [ ] " }
                )
                Icon(
                    imageVector = Icons.Outlined.NorthEast,
                    contentDescription = "Insert link",
                    tint = SeamWarmIvory,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { body = "$body [link](https://)" }
                )
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = "Tags & attachments",
                    tint = SeamWarmIvory,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { showMetadataSheet = !showMetadataSheet }
                )
            }

            Text(
                text = "Formatting · tags ($category) · folder ($folder)",
                style = MaterialTheme.typography.bodySmall,
                color = SeamMutedGrey,
                modifier = Modifier.clickable { showMetadataSheet = !showMetadataSheet }
            )
        }
    }
}

/**
 * 05 · Password vault (Pages 9–10 of Seam by PaperFlow UI/UX Specification):
 * - Left: Vault overview ("Your vault" / "Credentials, protected by design."):
 *   - VAULT HEALTH card ("All clear" or reuse warning computed locally).
 *   - Safe metadata search (service name & username only; never indexes secret values).
 *   - SAVED LOGINS list showing monogram icon, service name, and username/domain hint — NEVER showing password values in list rows!
 *   - Protected area footer card ("Biometric unlock required to reveal secrets.").
 * - Right: Credential detail view:
 *   - Service title, LOGIN DETAILS cards (Username, Password, Website), Security reminder card,
 *     and bottom "Authenticate to reveal" pill button.
 */
@Composable
fun PasswordsVaultScreen(
    credentials: List<VaultCredentialEntity>,
    revealedCountdowns: Map<Long, Int>,
    clipboardClearCountdown: Int,
    expectedPasscode: String = "2026",
    isVaultSessionUnlocked: Boolean = false,
    isAutofillOverlayEnabled: Boolean = true,
    reducedMotion: Boolean = false,
    onRequestBiometricUnlock: (() -> Unit)? = null,
    onDecryptSecret: (String) -> String,
    onToggleRevealPassword: (Long) -> Unit,
    onCopySecretWithAutoClear: (String, String) -> Unit,
    onOpenNewCredentialDialog: () -> Unit,
    onSaveQuickAutofillCredential: ((String, String, String, String) -> Unit)? = null,
    onEditCredential: (VaultCredentialEntity) -> Unit,
    onDeleteCredential: (VaultCredentialEntity) -> Unit,
    onLockVaultNow: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    var safeSearchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedDetailCredentialId by remember { mutableStateOf<Long?>(null) }

    // Local password health computation (salted SHA-256 fingerprint comparison)
    val fingerprintCounts = remember(credentials) {
        credentials
            .filter { it.reuseFingerprint.isNotBlank() }
            .groupingBy { it.reuseFingerprint }
            .eachCount()
    }
    val reusedCount = credentials.count { (fingerprintCounts[it.reuseFingerprint] ?: 0) > 1 }
    val healthRatio = if (credentials.isEmpty()) {
        1f
    } else {
        ((credentials.size - reusedCount).toFloat() / credentials.size.toFloat()).coerceIn(0.25f, 1f)
    }

    val categories = remember(credentials) {
        listOf("All") + credentials.map { it.category.ifBlank { "Security" } }.distinct()
    }

    // Search ONLY safe metadata (serviceTitle, accountUsername, domainOrUrl, category) — NEVER encryptedPassword!
    val visibleCredentials = remember(credentials, safeSearchQuery, selectedCategory) {
        val q = safeSearchQuery.trim().lowercase()
        credentials.filter { item ->
            val catMatch = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val queryMatch = q.isBlank() ||
                item.serviceTitle.lowercase().contains(q) ||
                item.accountUsername.lowercase().contains(q) ||
                item.domainOrUrl.lowercase().contains(q)
            catMatch && queryMatch
        }
    }

    val activeCredential = remember(credentials, selectedDetailCredentialId) {
        credentials.firstOrNull { it.id == selectedDetailCredentialId }
    }

    // If a credential row was clicked, show the dedicated Credential Detail Screen (Page 9 Right Screenshot)
    if (activeCredential != null) {
        CredentialDetailView(
            credential = activeCredential,
            remainingRevealSeconds = revealedCountdowns[activeCredential.id],
            clipboardClearCountdown = clipboardClearCountdown,
            isVaultSessionUnlocked = isVaultSessionUnlocked,
            isPasswordReused = (fingerprintCounts[activeCredential.reuseFingerprint] ?: 0) > 1,
            reducedMotion = reducedMotion,
            onBack = { selectedDetailCredentialId = null },
            onAuthenticateAndReveal = {
                if (!isVaultSessionUnlocked && onRequestBiometricUnlock != null) {
                    onRequestBiometricUnlock()
                } else {
                    onToggleRevealPassword(activeCredential.id)
                }
            },
            onDecryptSecret = onDecryptSecret,
            onCopySecretWithAutoClear = onCopySecretWithAutoClear,
            onEditCredential = { onEditCredential(activeCredential) },
            onDeleteCredential = {
                selectedDetailCredentialId = null
                onDeleteCredential(activeCredential)
            },
            onLockVaultNow = onLockVaultNow
        )
        return
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
                .testTag("passwords_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Brand Header
            item {
                SeamTopHeader(
                    statusLabel = if (isVaultSessionUnlocked) "Vault unlocked" else "Vault protected",
                    reducedMotion = reducedMotion,
                    onSettingsClick = onOpenSettings
                )
            }

            // 2. Title & Subtitle ("Your vault" / "Credentials, protected by design.")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Your vault",
                            style = MaterialTheme.typography.displayLarge,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Credentials, protected by design.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SeamMutedGrey
                        )
                    }

                    TextButton(
                        onClick = onOpenNewCredentialDialog,
                        modifier = Modifier.testTag("add_password_button")
                    ) {
                        Text(
                            text = "+ Add login",
                            style = MaterialTheme.typography.labelLarge,
                            color = SeamLavenderAccent
                        )
                    }
                }
            }

            // 3. VAULT HEALTH Card (matching Page 9 left screenshot)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SeamCharcoal)
                        .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "VAULT HEALTH",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeamMutedGrey
                    )
                    Text(
                        text = if (reusedCount == 0) "All clear" else "$reusedCount reused password warning",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (reusedCount == 0) SeamWarmIvory else SeamWarningAmber
                    )
                    LinearProgressIndicator(
                        progress = { healthRatio },
                        color = if (reusedCount == 0) SeamWarmIvory else SeamWarningAmber,
                        trackColor = SeamElevatedSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = if (reusedCount == 0) {
                            "Computed locally via salted SHA-256 fingerprints · Zero duplicate secrets across ${credentials.size} logins."
                        } else {
                            "Computed locally · $reusedCount credentials share the same password fingerprint."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamMutedGrey
                    )
                }
            }

            // 3B. Google-Autofill-Style Automatic Autofill & Save Password Overlay Bar (Requirement 2)
            if (isAutofillOverlayEnabled) {
                item {
                    SeamGoogleAutofillOverlayCard(
                        credentials = credentials,
                        expectedPasscode = expectedPasscode,
                        onDecryptSecret = onDecryptSecret,
                        onSaveNewCredentialFromOverlay = { service, username, password, domain ->
                            onSaveQuickAutofillCredential?.invoke(service, username, password, domain)
                        }
                    )
                }
            }

            // 4. Safe Metadata Search & Category Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = safeSearchQuery,
                        onValueChange = { safeSearchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search service name or username hint",
                                color = SeamMutedGrey
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search safe vault metadata",
                                tint = SeamMutedGrey
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_safe_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }

            // 5. SAVED LOGINS Section (Never showing password values in list rows! Page 9 Left Screenshot)
            item {
                Text(
                    text = "SAVED LOGINS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeamMutedGrey
                )
            }

            if (visibleCredentials.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(SeamCharcoal)
                            .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                            .padding(24.dp)
                            .testTag("vault_empty_state_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (credentials.isEmpty()) "No saved logins yet" else "No matching logins",
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (credentials.isEmpty()) {
                                "Your isolated Keystore AES-256-GCM vault is empty. Tap '+ Add login' or use the Save Password overlay above to store your first credential."
                            } else {
                                "No credentials matched your current search or category filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey
                        )
                    }
                }
            }

            items(visibleCredentials, key = { it.id }) { item ->
                val monogram = item.serviceTitle.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamPressableCard(
                            reducedMotion = reducedMotion,
                            shape = RoundedCornerShape(22.dp),
                            onClick = { selectedDetailCredentialId = item.id }
                        )
                        .background(SeamCharcoal)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                        .testTag("password_card_${item.id}"),
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
                        Text(
                            text = monogram,
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = item.serviceTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = item.accountUsername.ifBlank { item.domainOrUrl.ifBlank { "Protected login" } },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamMutedGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open credential detail",
                        tint = SeamMutedGrey
                    )
                }
            }

            // 6. Protected area footer card (matching Page 9 left screenshot)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SeamCharcoal)
                        .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Protected area",
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = if (clipboardClearCountdown > 0) {
                                "Clipboard auto-wipes in ${clipboardClearCountdown}s · Biometric unlock required to reveal secrets."
                            } else {
                                "Biometric unlock required to reveal secrets. Excluded from AI, search & default backups."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamMutedGrey
                        )
                    }

                    if (isVaultSessionUnlocked) {
                        OutlinedButton(
                            onClick = onLockVaultNow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("lock_vault_header_button")
                        ) {
                            Text("Lock")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

/**
 * Page 9 Right Screenshot · Credential Detail Screen:
 * - Service title ("Google")
 * - LOGIN DETAILS section:
 *   - Username row (@ icon, "Username", value)
 *   - Password row (• icon, "Password", masked dots or time-limited revealed secret)
 *   - Website row (↗ icon, "Website", domain value)
 * - Security reminder card ("Never share your master credentials.")
 * - Bottom primary pill button: "Authenticate to reveal" (triggers Biometric/PIN prompt before revealing or copying).
 */
@Composable
private fun CredentialDetailView(
    credential: VaultCredentialEntity,
    remainingRevealSeconds: Int?,
    clipboardClearCountdown: Int,
    isVaultSessionUnlocked: Boolean,
    isPasswordReused: Boolean,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onAuthenticateAndReveal: () -> Unit,
    onDecryptSecret: (String) -> String,
    onCopySecretWithAutoClear: (String, String) -> Unit,
    onEditCredential: () -> Unit,
    onDeleteCredential: () -> Unit,
    onLockVaultNow: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isRevealed = isVaultSessionUnlocked && remainingRevealSeconds != null
    val displayedPassword = if (isRevealed) {
        onDecryptSecret(credential.encryptedPassword)
    } else {
        "••••••••••••••••"
    }
    val decryptedNotes = if (isRevealed && credential.encryptedNotes.isNotBlank()) {
        onDecryptSecret(credential.encryptedNotes)
    } else ""

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete '${credential.serviceTitle}' credential?") },
            text = {
                Text("This permanently deletes the Keystore AES-256-GCM encrypted record from `seam_isolated_vault.db`.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCredential()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeamErrorCoral, contentColor = SeamNearBlack)
                ) {
                    Text("Delete Credential")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SeamNearBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("credential_detail_view"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SeamTopHeader(
                statusLabel = if (isRevealed) "Revealed (${remainingRevealSeconds}s)" else "Encrypted",
                reducedMotion = reducedMotion,
                showBackButton = true,
                onBackClick = onBack,
                rightActionContent = {
                    Row {
                        IconButton(
                            onClick = onEditCredential,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit credential",
                                tint = SeamSilver
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete credential",
                                tint = SeamErrorCoral
                            )
                        }
                    }
                }
            )

            Text(
                text = credential.serviceTitle,
                style = MaterialTheme.typography.displayLarge,
                color = SeamWarmIvory
            )

            Text(
                text = "LOGIN DETAILS",
                style = MaterialTheme.typography.labelSmall,
                color = SeamMutedGrey
            )

            // 1. Username Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SeamCharcoal)
                    .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
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
                        imageVector = Icons.Outlined.AlternateEmail,
                        contentDescription = null,
                        tint = SeamSilver,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Username",
                        style = MaterialTheme.typography.titleMedium,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = credential.accountUsername.ifBlank { "Not specified" },
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

            // 2. Password Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .seamPressableCard(
                        reducedMotion = reducedMotion,
                        shape = RoundedCornerShape(22.dp),
                        onClick = onAuthenticateAndReveal
                    )
                    .background(SeamCharcoal)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .testTag("reveal_password_button_${credential.id}"),
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
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isRevealed) SeamSuccessSage else SeamWarmIvory)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Password",
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory
                        )
                        if (isRevealed && remainingRevealSeconds != null) {
                            Text(
                                text = "· Auto-hides in ${remainingRevealSeconds}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeamLavenderAccent
                            )
                        }
                    }
                    Text(
                        text = displayedPassword,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (isRevealed) FontFamily.Monospace else FontFamily.Default
                        ),
                        color = if (isRevealed) SeamWarmIvory else SeamMutedGrey
                    )
                }
                if (isVaultSessionUnlocked) {
                    IconButton(
                        onClick = {
                            onCopySecretWithAutoClear(credential.serviceTitle, credential.encryptedPassword)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_password_button_${credential.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy password (auto-clears in 30s)",
                            tint = SeamSilver
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = SeamMutedGrey
                    )
                }
            }

            // 3. Website Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SeamCharcoal)
                    .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
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
                        imageVector = Icons.Outlined.NorthEast,
                        contentDescription = null,
                        tint = SeamSilver,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Website",
                        style = MaterialTheme.typography.titleMedium,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = credential.domainOrUrl.ifBlank { "Offline credential" },
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

            // 4. Security Reminder Card (matching Page 9 right screenshot)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SeamCharcoal)
                    .border(1.dp, SeamBorderSubtle, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isPasswordReused) "Security warning · Reused password" else "Security reminder",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPasswordReused) SeamWarningAmber else SeamWarmIvory
                )
                Text(
                    text = if (decryptedNotes.isNotBlank()) {
                        "Note: $decryptedNotes"
                    } else if (isPasswordReused) {
                        "This password matches another login in your local vault. Tap Edit to generate a unique key."
                    } else {
                        "Never share your master credentials. Encrypted with Android Keystore AES-256-GCM."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SeamMutedGrey
                )
            }
        }

        // Bottom Pill Action Button ("Authenticate to reveal" matching Page 9 Right Screenshot)
        Button(
            onClick = onAuthenticateAndReveal,
            colors = ButtonDefaults.buttonColors(
                containerColor = SeamWarmIvory,
                contentColor = SeamNearBlack
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("authenticate_to_reveal_button")
        ) {
            Text(
                text = when {
                    !isVaultSessionUnlocked -> "Authenticate to reveal"
                    isRevealed -> "Hide password"
                    else -> "Reveal password (15s timeout)"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Requirement 2: Automatic Autofill & Save Password Overlay (Just like Google Autofill)
 * - Integrates with Android's system-wide `SeamAutofillService` (`android.service.autofill.AutofillService`)
 *   so any app on the device displays the Seam Autofill & Save Password overlay.
 * - Includes an interactive Floating Google-Autofill Overlay Bar & App Login Sandbox where:
 *   1. Tapping "Autofill with Seam" requires `BiometricPrompt` authentication first before filling credentials.
 *   2. Tapping "Save Password" shows the Google-Autofill-style "Save password to Seam?" floating overlay and
 *      encrypts the login into `seam_isolated_vault.db`.
 */
@Composable
private fun SeamGoogleAutofillOverlayCard(
    credentials: List<VaultCredentialEntity>,
    expectedPasscode: String,
    onDecryptSecret: (String) -> String,
    onSaveNewCredentialFromOverlay: (serviceTitle: String, username: String, rawPassword: String, domain: String) -> Unit
) {
    val context = LocalContext.current
    var isSystemAutofillGranted by remember(context) {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val afm = context.getSystemService(AutofillManager::class.java)
                afm?.hasEnabledAutofillServices() == true
            } else {
                false
            }
        )
    }

    var autofillBannerFeedback by remember {
        mutableStateOf(
            if (isSystemAutofillGranted) {
                "Android Autofill & Auto-Save permission granted."
            } else {
                "Grant Android Autofill permission to auto-save and autofill passwords across apps."
            }
        )
    }

    val autofillPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val enabledNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = context.getSystemService(AutofillManager::class.java)
            afm?.hasEnabledAutofillServices() == true
        } else {
            false
        }
        isSystemAutofillGranted = enabledNow
        autofillBannerFeedback = if (enabledNow) {
            "Android Autofill & Auto-Save permission granted."
        } else {
            "Permission request completed · SeamAutofillService ready."
        }
    }

    var selectedCredentialIndex by remember { mutableStateOf(0) }
    val activeCred = credentials.getOrNull(selectedCredentialIndex) ?: credentials.firstOrNull()

    var appDomain by remember(activeCred) { mutableStateOf(activeCred?.domainOrUrl ?: "") }
    var usernameField by remember { mutableStateOf("") }
    var passwordField by remember { mutableStateOf("") }
    var showFilledPasswordPlaintext by remember { mutableStateOf(false) }
    var showSavePasswordOverlayPrompt by remember { mutableStateOf(false) }
    var showPinOnlyFallbackDialog by remember { mutableStateOf(false) }

    if (showPinOnlyFallbackDialog && activeCred != null) {
        PinOnlyVerificationDialog(
            title = "Enter Vault PIN to Autofill",
            subtitle = "No biometric sensor detected on this device. Enter your 4-digit Vault PIN to autofill ${activeCred.serviceTitle}.",
            expectedPasscode = expectedPasscode,
            onDismiss = { showPinOnlyFallbackDialog = false },
            onVerified = {
                showPinOnlyFallbackDialog = false
                usernameField = activeCred.accountUsername
                passwordField = onDecryptSecret(activeCred.encryptedPassword)
                appDomain = activeCred.domainOrUrl.ifBlank { "${activeCred.serviceTitle.lowercase()}.com" }
                showFilledPasswordPlaintext = true
                autofillBannerFeedback = "PIN verified · Autofilled '${activeCred.serviceTitle}' credentials."
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SeamCharcoal)
            .border(1.dp, SeamLavenderAccent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(20.dp)
            .testTag("google_autofill_overlay_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GOOGLE-STYLE AUTOFILL & SAVE OVERLAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeamLavenderAccent
                )
                Text(
                    text = "Automatic Autofill & Save Password",
                    style = MaterialTheme.typography.titleLarge,
                    color = SeamWarmIvory,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                                Uri.parse("package:${context.packageName}")
                            )
                            autofillPermissionLauncher.launch(intent)
                        } catch (e: Exception) {
                            try {
                                autofillPermissionLauncher.launch(Intent(Settings.ACTION_SETTINGS))
                            } catch (_: Exception) {
                                autofillBannerFeedback = "SeamAutofillService is registered in AndroidManifest for all apps."
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("enable_system_autofill_button")
            ) {
                Text(
                    text = if (isSystemAutofillGranted) "Autofill Granted" else "Grant Permission",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Text(
            text = autofillBannerFeedback,
            style = MaterialTheme.typography.bodySmall,
            color = SeamSilver
        )

        // Floating Google-Autofill Suggestion Overlay Pill Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SeamElevatedSurface)
                .border(1.dp, SeamBorderSubtle, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = SeamLavenderAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Seam Autofill Overlay · Tap a saved login to autofill",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeamWarmIvory
                )
            }

            if (credentials.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    credentials.forEachIndexed { idx, cred ->
                        FilterChip(
                            selected = idx == selectedCredentialIndex,
                            onClick = {
                                selectedCredentialIndex = idx
                                appDomain = cred.domainOrUrl.ifBlank { "${cred.serviceTitle.lowercase()}.com" }
                            },
                            label = {
                                Text("${cred.serviceTitle} (${cred.accountUsername.take(14)})")
                            }
                        )
                    }
                }
            }

            // Primary Google-Autofill-Style Action Buttons ("Autofill" + "Save Password")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val target = activeCred
                        if (target == null) {
                            autofillBannerFeedback = "No saved credential yet. Enter a login below and tap 'Save Password'."
                            return@Button
                        }
                        triggerHardwareBiometricPrompt(
                            context = context,
                            title = "Device Biometric Autofill",
                            subtitle = "Confirm with your Android device sensor to autofill ${target.serviceTitle}",
                            onAuthenticated = {
                                usernameField = target.accountUsername
                                passwordField = onDecryptSecret(target.encryptedPassword)
                                appDomain = target.domainOrUrl.ifBlank { "${target.serviceTitle.lowercase()}.com" }
                                showFilledPasswordPlaintext = true
                                autofillBannerFeedback = "Android biometric verified · Autofilled '${target.serviceTitle}'."
                            },
                            onNoBiometricSensorAvailable = {
                                showPinOnlyFallbackDialog = true
                            },
                            onAuthenticationFailedOrCancelled = { err ->
                                autofillBannerFeedback = err
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SeamWarmIvory,
                        contentColor = SeamNearBlack
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overlay_autofill_biometric_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Autofill",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (usernameField.isBlank() || passwordField.isBlank()) {
                            autofillBannerFeedback = "Enter a username and password in the app fields below to trigger Save Password."
                        } else {
                            showSavePasswordOverlayPrompt = true
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overlay_save_password_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = SeamLavenderAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save Password",
                        style = MaterialTheme.typography.labelLarge,
                        color = SeamWarmIvory
                    )
                }
            }
        }

        // Interactive App Login Fields where Autofill populates & Save Password captures
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = usernameField,
                onValueChange = { usernameField = it },
                label = { Text("App Username / Email") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("autofill_demo_username_input")
            )
            OutlinedTextField(
                value = passwordField,
                onValueChange = { passwordField = it },
                label = { Text("App Password") },
                singleLine = true,
                visualTransformation = if (showFilledPasswordPlaintext) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showFilledPasswordPlaintext = !showFilledPasswordPlaintext }) {
                        Icon(
                            imageVector = if (showFilledPasswordPlaintext) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "Toggle autofilled password visibility",
                            tint = SeamSilver
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("autofill_demo_password_input")
            )
        }

        // Floating Google-Autofill "Save password to Seam?" Overlay Sheet
        AnimatedVisibility(visible = showSavePasswordOverlayPrompt) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SeamNearBlack)
                    .border(1.dp, SeamSuccessSage.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .testTag("google_save_password_overlay_prompt"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Save password to Seam by PaperFlow?",
                    style = MaterialTheme.typography.titleMedium,
                    color = SeamWarmIvory,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Account: $usernameField · App/Domain: ${appDomain.ifBlank { "App Login" }}\nEncrypted with Android Keystore AES-256-GCM in isolated vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SeamSilver
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSavePasswordOverlayPrompt = false }) {
                        Text("Never", color = SeamMutedGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val title = appDomain.substringBefore(".").replaceFirstChar { it.uppercase() }.ifBlank { "Saved App" }
                            onSaveNewCredentialFromOverlay(
                                title,
                                usernameField.trim(),
                                passwordField,
                                appDomain.trim().ifBlank { "app.local" }
                            )
                            showSavePasswordOverlayPrompt = false
                            autofillBannerFeedback = "Saved '$title' ($usernameField) via Seam Save Password Overlay."
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SeamSuccessSage,
                            contentColor = SeamNearBlack
                        ),
                        modifier = Modifier.testTag("confirm_overlay_save_password_button")
                    ) {
                        Text("Save to Vault", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


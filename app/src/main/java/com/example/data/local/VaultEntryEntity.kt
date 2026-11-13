package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VaultItemType {
    NOTE,
    GALLERY,
    PASSWORD
}

@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloudId: String = "",
    val userId: String = "",
    val itemType: String = VaultItemType.NOTE.name,
    val title: String = "",
    val primaryContent: String = "", // Markdown reflection text or Gallery caption/story
    val secretValue: String = "",    // Camera/EXIF metadata or secondary note
    val websiteOrLocation: String = "", // Location, Album name, or Folder name
    val folderOrAlbum: String = "General", // Explicit Folder (for Notes) or Album (for Gallery)
    val mediaPath: String = "",      // App-private phone storage file path
    val ocrExtractedText: String = "", // Local OCR / metadata text extracted on-device
    val category: String = "Uncategorized",
    val moodTag: String = "Mindful",
    val aiSummary: String = "",
    val aiKeywords: String = "",     // Semantic synonyms & tags
    val isFavorite: Boolean = false, // Used as Pinned for Notes / Favorite for Gallery
    val isBiometricProtected: Boolean = false,
    val isAnalyzed: Boolean = false,
    val isCloudSynced: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VaultItemType {
    NOTE,
    GALLERY,
    PASSWORD,
    TASK
}

@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloudId: String = "",
    val userId: String = "",
    val itemType: String = VaultItemType.NOTE.name,
    val title: String = "",
    val primaryContent: String = "", // Markdown reflection text, Gallery caption, or Task notes
    val secretValue: String = "",    // Camera/EXIF metadata or serialized Task schedule metadata
    val websiteOrLocation: String = "", // Location, Album name, Folder, or Formatted schedule display
    val folderOrAlbum: String = "General", // Explicit Folder (Notes), Album (Gallery), or Schedule Bucket (Tasks)
    val mediaPath: String = "",      // App-private phone storage file path
    val ocrExtractedText: String = "", // Local OCR / metadata text extracted on-device
    val category: String = "Uncategorized",
    val moodTag: String = "Mindful", // For TASK items: "Pending" or "Done"
    val aiSummary: String = "",
    val aiKeywords: String = "",     // Semantic synonyms & tags
    val isFavorite: Boolean = false, // Used as Pinned for Notes / Favorite for Gallery / High Priority for Tasks
    val isBiometricProtected: Boolean = false,
    val isAnalyzed: Boolean = false,
    val isCloudSynced: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val isTaskCompleted: Boolean
        get() = itemType == VaultItemType.TASK.name && moodTag.equals("Done", ignoreCase = true)

    fun taskDueAtMillis(): Long? = parseTaskMetaMap()["due"]?.toLongOrNull()?.takeIf { it > 0L }

    fun taskDurationMinutes(): Int = parseTaskMetaMap()["dur"]?.toIntOrNull()?.coerceIn(15, 480) ?: 60

    fun taskCalendarEventId(): Long? = parseTaskMetaMap()["calId"]?.toLongOrNull()?.takeIf { it > 0L }

    fun isSyncedToGoogleCalendar(): Boolean = parseTaskMetaMap()["synced"] == "true"

    fun taskCalendarName(): String = parseTaskMetaMap()["calName"].orEmpty()

    private fun parseTaskMetaMap(): Map<String, String> {
        if (secretValue.isBlank()) return emptyMap()
        return secretValue.split("|").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx > 0) part.substring(0, idx) to part.substring(idx + 1) else null
        }.toMap()
    }

    companion object {
        fun encodeTaskMeta(
            dueAtMillis: Long?,
            durationMinutes: Int = 60,
            calendarEventId: Long? = null,
            syncedToCalendar: Boolean = false,
            calendarName: String = ""
        ): String {
            val cleanCalName = calendarName.replace("|", " ").replace("=", ":").take(40)
            return listOf(
                "due=${dueAtMillis ?: 0L}",
                "dur=${durationMinutes.coerceIn(15, 480)}",
                "calId=${calendarEventId ?: 0L}",
                "synced=$syncedToCalendar",
                "calName=$cleanCalName"
            ).joinToString("|")
        }
    }
}

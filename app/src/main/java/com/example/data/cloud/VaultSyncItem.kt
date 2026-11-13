package com.example.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class VaultSyncItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemType: String = "NOTE",
    val title: String = "",
    val category: String = "General",
    val aiSummary: String = "",
    val aiKeywords: String = "",
    val moodTag: String = "Mindful",
    val encryptedPayload: String = "",
    val isAnalyzed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries WHERE itemType != 'PASSWORD' ORDER BY isFavorite DESC, updatedAtMillis DESC")
    fun observeEntriesForSanctuary(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE itemType != 'PASSWORD' ORDER BY updatedAtMillis DESC")
    suspend fun getEntriesOnce(): List<VaultEntryEntity>

    @Query("SELECT * FROM vault_entries WHERE itemType != 'PASSWORD' AND isAnalyzed = 0 ORDER BY createdAtMillis ASC")
    suspend fun getUnanalyzedEntries(): List<VaultEntryEntity>

    @Query("SELECT * FROM vault_entries WHERE itemType != 'PASSWORD' AND isCloudSynced = 0 ORDER BY updatedAtMillis ASC")
    suspend fun getUnsyncedEntries(): List<VaultEntryEntity>

    @Query("SELECT * FROM vault_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): VaultEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: VaultEntryEntity)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("UPDATE vault_entries SET aiSummary = '', aiKeywords = '', isAnalyzed = 0")
    suspend fun clearAllDerivedAiInsights()
}

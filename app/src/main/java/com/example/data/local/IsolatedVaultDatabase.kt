package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Isolated Password Vault Entity stored in a separate Room database boundary (`seam_isolated_vault.db`).
 *
 * PRIVACY INVARIANT: Records in this database NEVER leave the device, NEVER enter AI prompts,
 * NEVER enter semantic search indexes, and NEVER sync to cloud backups unless explicit E2EE
 * vault export is invoked in a future milestone.
 */
@Entity(tableName = "isolated_vault_credentials")
data class VaultCredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val serviceTitle: String = "",
    val accountUsername: String = "",
    val encryptedPassword: String = "",
    val encryptedNotes: String = "",
    val domainOrUrl: String = "",
    val category: String = "Security",
    val reuseFingerprint: String = "", // Salted SHA-256 hash for local reuse detection only
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface IsolatedVaultDao {
    @Query("SELECT * FROM isolated_vault_credentials ORDER BY updatedAtMillis DESC")
    fun observeAllCredentials(): Flow<List<VaultCredentialEntity>>

    @Query("SELECT * FROM isolated_vault_credentials ORDER BY updatedAtMillis DESC")
    suspend fun getAllCredentialsOnce(): List<VaultCredentialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: VaultCredentialEntity): Long

    @Update
    suspend fun updateCredential(credential: VaultCredentialEntity)

    @Query("DELETE FROM isolated_vault_credentials WHERE id = :id")
    suspend fun deleteCredentialById(id: Long)
}

@Database(entities = [VaultCredentialEntity::class], version = 1, exportSchema = false)
abstract class IsolatedVaultDatabase : RoomDatabase() {
    abstract fun isolatedVaultDao(): IsolatedVaultDao

    companion object {
        @Volatile
        private var INSTANCE: IsolatedVaultDatabase? = null

        fun getInstance(context: Context): IsolatedVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IsolatedVaultDatabase::class.java,
                    "seam_isolated_vault.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

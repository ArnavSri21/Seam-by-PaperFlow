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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class PasswordStrengthTier(val label: String) {
    STRONG("Strong"),
    FAIR("Fair"),
    WEAK("Weak")
}

/**
 * Isolated Password Vault Room Entity (`seam_isolated_vault.db`).
 *
 * - `encryptedPassword` & `encryptedNotes` hold Base64 AES-256-GCM ciphertext encrypted via
 *   `KeystoreVaultCrypto` (Android Keystore hardware-backed key).
 * - `maskedPreview` stores a constant-length UI mask (`••••••••••••`) so list views never decrypt
 *   or leak credential length/plaintext by default.
 * - `passwordStrengthScore` stores local entropy score (0..100) computed at save time.
 *
 * PRIVACY INVARIANT: Records in this database NEVER leave the device, NEVER enter AI prompts,
 * NEVER enter semantic search indexes, and NEVER sync to cloud backups.
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
    val reuseFingerprint: String = "",
    val passwordStrengthScore: Int = 80,
    val maskedPreview: String = "••••••••••••",
    val encryptionAlgorithm: String = "AES-256-GCM",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun strengthTier(): PasswordStrengthTier = when {
        passwordStrengthScore >= 75 -> PasswordStrengthTier.STRONG
        passwordStrengthScore >= 45 -> PasswordStrengthTier.FAIR
        else -> PasswordStrengthTier.WEAK
    }

    fun maskedPasswordOrDefault(): String = maskedPreview.ifBlank { "••••••••••••" }
}

@Dao
interface IsolatedVaultDao {
    @Query("SELECT * FROM isolated_vault_credentials ORDER BY updatedAtMillis DESC")
    fun observeAllCredentials(): Flow<List<VaultCredentialEntity>>

    @Query("SELECT * FROM isolated_vault_credentials WHERE category = :category ORDER BY updatedAtMillis DESC")
    fun observeCredentialsByCategory(category: String): Flow<List<VaultCredentialEntity>>

    @Query(
        "SELECT * FROM isolated_vault_credentials " +
            "WHERE serviceTitle LIKE '%' || :query || '%' " +
            "OR accountUsername LIKE '%' || :query || '%' " +
            "OR domainOrUrl LIKE '%' || :query || '%' " +
            "ORDER BY updatedAtMillis DESC"
    )
    fun searchSafeMetadata(query: String): Flow<List<VaultCredentialEntity>>

    @Query("SELECT * FROM isolated_vault_credentials ORDER BY updatedAtMillis DESC")
    suspend fun getAllCredentialsOnce(): List<VaultCredentialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: VaultCredentialEntity): Long

    @Update
    suspend fun updateCredential(credential: VaultCredentialEntity)

    @Query("DELETE FROM isolated_vault_credentials WHERE id = :id")
    suspend fun deleteCredentialById(id: Long)
}

@Database(entities = [VaultCredentialEntity::class], version = 3, exportSchema = false)
abstract class IsolatedVaultDatabase : RoomDatabase() {
    abstract fun isolatedVaultDao(): IsolatedVaultDao

    companion object {
        @Volatile
        private var INSTANCE: IsolatedVaultDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE isolated_vault_credentials ADD COLUMN passwordStrengthScore INTEGER NOT NULL DEFAULT 80")
                db.execSQL("ALTER TABLE isolated_vault_credentials ADD COLUMN maskedPreview TEXT NOT NULL DEFAULT '••••••••••••'")
                db.execSQL("ALTER TABLE isolated_vault_credentials ADD COLUMN encryptionAlgorithm TEXT NOT NULL DEFAULT 'AES-256-GCM'")
            }
        }

        fun getInstance(context: Context): IsolatedVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IsolatedVaultDatabase::class.java,
                    "seam_isolated_vault.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

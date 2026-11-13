package com.example.data.cloud

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeVaultItems(userId: String = requireUserId()): Flow<List<VaultSyncItem>> = flow {
        val path = "users/$userId/vault_items"
        emitAll(
            db.collection("users").document(userId).collection("vault_items")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VaultSyncItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getVaultItems(userId: String = requireUserId()): Result<List<VaultSyncItem>> {
        val path = "users/$userId/vault_items"
        return try {
            val snapshot = db.collection("users").document(userId).collection("vault_items")
                .get()
                .await()
            Result.success(
                snapshot.toObjects(
                    VaultSyncItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getVaultItemById(itemId: String, userId: String = requireUserId()): Result<VaultSyncItem?> {
        val docRef = db.collection("users").document(userId).collection("vault_items").document(itemId)
        return try {
            val snapshot = docRef.get().await()
            Result.success(
                snapshot.toObject(
                    VaultSyncItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createVaultItem(
        itemId: String,
        itemType: String,
        title: String,
        category: String,
        aiSummary: String,
        aiKeywords: String,
        moodTag: String,
        encryptedPayload: String,
        isAnalyzed: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val docRef = if (itemId.isNotBlank()) {
            db.collection("users").document(uid).collection("vault_items").document(itemId)
        } else {
            db.collection("users").document(uid).collection("vault_items").document()
        }
        val payload = mapOf(
            "userId" to uid,
            "itemType" to itemType,
            "title" to title,
            "category" to category,
            "aiSummary" to aiSummary,
            "aiKeywords" to aiKeywords,
            "moodTag" to moodTag,
            "encryptedPayload" to encryptedPayload,
            "isAnalyzed" to isAnalyzed,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateVaultItem(item: VaultSyncItem): Result<Unit> {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "VaultSyncItem ID must not be blank for update" }
        requireNotNull(item.createdAt) { "VaultSyncItem createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("vault_items").document(item.id)
        return try {
            docRef.set(item.copy(userId = uid, updatedAt = null)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteVaultItem(itemId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vault_items").document(itemId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}

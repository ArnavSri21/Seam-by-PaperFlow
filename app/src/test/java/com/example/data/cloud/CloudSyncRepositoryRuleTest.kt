package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createVaultItem_validPayload_createsDocumentAndReturnsId() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)
        val uniqueId = "item_${UUID.randomUUID().toString().replace("-", "_")}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createVaultItem(
                itemId = uniqueId,
                itemType = "NOTE",
                title = "Midnight Journal",
                category = "Reflection",
                aiSummary = "Quiet thoughts under the stars.",
                aiKeywords = "quiet, stars, night, meditation",
                moodTag = "Serene",
                encryptedPayload = "Encrypted reflection content",
                isAnalyzed = true
            )
        }
        assertTrue(createResult.isSuccess)
        assertEquals(uniqueId, createResult.getOrThrow())
    }

    @Test
    fun getVaultItems_authenticatedOwner_returnsMatchingItems() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)
        val uniqueId = "item_${UUID.randomUUID().toString().replace("-", "_")}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createVaultItem(
                itemId = uniqueId,
                itemType = "PASSWORD",
                title = "GitHub Token",
                category = "Developer",
                aiSummary = "Source code repository access credential.",
                aiKeywords = "github, developer, token, git, ssh",
                moodTag = "Secure",
                encryptedPayload = "ENC_PAYLOAD_123",
                isAnalyzed = true
            ).getOrThrow()
        }

        val itemsResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getVaultItems() }
        assertTrue(itemsResult.isSuccess)
        assertTrue(itemsResult.getOrThrow().any { it.id == uniqueId })
    }

    @Test
    fun observeVaultItems_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)
        val uniqueId = "item_${UUID.randomUUID().toString().replace("-", "_")}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createVaultItem(
                itemId = uniqueId,
                itemType = "GALLERY",
                title = "Kyoto Bamboo Forest",
                category = "Travel",
                aiSummary = "Emerald bamboo stalks filtering morning light.",
                aiKeywords = "kyoto, bamboo, forest, nature, japan, serene",
                moodTag = "Peaceful",
                encryptedPayload = "local_uri://kyoto.jpg",
                isAnalyzed = true
            ).getOrThrow()
        }

        val emittedItems = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeVaultItems(aliceUid).first { list -> list.any { it.id == uniqueId } }
        }
        assertTrue(emittedItems.any { it.id == uniqueId })
    }

    @Test
    fun getVaultItemById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore)
        val uniqueId = "item_${UUID.randomUUID().toString().replace("-", "_")}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createVaultItem(
                itemId = uniqueId,
                itemType = "NOTE",
                title = "Private Thought",
                category = "Personal",
                aiSummary = "Summary",
                aiKeywords = "private, personal",
                moodTag = "Calm",
                encryptedPayload = "Secret",
                isAnalyzed = true
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getVaultItemById(uniqueId, userId = aliceUid)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeVaultItems_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = CloudSyncRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeVaultItems(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caughtException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@paperflow.test"
        const val BOB_EMAIL = "bob@paperflow.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

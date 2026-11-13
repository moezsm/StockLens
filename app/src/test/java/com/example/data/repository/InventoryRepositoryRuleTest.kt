package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.InventoryItem
import com.example.model.PlanTier
import com.example.model.ScanType
import com.example.model.ScannedItemCandidate
import com.example.model.UserPersona
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class InventoryRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureInitialWorkspace_andCrudItem_succeedsForOwner() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val repository = InventoryRepository(firestore)

        val initResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureInitialWorkspace(uid, "Alice Pantry", "alice@test.com")
        }
        assertTrue(initResult.isSuccess)

        val stores = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStores(uid).first { it.isNotEmpty() }
        }
        val defaultStore = stores.first()

        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertItem(
                uid,
                InventoryItem(
                    userId = uid,
                    storeId = defaultStore.id,
                    name = "Oat Milk ${UUID.randomUUID().toString().take(4)}",
                    category = "Dairy",
                    quantity = 3.0,
                    unit = "L",
                    minThreshold = 2.0,
                    maxThreshold = 8.0,
                    unitCost = 4.2,
                    expiryEpochMs = System.currentTimeMillis() + 5 * 86_400_000L,
                    lastScanType = "receipt"
                )
            ).getOrThrow()
        }
        assertTrue(itemId.isNotEmpty())

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeItems(uid).first { list -> list.any { it.id == itemId } }
        }
        val created = items.first { it.id == itemId }

        val scanResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.applyScanResults(
                userId = uid,
                storeId = defaultStore.id,
                scanType = ScanType.RECEIPT,
                candidates = listOf(
                    ScannedItemCandidate(
                        tempId = "t1",
                        name = created.name,
                        category = "Dairy",
                        quantity = 2.0,
                        unit = "L",
                        unitCost = 4.2,
                        estimatedShelfLifeDays = 10,
                        suggestedMinThreshold = 2.0,
                        actionHint = "add",
                        matchedExistingItemId = created.id
                    )
                ),
                existingItems = items
            )
        }
        assertTrue(scanResult.isSuccess)

        val updatedItems = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeItems(uid).first { list ->
                list.firstOrNull { it.id == itemId }?.quantity == 5.0
            }
        }
        assertEquals(5.0, updatedItems.first { it.id == itemId }.quantity, 0.01)

        val tierUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateUserSettings(uid, persona = UserPersona.STORE, tier = PlanTier.PREMIUM)
        }
        assertTrue(tierUpdate.isSuccess)
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID().toString().take(6)}@test.com")
        val aliceRepo = InventoryRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureInitialWorkspace(aliceUid, "Alice", null).getOrThrow()
        }

        signInTestUser("bob_iso_${UUID.randomUUID().toString().take(6)}@test.com")
        val bobRepo = InventoryRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeItems(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val fakeUid = "unauth_user_123"
        auth.signOut()
        val repository = InventoryRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeItems(fakeUid).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated read")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

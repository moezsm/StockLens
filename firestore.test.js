const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

const serverTimestamp = () => require("firebase/firestore").serverTimestamp();

async function seedStore(uid, storeId = "store_1") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.doc(`users/${uid}/stores/${storeId}`).set({
      userId: uid,
      name: "Main Pantry",
      type: "pantry",
      isDefault: true,
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });
}

test("Unauthenticated user cannot read or list items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/items`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}`).get());
});

test("Cross-user isolation: Bob cannot read or write Alice's stores or items", async () => {
  await seedStore(ALICE_UID, "store_1");
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc(`users/${ALICE_UID}/items/item_1`).set({
      userId: ALICE_UID,
      storeId: "store_1",
      name: "Organic Milk",
      category: "Dairy",
      quantity: 2,
      unit: "L",
      minThreshold: 1,
      maxThreshold: 6,
      unitCost: 3.5,
      expiryEpochMs: Date.now() + 86400000,
      lastScanType: "receipt",
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/items/item_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/items`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/stores/store_1`).get());
});

test("Authenticated owner can create and update user profile, store, item, and activity log", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID);
  const db = aliceCtx.firestore();

  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    db.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Alice Chef",
      email: "alice@example.com",
      persona: "chef",
      tier: "free",
      activeStoreId: "store_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    db.doc(`users/${ALICE_UID}/stores/store_1`).set({
      userId: ALICE_UID,
      name: "Bistro Kitchen",
      type: "kitchen",
      isDefault: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    db.doc(`users/${ALICE_UID}/items/item_1`).set({
      userId: ALICE_UID,
      storeId: "store_1",
      name: "Butter",
      category: "Dairy",
      quantity: 4,
      unit: "kg",
      minThreshold: 2,
      maxThreshold: 10,
      unitCost: 8.5,
      expiryEpochMs: 1800000000000,
      lastScanType: "fridge",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    db.doc(`users/${ALICE_UID}/activity_logs/log_1`).set({
      userId: ALICE_UID,
      storeId: "store_1",
      itemId: "item_1",
      itemName: "Butter",
      type: "restock_order",
      quantityDelta: 5,
      unit: "kg",
      estimatedValue: 42.5,
      reason: "Below threshold",
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid restock order status progression: pending -> ordered -> received
  await assertSucceeds(
    db.doc(`users/${ALICE_UID}/activity_logs/log_1`).update({
      status: "ordered",
      updatedAt: now,
    })
  );
  await assertSucceeds(
    db.doc(`users/${ALICE_UID}/activity_logs/log_1`).update({
      status: "received",
      updatedAt: now,
    })
  );

  // Terminal state lock: cannot update once received
  await assertFails(
    db.doc(`users/${ALICE_UID}/activity_logs/log_1`).update({
      status: "ordered",
      updatedAt: now,
    })
  );
});

test("Shadow update with ghost field and orphaned item without store are rejected", async () => {
  await seedStore(ALICE_UID, "store_1");
  const db = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Ghost field rejected
  await assertFails(
    db.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Alice",
      persona: "pantry",
      tier: "free",
      activeStoreId: "store_1",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Orphaned item referencing non-existent store rejected
  await assertFails(
    db.doc(`users/${ALICE_UID}/items/item_orphan`).set({
      userId: ALICE_UID,
      storeId: "nonexistent_store",
      name: "Apples",
      category: "Produce",
      quantity: 3,
      unit: "pcs",
      minThreshold: 1,
      maxThreshold: 10,
      unitCost: 1.0,
      expiryEpochMs: 0,
      lastScanType: "manual",
      createdAt: now,
      updatedAt: now,
    })
  );
});

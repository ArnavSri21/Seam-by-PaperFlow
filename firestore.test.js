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

test("Unauthenticated user: cannot read vault_items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vault_items").get());
});

test("Authenticated user: cannot read another user's vault_items", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("vault_items").get());
});

test("Authenticated user: can create and read their own vault_item", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("vault_items").doc("item_1");
  const now = new Date();
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      itemType: "NOTE",
      title: "Midnight Reflection",
      category: "Mindfulness",
      aiSummary: "Reflecting on stillness and creative flow.",
      aiKeywords: "stillness, creativity, reflection, night",
      moodTag: "Serene",
      encryptedPayload: "The mind settles when thoughts are written on paper.",
      isAnalyzed: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("vault_items").get());
});

test("Shadow update test: rejects unexpected ghost field on create or update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("vault_items").doc("item_ghost");
  const now = new Date();
  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      itemType: "NOTE",
      title: "Shadow Note",
      category: "Mindfulness",
      aiSummary: "Summary",
      aiKeywords: "keywords",
      moodTag: "Calm",
      encryptedPayload: "Payload",
      isAnalyzed: true,
      isAdminField: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Value poisoning test: rejects invalid itemType enum", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("vault_items").doc("item_poison");
  const now = new Date();
  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      itemType: "INVALID_TYPE",
      title: "Poison Note",
      category: "Mindfulness",
      aiSummary: "Summary",
      aiKeywords: "keywords",
      moodTag: "Calm",
      encryptedPayload: "Payload",
      isAnalyzed: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

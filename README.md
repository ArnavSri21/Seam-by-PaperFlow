# Seam by PaperFlow

A calm, local-first Android sanctuary for **personal memories (Gallery)**, **Markdown reflections (Notes)**, and an **isolated Keystore-encrypted Password Vault**, built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, and on-device / opt-in AI.

---

## Core Principles & Privacy Architecture

1. **100% Local-First & Usable Offline**
   - All memories, reflections, and vault credentials are stored in app-private storage (`context.filesDir` and local Room SQLite databases).
   - Works completely offline immediately without forcing a login wall.
   - **Google Sign-In (`CredentialManager` / `GetSignInWithGoogleOption`)** is an optional identity and opt-in cloud backup trigger—never used as an encryption key.

2. **Strict Password Vault Isolation Boundary**
   - **Separate Database Boundary**: Vault credentials live in `IsolatedVaultDatabase` (`seam_isolated_vault.db`), completely separate from Sanctuary Notes and Gallery items (`PaperFlowDatabase` / `paper_flow_vault.db`).
   - **Hardware-Backed Keystore Encryption**: Every password and recovery note is encrypted with **Android Keystore AES-256-GCM** (`KeystoreVaultCrypto`) using a non-exportable `SecretKey` and unique 12-byte IV per encryption operation.
   - **Zero AI / Zero Search / Zero Cloud Leakage**:
     - Vault credentials are **NEVER** sent to AI prompts (local or cloud), cloud backup, analytics, logs, notifications, or semantic search indexes.
     - The Search screen (`SemanticSearchScreen`) queries only Notes and Gallery items and explicitly excludes Vault credentials.
   - **Active Vault Protections**:
     - Dedicated **Biometric (`BiometricPrompt`) + 4-Digit PIN Gate** before viewing Vault credentials.
     - **15-Second Reveal Timeout**: Revealed passwords automatically re-mask after 15 seconds with a live countdown badge.
     - **30-Second Sensitive Clipboard Auto-Wipe**: Copied secrets are flagged with `ClipDescription.EXTRA_IS_SENSITIVE` (Android 13+) and automatically cleared from the system clipboard after 30 seconds.
     - **Cryptographic Password Generator** & **Local Salted SHA-256 Password Reuse Warnings**.

3. **Transparent On-Device & Opt-In AI**
   - **Default Mode (`LOCAL_ON_DEVICE`)**: Runs 100% offline on-device keyword extraction, category/mood classification, EXIF/resolution inspection, and trigram cosine similarity without network calls.
   - **Opt-In Cloud AI (`CLOUD_GEMINI_OPT_IN`)**: Requires explicit user consent via a transparent disclosure dialog before sending Note/Gallery text to Gemini (`Firebase.ai`).
   - **Full User Control**: Users can switch between On-Device AI, Opt-In Cloud AI, or Paused at any time, rerun indexing, or wipe all derived AI summaries and keywords with one tap.

4. **Editorial Dark-First Design & Motion**
   - Preserves the circular **PaperFlow emblem** paired with the **SEAM by PaperFlow** wordmark.
   - Integrates the 5 motion references (`paper_flow_intro`, `liquid_reflection`, `sync_uploading`, `gallery_orbit`, `vault_rings`) with full **Reduced Motion** accessibility support.

---

## Permission Rationale

- `INTERNET` / `ACCESS_NETWORK_STATE`: Used only when the user explicitly opts into Google Sign-In, Opt-In Cloud AI, or Opt-In Cloud Backup.
- `USE_BIOMETRIC`: Used by `BiometricPrompt` to gate access to the Isolated Password Vault.
- **Zero Storage Permissions**: Media imports use the zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) and copy selected photos into app-private storage (`files/private_gallery_vault`).

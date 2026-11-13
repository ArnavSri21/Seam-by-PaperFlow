# Google AI Studio implementation brief — Seam by PaperFlow

Build **Seam by PaperFlow**, a native Android Kotlin + Jetpack Compose app: a calm, private, local-first home for personal memories, gallery items, notes/reflections and passwords.

## Visual reference is mandatory
Use the user's supplied premium dark password-manager screenshot as the primary UI reference. Match its visual qualities: near-black and charcoal layers, soft silver/ivory typography, quiet luminous gradients, fine grid texture, glossy but restrained cards, compact pill-shaped search, floating rounded bottom navigation, generous negative space and refined iconography. The reference is a design target, not permission to copy Proton Pass logos, names or exact proprietary assets.

Motion should be elegant and atmospheric, not generic spinning decorations:
- Ambient light drifts slowly across the background.
- Fine grid and blurred light remain subtle and low contrast.
- Hero memory illustration uses thin elliptical orbit lines and a slow luminous sweep.
- Cards use short spring press feedback and slight elevation/scale changes.
- Screen changes use restrained fade + small directional movement.
- Logo motion is slow, precise and subtle; keep the mark visually stable.
- Avoid constant high-speed rotation, bouncing, excessive particles, neon gradients and large motion that competes with reading.
- Respect system reduced-motion settings and provide a static fallback.

## Privacy requirements
- Fully usable offline without sign-in.
- Store user data locally by default in app-private storage.
- Google Credential Manager sign-in is optional identity, not encryption.
- Encrypt vault secrets with AES-GCM and Android Keystore-backed keys.
- Keep vault storage in a separate database/repository boundary.
- Never send passwords to AI, cloud, analytics, logs, notifications or semantic indexes.
- Cloud backup disabled by default; future backup must be opt-in and end-to-end encrypted.
- Use Android Photo Picker / SAF instead of broad media permissions.
- Clearly disclose any content leaving the device for optional cloud AI.

## Modules and screens
Home dashboard, Memories/Gallery, Notes/Reflections, Password Vault, Search, Settings.
Gallery: timeline, albums, tags, favorites, metadata, OCR/local descriptions, user-approved import.
Notes: rich text/Markdown, folders, tags, pinned notes, attachments, autosave, export.
Vault: biometric/device credential gate, reveal timeout, clipboard auto-clear, password generator, local reuse warnings. No fake buttons or plaintext secrets.
Search: lexical FTS5 + local semantic search, filters by content type/date/tags/favorites/folder, explain match relevance. Exclude vault completely.
AI: local tagging, OCR/metadata extraction, embeddings and summaries where practical; optional cloud enhancement only with explicit consent. User can pause, disable, rerun and delete derived insights.
Background: WorkManager unique periodic work, incremental and resumable jobs, battery/network constraints, backoff, no continuous polling or background upload.

## Architecture
Clean Architecture + MVVM, Hilt, Room, Kotlin Flow/Coroutines, WorkManager.
Packages: core/security, data/local, data/repository, domain, feature/home, memories, notes, vault, search, settings, ai/local, ai/cloud, ai/index, sync.
Define interfaces for embedding models and cloud providers. Keep cloud sync boundary disabled until enabled by user.

## Deliverables
A buildable Android Studio project, source, Room migrations, unit/UI tests, setup docs, OAuth configuration guide, privacy threat model, permission rationale, and explicit list of unfinished integrations. Implement a real MVP first. Do not claim unimplemented features are complete. Never trade privacy for convenience.

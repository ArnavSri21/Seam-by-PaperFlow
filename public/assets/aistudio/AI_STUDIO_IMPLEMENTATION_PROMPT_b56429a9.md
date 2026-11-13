# Google AI Studio implementation brief — Seam by PaperFlow

Build **Seam by PaperFlow**, a native Android Kotlin + Jetpack Compose app: a calm, private, local-first home for personal memories, gallery items, notes/reflections and passwords.

## Brand / supplied motion references
Use the supplied MP4s as visual direction: (1) editorial monochrome calendar-like memory index, (2) fine luminous upload progress indicator, (3) photos arranging into an orbital memory constellation, (4) slow 3D concentric wire rings, and (5) atmospheric smoke resolving into the PaperFlow emblem. Preserve the PaperFlow emblem; do not redraw it inaccurately. Until a clean transparent vector/logo export is available, use a refined SEAM wordmark with “by PaperFlow”. Dark near-black canvas, warm ivory text, silver/lavender accents, subtle grain, generous whitespace and restrained motion. Respect reduced-motion settings. Include a complete light theme.

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
A buildable Android Studio project, Gradle wrapper/version catalog, source, Room migrations, unit/UI tests, setup docs, OAuth configuration guide, privacy threat model, permission rationale, and explicit list of unfinished integrations. Implement a real MVP first. Do not claim unimplemented features are complete. Never trade privacy for convenience.

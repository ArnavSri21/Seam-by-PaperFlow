# Seam by PaperFlow
Local-first Android personal space for memories, notes, reflections and a separately protected password vault.

## Stack
Kotlin · Jetpack Compose · Room · WorkManager · Android BiometricPrompt · Android Keystore.
Open this folder in Android Studio, install SDK 35, sync Gradle and run the `app` configuration.

## Current MVP
- Compose dark-mode home, navigation, search and note capture.
- Room-backed offline notes.
- WorkManager scheduled analysis placeholder.
- Orbital-ring brand motion treatment.
- AI Studio implementation brief included.

## Important limitations
This is a starter MVP, not a finished secure password manager. The vault UI and encryption integration, Google Credential Manager OAuth configuration, semantic embeddings, media import, and cloud backup are specified for the next milestones but are not represented as completed features. Do not store real passwords until the vault is implemented and security-reviewed.

OAuth is identity, not encryption. Passwords must never enter AI prompts, semantic indexes, logs, analytics or cloud requests. Local use must work without Google sign-in.

## Visual refresh (reference-led)
The refreshed Compose UI uses the supplied premium dark password-manager screenshot as a visual reference: near-black surfaces, soft silver typography, subtle grid/ambient lighting, layered charcoal cards, rounded search, compact bottom navigation and restrained motion. The animated background uses a slow-moving radial light and fine grid; the hero uses a gentle orbital-line sweep; navigation and cards use short spring/fade transitions. The visual language is inspired by the reference, not a copy of Proton branding.

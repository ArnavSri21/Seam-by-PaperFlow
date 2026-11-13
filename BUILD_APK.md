# Build the Seam APK

This environment does not have the Android SDK or Gradle installed, so I cannot compile and verify an APK here.

## Build automatically with GitHub Actions
1. Push the contents of this project folder to your GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Build Seam APK**.
4. Select **Run workflow** (or let a push to `main` / `master` trigger it).
5. Wait for the workflow to finish.
6. Open the completed workflow run and download the `seam-debug-apk` artifact.
7. Extract it; `app-debug.apk` is the installable debug build.

## Build locally
Open the project in Android Studio with JDK 17 and Android SDK Platform 35 installed, then run:
```bash
gradle :app:assembleDebug
```
The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Important
This produces a debug APK of the current starter. It is not a production release and the password vault, Google OAuth, gallery import and AI integrations are not fully implemented. Do not use it to store real passwords.

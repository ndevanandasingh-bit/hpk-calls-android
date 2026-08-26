# Build the V2.0.0 APK

## Recommended no-cost method: GitHub Actions

This project includes `.github/workflows/android-apk.yml`.

1. Create a **new GitHub repository** for the native app, for example `hpk-calls-android`.
2. Upload the complete contents of this project while preserving its folders.
3. Open the repository's **Actions** tab.
4. Open **Build HPK Calls Android APK**.
5. Choose **Run workflow**.
6. When the workflow is green, open that run.
7. Under **Artifacts**, download `HPK-Calls-Android-V2.0.0-APK`.
8. Extract the downloaded artifact ZIP.
9. Install `HPK-Calls-Android-V2.0.0-debug.apk` on the Android phone.

The debug APK is intended for testing. A later production release should use a private persistent release signing key so updates can be installed over previous production APKs.

## Local Android Studio build

Open the project folder in Android Studio and build the `debug` variant. The project uses Java 17, Android Gradle Plugin 8.7.3, compile/target SDK 35 and min SDK 26.

# HPK Calls Android V2.0.0 — Build/Static Test Report

## Passed in this environment

- Project directory and Gradle structure created successfully.
- Android manifest XML parses successfully.
- All Android resource XML files parse successfully (11 XML files checked).
- Java source brace/syntax sanity check completed; no Java parser-level syntax error was found before Android SDK symbol resolution.
- No AndroidX runtime dependency is required by the Java source.
- HTTPS-only network security policy is present.
- Native notification channels, Answer/Decline actions and foreground direct-call monitor are wired in the manifest/source.
- V1.14.0 backend reference starts successfully under Node.js.
- Backend `/api/health`, user registration, heartbeat and direct inbox endpoints passed a local compatibility smoke test.
- GitHub Actions workflow is included to install Android SDK 35 and build the debug APK.

## Not executable locally here

This runtime contains Java but does not contain an Android SDK/Android Gradle toolchain, and outbound dependency resolution is unavailable from the shell. Therefore the actual APK cannot be compiled in this container. The included GitHub Actions workflow performs the real Android compilation in a standard Android build environment.

## Device tests still required after APK build

- Android notification permission.
- Microphone/camera runtime permission bridge.
- Background direct-call monitor on the target phone.
- Native ringtone/vibration.
- Full-screen/lock-screen notification behavior for the phone's Android/OEM version.
- Answer/Decline action flow.
- Voice/video media connection on two physical phones.
- Speaker/earpiece/Bluetooth routing.
- Wi-Fi/mobile network switching.
- OEM battery optimization behavior.

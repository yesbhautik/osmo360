# Building

The Android project lives at the repository root.

## Requirements

- JDK 17
- Android SDK with API 35 installed
- Android build tools installed through Android Studio or `sdkmanager`

## Android Studio

Open this repository in Android Studio and run the `app` configuration.
Android Studio normally creates a local `local.properties` file pointing to your
SDK. Do not commit that file.

## Command Line

Set `ANDROID_HOME` to your Android SDK path, then build:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`, APKs, and build outputs are intentionally ignored by Git.

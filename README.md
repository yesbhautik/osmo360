# Osmo360 Android Live Preview

Unofficial Android app prototype for DJI Osmo 360 live preview, 360 camera
streaming, BLE wake-up, Wi-Fi camera connection, and local H.264 preview
rendering.

**Languages:** English | [中文](docs/i18n/zh-CN/README.md) | [Русский](docs/i18n/ru/README.md)

This project helps Android developers experiment with connecting to an Osmo 360
camera from a native Android app. A user can enter their own camera Wi-Fi SSID,
Wi-Fi password, optional BLE name, optional BSSID/MAC, and camera IP address
inside the app. Settings are stored locally on the device with Android
`SharedPreferences`.

## Features

- Android native Osmo 360 live preview prototype
- In-app camera connection settings
- Bluetooth/BLE discovery and wake-up flow
- Android `WifiNetworkSpecifier` camera Wi-Fi connection
- Local H.264 preview rendering with Android `MediaCodec`
- Connection troubleshooting states for Bluetooth, Wi-Fi visibility, and camera
  discovery
- Public, cleaned source layout for GitHub publication

## Use Cases

- Osmo 360 Android live preview experiments
- 360 camera Android streaming research
- BLE-assisted Wi-Fi camera connection prototypes
- Local camera preview apps using H.264 and `MediaCodec`
- Unofficial interoperability testing with cameras you own

## Disclaimer

This project is independent and unofficial. It is not affiliated with, endorsed
by, sponsored by, or supported by DJI. DJI, Osmo, and Mimo are trademarks of
their respective owners.

Use this software only with cameras you own or have permission to operate. This
prototype is provided for interoperability and experimentation and may stop
working with firmware or app updates.

## Current Status

- Version: `v0.0.2`
- Platform: Android
- Camera target: Osmo 360
- Status: experimental prototype

## Build

Open this repository in Android Studio and run the `app` configuration, or build
from a terminal:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Run

1. Install and open `Osmo Preview`.
2. Turn on Bluetooth.
3. Tap `Settings`.
4. Enter your camera Wi-Fi SSID and password.
5. Optionally enter the BLE name and BSSID/MAC if you know them.
6. Keep the default camera IP unless your camera uses a different address.
7. Confirm you own or have permission to connect to the camera.
8. Save settings, return to the main screen, and tap `Start Osmo Preview`.

If the camera Wi-Fi is not visible, the app attempts a BLE wake-up first and can
learn the actual camera Wi-Fi SSID from discovery.

## Documentation

- [Building](docs/development/BUILDING.md)
- [Architecture](docs/development/ARCHITECTURE.md)
- [Connection Flow](docs/development/CONNECTION_FLOW.md)
- [Troubleshooting](docs/development/TROUBLESHOOTING.md)
- [Roadmap](docs/development/ROADMAP.md)
- [Legal Notice](docs/legal/NOTICE.md)

## Keywords

`osmo360`, `osmo 360`, `DJI Osmo 360`, `Android live preview`, `360 camera`,
`BLE camera`, `Wi-Fi camera`, `H.264 preview`, `MediaCodec`, `Android camera
streaming`, `WifiNetworkSpecifier`

## Publishing Notes

Only publish the clean app source. Do not publish local captures, decompiled
third-party code, packet traces, logs, device identifiers, or private research
notes. See `PUBLISHING.md` for the pre-publication checklist.

# Connection Flow

The app is designed around a single user action: tap **Start Osmo Preview**.

## Startup Path

1. Load saved camera settings from the in-app settings panel.
2. Check whether the Android device is already connected to the camera Wi-Fi
   network and has a `192.168.2.x` address.
3. If already connected, bind the process to that Wi-Fi network and start
   preview.
4. If the configured SSID is visible, ask Android to connect through
   `WifiNetworkSpecifier`.
5. If the configured SSID is not visible, use BLE to find or wake the camera,
   then retry Wi-Fi.
6. If Bluetooth is off, prompt the user to enable Bluetooth before reporting
   that the camera is not found.

## Why Bluetooth Matters

For this camera, Bluetooth can be necessary before Wi-Fi preview works. When
Bluetooth is off, the phone may not discover or wake the camera, so Wi-Fi scans
can look like the camera does not exist even when credentials are correct.

The app should therefore distinguish these states:

- Bluetooth off: ask the user to enable Bluetooth.
- Bluetooth permission missing: ask the user to grant permission.
- BLE scan finds camera: run BLE preflight and then request Wi-Fi.
- BLE scan does not find camera and Wi-Fi SSID is absent: report camera not
  discoverable.
- Wi-Fi SSID is visible but Android cannot connect: then credentials, BSSID, or
  camera Wi-Fi state are likely suspects.

## Configuration Notes

- BSSID is optional and should be left blank unless the user knows it.
- The default camera host is `192.168.2.1`.
- The app stores settings locally using Android `SharedPreferences`.
- Reinstalling the app may clear settings depending on install flow and backup
  behavior.

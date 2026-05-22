# Troubleshooting

This guide is written for development builds and field testing.

## Camera Not Found

Check these in order:

1. The camera is powered on.
2. Bluetooth is enabled on the Android device.
3. Location permission and nearby Bluetooth permissions are granted to the app.
4. DJI Mimo or another app is not already connected to the camera.
5. The camera Wi-Fi SSID is visible in Android Wi-Fi settings.
6. The app settings use the exact camera SSID and passphrase.

If Bluetooth was off, turn it on and tap **Start Osmo Preview** again.

## Android Wi-Fi Dialog Says No Device Found

This usually means Android cannot currently see the configured SSID. It does not
necessarily mean the password is wrong.

Likely causes:

- Camera Wi-Fi is asleep or disabled.
- Bluetooth was off, so the app could not wake/discover the camera.
- The saved SSID does not match the actual camera SSID.
- Camera is already connected to another phone/app.
- The camera is too far away or in a noisy Wi-Fi environment.

## Correct Credentials Still Fail

Only treat this as a credential issue after Android can see the SSID. If the SSID
is visible but connection fails:

- Re-enter the passphrase in Settings.
- Leave BSSID blank.
- Restart camera Wi-Fi.
- Forget old camera Wi-Fi entries in Android Wi-Fi settings and retry.

## Preview Starts But Freezes

The app includes keepalives and recovery paths, but this area is still evolving.
Capture app logs and note the visible counters:

- packet count
- media count
- H.264 bytes
- queued frames
- rendered frames

If packet count keeps increasing but rendered frames stop, the problem is likely
in media framing, decode, or preview sustain logic rather than Wi-Fi association.

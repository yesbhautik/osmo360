# Architecture

This repository contains an Android prototype for previewing a DJI Osmo 360
camera stream. The app is intentionally small: most behavior currently lives in
`MainActivity` so that the connection and media pipeline can be inspected in one
place while the protocol work is still evolving.

## Major Components

- **Configuration UI**: stores camera SSID, optional BLE name, optional BSSID,
  Wi-Fi passphrase, host IP, and user confirmation in `SharedPreferences`.
- **Discovery and connection**: checks whether the phone is already on the
  camera Wi-Fi, refreshes Wi-Fi scan results, uses BLE as a wake/discovery path,
  then requests the camera network through Android's `WifiNetworkSpecifier`.
- **BLE preflight**: scans for the configured camera name or the expected BLE
  service UUID, connects to the camera, enables notifications, and sends the
  wake/control frames needed before Wi-Fi preview.
- **UDP/TCP camera control**: sends the control and keepalive messages needed
  to start and sustain live preview.
- **H.264 decode path**: receives camera UDP payloads, extracts H.264 access
  units, feeds `MediaCodec`, and renders to a `SurfaceView`.
- **Watchdog/recovery**: tracks packet/media/render progress and escalates from
  light keepalives to stronger in-session recovery when the stream stalls.

## Current Shape

`MainActivity` is intentionally direct and stateful. Future maintainers may want
to split it into smaller classes once the camera behavior is stable:

- `CameraConfigStore`
- `CameraDiscoveryController`
- `BlePreflightController`
- `CameraNetworkController`
- `PreviewTransport`
- `H264PreviewDecoder`
- `PreviewWatchdog`

Avoid refactoring protocol-sensitive code until there is repeatable automated
test coverage for connection, preview startup, and long-running preview.

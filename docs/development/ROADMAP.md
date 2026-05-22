# Roadmap

This project is an unofficial interoperability prototype. The near-term goal is
to make connection and live preview reliable across common Android devices.

## Near Term

- Improve first-run onboarding for camera SSID, passphrase, Bluetooth, and
  permissions.
- Split connection failures into precise user-facing states.
- Add structured app logs that can be shared without exposing passwords,
  addresses, or private device identifiers.
- Add short automated smoke tests for settings validation and connection state
  transitions.

## Preview Stability

- Continue testing long-running preview sessions.
- Preserve the current tiered recovery model: light sustain, media re-arm, and
  controlled preview restart.
- Add clearer metrics for packets, media payloads, decode queue, rendered
  frames, and recovery attempts.

## Code Health

- Extract connection, BLE, transport, and decoder responsibilities from
  `MainActivity` after behavior stabilizes.
- Keep protocol-sensitive constants documented near their usage.
- Add tests around pure helpers first, then introduce integration test harnesses
  where Android device access is required.

## Public Project Hygiene

- Do not commit packet captures, private logs, BSSIDs, BLE addresses, Wi-Fi
  passwords, APKs, or decompiled third-party code.
- Keep public documentation focused on user-visible behavior and high-level
  development notes.
- Keep the DJI affiliation disclaimer visible in the README.

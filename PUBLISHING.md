# Publishing Checklist

Use this checklist before pushing the project to a public GitHub repository.

## Publish

- `README.md`
- `PUBLISHING.md`
- `.gitignore`
- Clean Android app source:
  - `settings.gradle`
  - `build.gradle`
  - `gradle.properties`
  - `gradlew`
  - `gradlew.bat`
  - `gradle/wrapper/`
  - `app/build.gradle`
  - `app/src/main/`

## Do Not Publish

- `captures/`
- `FINDINGS.md`
- `refs/`
- `viewer/`
- Decompiled APK/JADX output
- Packet captures (`.pcap`, `.pcapng`)
- Device logs (`.log`, UI XML dumps, Android connectivity dumps)
- Personal device names, BLE addresses, BSSIDs, IP traces, or Wi-Fi passwords
- Build outputs (`build/`, APK/AAB files)
- Local IDE state (`.idea/`, `.vscode/`)
- Signing keys (`*.jks`, `*.keystore`) or local config (`local.properties`, `.env`)
- Internal helper scripts that read private captures
- Local protocol research tooling, such as `osmo_probe.py` and
  `requirements.txt`, unless separately cleaned and reviewed

## Legal And Product Notes

This project should be presented as an unofficial interoperability prototype.
Do not use DJI logos, app assets, screenshots, or marketing copy. Do not claim
endorsement, certification, or compatibility beyond what you have tested.

Suggested repository disclaimer:

```text
This is an independent, unofficial project. It is not affiliated with,
endorsed by, sponsored by, or supported by DJI. DJI, Osmo, and Mimo are
trademarks of their respective owners. Use only with cameras you own or have
permission to operate.
```

If you want stronger legal certainty, have a lawyer review the repository before
publication, especially because the app interoperates with a proprietary camera
protocol.

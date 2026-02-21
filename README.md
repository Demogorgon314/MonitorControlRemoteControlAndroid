# MonitorControl Remote Control Android

Android app for controlling displays through the MonitorControl Remote HTTP API.

## What it does

- Connects to a MonitorControl host with `host + port + bearer token`
- Scans your local network for candidate hosts from the settings dialog
- Shows connected displays and their current status
- Supports global controls for brightness, volume, and power
- Supports per-display controls for brightness, volume, power, and input source
- Persists connection settings and last known input source per display (DataStore)

## Requirements

- Android 8.0+ (API 26)
- A running MonitorControl Remote HTTP API server on your network
- Valid bearer token for the server
- JDK 11
- Android Studio version that supports AGP `8.13.2` and Kotlin `2.0.21`

## Quick start

1. Open this project in Android Studio.
2. Let Gradle sync complete.
3. Run the `app` configuration on a device or emulator.
4. On first launch, configure:
   - `Host` (or use auto scan)
   - `Port` (default `51423`)
   - `Bearer Token`
5. Save settings and start controlling displays.

## API endpoints used by the app

- `GET /api/v1/health`
- `GET /api/v1/displays`
- `POST /api/v1/displays/brightness`
- `POST /api/v1/displays/{id}/brightness`
- `POST /api/v1/displays/volume`
- `POST /api/v1/displays/{id}/volume`
- `POST /api/v1/displays/power`
- `POST /api/v1/displays/{id}/power`
- `POST /api/v1/displays/{id}/input`

## Build and test

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## Project structure

```text
app/src/main/java/com/demogorgon314/monitorcontrolremotecontrolandroid/
  data/
    local/       # DataStore-backed local persistence
    remote/      # Retrofit API models and network setup
    repository/  # API repository and error handling
    scan/        # LAN host scanner
  feature/
    home/        # Main screen, state, and ViewModel
    settings/    # Connection settings dialog
  ui/theme/      # Compose theme
```

## Notes

- The app currently uses HTTP (cleartext traffic is enabled in manifest).
- Network calls include bearer auth and retry handling for transient failures.

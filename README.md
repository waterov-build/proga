# ENDURO PLUS

Garmin Connect IQ watch application + Android companion for enduro racing.

Participants ride the same routes; their GPS tracks, checkpoint times, and
accumulated scores are compared on the Android companion app in real time
via Bluetooth Low Energy (BLE).

---

## Repository structure

```
proga/
├── garmin-watch-app/          # Monkey C application for Garmin watches
│   ├── manifest.xml           # App metadata, device targets, permissions
│   ├── monkey.jungle          # Build configuration (Enduro, Fenix 7, FR9xx)
│   ├── source/
│   │   ├── EnduroPlusApp.mc   # Main app: HUD, model, BLE delegate, FIT export
│   │   └── TrackRecorder.mc   # GPS polyline recorder (ring-buffer, 1000 pts)
│   └── resources/
│       └── layout.xml         # MainLayout: timer / score / speed / status labels
│
└── android-companion/         # Kotlin Android companion app
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle/libs.versions.toml
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            └── java/com/enduroplus/companion/
                ├── EnduroPlusApplication.kt  # Application class
                ├── BleProfile.kt             # UUID constants + result parser
                ├── BleManager.kt             # BLE scan / connect / GATT I/O
                ├── ResultsViewModel.kt       # Leaderboard StateFlow
                └── MainActivity.kt          # Leaderboard UI + scan button
```

---

## Garmin watch app

### Supported devices
Garmin Enduro, Enduro 2, Fenix 7 / 7S / 7X, Forerunner 945 / 955 / 965

### Build
Requires [Garmin Connect IQ SDK](https://developer.garmin.com/connect-iq/) 4.x.

```bash
# Using the Connect IQ command-line build tool:
cd garmin-watch-app
monkeyc -f monkey.jungle -o EnduroPlus.prg -y developer_key.der
```

### Watch UI

| Zone | Content | Colour |
|------|---------|--------|
| Top  | Race timer MM:SS | White |
| Upper-middle | Total score (pts) | Yellow |
| Lower-middle | Current speed (km/h) | Blue |
| Bottom | Next checkpoint name / FINISHED | Green |
| Footer | GPS accuracy (GOOD / OK / POOR) | Light grey |

### Race flow
1. Press **SELECT** → race starts, GPS & sensor recording begins.
2. Ride to each checkpoint; score is awarded automatically when within 20 m.
3. Press **SELECT** again → race ends, FIT activity saved to the watch.
4. FIT file syncs to Garmin Connect; custom `EnduroScore` field appears in
   the activity data.

### Scoring formula
```
score_per_cp = max(0, BASE_CHECKPOINT_SCORE + timeBonus)

timeBonus = clamp(parSec / elapsedSec, 0.5, 2.0) × 100 − 100
  → ratio = 2.0  →  +100 pts  (twice as fast as par)
  → ratio = 1.0  →    0 pts   (exactly on par)
  → ratio = 0.5  →  −50 pts   (twice as slow as par)
```

### BLE GATT profile

| Characteristic | UUID | Properties | Description |
|----------------|------|------------|-------------|
| SCORE    | `12340001-…` | Read / Notify | Serialised results |
| TRACK    | `12340002-…` | Read          | GPS polyline snapshot |
| CP_LIST  | `12340003-…` | Write         | Checkpoint list from companion |
| STATUS   | `12340004-…` | Read / Notify | Live status string |

#### `CP_LIST` payload format
One checkpoint per line: `name,latitude,longitude,parSec`  
Example:
```
CP1-Start,55.751244,37.618423,120
CP2-Rock,55.752100,37.619800,150
CP5-Finish,55.756000,37.624000,200
```
The `parSec` field carries the expected (par) time for that segment in seconds and
is used by the watch scoring formula.  It is optional for backwards compatibility;
if omitted the watch defaults to 120 s.

---

## Android companion app

Minimum SDK: **26 (Android 8.0)**  Target SDK: **34**

### Features
- Scans for nearby ENDURO PLUS watches filtered by service UUID.
- Auto-connects and subscribes to SCORE and STATUS notifications.
- Live leaderboard sorted by total score.
- **Course editor** — define checkpoints (name, lat/lon, par time in seconds),
  save courses to internal storage, and push the active course to all connected
  watches with one tap.  Par times are included in the `CP_LIST` BLE payload so
  the watch uses real segment times instead of the default placeholder.

### Build
```bash
cd android-companion
./gradlew assembleDebug
```

### Permissions
`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+) or `ACCESS_FINE_LOCATION`
(API ≤ 30).

---

## TODO / next steps
- Replace hardcoded checkpoint coordinates with a FIT course file loader.
- ~~Add map view to the companion (render track polylines per participant).~~ ✅ Done — `MapActivity` (OSMDroid).
- ~~Add FIT file export from companion to share full session data.~~ ✅ Done — JSON session export via Android share sheet.

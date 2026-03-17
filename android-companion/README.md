# Android Companion App

Kotlin + Jetpack Compose companion app for syncing Garmin data to the backend.

## Responsibilities
- Pair with Garmin device over BLE (Bluetooth Low Energy)
- Receive FIT files from Garmin via ConnectIQ SDK
- Upload FIT files to the backend `/api/v1/sync/upload` endpoint
- Download segment packs and push them to the Garmin device

## Setup
1. Open in Android Studio Hedgehog or later
2. Configure `local.properties` with your SDK path
3. Run on a physical device (BLE required)

# Data Flow

## Activity Upload
```
Garmin device → (FIT file via BLE) → Android Companion
Android Companion → POST /api/v1/sync/upload → Backend
Backend → fit_decode_service → activity_ingest_service
activity_ingest_service → attempt_validation_service
attempt_validation_service → leaderboard_service → PostgreSQL
```

## Segment Pack Distribution
```
Organiser → Admin Panel → POST /api/v1/events/{id}/pack
Backend → segment_pack_service → builds compressed SegmentPack
SegmentPack → stored in DB → delivered via GET /api/v1/sync/pack/{event_id}
Android Companion → pulls pack → pushes to Garmin via BLE
Garmin → LocalStore → available in SegmentSelectView
```

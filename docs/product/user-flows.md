# User Flows

## Rider – Race Day
1. Rider opens Garmin app → HomeView shows active event
2. Selects segment pack → SegmentSelectView
3. Starts ride → RideView begins recording
4. Approaches segment start geofence → SegmentEngine starts timer
5. Crosses segment finish → timer stops, time stored in LocalStore
6. End of ride → SyncStatusView shows upload pending
7. Opens Android companion → BLE sync transfers FIT file
8. Companion uploads to backend → leaderboard updated

## Organiser – Event Setup
1. Logs into admin panel
2. Creates event, defines segments on map
3. Publishes event pack → backend generates SegmentPack
4. Downloads QR code / share link for riders
5. Monitors live leaderboard during race

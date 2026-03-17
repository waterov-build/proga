# MVP 1.1 Scope

## In Scope
- Garmin app: segment pack download, ride recording, segment detection, result upload
- Android companion: BLE sync, manual upload to backend
- Backend: auth, segments CRUD, activity ingest, leaderboard read
- Admin panel: event & segment management, live leaderboard view

## Out of Scope (post-MVP)
- Live tracking map for spectators
- Automated DQ appeals via AI agent
- Edge device support (only Fenix & Enduro in MVP)

## Success Criteria
1. A rider finishes a timed segment → time appears on leaderboard within 60 s
2. Full event setup by organiser in < 30 min using admin panel
3. App runs on Fenix 7 & Enduro 3 without crashes over a 4-hour ride

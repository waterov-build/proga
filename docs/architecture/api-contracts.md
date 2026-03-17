# API Contracts

Base URL: `https://api.enduro.example.com/api/v1`

## Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | /auth/register | Register new user |
| POST | /auth/token | Login, returns JWT |
| POST | /auth/refresh | Refresh access token |

## Segments
| Method | Path | Description |
|--------|------|-------------|
| GET | /segments | List segments |
| POST | /segments | Create segment (organiser) |
| GET | /segments/{id} | Get segment detail |
| PATCH | /segments/{id} | Update segment |
| DELETE | /segments/{id} | Delete segment |

## Sync
| Method | Path | Description |
|--------|------|-------------|
| POST | /sync/upload | Upload FIT file from companion |
| GET | /sync/pack/{event_id} | Download segment pack |

## Leaderboards
| Method | Path | Description |
|--------|------|-------------|
| GET | /leaderboards/{segment_id} | Get segment leaderboard |
| GET | /leaderboards/event/{event_id} | Get event overall leaderboard |

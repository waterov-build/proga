# Enduro Garmin Platform

A multi-component platform for enduro segment racing on Garmin devices.

## Components

| Component | Description |
|-----------|-------------|
| `garmin-app/` | Connect IQ app (MonkeyC) for Fenix/Enduro/Edge devices |
| `android-companion/` | Android companion app for sync & management |
| `backend/` | FastAPI backend – segments, activities, leaderboards |
| `agent-service/` | AI agent service for race ops, segment building & support |
| `admin-panel/` | React admin panel for event organisers |

## Quick start

```bash
cp .env.example .env
docker-compose up -d
```

See `docs/` for full documentation.

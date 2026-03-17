# Deployment Guide

## Prerequisites
- Docker 24+ and Docker Compose v2
- A PostgreSQL 16 instance (or use the bundled one)

## Steps
```bash
git clone <repo>
cd enduro-garmin-platform
cp .env.example .env
# Edit .env with real secrets
docker-compose up -d
```

## Database migrations
```bash
docker-compose exec backend alembic upgrade head
```

## Health checks
- Backend: `GET http://localhost:8000/health`
- Agent service: `GET http://localhost:8001/health`

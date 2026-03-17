# Incident Playbook

## Leaderboard not updating
1. Check `docker-compose logs backend` for errors
2. Check Redis queue: `redis-cli llen activity_ingest_queue`
3. If queue stuck, restart workers: `docker-compose restart backend`

## Garmin app crashes on start
1. Check Connect IQ simulator logs
2. Verify segment pack format matches `SegmentPack.mc` schema
3. Roll back to previous app version via Connect IQ Store

## Database connection refused
1. `docker-compose ps db` — ensure container is running
2. Check `POSTGRES_*` env vars match DB configuration
3. Run `docker-compose exec db pg_isready`

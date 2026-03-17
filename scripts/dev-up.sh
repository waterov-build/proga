#!/usr/bin/env bash
set -euo pipefail

echo "Starting Enduro Garmin Platform dev environment..."

if [ ! -f .env ]; then
  cp .env.example .env
  echo ".env created from .env.example – please review and update secrets."
fi

docker-compose up -d db redis
echo "Waiting for PostgreSQL to be ready..."
until docker-compose exec -T db pg_isready -U "${POSTGRES_USER:-enduro}" > /dev/null 2>&1; do
  sleep 1
done
echo "PostgreSQL is ready."

docker-compose up -d backend agent-service admin-panel
echo "All services started."
echo "  Backend:       http://localhost:8000/docs"
echo "  Agent service: http://localhost:8001/docs"
echo "  Admin panel:   http://localhost:3000"

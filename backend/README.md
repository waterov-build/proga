# Backend Service

FastAPI backend for the Enduro Garmin Platform.

## Setup

```bash
pip install -r requirements.txt
alembic upgrade head
uvicorn app.main:app --reload
```

## Structure
- `app/api/` – route handlers
- `app/services/` – business logic
- `app/db/` – SQLAlchemy models, migrations, repositories
- `app/schemas/` – Pydantic schemas
- `app/workers/` – Celery workers

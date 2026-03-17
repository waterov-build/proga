from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import auth, users, devices, segments, events, activities, leaderboards, sync
from app.core.config import settings

app = FastAPI(
    title="Enduro Garmin Platform API",
    version="1.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api/v1/auth", tags=["auth"])
app.include_router(users.router, prefix="/api/v1/users", tags=["users"])
app.include_router(devices.router, prefix="/api/v1/devices", tags=["devices"])
app.include_router(segments.router, prefix="/api/v1/segments", tags=["segments"])
app.include_router(events.router, prefix="/api/v1/events", tags=["events"])
app.include_router(activities.router, prefix="/api/v1/activities", tags=["activities"])
app.include_router(leaderboards.router, prefix="/api/v1/leaderboards", tags=["leaderboards"])
app.include_router(sync.router, prefix="/api/v1/sync", tags=["sync"])


@app.get("/health", tags=["health"])
async def health() -> dict:
    return {"status": "ok"}

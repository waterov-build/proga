from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import auth, sync, segments, leaderboards
from app.db.models import Base
from app.db.session import engine

Base.metadata.create_all(bind=engine)

app = FastAPI(title="Enduro Garmin Platform API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router,         prefix="/auth",         tags=["auth"])
app.include_router(sync.router,         prefix="/sync",         tags=["sync"])
app.include_router(segments.router,     prefix="/segments",     tags=["segments"])
app.include_router(leaderboards.router, prefix="/leaderboards", tags=["leaderboards"])


@app.get("/health")
def health():
    return {"status": "ok"}

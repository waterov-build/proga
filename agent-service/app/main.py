from fastapi import FastAPI

from app.agents.raceops_agent import router as raceops_router
from app.agents.segment_builder_agent import router as segment_builder_router
from app.agents.support_agent import router as support_router

app = FastAPI(title="Enduro Agent Service", version="1.1.0")

app.include_router(raceops_router, prefix="/api/v1/agents/raceops", tags=["raceops"])
app.include_router(segment_builder_router, prefix="/api/v1/agents/segment-builder", tags=["segment-builder"])
app.include_router(support_router, prefix="/api/v1/agents/support", tags=["support"])


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}

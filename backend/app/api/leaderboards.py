from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.services.leaderboard_service import LeaderboardService

router = APIRouter()


@router.get("/{segment_id}")
async def get_segment_leaderboard(segment_id: str, db: AsyncSession = Depends(get_db)) -> list:
    svc = LeaderboardService(db)
    return await svc.get_for_segment(segment_id)

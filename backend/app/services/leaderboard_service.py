from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Attempt, User


class LeaderboardService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def get_for_segment(self, segment_id: str) -> list[dict]:
        result = await self.db.execute(
            select(Attempt, User)
            .join(User, Attempt.user_id == User.id)
            .where(Attempt.segment_id == segment_id)
            .order_by(Attempt.time_ms)
        )
        rows = result.all()
        return [
            {"rank": i + 1, "user_email": user.email, "time_ms": attempt.time_ms}
            for i, (attempt, user) in enumerate(rows)
        ]

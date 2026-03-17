from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Segment
from app.schemas.segments import SegmentCreate


class SegmentRepository:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def list_all(self) -> list[Segment]:
        result = await self.db.execute(select(Segment))
        return list(result.scalars().all())

    async def create(self, payload: SegmentCreate) -> Segment:
        segment = Segment(**payload.model_dump())
        self.db.add(segment)
        await self.db.commit()
        await self.db.refresh(segment)
        return segment

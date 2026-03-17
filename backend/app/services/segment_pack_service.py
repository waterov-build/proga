import json
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.db.models import Event, Segment


class SegmentPackService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def build_pack(self, event_id: str) -> dict:
        event_result = await self.db.execute(select(Event).where(Event.id == event_id))
        event = event_result.scalar_one_or_none()
        if event is None:
            raise ValueError(f"Event {event_id} not found")

        seg_result = await self.db.execute(select(Segment).where(Segment.event_id == event_id))
        segments = seg_result.scalars().all()

        return {
            "event_id": event_id,
            "event_name": event.name,
            "version": 1,
            "segments": [
                {
                    "id": s.id,
                    "name": s.name,
                    "start_lat": s.start_lat,
                    "start_lon": s.start_lon,
                    "finish_lat": s.finish_lat,
                    "finish_lon": s.finish_lon,
                    "distance_m": s.distance_m,
                }
                for s in segments
            ],
        }

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Event
from app.schemas.events import EventCreate


class EventRepository:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def list_all(self) -> list[Event]:
        result = await self.db.execute(select(Event))
        return list(result.scalars().all())

    async def create(self, payload: EventCreate) -> Event:
        event = Event(**payload.model_dump())
        self.db.add(event)
        await self.db.commit()
        await self.db.refresh(event)
        return event

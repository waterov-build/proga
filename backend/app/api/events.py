from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.schemas.events import EventCreate, EventRead
from app.db.repositories.event_repository import EventRepository

router = APIRouter()


@router.get("/", response_model=list[EventRead])
async def list_events(db: AsyncSession = Depends(get_db)) -> list:
    repo = EventRepository(db)
    return await repo.list_all()


@router.post("/", response_model=EventRead, status_code=status.HTTP_201_CREATED)
async def create_event(payload: EventCreate, db: AsyncSession = Depends(get_db)) -> EventRead:
    repo = EventRepository(db)
    event = await repo.create(payload)
    return EventRead.model_validate(event)

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.schemas.segments import SegmentCreate, SegmentRead
from app.db.repositories.segment_repository import SegmentRepository

router = APIRouter()


@router.get("/", response_model=list[SegmentRead])
async def list_segments(db: AsyncSession = Depends(get_db)) -> list:
    repo = SegmentRepository(db)
    return await repo.list_all()


@router.post("/", response_model=SegmentRead, status_code=status.HTTP_201_CREATED)
async def create_segment(payload: SegmentCreate, db: AsyncSession = Depends(get_db)) -> SegmentRead:
    repo = SegmentRepository(db)
    segment = await repo.create(payload)
    return SegmentRead.model_validate(segment)

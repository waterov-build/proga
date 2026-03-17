from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List

from app.db.models import Segment, SegmentPack, User
from app.db.session import get_db
from app.schemas import SegmentOut, SegmentPackOut
from app.api.auth import get_current_user

router = APIRouter()


@router.get("", response_model=List[SegmentOut])
def list_segments(
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
):
    return db.query(Segment).all()


@router.get("/packs", response_model=List[SegmentPackOut])
def list_packs(
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
):
    return db.query(SegmentPack).all()

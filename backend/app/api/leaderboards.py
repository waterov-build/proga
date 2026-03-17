from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List

from app.db.models import Attempt, Segment, User
from app.db.session import get_db
from app.schemas import LeaderboardEntry, LeaderboardResponse
from app.api.auth import get_current_user
from sqlalchemy import func

router = APIRouter()


@router.get("/{segment_id}", response_model=LeaderboardResponse)
def get_leaderboard(
    segment_id: int,
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
):
    segment = db.query(Segment).filter(Segment.id == segment_id).first()
    if not segment:
        raise HTTPException(status_code=404, detail="Segment not found")

    # Best attempt per user
    subq = (
        db.query(
            Attempt.user_id,
            func.min(Attempt.elapsed_ms).label("best_ms"),
        )
        .filter(Attempt.segment_id == segment_id, Attempt.is_valid == True)
        .group_by(Attempt.user_id)
        .subquery()
    )

    rows = (
        db.query(User.username, subq.c.best_ms)
        .join(subq, User.id == subq.c.user_id)
        .order_by(subq.c.best_ms)
        .all()
    )

    entries = [
        LeaderboardEntry(rank=idx + 1, username=row.username, elapsed_ms=row.best_ms)
        for idx, row in enumerate(rows)
    ]

    return LeaderboardResponse(
        segment_id   = segment_id,
        segment_name = segment.name,
        entries      = entries,
    )

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db.models import Activity, Attempt, Segment, User
from app.db.session import get_db
from app.schemas import (
    ActivityUploadRequest, ActivityUploadResponse, LastResultResponse
)
from app.api.auth import get_current_user
from app.services.activity_ingest import ingest_activity
import json

router = APIRouter()


@router.post("/activity", response_model=ActivityUploadResponse)
def upload_activity(
    body: ActivityUploadRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    activity, attempts = ingest_activity(db, current_user, body.track)
    return ActivityUploadResponse(activity_id=activity.id, attempts=len(attempts))


@router.get("/last-result", response_model=LastResultResponse)
def last_result(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    attempt = (
        db.query(Attempt)
        .filter(Attempt.user_id == current_user.id, Attempt.is_valid == True)
        .order_by(Attempt.created_at.desc())
        .first()
    )
    if attempt is None:
        raise HTTPException(status_code=404, detail="No attempts found")

    rank = (
        db.query(Attempt)
        .filter(
            Attempt.segment_id == attempt.segment_id,
            Attempt.is_valid   == True,
            Attempt.elapsed_ms <= attempt.elapsed_ms,
        )
        .count()
    )
    best = (
        db.query(Attempt)
        .filter(Attempt.segment_id == attempt.segment_id, Attempt.is_valid == True)
        .order_by(Attempt.elapsed_ms)
        .first()
    )
    return LastResultResponse(
        segment_id   = attempt.segment_id,
        segment_name = attempt.segment.name,
        elapsed_ms   = attempt.elapsed_ms,
        rank         = rank,
        best_time_ms = best.elapsed_ms if best else attempt.elapsed_ms,
    )

from __future__ import annotations
import json
import math
from typing import List, Tuple

from sqlalchemy.orm import Session

from app.db.models import Activity, Attempt, Segment, User
from app.schemas import TrackPoint
from app.services.attempt_validation import validate_attempt


def ingest_activity(
    db: Session,
    user: User,
    track: List[TrackPoint],
) -> Tuple[Activity, List[Attempt]]:
    """Persist a raw GPS track and extract segment attempts from it."""

    raw_json = json.dumps([pt.model_dump() for pt in track])
    activity = Activity(user_id=user.id, raw_track=raw_json)
    db.add(activity)
    db.flush()

    segments: List[Segment] = db.query(Segment).all()
    attempts: List[Attempt] = []

    for segment in segments:
        result = _match_segment(track, segment)
        if result is None:
            continue

        elapsed_ms, is_valid, reason = validate_attempt(track, segment, result)
        attempt = Attempt(
            user_id       = user.id,
            activity_id   = activity.id,
            segment_id    = segment.id,
            elapsed_ms    = elapsed_ms,
            is_valid      = is_valid,
            invalid_reason= reason,
        )
        db.add(attempt)
        attempts.append(attempt)

    db.commit()
    db.refresh(activity)
    return activity, attempts


def _haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6_371_000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi  = math.radians(lat2 - lat1)
    dlam  = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def _match_segment(
    track: List[TrackPoint], segment: Segment, threshold_m: float = 20.0
) -> Tuple[int, int] | None:
    """Return (start_idx, end_idx) of the track points nearest to segment ends."""
    start_idx = end_idx = None

    for i, pt in enumerate(track):
        if start_idx is None:
            d = _haversine_m(pt.lat, pt.lon, segment.start_lat, segment.start_lon)
            if d <= threshold_m:
                start_idx = i
        else:
            d = _haversine_m(pt.lat, pt.lon, segment.end_lat, segment.end_lon)
            if d <= threshold_m:
                end_idx = i
                break

    if start_idx is None or end_idx is None or end_idx <= start_idx:
        return None
    return start_idx, end_idx

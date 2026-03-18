from __future__ import annotations
from typing import List, Optional, Tuple

from app.db.models import Segment
from app.schemas import TrackPoint

MIN_POINTS = 3
MAX_GAP_SECONDS = 30


def validate_attempt(
    track: List[TrackPoint],
    segment: Segment,
    indices: Tuple[int, int],
) -> Tuple[int, bool, Optional[str]]:
    """
    Validate a matched attempt slice and return (elapsed_ms, is_valid, reason).
    """
    start_idx, end_idx = indices
    slice_ = track[start_idx : end_idx + 1]

    if len(slice_) < MIN_POINTS:
        return 0, False, f"Too few GPS points ({len(slice_)} < {MIN_POINTS})"

    # Check for large time gaps (dropped signal)
    for i in range(1, len(slice_)):
        gap = slice_[i].time - slice_[i - 1].time
        if gap > MAX_GAP_SECONDS:
            return 0, False, f"GPS gap of {gap}s between points {start_idx+i-1} and {start_idx+i}"

    elapsed_ms = (slice_[-1].time - slice_[0].time) * 1000
    if elapsed_ms <= 0:
        return 0, False, "Non-positive elapsed time"

    return elapsed_ms, True, None

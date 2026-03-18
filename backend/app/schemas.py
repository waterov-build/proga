from __future__ import annotations
from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, EmailStr


# ---------- Auth ----------

class UserCreate(BaseModel):
    username: str
    email: EmailStr
    password: str


class UserOut(BaseModel):
    id: int
    username: str
    email: str
    is_active: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class Token(BaseModel):
    access_token: str
    token_type: str


class TokenData(BaseModel):
    username: Optional[str] = None


# ---------- Segments ----------

class SegmentOut(BaseModel):
    id: int
    name: str
    start_lat: float
    start_lon: float
    end_lat: float
    end_lon: float
    length_m: Optional[float] = None

    model_config = {"from_attributes": True}


class SegmentPackOut(BaseModel):
    id: int
    name: str
    description: Optional[str] = None
    segments: List[SegmentOut] = []

    model_config = {"from_attributes": True}


# ---------- Sync ----------

class TrackPoint(BaseModel):
    lat: float
    lon: float
    time: int          # unix timestamp


class ActivityUploadRequest(BaseModel):
    track: List[TrackPoint]


class ActivityUploadResponse(BaseModel):
    activity_id: int
    attempts: int


class LastResultResponse(BaseModel):
    segment_id: int
    segment_name: str
    elapsed_ms: int
    rank: int
    best_time_ms: int


# ---------- Leaderboards ----------

class LeaderboardEntry(BaseModel):
    rank: int
    username: str
    elapsed_ms: int

    model_config = {"from_attributes": True}


class LeaderboardResponse(BaseModel):
    segment_id: int
    segment_name: str
    entries: List[LeaderboardEntry]

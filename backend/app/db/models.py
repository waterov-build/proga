from datetime import datetime, timezone
from sqlalchemy import (
    Column, Integer, String, Float, DateTime, ForeignKey, Boolean, Text
)
from sqlalchemy.orm import relationship, DeclarativeBase


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"

    id            = Column(Integer, primary_key=True, index=True)
    username      = Column(String, unique=True, index=True, nullable=False)
    email         = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)
    is_active     = Column(Boolean, default=True)
    created_at    = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    activities    = relationship("Activity", back_populates="user")


class SegmentPack(Base):
    __tablename__ = "segment_packs"

    id          = Column(Integer, primary_key=True, index=True)
    name        = Column(String, nullable=False)
    description = Column(Text)
    created_at  = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    segments    = relationship("Segment", back_populates="pack")


class Segment(Base):
    __tablename__ = "segments"

    id          = Column(Integer, primary_key=True, index=True)
    pack_id     = Column(Integer, ForeignKey("segment_packs.id"))
    name        = Column(String, nullable=False)
    start_lat   = Column(Float, nullable=False)
    start_lon   = Column(Float, nullable=False)
    end_lat     = Column(Float, nullable=False)
    end_lon     = Column(Float, nullable=False)
    length_m    = Column(Float)
    created_at  = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    pack        = relationship("SegmentPack", back_populates="segments")
    attempts    = relationship("Attempt", back_populates="segment")


class Activity(Base):
    __tablename__ = "activities"

    id          = Column(Integer, primary_key=True, index=True)
    user_id     = Column(Integer, ForeignKey("users.id"))
    raw_track   = Column(Text)          # JSON-encoded list of {lat, lon, time}
    recorded_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    user        = relationship("User", back_populates="activities")
    attempts    = relationship("Attempt", back_populates="activity")


class Attempt(Base):
    __tablename__ = "attempts"

    id          = Column(Integer, primary_key=True, index=True)
    user_id     = Column(Integer, ForeignKey("users.id"))
    activity_id = Column(Integer, ForeignKey("activities.id"))
    segment_id  = Column(Integer, ForeignKey("segments.id"))
    elapsed_ms  = Column(Integer, nullable=False)
    is_valid    = Column(Boolean, default=True)
    invalid_reason = Column(String)
    created_at  = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    activity    = relationship("Activity", back_populates="attempts")
    segment     = relationship("Segment",  back_populates="attempts")

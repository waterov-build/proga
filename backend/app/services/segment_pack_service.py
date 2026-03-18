from __future__ import annotations
from typing import List, Optional

from sqlalchemy.orm import Session

from app.db.models import Segment, SegmentPack
from app.schemas import SegmentOut, SegmentPackOut


def create_pack(
    db: Session,
    name: str,
    description: Optional[str],
    segments_data: List[dict],
) -> SegmentPack:
    pack = SegmentPack(name=name, description=description)
    db.add(pack)
    db.flush()

    for s in segments_data:
        seg = Segment(
            pack_id   = pack.id,
            name      = s["name"],
            start_lat = s["start_lat"],
            start_lon = s["start_lon"],
            end_lat   = s["end_lat"],
            end_lon   = s["end_lon"],
            length_m  = s.get("length_m"),
        )
        db.add(seg)

    db.commit()
    db.refresh(pack)
    return pack


def get_pack(db: Session, pack_id: int) -> Optional[SegmentPack]:
    return db.query(SegmentPack).filter(SegmentPack.id == pack_id).first()


def list_packs(db: Session) -> List[SegmentPack]:
    return db.query(SegmentPack).all()

from pydantic import BaseModel


class SegmentCreate(BaseModel):
    event_id: str
    name: str
    start_lat: float
    start_lon: float
    finish_lat: float
    finish_lon: float
    distance_m: float | None = None


class SegmentRead(BaseModel):
    model_config = {"from_attributes": True}

    id: str
    event_id: str
    name: str
    start_lat: float
    start_lon: float
    finish_lat: float
    finish_lon: float
    distance_m: float | None

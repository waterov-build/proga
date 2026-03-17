from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class SegmentBuilderRequest(BaseModel):
    gpx_xml: str
    event_id: str


class SegmentBuilderResponse(BaseModel):
    segments: list[dict]


@router.post("/build", response_model=SegmentBuilderResponse)
async def build_segments(payload: SegmentBuilderRequest) -> SegmentBuilderResponse:
    # TODO: parse GPX and invoke segment builder tool chain
    return SegmentBuilderResponse(segments=[])

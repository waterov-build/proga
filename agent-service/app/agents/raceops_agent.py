from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class RaceOpsRequest(BaseModel):
    event_id: str
    query: str


class RaceOpsResponse(BaseModel):
    result: str


@router.post("/run", response_model=RaceOpsResponse)
async def run_raceops_agent(payload: RaceOpsRequest) -> RaceOpsResponse:
    # TODO: integrate LangChain agent with tools
    return RaceOpsResponse(result=f"[raceops stub] event={payload.event_id} query={payload.query}")

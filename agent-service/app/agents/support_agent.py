from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class SupportRequest(BaseModel):
    user_id: str
    message: str


class SupportResponse(BaseModel):
    reply: str


@router.post("/chat", response_model=SupportResponse)
async def support_chat(payload: SupportRequest) -> SupportResponse:
    # TODO: integrate LangChain conversational agent
    return SupportResponse(reply="[support stub] " + payload.message)

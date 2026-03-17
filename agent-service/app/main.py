from fastapi import FastAPI
from pydantic import BaseModel
from app.raceops_agent import RaceOpsAgent

app = FastAPI(title="Enduro RaceOps Agent", version="1.0.0")
agent = RaceOpsAgent()


class AgentRequest(BaseModel):
    query: str
    context: dict = {}


class AgentResponse(BaseModel):
    answer: str
    tool_calls: list = []


@app.post("/agent/query", response_model=AgentResponse)
async def query_agent(req: AgentRequest):
    result = await agent.run(req.query, req.context)
    return result


@app.get("/health")
def health():
    return {"status": "ok"}

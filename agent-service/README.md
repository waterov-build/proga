# Agent Service

AI-powered race operations agents built on LangChain.

## Agents
- **RaceOps Agent** – monitors live leaderboard anomalies, flags disputes
- **Segment Builder Agent** – converts a GPX track into a segment definition
- **Support Agent** – answers rider queries about results and rules

## Setup
```bash
pip install -r requirements.txt
OPENAI_API_KEY=sk-... uvicorn app.main:app --port 8001 --reload
```

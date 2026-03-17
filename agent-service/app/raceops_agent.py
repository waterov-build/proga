from __future__ import annotations
import os
import json
from typing import Any, Dict, List

from openai import AsyncOpenAI

from app.tools.validate_attempt     import validate_attempt_tool, VALIDATE_ATTEMPT_SCHEMA
from app.tools.explain_invalid_attempt import explain_invalid_attempt_tool, EXPLAIN_INVALID_SCHEMA
from app.tools.build_event_report   import build_event_report_tool, BUILD_EVENT_REPORT_SCHEMA

TOOLS = [
    VALIDATE_ATTEMPT_SCHEMA,
    EXPLAIN_INVALID_SCHEMA,
    BUILD_EVENT_REPORT_SCHEMA,
]

TOOL_MAP = {
    "validate_attempt":       validate_attempt_tool,
    "explain_invalid_attempt": explain_invalid_attempt_tool,
    "build_event_report":     build_event_report_tool,
}

SYSTEM_PROMPT = """You are RaceOps, an AI assistant for enduro MTB race management.
You help race directors validate segment attempts, explain disqualifications to riders,
and generate post-event reports. Use the available tools when needed."""


class RaceOpsAgent:

    def __init__(self) -> None:
        self.client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY"))
        self.model  = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

    async def run(self, query: str, context: Dict[str, Any]) -> Dict[str, Any]:
        messages: List[Dict] = [
            {"role": "system",  "content": SYSTEM_PROMPT},
            {"role": "user",    "content": f"Context: {json.dumps(context)}\n\nQuery: {query}"},
        ]
        tool_calls_log: List[Dict] = []

        while True:
            response = await self.client.chat.completions.create(
                model    = self.model,
                messages = messages,
                tools    = TOOLS,
                tool_choice = "auto",
            )
            msg = response.choices[0].message

            if msg.tool_calls:
                messages.append(msg)
                for tc in msg.tool_calls:
                    fn_name = tc.function.name
                    fn_args = json.loads(tc.function.arguments)
                    fn      = TOOL_MAP.get(fn_name)
                    result  = await fn(**fn_args) if fn else {"error": "unknown tool"}
                    tool_calls_log.append({"tool": fn_name, "args": fn_args, "result": result})
                    messages.append({
                        "role":         "tool",
                        "tool_call_id": tc.id,
                        "content":      json.dumps(result),
                    })
            else:
                return {
                    "answer":     msg.content or "",
                    "tool_calls": tool_calls_log,
                }

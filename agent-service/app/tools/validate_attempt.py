from __future__ import annotations
import httpx
import os
from typing import Any, Dict

BACKEND_URL = os.getenv("BACKEND_URL", "http://backend:8000")

VALIDATE_ATTEMPT_SCHEMA = {
    "type": "function",
    "function": {
        "name": "validate_attempt",
        "description": (
            "Check whether a recorded segment attempt is valid. "
            "Returns is_valid flag and a reason string if invalid."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "attempt_id": {
                    "type": "integer",
                    "description": "The ID of the attempt to validate."
                },
                "token": {
                    "type": "string",
                    "description": "Bearer token for the backend API."
                }
            },
            "required": ["attempt_id", "token"]
        }
    }
}


async def validate_attempt_tool(attempt_id: int, token: str) -> Dict[str, Any]:
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            f"{BACKEND_URL}/sync/attempts/{attempt_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10.0,
        )
    if resp.status_code != 200:
        return {"error": f"Backend returned {resp.status_code}"}
    data = resp.json()
    return {
        "attempt_id":    attempt_id,
        "is_valid":      data.get("is_valid"),
        "elapsed_ms":    data.get("elapsed_ms"),
        "invalid_reason": data.get("invalid_reason"),
    }

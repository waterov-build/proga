from __future__ import annotations
import httpx
import os
from typing import Any, Dict

BACKEND_URL = os.getenv("BACKEND_URL", "http://backend:8000")

BUILD_EVENT_REPORT_SCHEMA = {
    "type": "function",
    "function": {
        "name": "build_event_report",
        "description": (
            "Fetch leaderboard data for every segment in a segment pack and "
            "compile a structured post-event report."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "pack_id": {
                    "type": "integer",
                    "description": "The segment pack ID to generate the report for."
                },
                "token": {
                    "type": "string",
                    "description": "Bearer token for the backend API."
                }
            },
            "required": ["pack_id", "token"]
        }
    }
}


async def build_event_report_tool(pack_id: int, token: str) -> Dict[str, Any]:
    headers = {"Authorization": f"Bearer {token}"}
    async with httpx.AsyncClient() as client:
        packs_resp = await client.get(
            f"{BACKEND_URL}/segments/packs",
            headers=headers,
            timeout=10.0,
        )
        if packs_resp.status_code != 200:
            return {"error": f"Could not fetch packs: {packs_resp.status_code}"}

        pack = next(
            (p for p in packs_resp.json() if p["id"] == pack_id), None
        )
        if pack is None:
            return {"error": f"Pack {pack_id} not found"}

        report_segments = []
        for segment in pack.get("segments", []):
            lb_resp = await client.get(
                f"{BACKEND_URL}/leaderboards/{segment['id']}",
                headers=headers,
                timeout=10.0,
            )
            leaderboard = lb_resp.json() if lb_resp.status_code == 200 else []
            report_segments.append({
                "segment_id":   segment["id"],
                "segment_name": segment["name"],
                "leaderboard":  leaderboard,
            })

    return {
        "pack_id":   pack_id,
        "pack_name": pack["name"],
        "segments":  report_segments,
    }

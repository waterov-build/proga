from __future__ import annotations
from typing import Any, Dict

EXPLAIN_INVALID_SCHEMA = {
    "type": "function",
    "function": {
        "name": "explain_invalid_attempt",
        "description": (
            "Generate a human-readable explanation for why a segment attempt was "
            "marked invalid. Takes the raw invalid_reason code and returns a "
            "rider-friendly message."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "invalid_reason": {
                    "type": "string",
                    "description": "The raw invalid_reason string from the attempt record."
                },
                "segment_name": {
                    "type": "string",
                    "description": "The name of the segment for context."
                }
            },
            "required": ["invalid_reason", "segment_name"]
        }
    }
}

_REASON_TEMPLATES: Dict[str, str] = {
    "Too few GPS points": (
        "Your GPS signal was too weak during the {segment_name} segment. "
        "We didn't record enough location points to confirm a valid run. "
        "Make sure your device has a clear view of the sky."
    ),
    "GPS gap": (
        "There was a significant gap in your GPS track on {segment_name}, "
        "likely due to a tunnel, dense tree cover, or a signal dropout. "
        "The attempt cannot be counted for the leaderboard."
    ),
    "Non-positive elapsed time": (
        "The recorded timestamps for {segment_name} appear inconsistent — "
        "the end time was not after the start time. "
        "This is likely a device clock issue."
    ),
}


async def explain_invalid_attempt_tool(
    invalid_reason: str, segment_name: str
) -> Dict[str, Any]:
    for key, template in _REASON_TEMPLATES.items():
        if key.lower() in invalid_reason.lower():
            return {
                "explanation": template.format(segment_name=segment_name),
                "matched_key": key,
            }
    return {
        "explanation": (
            f"Your attempt on {segment_name} was marked invalid: {invalid_reason}. "
            "Please contact a race official if you believe this is an error."
        ),
        "matched_key": None,
    }

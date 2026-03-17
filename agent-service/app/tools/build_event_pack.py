"""Tool: build_event_pack — assembles a SegmentPack payload for an event."""
from typing import Any


def build_event_pack(event: dict[str, Any], segments: list[dict[str, Any]]) -> dict:
    return {
        "event_id": event["id"],
        "event_name": event["name"],
        "version": 1,
        "segments": [
            {
                "id": s["id"],
                "name": s["name"],
                "start_lat": s["start_lat"],
                "start_lon": s["start_lon"],
                "finish_lat": s["finish_lat"],
                "finish_lon": s["finish_lon"],
                "distance_m": s.get("distance_m"),
            }
            for s in segments
        ],
    }

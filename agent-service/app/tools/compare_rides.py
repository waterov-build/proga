"""Tool: compare_rides — computes stat diff between two ride attempts."""
from typing import Any

from app.utils import fmt_ms


def compare_rides(attempt_a: dict[str, Any], attempt_b: dict[str, Any]) -> dict:
    delta_ms = attempt_a["time_ms"] - attempt_b["time_ms"]
    return {
        "delta_ms": delta_ms,
        "faster": "a" if delta_ms < 0 else "b",
        "delta_formatted": fmt_ms(abs(delta_ms)),
    }

"""Tool: compare_rides — computes stat diff between two ride attempts."""
from typing import Any


def compare_rides(attempt_a: dict[str, Any], attempt_b: dict[str, Any]) -> dict:
    delta_ms = attempt_a["time_ms"] - attempt_b["time_ms"]
    return {
        "delta_ms": delta_ms,
        "faster": "a" if delta_ms < 0 else "b",
        "delta_formatted": _fmt(abs(delta_ms)),
    }


def _fmt(ms: int) -> str:
    s = ms // 1000
    return f"{s // 60:02d}:{s % 60:02d}.{(ms % 1000) // 10:02d}"

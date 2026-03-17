"""Shared utility functions for the agent service."""


def fmt_ms(ms: int) -> str:
    """Format milliseconds as MM:SS.hh."""
    s = ms // 1000
    return f"{s // 60:02d}:{s % 60:02d}.{(ms % 1000) // 10:02d}"

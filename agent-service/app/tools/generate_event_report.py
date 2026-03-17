"""Tool: generate_event_report — creates a markdown summary report for an event."""
from typing import Any

from app.utils import fmt_ms


def generate_event_report(event: dict[str, Any], leaderboards: list[dict[str, Any]]) -> str:
    lines = [f"# Event Report: {event['name']}\n"]
    for lb in leaderboards:
        lines.append(f"## Segment: {lb['segment_name']}\n")
        lines.append("| Rank | Rider | Time |")
        lines.append("|------|-------|------|")
        for entry in lb.get("entries", []):
            lines.append(f"| {entry['rank']} | {entry['user_email']} | {fmt_ms(entry['time_ms'])} |")
        lines.append("")
    return "\n".join(lines)

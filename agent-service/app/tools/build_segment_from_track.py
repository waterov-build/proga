"""Tool: build_segment_from_track — converts a GPX track to segment definitions."""
import xml.etree.ElementTree as ET
from typing import Any


def build_segment_from_track(gpx_xml: str) -> list[dict[str, Any]]:
    """Parse a GPX track and return start/finish coordinates for each segment candidate."""
    ns = {"gpx": "http://www.topografix.com/GPX/1/1"}
    root = ET.fromstring(gpx_xml)
    segments = []
    for trk in root.findall("gpx:trk", ns):
        for trkseg in trk.findall("gpx:trkseg", ns):
            points = trkseg.findall("gpx:trkpt", ns)
            if len(points) < 2:
                continue
            start = points[0]
            finish = points[-1]
            segments.append({
                "start_lat": float(start.attrib["lat"]),
                "start_lon": float(start.attrib["lon"]),
                "finish_lat": float(finish.attrib["lat"]),
                "finish_lon": float(finish.attrib["lon"]),
            })
    return segments

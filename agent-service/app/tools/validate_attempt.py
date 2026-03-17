"""Tool: validate_attempt — checks whether an attempt GPS trace satisfies geofence rules."""
import math
from typing import Any


def validate_attempt(attempt: dict[str, Any], segment: dict[str, Any], radius_m: float = 30.0) -> dict:
    gps_points = attempt.get("gps_points", [])
    if not gps_points:
        return {"valid": False, "reason": "No GPS data"}

    def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        R = 6_371_000.0
        phi1, phi2 = math.radians(lat1), math.radians(lat2)
        dphi = math.radians(lat2 - lat1)
        dl = math.radians(lon2 - lon1)
        a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dl / 2) ** 2
        return 2 * R * math.asin(math.sqrt(a))

    start_ok = any(
        haversine(p["lat"], p["lon"], segment["start_lat"], segment["start_lon"]) <= radius_m
        for p in gps_points
    )
    finish_ok = any(
        haversine(p["lat"], p["lon"], segment["finish_lat"], segment["finish_lon"]) <= radius_m
        for p in gps_points
    )

    if not start_ok:
        return {"valid": False, "reason": "Start geofence not triggered"}
    if not finish_ok:
        return {"valid": False, "reason": "Finish geofence not triggered"}
    return {"valid": True, "reason": None}

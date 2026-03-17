import math
from dataclasses import dataclass
from typing import Any


@dataclass
class ValidationResult:
    valid: bool
    reason: str | None = None


class AttemptValidationService:
    START_RADIUS_M: float = 30.0
    FINISH_RADIUS_M: float = 30.0

    def validate(self, attempt: dict[str, Any], segment: dict[str, Any]) -> ValidationResult:
        gps_points: list[dict] = attempt.get("gps_points", [])
        if not gps_points:
            return ValidationResult(valid=False, reason="No GPS data")

        start_dist = min(
            self._haversine(p["lat"], p["lon"], segment["start_lat"], segment["start_lon"])
            for p in gps_points
        )
        finish_dist = min(
            self._haversine(p["lat"], p["lon"], segment["finish_lat"], segment["finish_lon"])
            for p in gps_points
        )

        if start_dist > self.START_RADIUS_M:
            return ValidationResult(valid=False, reason="Start geofence not triggered")
        if finish_dist > self.FINISH_RADIUS_M:
            return ValidationResult(valid=False, reason="Finish geofence not triggered")

        return ValidationResult(valid=True)

    @staticmethod
    def _haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        R = 6_371_000.0
        phi1, phi2 = math.radians(lat1), math.radians(lat2)
        dphi = math.radians(lat2 - lat1)
        dlambda = math.radians(lon2 - lon1)
        a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
        return 2 * R * math.asin(math.sqrt(a))
